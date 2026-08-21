package com.riskfreeroutes.app.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.riskfreeroutes.app.model.CommunityReport;

import java.util.ArrayList;
import java.util.List;

/**
 * FirestoreReportsRepository.java
 *
 * This repository is responsible for fetching and listening to real-time updates
 * from the "community_reports" collection in Cloud Firestore.
 */
public class FirestoreReportsRepository {

    private static final String TAG = "ReportsRepo";
    private final FirebaseFirestore db;
    private final CollectionReference reportsRef;

    public FirestoreReportsRepository() {
        // Initialize Firestore
        db = FirebaseFirestore.getInstance();
        reportsRef = db.collection("community_reports");
    }

    /**
     * Gets a real-time LiveData stream of Community Reports.
     * Also performs client-side expiry filtering: if a report's expiryDate
     * has passed, we write status="expired" back to Firestore so future
     * queries skip it automatically.
     */
    public LiveData<List<CommunityReport>> getLiveReports() {
        MutableLiveData<List<CommunityReport>> liveData = new MutableLiveData<>();

        reportsRef.whereEqualTo("status", "active")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        return;
                    }

                    List<CommunityReport> reports = new ArrayList<>();
                    if (value != null) {
                        ReportRepository autoExpirer = new ReportRepository();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            CommunityReport report = doc.toObject(CommunityReport.class);
                            if (report == null || report.getLocation() == null) continue;

                            // CLIENT-SIDE AUTO-EXPIRY: if expiryDate passed, mark expired
                            if (report.isExpired()) {
                                autoExpirer.markExpired(doc.getId()); // write-back
                                continue; // Don't include in results
                            }

                            reports.add(report);
                        }
                    }

                    liveData.postValue(reports);
                });

        return liveData;
    }

    /**
     * Same as getLiveReports() but also returns doc IDs, needed by
     * ReportVerificationHelper to call voteYes/voteNo on the right document.
     *
     * Returns a Pair-like container: index i in reports matches index i in docIds.
     */
    public LiveData<ReportsWithIds> getLiveReportsWithIds() {
        MutableLiveData<ReportsWithIds> liveData = new MutableLiveData<>();

        reportsRef.whereEqualTo("status", "active")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        return;
                    }

                    List<CommunityReport> reports = new ArrayList<>();
                    List<String> docIds = new ArrayList<>();

                    if (value != null) {
                        ReportRepository autoExpirer = new ReportRepository();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            CommunityReport report = doc.toObject(CommunityReport.class);
                            if (report == null || report.getLocation() == null) continue;
                            if (report.isExpired()) {
                                autoExpirer.markExpired(doc.getId());
                                continue;
                            }
                            reports.add(report);
                            docIds.add(doc.getId());
                        }
                    }

                    liveData.postValue(new ReportsWithIds(reports, docIds));
                });

        return liveData;
    }

    /** Container pairing reports with their Firestore document IDs. */
    public static class ReportsWithIds {
        public final List<CommunityReport> reports;
        public final List<String> docIds;
        public ReportsWithIds(List<CommunityReport> reports, List<String> docIds) {
            this.reports = reports;
            this.docIds = docIds;
        }
    }



    /**
     * Submits a new incident report to Firestore.
     *
     * @param report   The CommunityReport to save.
     * @param callback Called with success or failure.
     */
    public void submitReport(CommunityReport report, SubmitCallback callback) {
        reportsRef.add(report)
            .addOnSuccessListener(ref -> {
                Log.d(TAG, "Report submitted: " + ref.getId());
                if (callback != null) callback.onSuccess();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to submit report", e);
                if (callback != null) callback.onFailure(e);
            });
    }

    /** Callback interface for report submission. */
    public interface SubmitCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
}


