package com.riskfreeroutes.app.repository;

import android.location.Location;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GuardianRepository.java — Handles Guardian Mode logs and SOS events in Firestore.
 *
 * WHY SUBCOLLECTIONS?
 * Both guardian_logs and sos_history are private — they belong to ONE user
 * and should never be readable by anyone else. By storing them at:
 *   users/{uid}/guardian_logs/{logId}
 *   users/{uid}/sos_history/{sosId}
 * our Firestore security rules automatically restrict access to the owner.
 *
 * GUARDIAN MODE FLOW:
 * 1. User starts navigation → JourneyHistoryRepository.startJourney() returns a journeyId
 * 2. GuardianModeService receives that journeyId
 * 3. If it detects a deviation, long stop, or risk zone, it calls
 *    logGuardianEvent(event, location, status, journeyId)
 * 4. That journeyId is stored in the guardian_logs document so we can later
 *    show "during this trip, these events occurred"
 *
 * SOS FLOW:
 * 1. User taps SOS → triggerSOS(location, contactPhones) is called
 * 2. We write a document with status = "active"
 * 3. After the alert is sent, we update the document with the notified contact list
 * 4. When the user cancels → resolveSOS(sosDocId) sets status = "resolved"
 */
public class GuardianRepository {

    private static final String TAG = "GuardianRepository";

    private final FirebaseFirestore db;
    private final String userId;

