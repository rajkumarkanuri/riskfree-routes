package com.riskfreeroutes.app.network.dto;

/**
 * Data Transfer Object (DTO) for the Complete Profile API request.
 * Retrofit and Gson will automatically convert this Java object into a JSON
 * string to send in the HTTP POST body.
 */
public class CompleteProfileRequest {
    private String name;
    private String phone;
    private String safetyMode;

    public CompleteProfileRequest(String name, String phone, String safetyMode) {
        this.name = name;
        this.phone = phone;
        this.safetyMode = safetyMode;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSafetyMode() { return safetyMode; }
    public void setSafetyMode(String safetyMode) { this.safetyMode = safetyMode; }
}
