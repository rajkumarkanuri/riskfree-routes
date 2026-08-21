package com.riskfreeroutes.app.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.model.ReportCategory;

import java.util.List;
import java.util.Map;

/**
 * ReportRepository.java — Handles ALL data operations for community reports.
 *
 * WHY THIS EXISTS (MVVM pattern):
 * The SubmitReportActivity should NOT talk directly to Firestore or Cloudinary.
 * Instead, it talks to ReportViewModel, which calls this Repository.
 * This keeps the UI layer "dumb" (just showing states) while all network/DB
 * logic lives here. Easy to test, easy to swap implementations.
 *
 * RESPONSIBILITIES:
 * 1. Upload a photo to Cloudinary (if provided) and get back a URL
 * 2. Write the full CommunityReport document to Firestore
 * 3. Handle Yes/No verification votes on existing reports
 */
public class ReportRepository {

    private static final String TAG = "ReportRepository";
    private static final String COLLECTION = "community_reports";

    /** Cloudinary unsigned upload preset (no backend required — safe for a diploma project). */
    private static final String CLOUDINARY_UPLOAD_PRESET = "riskfree_unsigned";

    /** Drop below this verification count → auto-expire the report. */
    private static final int EXPIRY_THRESHOLD = -3;

    private final FirebaseFirestore db;

