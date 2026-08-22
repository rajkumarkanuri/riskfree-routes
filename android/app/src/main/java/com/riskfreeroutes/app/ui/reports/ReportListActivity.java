package com.riskfreeroutes.app.ui.reports;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityReportListBinding;
import com.riskfreeroutes.app.databinding.ItemMyReportBinding;
import com.riskfreeroutes.app.model.CommunityReport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ReportListActivity — Displays the logged-in user's submitted hazard reports
 * with intentional status differentiation, relative timestamps, and filtering.
 */
public class ReportListActivity extends AppCompatActivity {

    private ActivityReportListBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ReportAdapter adapter;

    private final List<CommunityReport> rawReportsList = new ArrayList<>();
    private final List<CommunityReport> filteredReportsList = new ArrayList<>();

    private enum FilterMode { ALL, ACTIVE, RESOLVED }
    private FilterMode currentFilter = FilterMode.ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        setupToolbar();
        setupFilters();
        setupRecyclerView();
        setupClickListeners();
        loadReports();
    }

    private void setupToolbar() {
        binding.btnBack.setNavigationOnClickListener(v -> finish());
        binding.btnNewReportTop.setOnClickListener(v -> {
            startActivity(new Intent(this, SubmitReportActivity.class));
        });
    }

    private void setupFilters() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                binding.chipFilterAll.setChecked(true);
                return;
            }
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipFilterAll) {
                currentFilter = FilterMode.ALL;
            } else if (checkedId == R.id.chipFilterActive) {
                currentFilter = FilterMode.ACTIVE;
            } else if (checkedId == R.id.chipFilterResolved) {
                currentFilter = FilterMode.RESOLVED;
            }
            applyCurrentFilter();
        });
    }

    private void setupRecyclerView() {
        binding.recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(filteredReportsList);
        binding.recyclerReports.setAdapter(adapter);
    }

    private void setupClickListeners() {
        binding.btnEmptySubmitReport.setOnClickListener(v -> {
            startActivity(new Intent(this, SubmitReportActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh reports if user returns from SubmitReportActivity
        loadReports();
    }

    private void loadReports() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            binding.progressBar.setVisibility(View.GONE);
            showEmptyState("Sign In to View Reports", "Please log in to review and manage your submitted safety hazard reports.");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("community_reports")
                .whereEqualTo("reporterId", currentUser.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    rawReportsList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        CommunityReport report = document.toObject(CommunityReport.class);
                        rawReportsList.add(report);
                    }

                    // Sort in-memory by timestamp descending (newest first)
                    Collections.sort(rawReportsList, (r1, r2) -> {
                        if (r1.getTimestamp() == null && r2.getTimestamp() == null) return 0;
                        if (r1.getTimestamp() == null) return 1;
                        if (r2.getTimestamp() == null) return -1;
                        return r2.getTimestamp().compareTo(r1.getTimestamp());
                    });

                    binding.progressBar.setVisibility(View.GONE);
                    applyCurrentFilter();
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Snackbar.make(binding.getRoot(), "Failed to load reports", Snackbar.LENGTH_LONG).show();
                    applyCurrentFilter();
                });
    }

    private void applyCurrentFilter() {
        filteredReportsList.clear();

        for (CommunityReport report : rawReportsList) {
            boolean isLiveActive = "active".equalsIgnoreCase(report.getStatus()) && !report.isExpired();
            if (currentFilter == FilterMode.ALL) {
                filteredReportsList.add(report);
            } else if (currentFilter == FilterMode.ACTIVE && isLiveActive) {
                filteredReportsList.add(report);
            } else if (currentFilter == FilterMode.RESOLVED && !isLiveActive) {
                filteredReportsList.add(report);
            }
        }

        if (filteredReportsList.isEmpty()) {
            if (rawReportsList.isEmpty()) {
                showEmptyState("No Community Reports Yet", "When you encounter hazards like unlit roads, aggressive activity, or sudden roadblocks, report them here to warn fellow commuters and earn community trust badges.");
            } else if (currentFilter == FilterMode.ACTIVE) {
                showEmptyState("No Active Hazards", "All your submitted reports have naturally expired or been resolved. Safe streets!");
            } else {
                showEmptyState("No Archived Reports", "You don't have any archived or resolved community reports.");
            }
        } else {
            binding.emptyState.setVisibility(View.GONE);
            binding.recyclerReports.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private void showEmptyState(String title, String desc) {
        binding.emptyState.setVisibility(View.VISIBLE);
        binding.recyclerReports.setVisibility(View.GONE);
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptyDescription.setText(desc);
    }

    // ── Adapter for My Reports ────────────────────────────────────────────────
    private static class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

        private final List<CommunityReport> reports;

        public ReportAdapter(List<CommunityReport> reports) {
            this.reports = reports;
        }

        @NonNull
        @Override
        public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemMyReportBinding itemBinding = ItemMyReportBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ReportViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
            CommunityReport report = reports.get(position);
            ItemMyReportBinding b = holder.binding;
            android.content.Context ctx = holder.itemView.getContext();

            // 1. Category and Subcategory
            String category = report.getMainCategory() != null ? report.getMainCategory() : "Safety Hazard";
            String subcategory = report.getSubCategory() != null && !report.getSubCategory().isEmpty() 
                    ? report.getSubCategory() 
                    : category;
            
            b.tvCategory.setText(category);
            b.tvSubCategory.setText(subcategory);

            // Description
            String desc = report.getDescription() != null ? report.getDescription().trim() : "";
            if (!desc.isEmpty()) {
                b.tvDescription.setVisibility(View.VISIBLE);
                b.tvDescription.setText(desc);
            } else {
                b.tvDescription.setVisibility(View.GONE);
            }

            // Photo Preview Badge
            if (report.getImageUrl() != null && !report.getImageUrl().trim().isEmpty()) {
                b.containerPhotoPreview.setVisibility(View.VISIBLE);
            } else {
                b.containerPhotoPreview.setVisibility(View.GONE);
            }

            // 2. Relative Timestamp
            if (report.getTimestamp() != null && report.getTimestamp().toDate() != null) {
                Date date = report.getTimestamp().toDate();
                long now = System.currentTimeMillis();
                long diff = now - date.getTime();

                if (diff < DateUtils.MINUTE_IN_MILLIS) {
                    b.tvTimestamp.setText("Just now");
                } else if (diff < DateUtils.DAY_IN_MILLIS * 7) {
                    CharSequence relative = DateUtils.getRelativeTimeSpanString(
                            date.getTime(), now, DateUtils.MINUTE_IN_MILLIS);
                    b.tvTimestamp.setText(relative);
                } else {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    b.tvTimestamp.setText(sdf.format(date));
                }
            } else {
                b.tvTimestamp.setText("Recently reported");
            }

            // 3. Status and Visual Distinction
            boolean isActive = "active".equalsIgnoreCase(report.getStatus()) && !report.isExpired();
            int verifications = report.getVerificationCount();

            if (isActive) {
                // Active Card Styling: Subtle glow and green status
                b.cardReport.setStrokeColor(ContextCompat.getColor(ctx, R.color.primary_blue_dark));
                b.tvStatus.setText("Active in Navigation");
                b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.success_green));
                b.tvStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.success_green_subtle)));

                // Confirmation Badge
                b.ivVerificationIcon.setImageResource(R.drawable.ic_thumb_up);
                b.ivVerificationIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.success_green)));
                b.tvVerification.setTextColor(ContextCompat.getColor(ctx, R.color.success_green));
                if (verifications > 0) {
                    b.tvVerification.setText(verifications + " community confirmation" + (verifications > 1 ? "s" : ""));
                } else {
                    b.tvVerification.setText("Awaiting confirmations");
                }
            } else {
                // Expired / Resolved Card Styling: Subdued styling
                b.cardReport.setStrokeColor(ContextCompat.getColor(ctx, R.color.colorSurfaceBorder));
                b.tvStatus.setText("Archived / Resolved");
                b.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted));
                b.tvStatus.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.colorSurfaceVariant)));

                // Resolved icon
                b.ivVerificationIcon.setImageResource(R.drawable.ic_check);
                b.ivVerificationIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.text_muted)));
                b.tvVerification.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted));
                b.tvVerification.setText(verifications > 0 ? (verifications + " confirmed before archiving") : "Archived");
            }
        }

        @Override
        public int getItemCount() {
            return reports.size();
        }

        static class ReportViewHolder extends RecyclerView.ViewHolder {
            final ItemMyReportBinding binding;
            ReportViewHolder(ItemMyReportBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