    public GuardianRepository() {
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = (user != null) ? user.getUid() : "anonymous";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GUARDIAN LOGS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Logs a Guardian Mode event to Firestore.
     *
     * This is called by GuardianModeService whenever it detects something worth
     * recording: the user has deviated from the route, stopped for too long, or
     * entered a known crime zone.
     *
     * WHY WE NEED journeyId:
     * Each guardian log entry is linked to the journey that was active when the
     * event happened. This lets us later show a timeline like:
     *   "Journey to MG Road (18:40) → 1 long stop detected at 18:52"
     * Without journeyId, the logs would be floating events with no context.
     *
     * @param event     What happened. Valid values:
     *                    "deviation"  → user is off the planned route
     *                    "long_stop"  → user has been stationary for too long
     *                    "risk_zone"  → user entered a known crime/risk zone
     * @param location  GPS coordinates when the event was detected.
     * @param status    Urgency level:
     *                    "warning"   → initial detection, monitoring continues
     *                    "escalated" → warning was ignored, contacts may be notified
     *                    "resolved"  → event ended without escalation
     * @param journeyId The Firestore document ID of the active journey (from
     *                  JourneyHistoryRepository.startJourney()). May be null if
     *                  the journey failed to save — we handle that gracefully.
     */
    public void logGuardianEvent(String event, Location location, String status, String journeyId) {
        if (userId.equals("anonymous")) {
            Log.w(TAG, "logGuardianEvent: user not authenticated, skipping");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("event", event);
        data.put("timestamp", Timestamp.now());
        data.put("latitude", location != null ? location.getLatitude() : 0.0);
        data.put("longitude", location != null ? location.getLongitude() : 0.0);
        data.put("status", status);

        // Link this log entry back to the active journey.
        // If journeyId is null (journey failed to save to Firestore),
        // we store "" so the document is still valid and queryable.
        data.put("journeyId", journeyId != null ? journeyId : "");

        db.collection("users").document(userId)
            .collection("guardian_logs")
            .add(data)
            .addOnSuccessListener(ref -> Log.d(TAG, "Guardian log saved: " + ref.getId()
                + " (journey: " + journeyId + ")"))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to save guardian log", e));
    }

    /**
     * Legacy overload — for any existing call sites that don't pass journeyId yet.
     * Delegates to the full version with journeyId = null.
     *
     * Remove this once all call sites are updated to pass journeyId.
     */
    public void logGuardianEvent(String event, Location location, String status) {
        logGuardianEvent(event, location, status, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SOS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Triggers an SOS alert by writing a document to sos_history.
     *
     * After calling this method, the caller should:
     *   1. Send SMS/WhatsApp to all trusted contacts
     *   2. Call updateSosContacts(sosDocId, contactPhones) with the list of who was notified
     *
     * @param location      GPS coordinates where SOS was triggered. May be null
     *                      if location is unavailable (we handle it gracefully).
     * @param callback      Called back with the new Firestore document ID, which
     *                      you need to later call resolveSOS() with.
     */
    public void triggerSOS(Location location, SosCallback callback) {
        if (userId.equals("anonymous")) {
            Log.w(TAG, "triggerSOS: user not authenticated, skipping");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("location", location != null
            ? new GeoPoint(location.getLatitude(), location.getLongitude())
            : null);
        data.put("triggeredAt", Timestamp.now());
        data.put("resolvedAt", null);
        data.put("contactsNotified", new ArrayList<>());  // populated after SMS is sent
        data.put("status", "active");

        db.collection("users").document(userId)
            .collection("sos_history")
            .add(data)
            .addOnSuccessListener(ref -> {
                Log.d(TAG, "SOS triggered: " + ref.getId());
                if (callback != null) callback.onSosCreated(ref.getId());
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to trigger SOS", e);
                if (callback != null) callback.onSosCreated(null);
            });
    }

    /**
     * Legacy overload — for existing call sites that don't use the callback.
     */
    public void triggerSOS(Location location) {
        triggerSOS(location, null);
    }

    /**
     * Fetches trusted contacts, sends SMS, and triggers SOS in Firestore.
     */
    public void fetchContactsAndTriggerSOS(android.content.Context context, String userName, Location location, SosCallback callback) {
        TrustedContactRepository contactRepo = new TrustedContactRepository();
        contactRepo.getAllContactPhones(phones -> {
            if (phones.isEmpty()) {
                Log.w(TAG, "No trusted contacts found. Skipping SMS.");
                triggerSOS(location, sosDocId -> {
                    if (callback != null) callback.onSosCreated(sosDocId);
                });
                return;
            }
            
            String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());
            String existingShareUrl = com.riskfreeroutes.app.repository.ActiveRouteRepository.getInstance().getActiveShareUrl();

            if (existingShareUrl != null) {
                // Reuse existing share from active navigation
                sendSmsAndTriggerSos(context, phones, userName, existingShareUrl, time, location, callback);
            } else {
                // Standalone SOS: start a new live share
                LiveShareRepository liveShareRepo = new LiveShareRepository();
                liveShareRepo.startLiveShare(null, null, null, null, 100, new LiveShareRepository.ShareCallback() {
                    @Override
                    public void onShareStarted(String shareUrl, String shareToken) {
                        Log.d("SOS_DEBUG", "startLiveShare called, token: " + shareToken);
                        com.riskfreeroutes.app.repository.ActiveRouteRepository.getInstance().setActiveShareUrl(shareUrl);
                        com.riskfreeroutes.app.repository.ActiveRouteRepository.getInstance().setActiveShareToken(shareToken);
                        sendSmsAndTriggerSos(context, phones, userName, shareUrl, time, location, callback);
                    }

                    @Override
                    public void onError(Exception e) {
                        // Fallback to static link
                        sendSmsAndTriggerSos(context, phones, userName, null, time, location, callback);
                    }
                });
            }
        });
    }

    private void sendSmsAndTriggerSos(android.content.Context context, List<String> phones, String userName, String shareUrl, String time, Location location, SosCallback callback) {
        String lat = location != null ? String.valueOf(location.getLatitude()) : "Unknown";
        String lng = location != null ? String.valueOf(location.getLongitude()) : "Unknown";

        String trackingLine = shareUrl != null 
                ? "Track live location: " + shareUrl + "\n" 
                : "Current Location:\nhttps://maps.google.com/?q=" + lat + "," + lng + "\n";

        Log.d("SOS_DEBUG", "Building SMS with URL: " + (shareUrl != null ? shareUrl : "static maps link"));

        String message = "EMERGENCY ALERT — Risk Free Routes\n"
            + userName + " may need immediate assistance.\n"
            + trackingLine
            + "Time:\n" + time + "\n"
            + "Please contact them immediately.";

        com.riskfreeroutes.app.service.SmsHelper.sendEmergencySms(context, phones, message);

        triggerSOS(location, sosDocId -> {
            if (sosDocId != null && !phones.isEmpty()) {
                updateSosContacts(sosDocId, phones);
            }
            if (callback != null) {
                callback.onSosCreated(sosDocId);
            }
        });
    }



    /**
     * Updates the sos_history document with the list of contacts that were notified.
     * Call this AFTER you've sent SMS messages so the list is accurate.
     *
     * @param sosDocId      The document ID returned by triggerSOS().
     * @param contactPhones List of phone numbers that received the SOS alert.
     */
    public void updateSosContacts(String sosDocId, List<String> contactPhones) {
        if (sosDocId == null || userId.equals("anonymous")) return;

        db.collection("users").document(userId)
            .collection("sos_history").document(sosDocId)
            .update("contactsNotified", contactPhones)
            .addOnSuccessListener(v -> Log.d(TAG, "SOS contacts updated for: " + sosDocId))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to update SOS contacts", e));
    }

    /**
     * Resolves an active SOS alert (user tapped "I am safe / Cancel SOS").
     *
     * Sets status = "resolved" and records the resolution timestamp.
     *
     * @param sosDocId The document ID returned by triggerSOS().
     */
    public void resolveSOS(String sosDocId) {
        if (sosDocId == null || userId.equals("anonymous")) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "resolved");
        updates.put("resolvedAt", Timestamp.now());

        db.collection("users").document(userId)
            .collection("sos_history").document(sosDocId)
            .update(updates)
            .addOnSuccessListener(v -> Log.d(TAG, "SOS resolved: " + sosDocId))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to resolve SOS", e));

        String token = com.riskfreeroutes.app.repository.ActiveRouteRepository.getInstance().getActiveShareToken();
        if (token != null) {
            new com.riskfreeroutes.app.repository.LiveShareRepository().endLiveShare(token);
        }
    }

    // ── CALLBACK ──────────────────────────────────────────────────────────────

    /** Callback for triggerSOS() — provides the new Firestore document ID */
    public interface SosCallback {
        /** @param sosDocId The new document ID, or null if the write failed. */
        void onSosCreated(String sosDocId);
    }

    // ── PROFILE QUERIES ──────────────────────────────────────────────────────

    public interface CountCallback {
        void onResult(int count);
    }

    public interface TimestampCallback {
        void onResult(com.google.firebase.Timestamp timestamp);
    }

    public void getGuardianLogCount(CountCallback callback) {
        if (userId.equals("anonymous")) { if (callback != null) callback.onResult(0); return; }
        db.collection("users").document(userId).collection("guardian_logs")
            .get()
            .addOnSuccessListener(snap -> { if (callback != null) callback.onResult(snap.size()); })
            .addOnFailureListener(e -> { if (callback != null) callback.onResult(0); });
    }

    public void getLastGuardianTimestamp(TimestampCallback callback) {
        if (userId.equals("anonymous")) { if (callback != null) callback.onResult(null); return; }
        db.collection("users").document(userId).collection("guardian_logs")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener(snap -> {
                if (!snap.isEmpty()) {
                    com.google.firebase.Timestamp ts = snap.getDocuments().get(0).getTimestamp("timestamp");
                    if (callback != null) callback.onResult(ts);
                } else {
                    if (callback != null) callback.onResult(null);
                }
            })
            .addOnFailureListener(e -> { if (callback != null) callback.onResult(null); });
    }

    public void getSosCount(CountCallback callback) {
        if (userId.equals("anonymous")) { if (callback != null) callback.onResult(0); return; }
        db.collection("users").document(userId).collection("sos_history")
            .get()
            .addOnSuccessListener(snap -> { if (callback != null) callback.onResult(snap.size()); })
            .addOnFailureListener(e -> { if (callback != null) callback.onResult(0); });
    }

    public void getLastSosTimestamp(TimestampCallback callback) {
        if (userId.equals("anonymous")) { if (callback != null) callback.onResult(null); return; }
        db.collection("users").document(userId).collection("sos_history")
            .orderBy("triggeredAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener(snap -> {
                if (!snap.isEmpty()) {
                    com.google.firebase.Timestamp ts = snap.getDocuments().get(0).getTimestamp("triggeredAt");
                    if (callback != null) callback.onResult(ts);
                } else {
                    if (callback != null) callback.onResult(null);
                }
            })
            .addOnFailureListener(e -> { if (callback != null) callback.onResult(null); });
    }
}
