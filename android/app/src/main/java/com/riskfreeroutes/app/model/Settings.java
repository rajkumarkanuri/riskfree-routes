package com.riskfreeroutes.app.model;

public class Settings {
    private boolean notificationsEnabled;
    private boolean smsAlertsEnabled;
    private String defaultSafetyMode;
    private String mapType;
    private boolean heatmapDefaultOn;
    private boolean voiceSosEnabled;

    public Settings() {
        this.notificationsEnabled = true;
        this.smsAlertsEnabled = false;
        this.defaultSafetyMode = "Standard";
        this.mapType = "Normal";
        this.heatmapDefaultOn = false;
        this.voiceSosEnabled = false;
    }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public boolean isSmsAlertsEnabled() { return smsAlertsEnabled; }
    public void setSmsAlertsEnabled(boolean smsAlertsEnabled) { this.smsAlertsEnabled = smsAlertsEnabled; }

    public boolean isVoiceSosEnabled() { return voiceSosEnabled; }
    public void setVoiceSosEnabled(boolean voiceSosEnabled) { this.voiceSosEnabled = voiceSosEnabled; }

    public String getDefaultSafetyMode() { return defaultSafetyMode; }
    public void setDefaultSafetyMode(String defaultSafetyMode) { this.defaultSafetyMode = defaultSafetyMode; }

    public String getMapType() { return mapType; }
    public void setMapType(String mapType) { this.mapType = mapType; }

    public boolean isHeatmapDefaultOn() { return heatmapDefaultOn; }
    public void setHeatmapDefaultOn(boolean heatmapDefaultOn) { this.heatmapDefaultOn = heatmapDefaultOn; }
}
