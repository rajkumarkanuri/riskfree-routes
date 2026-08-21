package com.riskfreeroutes.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

/**
 * Journey.java — A single navigation session (one trip from A to B).
 *
 * Firestore path: users/{uid}/journey_history/{journeyId}
 *
 * WHY A SUBCOLLECTION?
 * Journey documents are private — they belong to ONE user and no one else
 * should be able to read them. By nesting them under users/{uid}/,
 * the security rules can enforce this with a single line.
 *
 * LIFECYCLE OF A JOURNEY DOCUMENT:
 * 1. Navigation starts → JourneyHistoryRepository.startJourney() creates this
 *    document with status = "in_progress"
 * 2. GPS ticks during navigation → we update distanceTraveledMeters incrementally
 * 3. Navigation ends → JourneyHistoryRepository.endJourney() sets:
 *    - endTimestamp = now()
 *    - actualDuration = (endTimestamp - startTimestamp) in seconds
 *    - status = "completed" or "ended_early"
 *    - safeArrivalNotified = true (if we sent a check-in notification)
 *
 * FIELD MAPPING (Firestore field name → Java field name, they must match exactly):
 *   origin              → origin  (GeoPoint)
 *   originAddress       → originAddress  (String)
 *   destination         → destination  (GeoPoint)
 *   destinationAddress  → destinationAddress  (String)
 *   routeType           → routeType  (String: "Safest" | "Fastest Safe" | "Shortest")
 *   safetyScore         → safetyScore  (int, 0–100)
 *   distance            → distance  (double, meters)
 *   estimatedDuration   → estimatedDuration  (long, seconds — from Directions API)
 *   actualDuration      → actualDuration  (long, seconds — computed at journey end)
 *   startTimestamp      → startTimestamp  (Timestamp)
 *   endTimestamp        → endTimestamp  (Timestamp, null until journey ends)
 *   status              → status  (String: "in_progress" | "completed" | "ended_early")
 *   safeArrivalNotified → safeArrivalNotified  (boolean)
 */
public class Journey {

    // ── WHO & WHERE ───────────────────────────────────────────────────────────
    // userId is NOT stored in the document itself — it's encoded in the path.
    // path = users/{userId}/journey_history/{journeyId}
    // We keep it as a field too for convenience in code (and backwards compat).
    private String userId;

    // GPS coordinates of the starting point
    private GeoPoint origin;
    // Human-readable address of the starting point (e.g., "Connaught Place, Delhi")
    private String originAddress;

    // GPS coordinates of the destination
    private GeoPoint destination;
    // Human-readable address of the destination
    private String destinationAddress;

    // ── ROUTE METADATA ────────────────────────────────────────────────────────

    // Which route type did the user choose?
    // Valid: "Safest" | "Fastest Safe" | "Shortest"
    private String routeType;

    // Safety score calculated by SafetyScoreCalculator for this specific route (0–100)
    private int safetyScore;

    // Total route distance in METERS (as returned by Directions API)
    private double distance;

    // Duration estimated by Directions API at the START of the journey (in seconds)
    // This is fixed when navigation begins.
    private long estimatedDuration;

    // Duration actually taken, calculated at the END: endTimestamp - startTimestamp
    // Allows us to compare "did this route take longer than expected?"
    private long actualDuration;

    // ── TIMESTAMPS ────────────────────────────────────────────────────────────

    private Timestamp startTimestamp;

    // null while the journey is in progress; set when navigation ends
    private Timestamp endTimestamp;

    // ── STATUS ────────────────────────────────────────────────────────────────

    // "in_progress" → user is currently navigating
    // "completed"   → user reached the destination
    // "ended_early" → user tapped "End Navigation" before arriving
    private String status;

    // ── SAFE ARRIVAL CHECK-IN ─────────────────────────────────────────────────
    // When navigation completes, the app can optionally notify trusted contacts
    // "Your contact has arrived safely". This flag records whether we sent that.
    private boolean safeArrivalNotified;

    // ── REQUIRED EMPTY CONSTRUCTOR ────────────────────────────────────────────
    // Firestore needs this to convert documents back to Journey objects
    public Journey() {}

