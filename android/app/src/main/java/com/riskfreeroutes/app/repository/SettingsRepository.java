package com.riskfreeroutes.app.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.model.Settings;

public class SettingsRepository {
    private final FirebaseFirestore db;

    public interface SettingsCallback {
        void onSuccess(Settings settings);
        void onFailure(Exception e);
    }
    
    public interface UpdateCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public SettingsRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    private String getUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return null;
    }

    public void getSettings(SettingsCallback callback) {
        String uid = getUid();
        if (uid == null) {
            if (callback != null) callback.onFailure(new Exception("Not logged in"));
            return;
        }
        db.collection("users").document(uid).collection("settings").document("preferences")
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Settings settings = documentSnapshot.toObject(Settings.class);
                    if (callback != null) callback.onSuccess(settings);
                } else {
                    if (callback != null) callback.onSuccess(new Settings()); // return defaults
                }
            })
            .addOnFailureListener(e -> {
                if (callback != null) callback.onFailure(e);
            });
    }
    
    public void saveSettings(Settings settings, UpdateCallback callback) {
        String uid = getUid();
        if (uid == null) {
            if (callback != null) callback.onFailure(new Exception("Not logged in"));
            return;
        }
        db.collection("users").document(uid).collection("settings").document("preferences")
            .set(settings)
            .addOnSuccessListener(aVoid -> {
                if (callback != null) callback.onSuccess();
            })
            .addOnFailureListener(e -> {
                if (callback != null) callback.onFailure(e);
            });
    }
}
