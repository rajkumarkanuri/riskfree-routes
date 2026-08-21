package com.riskfreeroutes.app.utils;

import java.util.List;

public class SafetyScoreResult {
    private int score; // 0 to 100
    private String riskLevel; // Safe, Moderate, High Risk
    private List<String> reasons; // Bullet points explaining the score
    private int nearbyPoliceCount;
    private int nearbyHospitalCount;
    private int verifiedHazardsCount;

    public SafetyScoreResult(int score, String riskLevel, List<String> reasons, int nearbyPoliceCount, int nearbyHospitalCount, int verifiedHazardsCount) {
        this.score = score;
        this.riskLevel = riskLevel;
        this.reasons = reasons;
        this.nearbyPoliceCount = nearbyPoliceCount;
        this.nearbyHospitalCount = nearbyHospitalCount;
        this.verifiedHazardsCount = verifiedHazardsCount;
    }

    public int getScore() { return score; }
    public String getRiskLevel() { return riskLevel; }
    public List<String> getReasons() { return reasons; }
    public int getNearbyPoliceCount() { return nearbyPoliceCount; }
    public int getNearbyHospitalCount() { return nearbyHospitalCount; }
    public int getVerifiedHazardsCount() { return verifiedHazardsCount; }
}