    // ── CONSTRUCTOR (for creating at navigation start) ────────────────────────
    public Journey(String userId, GeoPoint origin, String originAddress,
                   GeoPoint destination, String destinationAddress,
                   String routeType, int safetyScore,
                   double distanceMeters, long estimatedDurationSeconds) {
        this.userId = userId;
        this.origin = origin;
        this.originAddress = originAddress;
        this.destination = destination;
        this.destinationAddress = destinationAddress;
        this.routeType = routeType;
        this.safetyScore = safetyScore;
        this.distance = distanceMeters;
        this.estimatedDuration = estimatedDurationSeconds;
        this.actualDuration = 0;
        this.startTimestamp = Timestamp.now();
        this.endTimestamp = null;
        this.status = "in_progress";
        this.safeArrivalNotified = false;
    }

    // ── LEGACY CONSTRUCTOR — kept for backward compatibility ─────────────────
    // Existing code that uses the old constructor still works.
    public Journey(String userId, String originName, String destinationName,
                   GeoPoint origin, GeoPoint destination, String routeType) {
        this.userId = userId;
        this.origin = origin;
        this.originAddress = originName;
        this.destination = destination;
        this.destinationAddress = destinationName;
        this.routeType = routeType;
        this.startTimestamp = Timestamp.now();
        this.status = "in_progress";
        this.safeArrivalNotified = false;
    }

    // ── GETTERS & SETTERS ─────────────────────────────────────────────────────

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public GeoPoint getOrigin() { return origin; }
    public void setOrigin(GeoPoint origin) { this.origin = origin; }

    public String getOriginAddress() { return originAddress; }
    public void setOriginAddress(String originAddress) { this.originAddress = originAddress; }

    // Legacy alias — old code that calls getOriginName() still works
    public String getOriginName() { return originAddress; }
    public void setOriginName(String name) { this.originAddress = name; }

    public GeoPoint getDestination() { return destination; }
    public void setDestination(GeoPoint destination) { this.destination = destination; }

    public String getDestinationAddress() { return destinationAddress; }
    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    // Legacy alias
    public String getDestinationName() { return destinationAddress; }
    public void setDestinationName(String name) { this.destinationAddress = name; }

    public String getRouteType() { return routeType; }
    public void setRouteType(String routeType) { this.routeType = routeType; }

    public int getSafetyScore() { return safetyScore; }
    public void setSafetyScore(int safetyScore) { this.safetyScore = safetyScore; }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }

    // Legacy aliases for old code using distanceKm / durationMinutes
    public double getDistanceKm() { return distance / 1000.0; }
    public void setDistanceKm(double km) { this.distance = km * 1000.0; }

    public long getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(long estimatedDuration) {
        this.estimatedDuration = estimatedDuration;
    }

    public long getActualDuration() { return actualDuration; }
    public void setActualDuration(long actualDuration) { this.actualDuration = actualDuration; }

    // Legacy alias — old code using durationMinutes
    public int getDurationMinutes() { return (int)(estimatedDuration / 60); }
    public void setDurationMinutes(int minutes) { this.estimatedDuration = (long) minutes * 60; }

    public Timestamp getStartTimestamp() { return startTimestamp; }
    public void setStartTimestamp(Timestamp startTimestamp) { this.startTimestamp = startTimestamp; }

    // Legacy alias
    public Timestamp getStartedAt() { return startTimestamp; }
    public void setStartedAt(Timestamp ts) { this.startTimestamp = ts; }

    public Timestamp getEndTimestamp() { return endTimestamp; }
    public void setEndTimestamp(Timestamp endTimestamp) { this.endTimestamp = endTimestamp; }

    // Legacy alias
    public Timestamp getCompletedAt() { return endTimestamp; }
    public void setCompletedAt(Timestamp ts) { this.endTimestamp = ts; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSafeArrivalNotified() { return safeArrivalNotified; }
    public void setSafeArrivalNotified(boolean safeArrivalNotified) {
        this.safeArrivalNotified = safeArrivalNotified;
    }
}
