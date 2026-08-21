package com.riskfreeroutes.app.model;

import com.google.android.gms.maps.model.LatLng;
import java.util.List;

/**
 * Represents a decoded route from the Directions API.
 */
public class Route {
    public static class RouteStep {
        public final String instruction;
        public final LatLng endLocation;
        
        public RouteStep(String instruction, LatLng endLocation) {
            this.instruction = instruction;
            this.endLocation = endLocation;
        }
    }

    private final String summary;
    private final String distance;
    private final String duration;
    private final String durationInTraffic; // ETA considering live traffic
    private final String trafficCondition; // "heavy traffic", "moderate traffic", "low traffic"
    private final List<LatLng> decodedPath;
    
    private int safetyScore; // Calculated locally later
    private String routeType; // "Safest Route", "Fastest Safe Route"
    private boolean isSelected; // True if this is the highlighted route
    private String safetyLabel; // Human-readable safety condition set by calculator
    private final List<RouteStep> steps;

    public Route(String summary, String distance, String duration, String durationInTraffic, String trafficCondition, List<LatLng> decodedPath, List<RouteStep> steps) {
        this.summary = summary;
        this.distance = distance;
        this.duration = duration;
        this.durationInTraffic = durationInTraffic;
        this.trafficCondition = trafficCondition;
        this.decodedPath = decodedPath;
        this.steps = steps;
        this.isSelected = false;
    }

    public String getSummary() { return summary; }
    public String getDistance() { return distance; }
    public String getDuration() { return duration; }
    public String getDurationInTraffic() { return durationInTraffic; }
    public String getTrafficCondition() { return trafficCondition; }
    public List<LatLng> getDecodedPath() { return decodedPath; }
    public List<LatLng> getPath() { return decodedPath; }
    public List<RouteStep> getSteps() { return steps; }
    
    public int getSafetyScore() { return safetyScore; }
    public void setSafetyScore(int safetyScore) { this.safetyScore = safetyScore; }

    public String getRouteType() { return routeType; }
    public void setRouteType(String routeType) { this.routeType = routeType; }
    
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    public String getSafetyLabel() { return safetyLabel != null ? safetyLabel : trafficCondition; }
    public void setSafetyLabel(String safetyLabel) { this.safetyLabel = safetyLabel; }
}

