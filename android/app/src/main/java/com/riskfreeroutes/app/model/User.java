package com.riskfreeroutes.app.model;

import com.google.firebase.Timestamp;

public class User {

    private String uid;
    private String fullName; // Note: In old model this was 'name', we are keeping it as 'name' for backward compatibility but adding these new fields
    private String name;
    private String email;
    private String phone;
    private String profileImageUrl;
    private String gender;
    private Timestamp dateOfBirth;
    private String safetyMode;
    private int trustScore;
    private String badge;
    private int reportsSubmitted;
    private int verifiedReports;
    private int totalJourneys;
    private double avgSafetyScore; // Running average of safetyScore across completed journeys
    private String deviceToken;
    private boolean trustedReporterBadge; // Legacy field, keeping for safety but 'badge' is new
    private Timestamp createdAt;
    private Timestamp lastLogin;

    public User() {}

    public User(String uid, String name, String email, String phone) {
        this.uid = uid;
        this.name = name;
        this.fullName = name;
        this.email = email;
        this.phone = phone;
        this.profileImageUrl = "";
        this.gender = null;
        this.dateOfBirth = null;
        this.safetyMode = "Standard";
        this.trustScore = 50; // Starting neutral score
        this.badge = "None";
        this.reportsSubmitted = 0;
        this.verifiedReports = 0;
        this.totalJourneys = 0;
        this.avgSafetyScore = 0.0;
        this.deviceToken = null;
        this.trustedReporterBadge = false;
        this.createdAt = Timestamp.now();
        this.lastLogin = Timestamp.now();
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Timestamp getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(Timestamp dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getSafetyMode() { return safetyMode; }
    public void setSafetyMode(String safetyMode) { this.safetyMode = safetyMode; }
    
    public int getTrustScore() { return trustScore; }
    public void setTrustScore(int trustScore) { this.trustScore = trustScore; }
    
    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }
    
    public int getReportsSubmitted() { return reportsSubmitted; }
    public void setReportsSubmitted(int reportsSubmitted) { this.reportsSubmitted = reportsSubmitted; }
    
    public int getVerifiedReports() { return verifiedReports; }
    public void setVerifiedReports(int verifiedReports) { this.verifiedReports = verifiedReports; }
    
    public int getTotalJourneys() { return totalJourneys; }
    public void setTotalJourneys(int totalJourneys) { this.totalJourneys = totalJourneys; }

    public double getAvgSafetyScore() { return avgSafetyScore; }
    public void setAvgSafetyScore(double avgSafetyScore) { this.avgSafetyScore = avgSafetyScore; }
    
    public String getDeviceToken() { return deviceToken; }
    public void setDeviceToken(String deviceToken) { this.deviceToken = deviceToken; }

    public boolean isTrustedReporterBadge() { return trustedReporterBadge; }
    public void setTrustedReporterBadge(boolean trustedReporterBadge) { this.trustedReporterBadge = trustedReporterBadge; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getLastLogin() { return lastLogin; }
    public void setLastLogin(Timestamp lastLogin) { this.lastLogin = lastLogin; }
}
