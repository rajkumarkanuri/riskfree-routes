package com.riskfreeroutes.backend.dto;

/**
 * CompleteProfileRequest — The JSON body we expect from the Android app
 * when the user submits the Complete Profile form.
 */
public class CompleteProfileRequest {
    private String name;
    private String phone;
    private String safetyMode;

    // Getters & Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSafetyMode() { return safetyMode; }
    public void setSafetyMode(String safetyMode) { this.safetyMode = safetyMode; }
}
