package com.riskfreeroutes.app.repository;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * JourneyHistoryRepository.java
 *
 * WHY THIS EXISTS:
 * Every time a user starts navigation, we want to record that trip in Firestore
 * under the "journey_history" collection. This gives us:
 *   - A permanent log of all trips for future "Journey History" screen
 *   - Data for safety analysis (which routes are actually used)
 *   - Guardian Mode logs can reference the journey document ID
 *
 * HOW IT WORKS:
 * 1. When navigation starts → call startJourney() → creates a Firestore doc
 *    with status "in_progress", returns the document ID
 * 2. When navigation ends → call endJourney() with that document ID
 *    → updates the doc with end time, distance, final status
 *
 * NOTE: We do NOT write on every GPS tick — that would be very expensive.
 * We write exactly twice per trip: on start and on end.
 */
public class JourneyHistoryRepository {

    private static final String TAG = "JourneyHistoryRepo";
    // The Firestore collection name — all trips live here
    private static final String COLLECTION = "journey_history";

    private final FirebaseFirestore db;

    public JourneyHistoryRepository() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Creates a new journey document in Firestore when navigation starts.
     *
     * @param originLat     Starting latitude
     * @param originLng     Starting longitude
     * @param destLat       Destination latitude
     * @param destLng       Destination longitude
     * @param safetyScore   The safety score of the chosen route (0–100)
     * @param callback      Called back with the new document ID on success, or null on failure
     */
    public void startJourney(double originLat, double originLng,
                             double destLat, double destLng,
                             int safetyScore,
                             JourneyStartCallback callback) {

        // Get the current logged-in user (may be null if not authenticated)
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userId = (user != null) ? user.getUid() : "anonymous";

        // Build the Firestore document fields
        Map<String, Object> data = new HashMap<>();
        // userId is implicit in the path now, but we can leave it in data if needed or remove it.
        data.put("originLat", originLat);
        data.put("originLng", originLng);
        data.put("destLat", destLat);
        data.put("destLng", destLng);
        data.put("safetyScore", safetyScore);
        // status "in_progress" means navigation is actively running
        data.put("status", "in_progress");
        data.put("startTimestamp", Timestamp.now());
        data.put("endTimestamp", null);
        data.put("distanceTraveledMeters", 0.0);

        // .add() auto-generates a unique document ID
        if (userId.equals("anonymous")) {
            if (callback != null) callback.onStarted(null);
            return;
        }

        db.collection("users").document(userId).collection(COLLECTION).add(data)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "Journey started, ID: " + ref.getId());
                    if (callback != null) callback.onStarted(ref.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to start journey", e);
                    if (callback != null) callback.onStarted(null); // null = failure
                });
    }

    /**
     * Updates the journey document when navigation ends.
     *
     * WHY A TRANSACTION FOR THE COUNTERS?
     * When a journey completes, we need to update BOTH totalJourneys AND avgSafetyScore
     * on the user document. The avgSafetyScore uses a running average formula:
     *   newAvg = ((oldAvg × totalJourneys) + journeySafetyScore) / (totalJourneys + 1)
     * This is a read-then-write pattern — we must read the current values before writing.
     * A Firestore transaction guarantees this happens atomically, preventing race conditions
     * if two journeys complete at nearly the same time.
     *
     * @param journeyId              The document ID returned by startJourney()
     * @param status                 "completed" if user reached the destination,
     *                               "ended_early" if they tapped Exit
     * @param distanceTraveledMeters How far the user actually traveled during this session
     */
    public void endJourney(String journeyId, String status, double distanceTraveledMeters) {
        if (journeyId == null) {
            // Journey was never saved (e.g. Firestore was offline at start) — skip silently
            Log.w(TAG, "endJourney called with null journeyId, skipping");
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userId = (user != null) ? user.getUid() : "anonymous";
        if (userId.equals("anonymous")) return;

        DocumentReference journeyRef = db.collection("users").document(userId)
                .collection(COLLECTION).document(journeyId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("endTimestamp", Timestamp.now());
        updates.put("distanceTraveledMeters", distanceTraveledMeters);

        journeyRef.update(updates)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Journey ended: " + journeyId + " → " + status);
                    if ("completed".equals(status)) {
                        // Use a TRANSACTION to atomically update totalJourneys + avgSafetyScore
                        updateJourneyStatsWithTransaction(userId, journeyId);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to end journey: " + journeyId, e));
    }

    /**
     * Uses a Firestore transaction to atomically read the journey's safetyScore,
     * read the current user counters, compute a new running average, and write both
     * totalJourneys and avgSafetyScore in one atomic operation.
     *
     * RUNNING AVERAGE FORMULA:
     *   newAvg = ((oldAvg × oldCount) + newScore) / (oldCount + 1)
     *
     * This avoids re-querying ALL journeys every time — O(1) instead of O(n).
     */
    private void updateJourneyStatsWithTransaction(String userId, String journeyId) {
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentReference journeyRef = db.collection("users").document(userId)
                .collection(COLLECTION).document(journeyId);

        db.runTransaction(transaction -> {
            // Step 1: Read the journey document to get its safetyScore
            com.google.firebase.firestore.DocumentSnapshot journeySnap = transaction.get(journeyRef);
            Long safetyScoreLong = journeySnap.getLong("safetyScore");
            int journeySafetyScore = (safetyScoreLong != null) ? safetyScoreLong.intValue() : 0;

            // Step 2: Read the user document to get current counters
            com.google.firebase.firestore.DocumentSnapshot userSnap = transaction.get(userRef);
            Long oldCountLong = userSnap.getLong("totalJourneys");
            int oldCount = (oldCountLong != null) ? oldCountLong.intValue() : 0;
            Double oldAvg = userSnap.getDouble("avgSafetyScore");
            double currentAvg = (oldAvg != null) ? oldAvg : 0.0;

            // Step 3: Compute new running average
            // Formula: newAvg = ((oldAvg × oldCount) + newScore) / (oldCount + 1)
            double newAvg = ((currentAvg * oldCount) + journeySafetyScore) / (oldCount + 1);

            // Step 4: Write both fields atomically
            Map<String, Object> counterUpdates = new HashMap<>();
            counterUpdates.put("totalJourneys", oldCount + 1);
            counterUpdates.put("avgSafetyScore", newAvg);
            transaction.update(userRef, counterUpdates);

            return null; // transaction committed successfully
        })
        .addOnSuccessListener(unused ->
            Log.d(TAG, "Updated totalJourneys + avgSafetyScore for journey: " + journeyId))
        .addOnFailureListener(e ->
            Log.e(TAG, "Failed to update journey stats via transaction", e));
    }

    /** Callback interface for startJourney() — receives the new document ID */
    public interface JourneyStartCallback {
        /** @param journeyId The Firestore document ID, or null if the write failed */
        void onStarted(String journeyId);
    }

    // ── PROFILE QUERIES ──────────────────────────────────────────────────────

    public interface JourneyQueryCallback {
        void onResult(String lastJourneyDate, int avgSafetyScore, int completedCount);
    }

    public void getJourneyStats(JourneyQueryCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userId = (user != null) ? user.getUid() : "anonymous";
        if (userId.equals("anonymous")) {
            if (callback != null) callback.onResult(null, 0, 0);
            return;
        }

        db.collection("users").document(userId).collection(COLLECTION)
            .orderBy("startTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snap -> {
                if (snap.isEmpty()) {
                    if (callback != null) callback.onResult(null, 0, 0);
                    return;
                }

                String lastDate = null;
                int totalScore = 0;
                int scoreCount = 0;
                int completed = 0;

                for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                    if (lastDate == null) {
                        com.google.firebase.Timestamp ts = doc.getTimestamp("startTimestamp");
                        if (ts != null) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
                            lastDate = sdf.format(ts.toDate());
                        }
                    }
                    Long score = doc.getLong("safetyScore");
                    if (score != null && score > 0) {
                        totalScore += score.intValue();
                        scoreCount++;
                    }
                    String status = doc.getString("status");
                    if ("completed".equals(status)) completed++;
                }

                int avg = scoreCount > 0 ? totalScore / scoreCount : 0;
                if (callback != null) callback.onResult(lastDate, avg, completed);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to query journey stats", e);
                if (callback != null) callback.onResult(null, 0, 0);
            });
    }

    // ── JOURNEY HISTORY LIST ─────────────────────────────────────────────────

    public interface JourneyListCallback {
        void onResult(java.util.List<com.riskfreeroutes.app.model.Journey> journeys);
        void onError(Exception e);
    }

    /**
     * Fetches all journeys for the current user, ordered by startTimestamp descending.
     * Used by JourneyHistoryActivity to populate the RecyclerView.
     */
    public void getAllJourneys(JourneyListCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userId = (user != null) ? user.getUid() : "anonymous";
        if (userId.equals("anonymous")) {
            if (callback != null) callback.onResult(new java.util.ArrayList<>());
            return;
        }

        db.collection("users").document(userId).collection(COLLECTION)
            .orderBy("startTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snap -> {
                java.util.List<com.riskfreeroutes.app.model.Journey> journeys =
                    snap.toObjects(com.riskfreeroutes.app.model.Journey.class);
                if (callback != null) callback.onResult(journeys);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to fetch journey list", e);
                if (callback != null) callback.onError(e);
            });
    }
}