    public ReportRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PHOTO UPLOAD (Cloudinary)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Uploads a photo Uri to Cloudinary using an unsigned upload preset.
     *
     * HOW CLOUDINARY UNSIGNED UPLOAD WORKS:
     * We create an "unsigned upload preset" in the Cloudinary dashboard.
     * This allows direct client → Cloudinary uploads without needing a
     * backend server to sign each request. Fine for a diploma project.
     *
     * @param context  Android context for the Cloudinary SDK.
     * @param imageUri The local file Uri (from camera or gallery picker).
     * @param callback Called with the Cloudinary secure_url on success, or error on failure.
     */
    public void uploadPhoto(Context context, Uri imageUri, PhotoUploadCallback callback) {
        // Make sure Cloudinary is initialized (RiskFreeRoutesApp should call MediaManager.init)
        try {
            MediaManager.get().upload(imageUri)
                .unsigned(CLOUDINARY_UPLOAD_PRESET)
                .option("folder", "community_reports")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Cloudinary upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        int progress = (int) (100 * bytes / totalBytes);
                        callback.onProgress(progress);
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // Cloudinary returns a "secure_url" field — the HTTPS URL of the uploaded image
                        String url = (String) resultData.get("secure_url");
                        Log.d(TAG, "Cloudinary upload success: " + url);
                        callback.onSuccess(url);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Cloudinary upload error: " + error.getDescription());
                        callback.onFailure(new Exception(error.getDescription()));
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        Log.w(TAG, "Cloudinary upload rescheduled: " + error.getDescription());
                    }
                })
                .dispatch();
        } catch (Exception e) {
            Log.e(TAG, "Cloudinary not initialized or upload failed", e);
            callback.onFailure(e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBMIT REPORT (Firestore write)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Writes the completed CommunityReport to Firestore.
     *
     * Before writing, we set the expiryDate based on mainCategory using
     * ReportCategory.expiryFor() — so Safety/Emergency reports last 6h,
     * Weather 12h, and Road Issues/Infrastructure 3 days.
     *
     * @param report   The CommunityReport to save.
     * @param callback Called on success (with the new Firestore document ID) or failure.
     */
    public void submitReport(CommunityReport report, SubmitCallback callback) {
        // Ensure expiry is set correctly based on main category
        if (report.getExpiryDate() == null) {
            report.setExpiryDate(ReportCategory.expiryFor(report.getMainCategory()));
        }

        // Ensure default severity is set
        if (report.getSeverity() == 0) {
            report.setSeverity(ReportCategory.defaultSeverity(report.getMainCategory()));
        }

        // Compute reportWeight (severity * base weight)
        int baseWeight = 10; // Default for Road Issues / Infrastructure / Other
        if (ReportCategory.EMERGENCY.equals(report.getMainCategory())) baseWeight = -30;
        else if (ReportCategory.SAFETY.equals(report.getMainCategory())) baseWeight = -25;
        else if (ReportCategory.WEATHER.equals(report.getMainCategory())) baseWeight = -15;
        report.setReportWeight(report.getSeverity() * baseWeight);

        db.collection(COLLECTION)
            .add(report)
            .addOnSuccessListener(ref -> {
                Log.d(TAG, "Report submitted: " + ref.getId());
                if (report.getReporterId() != null && !report.getReporterId().isEmpty()) {
                    db.collection("users").document(report.getReporterId())
                        .update("reportsSubmitted", FieldValue.increment(1))
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to increment reportsSubmitted", e));
                }
                if (callback != null) callback.onSuccess(ref.getId());
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to submit report", e);
                if (callback != null) callback.onFailure(e);
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERIFICATION (Yes/No votes)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Records a "Yes, still happening" vote from a user on a specific report.
     *
     * HOW DUPLICATE-VOTE PREVENTION WORKS:
     * We use a Firestore TRANSACTION — a read + write that happens atomically.
     * Inside the transaction:
     *   1. We READ the document to get the current verifiedBy array.
     *   2. We CHECK if the userId is already in that array.
     *   3. If YES → abort (don't change anything). The user already voted.
     *   4. If NO  → increment verificationCount and add userId to verifiedBy.
     *
     * WHY A TRANSACTION AND NOT JUST arrayUnion?
     * arrayUnion() stops the user from appearing twice in the array — but it
     * doesn't stop verificationCount from incrementing again. A user could tap
     * "Yes" 10 times: the array stays correct but the count goes to +10.
     * The transaction reads the current state and only writes if the user is new.
     *
     * @param reportDocId  Firestore document ID of the report to vote on.
     * @param userId       Firebase Auth UID of the voting user.
     * @param callback     Optional: called with success=true/false after the vote.
     */
    public void voteYes(String reportDocId, String userId, VoteCallback callback) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // Step 1: Read the current state of the report inside the transaction
            DocumentSnapshot snapshot = transaction.get(
                db.collection(COLLECTION).document(reportDocId));

            // Step 2: Check if this user has already voted
            List<String> verifiedBy = getVerifiedBy(snapshot);
            if (verifiedBy != null && verifiedBy.contains(userId)) {
                // ALREADY VOTED — throw an exception to abort the transaction cleanly.
                // This is the standard way to abort a Firestore transaction.
                throw new RuntimeException("ALREADY_VOTED");
            }

            // Step 3: User hasn't voted yet — increment count and add to array
            Long currentCount = snapshot.getLong("verificationCount");
            long newCount = (currentCount != null ? currentCount : 0) + 1;

            if (newCount == 2) {
                String reporterId = snapshot.getString("reporterId");
                if (reporterId != null && !reporterId.isEmpty()) {
                    // All gets must happen before any updates in a transaction
                    DocumentSnapshot userSnap = transaction.get(db.collection("users").document(reporterId));
                    
                    Long currentTrustScore = userSnap.getLong("trustScore");
                    long trustScore = currentTrustScore != null ? currentTrustScore : 50;
                    trustScore = Math.min(100, Math.max(0, trustScore + 5)); // +5 and clamp
                    
                    Long currentVerifiedReports = userSnap.getLong("verifiedReports");
                    long verifiedReports = (currentVerifiedReports != null ? currentVerifiedReports : 0) + 1;
                    
                    String badge = userSnap.getString("badge");
                    boolean awardBadge = verifiedReports >= 10 && trustScore >= 70 && !"Trusted Reporter".equals(badge);
                    
                    transaction.update(
                        db.collection("users").document(reporterId),
                        "verifiedReports", verifiedReports,
                        "trustScore", trustScore
                    );
                    
                    if (awardBadge) {
                        transaction.update(
                            db.collection("users").document(reporterId),
                            "badge", "Trusted Reporter"
                        );
                        // One-time celebratory notification
                        java.util.Map<String, Object> notification = new java.util.HashMap<>();
                        notification.put("type", "Community Alert");
                        notification.put("title", "You earned the Trusted Reporter badge!");
                        notification.put("message", "Thank you for consistently submitting accurate reports.");
                        notification.put("timestamp", FieldValue.serverTimestamp());
                        notification.put("read", false);
                        transaction.set(db.collection("users").document(reporterId).collection("notifications").document(), notification);
                    }
                }

                // Newly verified!
                transaction.update(
                    db.collection(COLLECTION).document(reportDocId),
                    "verificationCount", FieldValue.increment(1),
                    "verifiedBy", FieldValue.arrayUnion(userId),
                    "verified", true
                );
            } else {
                transaction.update(
                    db.collection(COLLECTION).document(reportDocId),
                    "verificationCount", FieldValue.increment(1),
                    "verifiedBy", FieldValue.arrayUnion(userId)
                );
            }
            return null; // transaction committed
        })
        .addOnSuccessListener(unused -> {
            Log.d(TAG, "Yes vote recorded for: " + reportDocId);
            if (callback != null) callback.onResult(true, null);
        })
        .addOnFailureListener(e -> {
            if ("ALREADY_VOTED".equals(e.getMessage())) {
                // Not a real error — user already voted, just ignore silently
                Log.d(TAG, "Ignored duplicate Yes vote from: " + userId);
                if (callback != null) callback.onResult(false, "You have already voted on this report.");
            } else {
                Log.e(TAG, "Failed to record Yes vote", e);
                if (callback != null) callback.onResult(false, "Vote failed: " + e.getMessage());
            }
        });
    }

    /** Legacy overload — for existing call sites that don't need the callback. */
    public void voteYes(String reportDocId, String userId) {
        voteYes(reportDocId, userId, null);
    }

    /**
     * Records a "No, already resolved" vote from a user.
     *
     * Same duplicate-vote guard as voteYes() — uses a transaction to
     * atomically check verifiedBy before decrementing verificationCount.
     *
     * AUTO-EXPIRY:
     * If verificationCount would drop below EXPIRY_THRESHOLD (-3) after this vote,
     * we also set status = "expired". The crowd is saying this issue is resolved.
     * We read the current count inside the transaction so the threshold check is accurate.
     *
     * @param reportDocId  Firestore document ID of the report.
     * @param userId       Firebase Auth UID of the voting user.
     * @param callback     Optional: called with success=true/false after the vote.
     */
    public void voteNo(String reportDocId, String userId, VoteCallback callback) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            // Step 1: Read current state
            DocumentSnapshot snapshot = transaction.get(
                db.collection(COLLECTION).document(reportDocId));

            // Step 2: Check for duplicate vote
            List<String> verifiedBy = getVerifiedBy(snapshot);
            if (verifiedBy != null && verifiedBy.contains(userId)) {
                throw new RuntimeException("ALREADY_VOTED");
            }

            // Step 3: What will the new count be after this vote?
            Long currentCount = snapshot.getLong("verificationCount");
            long newCount = (currentCount != null ? currentCount : 0) - 1;

            if (newCount <= EXPIRY_THRESHOLD) {
                // Determine trustScore penalty before any updates
                String reporterId = snapshot.getString("reporterId");
                if (reporterId != null && !reporterId.isEmpty()) {
                    DocumentSnapshot userSnap = transaction.get(db.collection("users").document(reporterId));
                    Long currentTrustScore = userSnap.getLong("trustScore");
                    long trustScore = currentTrustScore != null ? currentTrustScore : 50;
                    trustScore = Math.min(100, Math.max(0, trustScore - 10)); // -10 and clamp
                    
                    transaction.update(
                        db.collection("users").document(reporterId),
                        "trustScore", trustScore
                    );
                }

                // Auto-expire: the crowd has decided this report is stale/resolved
                transaction.update(
                    db.collection(COLLECTION).document(reportDocId),
                    "verificationCount", FieldValue.increment(-1),
                    "verifiedBy",        FieldValue.arrayUnion(userId),
                    "status",            "expired"
                );
                Log.d(TAG, "Report will be auto-expired: " + reportDocId);
            } else {
                transaction.update(
                    db.collection(COLLECTION).document(reportDocId),
                    "verificationCount", FieldValue.increment(-1),
                    "verifiedBy",        FieldValue.arrayUnion(userId)
                );
            }
            return null;
        })
        .addOnSuccessListener(unused -> {
            Log.d(TAG, "No vote recorded for: " + reportDocId);
            if (callback != null) callback.onResult(true, null);
        })
        .addOnFailureListener(e -> {
            if ("ALREADY_VOTED".equals(e.getMessage())) {
                Log.d(TAG, "Ignored duplicate No vote from: " + userId);
                if (callback != null) callback.onResult(false, "You have already voted on this report.");
            } else {
                Log.e(TAG, "Failed to record No vote", e);
                if (callback != null) callback.onResult(false, "Vote failed: " + e.getMessage());
            }
        });
    }

    /** Legacy overload — for existing call sites that don't need the callback.
     *  The old signature included currentVoteCount but that was unreliable
     *  (could be stale by the time the call arrives). We now read it inside
     *  the transaction, so currentVoteCount is ignored here for safety. */
    public void voteNo(String reportDocId, String userId, int currentVoteCount) {
        voteNo(reportDocId, userId, null);
    }

    /**
     * Client-side marks a report as expired in Firestore.
     * Called when we detect expiryDate has passed during a read.
     */
    public void markExpired(String reportDocId) {
        db.collection(COLLECTION).document(reportDocId)
            .update("status", "expired")
            .addOnSuccessListener(unused -> Log.d(TAG, "Marked expired: " + reportDocId))
            .addOnFailureListener(e -> Log.w(TAG, "Could not mark expired: " + e.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CALLBACKS
    // ─────────────────────────────────────────────────────────────────────────

    public interface PhotoUploadCallback {
        void onProgress(int percent);
        void onSuccess(String cloudinaryUrl);
        void onFailure(Exception e);
    }

    public interface SubmitCallback {
        void onSuccess(String documentId);
        void onFailure(Exception e);
    }

    /**
     * Callback for voteYes() and voteNo() results.
     * success=true  → vote was recorded
     * success=false → user already voted, or network error (check message)
     */
    public interface VoteCallback {
        void onResult(boolean success, String message);
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    /**
     * Safely reads the "verifiedBy" field from a Firestore snapshot as a List<String>.
     *
     * WHY THIS EXISTS:
     * snapshot.get("verifiedBy") returns Object (could be List<Object>, null, etc.)
     * Casting it directly to List<String> compiles but emits an "unchecked cast" warning
     * because at runtime Java cannot verify the generic type due to type erasure.
     *
     * This helper:
     *   1. Returns null if the field is missing
     *   2. Iterates the list and builds a typed List<String> — fully type-safe at runtime
     *   3. Silences the warning without using @SuppressWarnings
     *
     * @param snapshot Firestore document snapshot to read from.
     * @return List<String> of UIDs who have voted, or null if the field doesn't exist.
     */
    @SuppressWarnings("unchecked")
    private List<String> getVerifiedBy(com.google.firebase.firestore.DocumentSnapshot snapshot) {
        Object raw = snapshot.get("verifiedBy");
        if (raw instanceof List) {
            return (List<String>) raw;
        }
        return null;
    }
}

