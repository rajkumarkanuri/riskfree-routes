package com.riskfreeroutes.app.ui.home;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.riskfreeroutes.app.maps.NavigationTracker;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.model.Route;
import com.riskfreeroutes.app.repository.DirectionsRepository;
import com.riskfreeroutes.app.repository.FirestoreReportsRepository;
import com.riskfreeroutes.app.repository.GuardianRepository;
import com.riskfreeroutes.app.repository.JourneyHistoryRepository;
import com.riskfreeroutes.app.repository.PlacesRepository;
import com.riskfreeroutes.app.utils.LocationHelper;
import android.os.CountDownTimer;
import com.riskfreeroutes.app.utils.SafetyScoreCalculator;

import java.util.Collections;
import java.util.List;

/**
 * HomeViewModel.java — The SINGLE ViewModel for the entire map session.
 *
 * WHY ONE VIEWMODEL FOR EVERYTHING:
 * Since we now have one Activity managing all map states (IDLE / SEARCH /
 * ROUTE_SELECTION / NAVIGATION), we need one ViewModel that persists across
 * all those state changes. This ViewModel survives screen rotations and state
 * transitions, keeping map data, routes, and navigation tracking all in memory
 * without requiring rebuilding when the UI state changes.
 *
 * It covers three responsibilities:
 *   1. Home/Search — Places autocomplete + current location
 *   2. Route Selection — Fetching and scoring routes via Directions API
 *   3. Navigation — Live GPS tracking, ETA calculation, journey persistence
 */
public class HomeViewModel extends AndroidViewModel implements NavigationTracker.NavigationUpdateListener {

    private final LocationHelper locationHelper;
    private final DirectionsRepository directionsRepository;
    private final PlacesRepository placesRepository;
    private final JourneyHistoryRepository journeyHistoryRepository;
    private final GuardianRepository guardianRepository;
    private final LiveData<List<CommunityReport>> communityReports;
    private LiveData<com.riskfreeroutes.app.repository.FirestoreReportsRepository.ReportsWithIds> reportsWithIds;

    // ── ROUTE SELECTION ───────────────────────────────────────────────────────
    // When currentDestination changes, routes are automatically refetched via SwitchMap
    private final MutableLiveData<LatLng> currentDestination = new MutableLiveData<>();
    private LiveData<List<Route>> routes;

    // ── PLACES SEARCH ─────────────────────────────────────────────────────────
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    private final LiveData<List<AutocompletePrediction>> placePredictions;

    // ── LAYER TOGGLES ─────────────────────────────────────────────────────────
    private final MutableLiveData<Boolean> isTrafficEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isHeatmapEnabled = new MutableLiveData<>(false);

    // ── NAVIGATION TRACKING ──────────────────────────────────────────────────
    // NavigationTracker wraps FusedLocationProviderClient for continuous GPS updates.
    // We implement NavigationTracker.NavigationUpdateListener (see bottom of class).
    private final NavigationTracker navigationTracker;

    // Live data streams that HomeActivity observes during NAVIGATION state
    private final MutableLiveData<Location> navLocation = new MutableLiveData<>();
    private final MutableLiveData<Integer> navEtaSeconds = new MutableLiveData<>();
    private final MutableLiveData<Double> navDistanceRemaining = new MutableLiveData<>();
    private final MutableLiveData<String> navInstruction = new MutableLiveData<>();
    private final MutableLiveData<Boolean> navLongStopWarning = new MutableLiveData<>();
    private final MutableLiveData<Integer> guardianCountdownSeconds = new MutableLiveData<>();

    private CountDownTimer guardianTimer;

    // ── JOURNEY PERSISTENCE ──────────────────────────────────────────────────
    // Firestore document ID of the current trip — set on nav start, used on nav end
    private String currentJourneyId = null;
    // Accumulated GPS distance during navigation (in meters)
    private double distanceTraveledMeters = 0.0;
    // Previous location, used to compute incremental distance on each update
    private Location lastNavLocation = null;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        locationHelper = new LocationHelper(application);

        String apiKey = getApiKey(application);
        directionsRepository = new DirectionsRepository(apiKey);
        placesRepository = new PlacesRepository(application, apiKey);
        journeyHistoryRepository = new JourneyHistoryRepository();
        guardianRepository = new GuardianRepository();

        // "this" implements NavigationUpdateListener — callbacks come to onLocationUpdated()
        navigationTracker = new NavigationTracker(application, this);

        FirestoreReportsRepository reportsRepo = new FirestoreReportsRepository();
        communityReports = reportsRepo.getLiveReports();
        reportsWithIds = reportsRepo.getLiveReportsWithIds();

