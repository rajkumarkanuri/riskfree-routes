package com.riskfreeroutes.app.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.model.Feedback;

public class FeedbackRepository {
    private final FirebaseFirestore db;

    public interface SubmitCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public FeedbackRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void submitFeedback(Feedback feedback, SubmitCallback callback) {
        db.collection("feedback")
            .add(feedback)
            .addOnSuccessListener(documentReference -> {
                if (callback != null) callback.onSuccess();
            })
            .addOnFailureListener(e -> {
                if (callback != null) callback.onFailure(e);
            });
    }
}
