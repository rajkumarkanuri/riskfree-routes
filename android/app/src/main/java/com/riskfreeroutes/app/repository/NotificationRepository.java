package com.riskfreeroutes.app.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.riskfreeroutes.app.model.Notification;
import java.util.List;

public class NotificationRepository {
    private final FirebaseFirestore db;
    
    public interface NotificationCallback {
        void onSuccess(List<Notification> notifications);
        void onFailure(Exception e);
    }

    public NotificationRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    private String getUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return null;
    }

    public void getNotifications(NotificationCallback callback) {
        String uid = getUid();
        if (uid == null) {
            if (callback != null) callback.onFailure(new Exception("Not logged in"));
            return;
        }
        db.collection("users").document(uid).collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                if (callback != null) {
                    callback.onSuccess(queryDocumentSnapshots.toObjects(Notification.class));
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) callback.onFailure(e);
            });
    }
    
    public void markAsRead(String notificationId) {
        String uid = getUid();
        if (uid == null) return;
        db.collection("users").document(uid).collection("notifications").document(notificationId)
            .update("read", true);
    }

    // ── PROFILE QUERIES ──────────────────────────────────────────────────────

    public interface CountCallback {
        void onResult(int count);
    }

    public void getTotalCount(CountCallback callback) {
        String uid = getUid();
        if (uid == null) { if (callback != null) callback.onResult(0); return; }
        db.collection("users").document(uid).collection("notifications")
            .get()
            .addOnSuccessListener(snap -> { if (callback != null) callback.onResult(snap.size()); })
            .addOnFailureListener(e -> { if (callback != null) callback.onResult(0); });
    }

    public void getUnreadCount(CountCallback callback) {
        String uid = getUid();
        if (uid == null) { if (callback != null) callback.onResult(0); return; }
        db.collection("users").document(uid).collection("notifications")
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener(snap -> { if (callback != null) callback.onResult(snap.size()); })
            .addOnFailureListener(e -> { if (callback != null) callback.onResult(0); });
    }
}
