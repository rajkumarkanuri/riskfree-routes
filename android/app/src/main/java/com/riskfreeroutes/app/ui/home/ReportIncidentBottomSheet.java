package com.riskfreeroutes.app.ui.home;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.GeoPoint;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.FragmentReportIncidentBinding;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.repository.FirestoreReportsRepository;

/**
 * ReportIncidentBottomSheet — a modal bottom sheet that lets the user
 * report a safety incident. Submits directly to Firestore.
 *
 * Usage (from HomeActivity):
 *   new ReportIncidentBottomSheet(currentLocation).show(getSupportFragmentManager(), "report");
 */
public class ReportIncidentBottomSheet extends BottomSheetDialogFragment {

    private FragmentReportIncidentBinding binding;
    private final Location currentLocation;
    private String selectedType = "OTHER";
    private int selectedSeverity = 2; // default medium

    public ReportIncidentBottomSheet(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportIncidentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Update location label
        if (currentLocation != null) {
            binding.tvLocationLabel.setText(String.format(
                "%.5f, %.5f", currentLocation.getLatitude(), currentLocation.getLongitude()
            ));
        }

        // ── Incident type chip selection ─────────────────────────────────
        binding.chipGroupType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_theft)          selectedType = "THEFT";
            else if (id == R.id.chip_assault)   selectedType = "ASSAULT";
            else if (id == R.id.chip_harassment)selectedType = "HARASSMENT";
            else if (id == R.id.chip_suspicious)selectedType = "SUSPICIOUS_PERSON";
            else if (id == R.id.chip_lighting)  selectedType = "POOR_LIGHTING";
            else if (id == R.id.chip_unsafe)    selectedType = "UNSAFE_AREA";
            else if (id == R.id.chip_accident)  selectedType = "ACCIDENT";
            else                                selectedType = "OTHER";
        });

        // ── Severity chip selection ──────────────────────────────────────
        binding.chipGroupSeverity.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chip_low)          selectedSeverity = 1;
            else if (id == R.id.chip_medium)  selectedSeverity = 2;
            else if (id == R.id.chip_high)    selectedSeverity = 4;
            else if (id == R.id.chip_critical)selectedSeverity = 5;
        });

        // ── Submit ───────────────────────────────────────────────────────
        binding.btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    private void submitReport() {
        if (currentLocation == null) {
            Toast.makeText(getContext(), "Location not available. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
            : "anonymous";
        String description = binding.etDescription.getText().toString().trim();

        GeoPoint geoPoint = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());

        CommunityReport report = new CommunityReport(uid, selectedType, description, geoPoint, selectedSeverity);

        // Disable button to prevent double submission
        binding.btnSubmitReport.setEnabled(false);
        binding.btnSubmitReport.setText("Submitting...");

        new FirestoreReportsRepository().submitReport(report, new FirestoreReportsRepository.SubmitCallback() {
            @Override
            public void onSuccess() {
                if (getContext() == null) return;
                Toast.makeText(getContext(), "Report submitted. Thank you for keeping the community safe.", Toast.LENGTH_LONG).show();
                dismiss();
            }

            @Override
            public void onFailure(Exception e) {
                if (getContext() == null) return;
                binding.btnSubmitReport.setEnabled(true);
                binding.btnSubmitReport.setText("Submit Report");
                Toast.makeText(getContext(), "Failed to submit. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
