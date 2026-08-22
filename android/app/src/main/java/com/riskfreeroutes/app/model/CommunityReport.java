package com.riskfreeroutes.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

import java.util.List;

/**
 * CommunityReport.java — Data model for a safety/hazard incident reported by a user.
 *
 * Firestore Collection: "community_reports"
 *
 * WHY THIS EXISTS:
 * This is the central data object that drives both the Safety Score Engine and the
 * Heatmap. Every time a user submits a report via SubmitReportActivity, one of
 * these documents is written to Firestore. Every time another user opens the app,
 * these documents are fetched and used to score routes.
 *
 * EXPIRY LOGIC (handled at write time in ReportRepository):
 * - Safety / Emergency reports expire in 6 hours (time-sensitive)
 * - Weather reports expire in 12 hours
 * - Road Issues / Infrastructure expire in 3 days
 *
 * VERIFICATION:
 * - Other users who are physically near an active report see a prompt:
 *   "Is this still happening?" → Yes (+1) / No (-1)
 * - If verificationCount drops below -3, we set status = "expired" early
 */
public class CommunityReport {

    // ── CORE CLASSIFICATION ──────────────────────────────────────────────────

    /** Top-level category: "Road Issues" | "Infrastructure" | "Safety" |
     *  "Weather" | "Emergency" | "Other" */
    private String mainCategory;

    /** Sub-category within mainCategory. E.g.:
     *  Road Issues → "Pothole", "Accident", "Road Closure", "Debris"
     *  Infrastructure → "Broken Streetlight", "No Signal", "Damaged Sign"
     *  Safety → "Suspicious Activity", "Theft", "Assault", "Harassment"
     *  Weather → "Flood", "Fog", "Ice", "Fallen Tree"
     *  Emergency → "Fire", "Medical", "Gas Leak"
     *  Other → "Other" */
    private String subCategory;

    /** Freeform description from the user. Required. */
    private String description;

    // ── LOCATION ─────────────────────────────────────────────────────────────

    /** Where it happened — Firestore GeoPoint(lat, lng). */
    private GeoPoint location;

    // ── MEDIA ────────────────────────────────────────────────────────────────

    /** Optional Cloudinary URL of a photo attached to the report. */
    private String imageUrl;

    // ── IDENTITY & TIMING ────────────────────────────────────────────────────

    /** Firebase Auth UID of the submitting user. */
    private String reporterId;

    /** Server-generated timestamp of when the report was submitted.
     *  Use Timestamp.now() as default; Firestore will update with server time. */
    private Timestamp timestamp;

    /** When this report should automatically expire.
     *  Calculated based on mainCategory at write time. */
    private Timestamp expiryDate;

    // ── STATUS & VERIFICATION ────────────────────────────────────────────────

    /** "active" or "expired". Checked both server-side (Firestore query) and
     *  client-side (compare expiryDate to now). */
    private String status;

    /** Running tally of community verifications:
     *  +1 for each "Yes, still happening" vote
     *  -1 for each "No, resolved" vote
     *  If this drops below -3, status is set to "expired" early. */
    private int verificationCount;

    /** List of Firebase Auth UIDs who have already voted on this report.
     *  Prevents duplicate votes from the same user. */
    private List<String> verifiedBy;

    /** Legacy severity field (1–5). Kept for backward compatibility with
     *  the existing SafetyScoreCalculator weight logic. */
    private int severity;

    /** Precomputed weight of the report: severityMultiplier × categoryBaseWeight */
    private int reportWeight;

    /** Whether the community has verified this report (verificationCount >= 2). */
    private boolean verified;

    // ── CONSTRUCTORS ─────────────────────────────────────────────────────────

    /** Required no-arg constructor for Firestore deserialization. */
    public CommunityReport() {}

    /** Full constructor used when submitting a new report. */
    public CommunityReport(String reporterId, String mainCategory, String subCategory,
                           String description, GeoPoint location, int severity,
                           String imageUrl, Timestamp expiryDate) {
        this.reporterId = reporterId;
        this.mainCategory = mainCategory;
        this.subCategory = subCategory;
        this.description = description;
        this.location = location;
        this.severity = severity;
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.timestamp = Timestamp.now();
        this.expiryDate = expiryDate;
        this.status = "active";
        this.verificationCount = 0;
        this.verified = false;
        this.reportWeight = 0;
    }

    /** Legacy constructor — kept for backward compatibility. */
    public CommunityReport(String reporterId, String type, String description,
                           GeoPoint location, int severity) {
        this.reporterId = reporterId;
        this.mainCategory = "Safety";
        this.subCategory = type;
        this.description = description;
        this.location = location;
        this.severity = severity;
        this.imageUrl = "";
        this.verified = false;
        this.reportWeight = 0;
        this.status = "active";
        this.timestamp = Timestamp.now();
        this.verificationCount = 0;
        // Default 6-hour expiry for legacy Safety reports
        this.expiryDate = new Timestamp(
            System.currentTimeMillis() / 1000 + 6 * 3600, 0);
    }

    // ── GETTERS & SETTERS ────────────────────────────────────────────────────

    public String getMainCategory() { return mainCategory; }
    public void setMainCategory(String mainCategory) { this.mainCategory = mainCategory; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    /** Returns subCategory for display; falls back to mainCategory. */
    public String getType() { return subCategory != null ? subCategory : mainCategory; }
    /** Legacy setter (maps to subCategory). */
    public void setType(String type) { this.subCategory = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public Timestamp getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Timestamp expiryDate) { this.expiryDate = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getVerificationCount() { return verificationCount; }
    public void setVerificationCount(int verificationCount) { this.verificationCount = verificationCount; }

    public List<String> getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(List<String> verifiedBy) { this.verifiedBy = verifiedBy; }

    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = severity; }

    public int getReportWeight() { return reportWeight; }
    public void setReportWeight(int reportWeight) { this.reportWeight = reportWeight; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    /** Returns true if this report has passed its expiry time. */
    @com.google.firebase.firestore.Exclude
    public boolean isExpired() {
        if (expiryDate == null) return false;
        return expiryDate.toDate().before(new java.util.Date());
    }

    /** Returns true if this report is effectively active (status=active AND not expired). */
    @com.google.firebase.firestore.Exclude
    public boolean isActive() {
        return "active".equalsIgnoreCase(status) && !isExpired();
    }
}
