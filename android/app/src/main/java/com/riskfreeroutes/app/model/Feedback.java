package com.riskfreeroutes.app.model;

import com.google.firebase.Timestamp;

public class Feedback {
    private String id;
    private String userId;
    private int rating;
    private String feedback;
    private Timestamp createdAt;

    public Feedback() {}

    public Feedback(String id, String userId, int rating, String feedback) {
        this.id = id;
        this.userId = userId;
        this.rating = rating;
        this.feedback = feedback;
        this.createdAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
