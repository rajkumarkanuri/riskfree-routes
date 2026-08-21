package com.riskfreeroutes.app.ui.navigation;

import android.app.Application;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.maps.NavigationTracker;
import com.riskfreeroutes.app.model.Route;
import com.riskfreeroutes.app.repository.ActiveRouteRepository;
import com.riskfreeroutes.app.repository.GuardianRepository;
import com.riskfreeroutes.app.repository.JourneyHistoryRepository;
import com.riskfreeroutes.app.repository.SettingsRepository;
import com.riskfreeroutes.app.repository.TrustedContactRepository;
import com.riskfreeroutes.app.repository.LiveShareRepository;
import com.riskfreeroutes.app.service.SmsHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NavigationViewModel — Manages all state and logic for the Navigation screen.
 *
 * ── OVERVIEW ─────────────────────────────────────────────────────────────────
 * This ViewModel:
 *   1. Receives GPS updates every 3 seconds from NavigationTracker
 *   2. Calculates remaining distance and ETA, posts them to LiveData
 *   3. Detects arrival (distance < 50m to destination)
 *   4. On arrival: runs the full Safe Arrival flow (Firestore update + SMS + guardian log)
 *   5. Detects long stops (stationary for 30+ seconds) → triggers emergency dialog
 *
 * ── WHY ANDROIDVIEWMODEL (not ViewModel)? ────────────────────────────────────
 * We need the Application context to call SmsHelper (which uses SmsManager).
 * AndroidViewModel provides `getApplication()` safely. Regular ViewModel does not.
 *
 * ── ARRIVAL DETECTION ────────────────────────────────────────────────────────
 * Every GPS tick passes through onLocationUpdated(). When distanceRemainingMeters < 50,
 * we consider the user "arrived". We guard this with `hasArrived` (boolean flag) so
 * the arrival flow only runs ONCE — otherwise it would trigger on every subsequent
 * GPS update that also has distance < 50m (which is ALL of them once you're there).
 *
 * ── SAFE ARRIVAL FLOW ────────────────────────────────────────────────────────
 * When arrival is detected, the following chain runs (in order):
 *   Step 1: Stop GPS tracking (no more battery drain)
 *   Step 2: Update journey_history doc: status="completed", endTimestamp, distance
 *           → This also atomically updates user's totalJourneys and avgSafetyScore
 *   Step 3: Log a guardian event: "destination_reached"
 *   Step 4: Read Settings to check smsAlertsEnabled
 *   Step 5: If smsAlertsEnabled: fetch trusted contact phones, send safe arrival SMS
 *           → Stamp safeArrivalSent=true on journey doc
 *   Step 6: Post arrivedLiveData=true and smsWasSent (for UI to react)
 *
 * Note: Steps 4-6 are nested callbacks. This is intentional — we MUST check settings
 * before sending SMS, and we MUST know the phone list before calling SmsHelper.
 * In a larger app, RxJava or Kotlin Coroutines would clean this up.
 */
public class NavigationViewModel extends AndroidViewModel
        implements NavigationTracker.NavigationUpdateListener {

    private static final String TAG = "NavigationViewModel";

    /** Radius in meters. If the user is within this of the destination, they've "arrived". */
    private static final double ARRIVAL_RADIUS_METERS = 50.0;

    // ── Dependencies (created once in constructor) ────────────────────────────
    private final NavigationTracker navigationTracker;
    private final JourneyHistoryRepository journeyHistoryRepo;
    private final GuardianRepository guardianRepo;
    private final SettingsRepository settingsRepo;
    private final TrustedContactRepository contactRepo;
    private final LiveShareRepository liveShareRepo;
    private String currentShareToken = null;
    private String currentShareUrl = null;

    // ── LiveData exposed to NavigationActivity ────────────────────────────────

    /** Current GPS location. Activity uses this to move the map marker. */
    private final MutableLiveData<Location> locationLiveData = new MutableLiveData<>();

    /** Remaining seconds to destination. Activity shows this as "X min". */
    private final MutableLiveData<Integer> etaSecondsLiveData = new MutableLiveData<>();

    /** Remaining meters to destination. Activity shows this as "X mi remaining". */
    private final MutableLiveData<Double> distanceRemainingLiveData = new MutableLiveData<>();

    /** Turn-by-turn instruction text. */
    private final MutableLiveData<String> instructionLiveData = new MutableLiveData<>();

    /** Fires true when a long stop is detected. Activity shows the emergency dialog. */
    private final MutableLiveData<Boolean> longStopWarningLiveData = new MutableLiveData<>();

    /**
     * Fires true when the user arrives at the destination.
     * The Activity observes this to show the Safe Arrival overlay.
     * This fires exactly ONCE per journey (guarded by hasArrived).
     */
    private final MutableLiveData<Boolean> arrivedLiveData = new MutableLiveData<>();

    /**
     * Fires true if the safe arrival SMS was actually sent to contacts.
     * Used by the overlay to show "Your trusted contacts have been notified".
     * Fires AFTER arrivedLiveData (after the SMS send attempt completes).
     */
    private final MutableLiveData<Boolean> smsWasSentLiveData = new MutableLiveData<>(false);

    // ── State fields ──────────────────────────────────────────────────────────

    /**
     * Prevents the arrival flow from running more than once.
     * Without this guard, every GPS tick within 50m of destination would re-trigger
     * the flow, causing duplicate Firestore writes and duplicate SMS sends.
     */
    private boolean hasArrived = false;

    /**
     * The Firestore document ID of the current active journey.
     * Set in the constructor from ActiveRouteRepository.
     * Used to call endJourney() and stamp safeArrivalSent.
     */
    private final String journeyId;

    /** Tracks total distance traveled so endJourney() can record it. */
    private double totalDistanceTraveledMeters = 0.0;
    private Location lastLocation = null;

    // ── CONSTRUCTOR ───────────────────────────────────────────────────────────

    public NavigationViewModel(@NonNull Application application) {
        super(application);

        // Create tracker (manages GPS subscription)
        this.navigationTracker = new NavigationTracker(application, this);

        // Create repositories (all are lightweight, just hold a Firestore reference)
        this.journeyHistoryRepo = new JourneyHistoryRepository();
        this.guardianRepo = new GuardianRepository();
        this.settingsRepo = new SettingsRepository();
        this.contactRepo = new TrustedContactRepository();
        this.liveShareRepo = new LiveShareRepository();

        // Read the active route from the singleton and configure the tracker
        Route activeRoute = ActiveRouteRepository.getInstance().getActiveRoute();
        if (activeRoute != null) {
            navigationTracker.setRoute(activeRoute);
            instructionLiveData.setValue("Head towards the route");
        }

        // Read the journey ID that was stored when navigation started.
        // This was set by (for example) RouteSelectionActivity after startJourney() returned.
        // If it's null, the safe arrival Firestore writes will be skipped gracefully.
        this.journeyId = ActiveRouteRepository.getInstance().getActiveJourneyId();
        Log.d(TAG, "NavigationViewModel created. journeyId=" + journeyId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PUBLIC METHODS (called by NavigationActivity)
    // ═════════════════════════════════════════════════════════════════════════

    /** Starts GPS tracking updates. Call from Activity.onResume(). */
    public void startNavigation() {
        navigationTracker.startTracking();
        
        // Start Live Location Sharing
        Route activeRoute = ActiveRouteRepository.getInstance().getActiveRoute();
        if (activeRoute != null && journeyId != null) {
            com.google.android.gms.maps.model.LatLng dest = null;
            if (activeRoute.getPath() != null && !activeRoute.getPath().isEmpty()) {
                dest = activeRoute.getPath().get(activeRoute.getPath().size() - 1);
            }
            String destAddress = "Your Destination";
            String originAddress = "Current Location"; // Or reverse geocode if needed
            int safetyScore = activeRoute.getSafetyScore();
            
            liveShareRepo.startLiveShare(journeyId, dest, destAddress, originAddress, safetyScore, new LiveShareRepository.ShareCallback() {
                @Override
                public void onShareStarted(String shareUrl, String shareToken) {
                    currentShareUrl = shareUrl;
                    currentShareToken = shareToken;
                    ActiveRouteRepository.getInstance().setActiveShareUrl(shareUrl);
                    
                    // Send proactive SMS
                    settingsRepo.getSettings(new SettingsRepository.SettingsCallback() {
                        @Override
                        public void onSuccess(com.riskfreeroutes.app.model.Settings settings) {
                            if (settings != null && settings.isSmsAlertsEnabled()) {
                                contactRepo.getAllContactPhones(phones -> {
                                    if (phones != null && !phones.isEmpty()) {
                                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                                        String userName = (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty())
                                                ? currentUser.getDisplayName() : "Your contact";
                                        String msg = "🛡️ Risk Free Routes\n" + userName + " has started a journey with Guardian Mode.\nTrack live location: " + shareUrl;
                                        SmsHelper.sendEmergencySms(getApplication(), phones, msg);
                                    }
                                });
                            }
                            if (settings != null && settings.isVoiceSosEnabled()) {
                                android.content.Intent serviceIntent = new android.content.Intent(getApplication(), com.riskfreeroutes.app.service.VoiceTriggerService.class);
                                getApplication().startService(serviceIntent);
                                Log.d(TAG, "Voice SOS service started");
                            }
                        }
                        @Override
                        public void onFailure(Exception e) {}
                    });
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to start live share", e);
                }
            });
        }
    }

    /**
     * Stops GPS tracking when the user manually ends navigation (taps "End" button).
     * Records the journey as "ended_early" in Firestore.
     */
    public void stopNavigation() {
        navigationTracker.stopTracking();
        // If the user manually exits before arriving, record as ended_early
        if (!hasArrived && journeyId != null) {
            Log.d(TAG, "User ended navigation early. Recording as ended_early.");
            journeyHistoryRepo.endJourney(journeyId, "ended_early", totalDistanceTraveledMeters);
        }
        if (currentShareToken != null) {
            liveShareRepo.endLiveShare(currentShareToken);
        }
        ActiveRouteRepository.getInstance().clear();
        android.content.Intent serviceIntent = new android.content.Intent(getApplication(), com.riskfreeroutes.app.service.VoiceTriggerService.class);
        getApplication().stopService(serviceIntent);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NAVIGATION TRACKER CALLBACKS (called from background thread)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Called every ~3 seconds by NavigationTracker with the latest location, speed,
     * remaining distance to destination, and estimated seconds remaining.
     *
     * This is the heart of the ViewModel — it processes every GPS update.
     *
     * THREAD NOTE: This is called from a background Thread (see NavigationTracker.processLocation()).
     * We use postValue() (not setValue()) throughout because postValue() is thread-safe.
     */
    @Override
    public void onLocationUpdated(Location location, double speedMps,
                                   double distanceRemainingMeters, int etaSeconds, String nextInstruction, double nextInstructionDistanceMeters) {
        // ── Update standard navigation LiveData ───────────────────────────────
        locationLiveData.postValue(location);
        etaSecondsLiveData.postValue(etaSeconds);
        distanceRemainingLiveData.postValue(distanceRemainingMeters);

        // ── Update Live Share Location ───────────────────────────────────────
        if (currentShareToken != null) {
            int safetyScore = ActiveRouteRepository.getInstance().getActiveRoute() != null ? ActiveRouteRepository.getInstance().getActiveRoute().getSafetyScore() : 100;
            liveShareRepo.updateLiveLocation(currentShareToken, location, distanceRemainingMeters, etaSeconds / 60, nextInstruction, nextInstructionDistanceMeters, safetyScore);
        }

        // ── Track cumulative distance for journey record ───────────────────────
        // We add the distance from the previous location to the current one.
        // This is more accurate than just using the route's total distance,
        // because the user might deviate slightly.
        if (lastLocation != null) {
            totalDistanceTraveledMeters += lastLocation.distanceTo(location);
        }
        lastLocation = location;

        // ── Update instruction text ───────────────────────────────────────────
        if (distanceRemainingMeters < ARRIVAL_RADIUS_METERS) {
            instructionLiveData.postValue("You have arrived at your destination! 🎉");
        } else if (distanceRemainingMeters < 200 && (nextInstruction == null || nextInstruction.equals("Follow the route"))) {
            instructionLiveData.postValue("Destination is very close");
        } else {
            instructionLiveData.postValue(nextInstruction != null ? nextInstruction : "Continue on route");
        }

        // ── ARRIVAL DETECTION ─────────────────────────────────────────────────
        // Only run the arrival flow ONCE per journey (hasArrived flag prevents repeats).
        if (distanceRemainingMeters < ARRIVAL_RADIUS_METERS && !hasArrived) {
            hasArrived = true; // set FIRST to prevent any race condition
            Log.d(TAG, "Arrival detected! distanceRemaining=" + distanceRemainingMeters + "m");
            triggerSafeArrivalFlow(location);
        }
    }

    /**
     * Called when the user has been stationary for 30+ seconds.
     * Triggers the emergency countdown dialog via NavigationActivity.
     */
    @Override
    public void onLongStopDetected() {
        Log.d(TAG, "Long stop detected — triggering emergency dialog");
        longStopWarningLiveData.postValue(true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SAFE ARRIVAL FLOW
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Orchestrates everything that happens when the user arrives safely at their destination.
     *
     * WHY DOES THIS LIVE IN THE VIEWMODEL?
     * ViewModels survive configuration changes (screen rotation). If this logic were
     * in the Activity and the user rotated their phone right at arrival, the Activity
     * would be destroyed mid-flow, potentially causing:
     *   - endJourney() called twice (on old and new Activity)
     *   - SMS sent twice
     *   - The UI showing nothing because the new Activity misses the arrival signal
     * By running everything in the ViewModel, we guarantee it runs exactly once.
     *
     * @param arrivalLocation The GPS location at the moment of arrival.
     */
    private void triggerSafeArrivalFlow(Location arrivalLocation) {
        Log.d(TAG, "Safe arrival flow started. journeyId=" + journeyId);

        // ── Step 1: Stop GPS tracking ─────────────────────────────────────────
        // We don't need GPS anymore — the user has arrived.
        // This conserves battery.
        navigationTracker.stopTracking();
        
        // End Live Share
        if (currentShareToken != null) {
            liveShareRepo.endLiveShare(currentShareToken);
        }

        // ── Step 2: Update journey_history document ───────────────────────────
        // Records: status="completed", endTimestamp=now, distanceTraveledMeters
        // ALSO atomically updates: user.totalJourneys++, user.avgSafetyScore (running avg)
        // This is all handled by JourneyHistoryRepository.endJourney() internally.
        if (journeyId != null) {
            journeyHistoryRepo.endJourney(journeyId, "completed", totalDistanceTraveledMeters);
            Log.d(TAG, "Journey ended as completed: " + journeyId);
        } else {
            Log.w(TAG, "journeyId is null — skipping Firestore journey update");
        }

        // ── Step 3: Log a guardian event ─────────────────────────────────────
        // This records "destination_reached" in the guardian_logs subcollection.
        // It's informational (status="info"), not an emergency.
        // Having this log lets us build a journey timeline feature later.
        guardianRepo.logGuardianEvent("destination_reached", arrivalLocation, "info", journeyId);
        Log.d(TAG, "Guardian log: destination_reached");

        // ── Step 4 + 5: Check settings, then conditionally send SMS ──────────
        // We MUST check smsAlertsEnabled first.
        // The user might have turned off SMS notifications in Settings,
        // in which case we skip the SMS but still do everything else.
        fetchSettingsAndMaybeSendSms(arrivalLocation);
    }

    /**
     * Reads the user's settings to check if SMS alerts are enabled.
     * If yes, fetches trusted contacts and sends the safe arrival SMS.
     * Always posts arrivedLiveData at the end so the UI updates regardless.
     *
     * WHY NESTED CALLBACKS?
     * Android Firestore reads are asynchronous. We need:
     *   1. getSettings() result → to know if SMS is enabled
     *   2. getAllContactPhones() result → to have the phone numbers
     *   Both are independent Firestore reads; we chain them sequentially here.
     *
     * @param arrivalLocation The GPS coordinates where the user arrived.
     */
    private void fetchSettingsAndMaybeSendSms(Location arrivalLocation) {
        settingsRepo.getSettings(new SettingsRepository.SettingsCallback() {
            @Override
            public void onSuccess(com.riskfreeroutes.app.model.Settings settings) {
                boolean smsEnabled = settings != null && settings.isSmsAlertsEnabled();
                Log.d(TAG, "smsAlertsEnabled=" + smsEnabled);

                if (smsEnabled) {
                    // SMS is allowed — fetch contacts and send
                    sendSafeArrivalSms(arrivalLocation);
                } else {
                    // SMS is disabled — skip SMS, still show in-app confirmation
                    Log.d(TAG, "SMS disabled in settings — skipping safe arrival SMS");
                    smsWasSentLiveData.postValue(false);
                    arrivedLiveData.postValue(true); // show the overlay
                }
            }

            @Override
            public void onFailure(Exception e) {
                // If settings read fails, default to NOT sending SMS (safe fallback)
                Log.w(TAG, "Failed to read settings, defaulting to no SMS", e);
                smsWasSentLiveData.postValue(false);
                arrivedLiveData.postValue(true);
            }
        });
    }

    /**
     * Fetches all trusted contact phone numbers, then sends the safe arrival SMS.
     * If no contacts exist, silently skips the SMS (no error — this is fine).
     *
     * @param arrivalLocation The GPS coordinates where the user arrived.
     */
    private void sendSafeArrivalSms(Location arrivalLocation) {
        contactRepo.getAllContactPhones(new TrustedContactRepository.ContactListCallback() {
            @Override
            public void onResult(List<String> phones) {
                if (phones == null || phones.isEmpty()) {
                    // No contacts → no one to notify. This is fine, not an error.
                    // The user will still see the in-app "You've arrived safely" overlay.
                    Log.d(TAG, "No trusted contacts found — skipping safe arrival SMS");
                    smsWasSentLiveData.postValue(false);
                    arrivedLiveData.postValue(true);
                    return;
                }

                // ── Build the safe arrival SMS message ────────────────────────
                // We need the user's display name for the message.
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                String userName = (currentUser != null && currentUser.getDisplayName() != null
                        && !currentUser.getDisplayName().isEmpty())
                        ? currentUser.getDisplayName()
                        : "Your contact";

                // Format arrival time as "HH:mm" (e.g., "18:43")
                String arrivalTime = new SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new Date());

                // Compose the message (positive, calm tone — unlike the emergency alert)
                String message = "✅ Risk Free Routes — Safe Arrival\n"
                        + userName + " has arrived safely at their destination.\n"
                        + "Arrival time: " + arrivalTime + "\n"
                        + "No action needed. This is an automated safe arrival notification.";

                // ── Send the SMS ──────────────────────────────────────────────
                // SmsHelper handles multi-part splitting, SecurityException, etc.
                // The caller (this ViewModel) just provides context, phones, and message.
                SmsHelper.sendEmergencySms(getApplication(), phones, message);
                Log.i(TAG, "Safe arrival SMS dispatched to " + phones.size() + " contacts");

                // ── Stamp safeArrivalSent on the journey document ─────────────
                // This lets us later show "SMS sent ✓" in Journey History,
                // and prevents re-sending if the screen is somehow recreated.
                if (journeyId != null) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("safeArrivalSent", true);
                    update.put("safeArrivalTimestamp", Timestamp.now());

                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users").document(user.getUid())
                                .collection("journey_history").document(journeyId)
                                .update(update)
                                .addOnSuccessListener(v ->
                                        Log.d(TAG, "safeArrivalSent=true stamped on journey"))
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "Failed to stamp safeArrivalSent", e));
                    }
                }

                // ── Signal the UI ─────────────────────────────────────────────
                smsWasSentLiveData.postValue(true);  // "contacts have been notified" line
                arrivedLiveData.postValue(true);      // show the arrival overlay
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LIVEDATA GETTERS
    // ═════════════════════════════════════════════════════════════════════════

    public LiveData<Location> getLocation() { return locationLiveData; }
    public LiveData<Integer> getEtaSeconds() { return etaSecondsLiveData; }
    public LiveData<Double> getDistanceRemaining() { return distanceRemainingLiveData; }
    public LiveData<String> getInstruction() { return instructionLiveData; }
    public LiveData<Boolean> getLongStopWarning() { return longStopWarningLiveData; }
    public LiveData<Boolean> getArrived() { return arrivedLiveData; }
    public LiveData<Boolean> getSmsWasSent() { return smsWasSentLiveData; }
}
