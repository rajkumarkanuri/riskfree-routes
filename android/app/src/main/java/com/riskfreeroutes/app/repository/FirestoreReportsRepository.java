package com.riskfreeroutes.app.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.model.CommunityReport;

import java.util.ArrayList;
import java.util.Collections;
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
     * In-memory sorting by timestamp prevents FAILED_PRECONDITION index errors.
     */
    public LiveData<List<CommunityReport>> getLiveReports() {
        MutableLiveData<List<CommunityReport>> liveData = new MutableLiveData<>();

        reportsRef.whereEqualTo("status", "active")
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

                        // Sort by timestamp descending
                        Collections.sort(reports, (r1, r2) -> {
                            if (r1.getTimestamp() == null && r2.getTimestamp() == null) return 0;
                            if (r1.getTimestamp() == null) return 1;
                            if (r2.getTimestamp() == null) return -1;
                            return r2.getTimestamp().compareTo(r1.getTimestamp());
                        });
                    }

                    liveData.postValue(reports);
                });

        return liveData;
    }

    /**
     * Same as getLiveReports() but also returns doc IDs, needed by
     * ReportVerificationHelper to call voteYes/voteNo on the right document.
     */
    public LiveData<ReportsWithIds> getLiveReportsWithIds() {
        MutableLiveData<ReportsWithIds> liveData = new MutableLiveData<>();

        reportsRef.whereEqualTo("status", "active")
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
                        List<ReportDocPair> pairs = new ArrayList<>();

                        for (DocumentSnapshot doc : value.getDocuments()) {
                            CommunityReport report = doc.toObject(CommunityReport.class);
                            if (report == null || report.getLocation() == null) continue;
                            if (report.isExpired()) {
                                autoExpirer.markExpired(doc.getId());
                                continue;
                            }
                            pairs.add(new ReportDocPair(report, doc.getId()));
                        }

                        // Sort by timestamp descending
                        Collections.sort(pairs, (p1, p2) -> {
                            if (p1.report.getTimestamp() == null && p2.report.getTimestamp() == null) return 0;
                            if (p1.report.getTimestamp() == null) return 1;
                            if (p2.report.getTimestamp() == null) return -1;
                            return p2.report.getTimestamp().compareTo(p1.report.getTimestamp());
                        });

                        for (ReportDocPair pair : pairs) {
                            reports.add(pair.report);
                            docIds.add(pair.docId);
                        }
                    }

                    liveData.postValue(new ReportsWithIds(reports, docIds));
                });

        return liveData;
    }

    private static class ReportDocPair {
        final CommunityReport report;
        final String docId;
        ReportDocPair(CommunityReport report, String docId) {
            this.report = report;
            this.docId = docId;
        }
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
