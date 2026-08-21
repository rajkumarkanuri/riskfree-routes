package com.riskfreeroutes.app.utils;

import android.location.Location;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.SphericalUtil;
import com.riskfreeroutes.app.databinding.DialogVerifyReportBinding;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.repository.ReportRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ReportVerificationHelper.java — Proximity-triggered Yes/No verification for community reports.
 *
 * WHY THIS EXISTS:
 * Community reports go stale quickly (a pothole gets fixed, a suspicious person leaves).
 * Instead of relying only on the timer-based expiry, we let people who are PHYSICALLY
 * NEAR the report confirm whether it is still happening. This crowdsourced verification
 * keeps the safety data as accurate and current as possible.
 *
 * HOW IT WORKS:
 * 1. On each location update from the user (called from HomeActivity's location observer),
 *    we iterate over all active reports.
 * 2. If the user is within PROXIMITY_METERS (150m) of a report they did NOT create
 *    and have NOT already voted on in this session, we show the dialog.
 * 3. To avoid spamming the user, we keep a Set of already-shown report IDs per session.
 * 4. The user's Yes/No vote is written to Firestore via ReportRepository.
 *
 * USAGE (from HomeActivity):
 *   verificationHelper = new ReportVerificationHelper(this);
 *   // In your location observer:
 *   verificationHelper.checkNearbyReports(currentLocation, reports, firestoreDocIds);
 */
public class ReportVerificationHelper {

    private static final String TAG = "VerificationHelper";

    /** Show the dialog when user is within this distance of a report. */
    private static final double PROXIMITY_METERS = 150.0;

    private final FragmentActivity activity;
    private final ReportRepository repository;
    private final String currentUserId;

    /**
     * Tracks which report IDs have ALREADY shown a verification dialog this session.
     * Resets when the app is killed — one prompt per report per session is enough.
     */
    private final Set<String> shownThisSession = new HashSet<>();

    public ReportVerificationHelper(FragmentActivity activity) {
        this.activity = activity;
        this.repository = new ReportRepository();
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
            : "anonymous";
    }

    /**
     * Called on each significant location update.
     * Checks if the user is within 150m of any active report they haven't voted on.
     *
     * @param userLocation  The user's current location.
     * @param reports       All currently active community reports.
     * @param docIds        Parallel list of Firestore document IDs (same index as reports).
     *                      This is required because CommunityReport doesn't store its own ID.
     */
    public void checkNearbyReports(Location userLocation,
                                   List<CommunityReport> reports,
                                   List<String> docIds) {
        if (userLocation == null || reports == null || reports.isEmpty()) return;

        LatLng userLatLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());

        for (int i = 0; i < reports.size(); i++) {
            CommunityReport report = reports.get(i);
            String docId = (docIds != null && i < docIds.size()) ? docIds.get(i) : null;

            if (docId == null) continue;
            if (!report.isActive()) continue;
            if (currentUserId.equals(report.getReporterId())) continue; // Skip own reports
            if (shownThisSession.contains(docId)) continue; // Already shown this session

            // Check if user has already voted (verifiedBy array)
            if (report.getVerifiedBy() != null && report.getVerifiedBy().contains(currentUserId)) continue;

            if (report.getLocation() == null) continue;

            LatLng reportLatLng = new LatLng(
                report.getLocation().getLatitude(),
                report.getLocation().getLongitude()
            );

            double distanceMeters = SphericalUtil.computeDistanceBetween(userLatLng, reportLatLng);

            if (distanceMeters <= PROXIMITY_METERS) {
                // Mark as shown BEFORE showing dialog to prevent race condition
                shownThisSession.add(docId);
                Log.d(TAG, "Showing verification for: " + docId + " (dist=" + (int)distanceMeters + "m)");
                showVerificationDialog(report, docId);
                break; // Only show ONE dialog at a time per location update
            }
        }
    }

    /**
     * Shows the "Is this still happening?" bottom sheet dialog.
     *
     * @param report The report to verify.
     * @param docId  Its Firestore document ID.
     */
    private void showVerificationDialog(CommunityReport report, String docId) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        DialogVerifyReportBinding binding = DialogVerifyReportBinding.inflate(
            LayoutInflater.from(activity), null, false);

        // Build the prompt text: "Pothole reported here. Is this still happening?"
        String subCat = report.getSubCategory() != null ? report.getSubCategory()
                      : report.getMainCategory() != null ? report.getMainCategory()
                      : "Safety issue";
        binding.tvReportDescription.setText(
            subCat + " reported nearby.\nIs this still happening?"
        );

        // YES — increment verificationCount
        binding.btnVerifyYes.setOnClickListener(v -> {
            repository.voteYes(docId, currentUserId);
            dialog.dismiss();
        });

        // NO — decrement verificationCount (may auto-expire if count ≤ -3)
        binding.btnVerifyNo.setOnClickListener(v -> {
            repository.voteNo(docId, currentUserId, report.getVerificationCount());
            dialog.dismiss();
        });

        dialog.setContentView(binding.getRoot());
        dialog.show();
    }
}