        // SwitchMap: whenever currentDestination changes, automatically call getRoutes()
        routes = Transformations.switchMap(currentDestination, dest -> {
            Location currLoc = locationHelper.getCurrentLocation().getValue();
            if (currLoc != null) {
                LatLng origin = new LatLng(currLoc.getLatitude(), currLoc.getLongitude());
                return calculateSafetyScores(directionsRepository.getRoutes(origin, dest));
            }
            return new MutableLiveData<>();
        });

        placePredictions = Transformations.switchMap(searchQuery, query -> {
            if (query == null || query.length() < 2) {
                return new MutableLiveData<>(Collections.emptyList());
            }
            return placesRepository.getPredictions(query);
        });
    }

    // ── LOCATION ──────────────────────────────────────────────────────────────

    public void startLocationUpdates() { locationHelper.startLocationUpdates(); }

    public void stopLocationUpdates() { locationHelper.stopLocationUpdates(); }

    public LiveData<Location> getCurrentLocation() { return locationHelper.getCurrentLocation(); }

    // ── COMMUNITY REPORTS ─────────────────────────────────────────────────────

    public LiveData<List<CommunityReport>> getCommunityReports() { return communityReports; }

    public LiveData<com.riskfreeroutes.app.repository.FirestoreReportsRepository.ReportsWithIds> getReportsWithIds() {
        return reportsWithIds;
    }

    // ── ROUTES ────────────────────────────────────────────────────────────────

    public LiveData<List<Route>> getRoutes() { return routes; }

    public void fetchRoutesToDestination(LatLng destination) {
        currentDestination.setValue(destination);
    }

    // ── PLACES SEARCH ─────────────────────────────────────────────────────────

    public void setSearchQuery(String query) { searchQuery.setValue(query); }

    public LiveData<List<AutocompletePrediction>> getPlacePredictions() { return placePredictions; }

    public LiveData<LatLng> fetchPlaceLatLng(String placeId) {
        return placesRepository.fetchPlaceLatLng(placeId);
    }

    // ── LAYER TOGGLES ─────────────────────────────────────────────────────────

    public LiveData<Boolean> getIsTrafficEnabled() { return isTrafficEnabled; }
    public void toggleTraffic() { isTrafficEnabled.setValue(Boolean.FALSE.equals(isTrafficEnabled.getValue())); }

    public LiveData<Boolean> getIsHeatmapEnabled() { return isHeatmapEnabled; }
    public void toggleHeatmap() { isHeatmapEnabled.setValue(Boolean.FALSE.equals(isHeatmapEnabled.getValue())); }

    // ── NAVIGATION ────────────────────────────────────────────────────────────

    /**
     * Starts live GPS navigation tracking for the given route.
     *
     * HOW THIS WORKS:
     * 1. We hand the route to NavigationTracker so it knows which polyline to project against
     * 2. We reset distance accumulators
     * 3. We start the FusedLocationProvider update loop (every 3 seconds)
     * 4. We record the trip start in Firestore — callback gives us the document ID
     *
     * @param route The user's chosen Route (contains the decoded LatLng polyline path)
     */
    public void startNavigation(Route route) {
        if (route == null) return;

        // Reset per-trip counters
        distanceTraveledMeters = 0.0;
        lastNavLocation = null;
        navInstruction.setValue("Starting navigation...");
        navLongStopWarning.setValue(false);

        // Tell NavigationTracker which polyline to track remaining distance against
        navigationTracker.setRoute(route);
        navigationTracker.startTracking();

        // Origin = current device GPS position
        Location currentLoc = locationHelper.getCurrentLocation().getValue();
        double originLat = currentLoc != null ? currentLoc.getLatitude() : 0;
        double originLng = currentLoc != null ? currentLoc.getLongitude() : 0;

        // Destination = last point in the decoded route polyline
        List<LatLng> path = route.getDecodedPath();
        double destLat = (!path.isEmpty()) ? path.get(path.size() - 1).latitude : 0;
        double destLng = (!path.isEmpty()) ? path.get(path.size() - 1).longitude : 0;

        // Write "in_progress" journey doc to Firestore; store the returned ID
        journeyHistoryRepository.startJourney(
                originLat, originLng, destLat, destLng, route.getSafetyScore(),
                journeyId -> {
                    currentJourneyId = journeyId;
                    Log.d("HomeViewModel", "Journey started in Firestore: " + journeyId);
                });
    }

    /**
     * Stops navigation and finalizes the trip record in Firestore.
     *
     * @param completed true if the user reached the destination automatically,
     *                  false if they manually tapped "Exit Navigation"
     */
    public void stopNavigation(boolean completed) {
        navigationTracker.stopTracking();
        navLongStopWarning.postValue(false);

        String finalStatus = completed ? "completed" : "ended_early";
        journeyHistoryRepository.endJourney(currentJourneyId, finalStatus, distanceTraveledMeters);

        // Clear state so a new trip starts clean
        currentJourneyId = null;
        distanceTraveledMeters = 0.0;
        lastNavLocation = null;
    }

    // LiveData getters observed by HomeActivity during NAVIGATION state
    public LiveData<Location> getNavLocation() { return navLocation; }
    public LiveData<Integer> getNavEtaSeconds() { return navEtaSeconds; }
    public LiveData<Double> getNavDistanceRemaining() { return navDistanceRemaining; }
    public LiveData<String> getNavInstruction() { return navInstruction; }
    public LiveData<Boolean> getNavLongStopWarning() { return navLongStopWarning; }
    public String getCurrentJourneyId() { return currentJourneyId; }

    // ── NavigationTracker.NavigationUpdateListener implementation ─────────────

    /**
     * Called by NavigationTracker on a background thread every ~3 seconds.
     * postValue() is used (not setValue()) because this arrives off the main thread.
     */
    @Override
    public void onLocationUpdated(Location location, double speedMps,
                                  double distanceRemainingMeters, int etaSeconds, String nextInstruction, double nextInstructionDistanceMeters) {
        navLocation.postValue(location);
        navEtaSeconds.postValue(etaSeconds);
        navDistanceRemaining.postValue(distanceRemainingMeters);

        // Accumulate actual distance traveled for the Firestore journey record
        if (lastNavLocation != null) {
            distanceTraveledMeters += lastNavLocation.distanceTo(location);
        }
        lastNavLocation = location;

        // Update the instruction string based on how far the destination is
        if (distanceRemainingMeters < 30) {
            navInstruction.postValue("You have arrived at your destination!");
        } else if (distanceRemainingMeters < 200 && (nextInstruction == null || nextInstruction.equals("Follow the route"))) {
            navInstruction.postValue("Destination is nearby");
        } else {
            navInstruction.postValue(nextInstruction != null ? nextInstruction : "Continue on route");
        }
    }

    public LiveData<Integer> getGuardianCountdownSeconds() { return guardianCountdownSeconds; }

    /**
     * Called by NavigationTracker when speed drops below 0.5 m/s for 30 seconds.
     * Guardian Mode will hook into this in a future sprint.
     */
    @Override
    public void onLongStopDetected() {
        if (Boolean.TRUE.equals(navLongStopWarning.getValue())) return; // Already active

        Log.d("HomeViewModel", "Guardian Mode trigger: Long stop detected!");
        navLongStopWarning.postValue(true);
        guardianRepository.logGuardianEvent("long_stop_detected", lastNavLocation, "triggered");

        if (guardianTimer != null) guardianTimer.cancel();
        
        // 30 second countdown
        guardianTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                guardianCountdownSeconds.postValue((int) (millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                guardianCountdownSeconds.postValue(0);
                triggerSOS();
            }
        }.start();
    }
    
    public void resolveGuardianAlert() {
        if (guardianTimer != null) {
            guardianTimer.cancel();
        }
        navLongStopWarning.postValue(false);
        guardianCountdownSeconds.postValue(30);
        guardianRepository.logGuardianEvent("user_resolved", lastNavLocation, "resolved");
        // Also reset NavigationTracker so it can trigger again if needed
        navigationTracker.resetLongStopDetection();
    }
    
    public void triggerSOS() {
        if (guardianTimer != null) {
            guardianTimer.cancel();
        }
        navLongStopWarning.postValue(false);
        String userName = "User";
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            userName = user.getDisplayName();
        }
        guardianRepository.fetchContactsAndTriggerSOS(getApplication(), userName, lastNavLocation, sosDocId -> {
            navInstruction.postValue("SOS Sent. Help is on the way.");
        });
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private LiveData<List<Route>> calculateSafetyScores(LiveData<List<Route>> rawRoutesLiveData) {
        return Transformations.map(rawRoutesLiveData, routesList -> {
            if (routesList == null || routesList.isEmpty()) return null;

            List<CommunityReport> currentReports = communityReports.getValue();
            for (Route r : routesList) {
                SafetyScoreCalculator.calculateAndSetScore(r, currentReports);
            }

            // Sort safest route first
            Collections.sort(routesList, (r1, r2) -> Integer.compare(r2.getSafetyScore(), r1.getSafetyScore()));

            for (int i = 0; i < routesList.size(); i++) {
                Route r = routesList.get(i);
                if (i == 0) {
                    r.setRouteType("Safest Route");
                    r.setSelected(true);
                } else if (i == 1) {
                    r.setRouteType("Fastest Safe Route");
                } else {
                    r.setRouteType("Alternative Route");
                }
            }
            return routesList;
        });
    }

    private String getApiKey(Application app) {
        try {
            ApplicationInfo ai = app.getPackageManager().getApplicationInfo(
                    app.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            return bundle.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
            Log.e("HomeViewModel", "Failed to load meta-data: " + e.getMessage());
        }
        return null;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Stop all location tracking when the ViewModel is destroyed (app closed)
        locationHelper.stopLocationUpdates();
        navigationTracker.stopTracking();
    }
}
