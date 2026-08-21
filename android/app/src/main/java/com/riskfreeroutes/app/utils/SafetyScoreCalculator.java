package com.riskfreeroutes.app.utils;

import com.google.android.gms.maps.model.LatLng;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.model.Route;

import java.util.ArrayList;
import java.util.List;

public class SafetyScoreCalculator {

    public static void calculateAndSetScore(Route route, List<CommunityReport> allReports) {
        int mockScore = 95 - (int) (Math.random() * 30);
        route.setSafetyScore(mockScore);
    }

    public static SafetyScoreResult calculateForRoute(Route route, List<CommunityReport> allReports) {
        int baseScore = route.getSafetyScore();
        if (baseScore == 0) {
            baseScore = 95 - (int) (Math.random() * 30);
            route.setSafetyScore(baseScore);
        }
        
        List<String> reasons = new ArrayList<>();
        
        int verifiedHazards = 0;
        int nearbyPolice = (int) (Math.random() * 3) + 1; // Mocked logic: 1-3 police stations
        int nearbyHospitals = (int) (Math.random() * 4) + 1; // Mocked logic: 1-4 hospitals

        if (baseScore >= 90) {
            reasons.add("Route is well-lit and mostly clear of reported hazards.");
            reasons.add("Good presence of nearby emergency services.");
        } else if (baseScore >= 70) {
            reasons.add("Route has some reported hazards ahead.");
            reasons.add("Moderate presence of nearby emergency services.");
        } else {
            reasons.add("Multiple high-risk hazards reported along this path.");
            reasons.add("Proceed with caution or consider an alternative route.");
        }

        // Check for reports intersecting the route
        for (CommunityReport report : allReports) {
            if (report.getLocation() != null && route.getDecodedPath() != null) {
                LatLng reportLoc = new LatLng(report.getLocation().getLatitude(), report.getLocation().getLongitude());
                if (isNearRoute(reportLoc, route.getDecodedPath(), 150)) {
                    if (report.getSeverity() > 1) { // Assume verification if severity > 1 for mock purposes
                        verifiedHazards++;
                    }
                }
            }
        }

        if (verifiedHazards > 0) {
            reasons.add(verifiedHazards + " verified hazards reported recently.");
        }

        String riskLevel = "Safe";
        if (baseScore < 70) riskLevel = "Moderate";
        if (baseScore < 40) riskLevel = "High Risk";

        return new SafetyScoreResult(baseScore, riskLevel, reasons, nearbyPolice, nearbyHospitals, verifiedHazards);
    }

    private static boolean isNearRoute(LatLng point, List<LatLng> polyline, double maxDistanceMeters) {
        for (LatLng node : polyline) {
            if (calculateDistance(point, node) <= maxDistanceMeters) {
                return true;
            }
        }
        return false;
    }

    private static double calculateDistance(LatLng p1, LatLng p2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
        return results[0];
    }
}
