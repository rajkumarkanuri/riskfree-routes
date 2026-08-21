package com.riskfreeroutes.app.repository;

import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LiveShareRepository — Manages real-time location sharing via Firestore.
 * This pushes the user's location to a public `live_shares` collection
 * where trusted contacts can view it via a public web link.
 */
public class LiveShareRepository {

    private static final String TAG = "LiveShareRepository";
    private final FirebaseFirestore db;
    private final String userId;
    private final String userName;

    public interface ShareCallback {
        void onShareStarted(String shareUrl, String shareToken);
        void onError(Exception e);
    }

    public LiveShareRepository() {
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        userId = (user != null) ? user.getUid() : "anonymous";
        userName = (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                ? user.getDisplayName() : "A risk free routes user";
    }

    /**
     * Starts a live share session by creating a document with a random UUID.
     * 
     * @param journeyId The current active journey ID
     * @param destination The destination coordinates
     * @param destinationAddress The destination address
     * @param originAddress The starting address
     * @param safetyScore The route's safety score
     * @param callback Returns the generated share URL and token
     */
    public void startLiveShare(String journeyId, LatLng destination, String destinationAddress, String originAddress, int safetyScore, ShareCallback callback) {
        if (userId.equals("anonymous")) {
            Log.w(TAG, "User not authenticated, skipping live share");
            if (callback != null) callback.onError(new IllegalStateException("Not authenticated"));
            return;
        }

        String shareToken = UUID.randomUUID().toString();
        // 6 hours from now
        long expiresAtMillis = System.currentTimeMillis() + (6 * 60 * 60 * 1000);
        Timestamp expiresAt = new Timestamp(expiresAtMillis / 1000, 0);

        Map<String, Object> data = new HashMap<>();
        data.put("uid", userId);
        data.put("journeyId", journeyId != null ? journeyId : "");
        data.put("userName", userName);
        data.put("currentLat", 0.0);
        data.put("currentLng", 0.0);
        data.put("lastUpdated", Timestamp.now());
        data.put("destination", destination != null ? new GeoPoint(destination.latitude, destination.longitude) : null);
        data.put("destinationAddress", destinationAddress != null ? destinationAddress : "Unknown");
        data.put("originAddress", originAddress != null ? originAddress : "Current Location");
        data.put("safetyScore", safetyScore);
        data.put("distanceRemaining", 0.0);
        data.put("etaMinutes", 0);
        data.put("nextInstruction", "Starting journey...");
        data.put("nextInstructionDistance", 0.0);
        data.put("isActive", true);
        data.put("createdAt", Timestamp.now());
        data.put("expiresAt", expiresAt);

        db.collection("live_shares").document(shareToken)
            .set(data)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Live share created with token: " + shareToken);
                String url = "https://riskfree-routes.web.app/track.html?token=" + shareToken;
                if (callback != null) callback.onShareStarted(url, shareToken);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to create live share", e);
                if (callback != null) callback.onError(e);
            });
    }

    /**
     * Updates the current location of an active live share.
     * This should be called on every GPS tick while the journey is active.
     */
    public void updateLiveLocation(String shareToken, Location location, double distanceRemaining, int etaMinutes, String nextInstruction, double nextInstructionDistance, int safetyScore) {
        if (shareToken == null || location == null || userId.equals("anonymous")) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("currentLat", location.getLatitude());
        updates.put("currentLng", location.getLongitude());
        updates.put("distanceRemaining", distanceRemaining);
        updates.put("etaMinutes", etaMinutes);
        if (nextInstruction != null) {
            updates.put("nextInstruction", nextInstruction);
            updates.put("nextInstructionDistance", nextInstructionDistance);
        }
        updates.put("safetyScore", safetyScore);
        updates.put("lastUpdated", Timestamp.now());

        db.collection("live_shares").document(shareToken)
            .update(updates)
            .addOnFailureListener(e -> Log.e(TAG, "Failed to update live location", e));
    }

    /**
     * Ends the live share session (e.g. when journey completes or is cancelled).
     */
    public void endLiveShare(String shareToken) {
        if (shareToken == null || userId.equals("anonymous")) return;

        db.collection("live_shares").document(shareToken)
            .update("isActive", false)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Live share ended for token: " + shareToken))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to end live share", e));
    }
}
