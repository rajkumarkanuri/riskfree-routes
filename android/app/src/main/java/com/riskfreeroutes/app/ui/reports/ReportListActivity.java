package com.riskfreeroutes.app.ui.reports;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.riskfreeroutes.app.databinding.ActivityReportListBinding;
import com.riskfreeroutes.app.databinding.ItemMyReportBinding;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportListActivity extends AppCompatActivity {

    private ActivityReportListBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ReportAdapter adapter;
    private List<CommunityReport> reportList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Initialize ViewBinding
        binding = ActivityReportListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize Firebase instances
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        
        // 8. Toolbar back button
        binding.btnBack.setNavigationOnClickListener(v -> finish());
        
        // Setup RecyclerView
        adapter = new ReportAdapter(reportList);
        binding.recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReports.setAdapter(adapter);
        
        loadReports();
    }
    
    private void loadReports() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            // User not logged in, just show empty state
            binding.progressBar.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
            return;
        }
        
        // 4. Show progress bar during load
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.emptyState.setVisibility(View.GONE);
        binding.recyclerReports.setVisibility(View.GONE);
        
        // 3. Query Firestore
        db.collection("community_reports")
                .whereEqualTo("reporterId", currentUser.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    reportList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        CommunityReport report = document.toObject(CommunityReport.class);
                        reportList.add(report);
                    }
                    
                    binding.progressBar.setVisibility(View.GONE);
                    
                    // 5. Show emptyState if no reports, else show RecyclerView
                    if (reportList.isEmpty()) {
                        binding.emptyState.setVisibility(View.VISIBLE);
                        binding.recyclerReports.setVisibility(View.GONE);
                    } else {
                        binding.emptyState.setVisibility(View.GONE);
                        binding.recyclerReports.setVisibility(View.VISIBLE);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Snackbar.make(binding.getRoot(), "Failed to load reports", Snackbar.LENGTH_LONG).show();
                });
    }

    // 2. Inner ReportAdapter
    private static class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

        private final List<CommunityReport> reports;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        public ReportAdapter(List<CommunityReport> reports) {
            this.reports = reports;
        }

        @NonNull
        @Override
        public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemMyReportBinding itemBinding = ItemMyReportBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ReportViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
            CommunityReport report = reports.get(position);
            
            // 6. Each item details
            holder.binding.tvCategory.setText(report.getMainCategory() != null ? report.getMainCategory() : "Unknown");
            holder.binding.tvSubCategory.setText(report.getSubCategory() != null ? report.getSubCategory() : "");
            holder.binding.tvDescription.setText(report.getDescription() != null ? report.getDescription() : "");
            
            // Format Timestamp
            if (report.getTimestamp() != null && report.getTimestamp().toDate() != null) {
                holder.binding.tvTimestamp.setText(dateFormat.format(report.getTimestamp().toDate()));
            } else {
                holder.binding.tvTimestamp.setText("Unknown date");
            }
            
            // Verification count
            holder.binding.tvVerification.setText("Verified: " + report.getVerificationCount());
            
            // Status formatting
            String status = report.getStatus() != null ? report.getStatus().toLowerCase() : "unknown";
            holder.binding.tvStatus.setText(report.getStatus() != null ? report.getStatus().toUpperCase() : "UNKNOWN");
            
            if ("active".equals(status)) {
                holder.binding.tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.success_green)); // profile_green equivalent
            } else if ("expired".equals(status)) {
                holder.binding.tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary)); // grey
            } else {
                holder.binding.tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.warning_amber)); // amber default
            }
        }

        @Override
        public int getItemCount() {
            return reports != null ? reports.size() : 0;
        }

        static class ReportViewHolder extends RecyclerView.ViewHolder {
            final ItemMyReportBinding binding;

            public ReportViewHolder(ItemMyReportBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
