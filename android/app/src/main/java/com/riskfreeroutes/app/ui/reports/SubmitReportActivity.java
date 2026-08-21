package com.riskfreeroutes.app.ui.reports;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.GeoPoint;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivitySubmitReportBinding;
import com.riskfreeroutes.app.model.ReportCategory;
import com.riskfreeroutes.app.ui.home.HomeActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SubmitReportActivity.java — The Community Safety Report submission screen.
 *
 * This Activity handles the UI for the report submission form.
 * All business logic (Cloudinary upload, Firestore write) lives in ReportViewModel.
 * This class ONLY:
 *  1. Renders the current ViewModel state on screen
 *  2. Sends user interactions (button taps, text input) to the ViewModel
 *  3. Shows dialogs/toasts based on what the ViewModel tells it
 *
 * KEY DESIGN DECISIONS:
 * - Submit button is DISABLED while a photo is uploading (observes isPhotoUploading LiveData)
 *   This prevents submitting with imageUrl = null when a photo was selected
 * - Photo remove (×) button clears the photo state in ViewModel + resets UI
 * - Success dialog is a premium custom dialog (not a stock AlertDialog)
 */
public class SubmitReportActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ActivitySubmitReportBinding binding;
    private ReportViewModel viewModel;
    private FusedLocationProviderClient fusedLocationClient;

    // Holds the GPS position for the Firestore GeoPoint field
    private GeoPoint currentGeoPoint;

    // The URI of the file we create before launching the camera.
    // We need to store this here because it gets referenced in the camera callback.
    private Uri cameraImageUri;

    private GoogleMap googleMap;

    // ── ACTIVITY RESULT LAUNCHERS ─────────────────────────────────────────────
    // Modern replacement for deprecated startActivityForResult().
    // Each launcher handles one type of result (camera, gallery, permissions).

    /**
     * Camera launcher — takes a photo and saves it to cameraImageUri.
     * On success: shows preview + triggers Cloudinary upload.
     */
    private final ActivityResultLauncher<Uri> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && cameraImageUri != null) {
                showPhotoPreview(cameraImageUri);
                viewModel.uploadPhoto(cameraImageUri);
            }
        });

    /**
     * Gallery launcher — picks an image from the user's photo library.
     * On success: shows preview + triggers Cloudinary upload.
     */
    private final ActivityResultLauncher<String> galleryLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                showPhotoPreview(uri);
                viewModel.uploadPhoto(uri);
            }
        });

    /**
     * Location permission launcher — requests ACCESS_FINE_LOCATION.
     * On grant: immediately captures location.
     */
    private final ActivityResultLauncher<String[]> locationPermLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
            if (Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                captureLocation();
            } else {
                binding.tvLocationAddress.setText("Location permission denied");
            }
        });

    // ── LIFECYCLE ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubmitReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReportViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize the small embedded MapView inside the form.
        // This MapView must go through its own lifecycle calls (see onResume, onPause, etc.)
        binding.mapPreview.onCreate(savedInstanceState);
        try {
            MapsInitializer.initialize(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        binding.mapPreview.getMapAsync(this);

        setupToolbar();
        setupCategoryCards();
        setupFormButtons();
        setupBottomNavigation();
        observeViewModel();
        checkAndRequestLocation();
    }

    // ── SETUP METHODS ─────────────────────────────────────────────────────────

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_reports);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_reports) {
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, com.riskfreeroutes.app.ui.profile.ProfileActivity.class));
                overridePendingTransition(0, 0);
                return false;
            } else if (id == R.id.nav_nearby) {
                startActivity(new Intent(this, com.riskfreeroutes.app.ui.nearby.NearbyPlacesActivity.class));
                overridePendingTransition(0, 0);
                return false;
            }
            return true;
        });
    }

    private void setupCategoryCards() {
        // Wire each category card to the selectCategory() helper below.
        // The helper updates the ViewModel + highlights the selected card + shows subcategory chips.
        binding.cardRoadIssues.setOnClickListener(v -> selectCategory(ReportCategory.ROAD_ISSUES));
        binding.cardInfrastructure.setOnClickListener(v -> selectCategory(ReportCategory.INFRASTRUCTURE));
        binding.cardSafety.setOnClickListener(v -> selectCategory(ReportCategory.SAFETY));
        binding.cardWeather.setOnClickListener(v -> selectCategory(ReportCategory.WEATHER));
        binding.cardEmergency.setOnClickListener(v -> selectCategory(ReportCategory.EMERGENCY));
        binding.cardOther.setOnClickListener(v -> selectCategory(ReportCategory.OTHER));
    }

    private void setupFormButtons() {
        // Location refresh
        binding.btnRefreshLocation.setOnClickListener(v -> checkAndRequestLocation());

        // Photo picker buttons (in the empty state)
        binding.btnPickCamera.setOnClickListener(v -> launchCamera());
        binding.btnPickGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        // Remove photo button (shown over the preview)
        binding.btnRemovePhoto.setOnClickListener(v -> {
            viewModel.removePhoto();
            // Switch UI back to the empty state
            binding.containerPhotoPreview.setVisibility(View.GONE);
            binding.containerPhotoEmpty.setVisibility(View.VISIBLE);
        });

        // Submit — builds the report and writes to Firestore
        binding.btnSubmit.setOnClickListener(v -> {
            String desc = binding.etDescription.getText().toString().trim();
            int severity = getSelectedSeverity();
            viewModel.submitReport(desc, currentGeoPoint, severity);
        });
    }

    // ── PHOTO STATE ───────────────────────────────────────────────────────────

    /**
     * Shows the selected image in the preview container and hides the empty state.
     * Called immediately after the user picks a photo (before upload completes).
     */
    private void showPhotoPreview(Uri uri) {
        binding.imgPhotoPreview.setImageURI(uri);
        binding.containerPhotoEmpty.setVisibility(View.GONE);
        binding.containerPhotoPreview.setVisibility(View.VISIBLE);
    }

    // ── CATEGORY SELECTION ────────────────────────────────────────────────────

    private void selectCategory(String category) {
        viewModel.setMainCategory(category);
        resetCategoryCards();
        highlightCard(category);

        // Build subcategory chips dynamically for the selected category
        binding.containerSubcategories.setVisibility(View.VISIBLE);
        binding.chipGroupSubcategory.removeAllViews();
        List<String> subs = ReportCategory.SUBCATEGORIES.get(category);
        if (subs != null) {
            for (String sub : subs) {
                Chip chip = new Chip(this);
                chip.setText(sub);
                chip.setCheckable(true);
                // Use app blue for stroke when checked
                chip.setChipBackgroundColorResource(R.color.colorSurfaceVariant);
                chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
                chip.setChipStrokeWidth(1f);
                chip.setChipStrokeColorResource(R.color.colorSurfaceBorder);
                chip.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) viewModel.setSubCategory(sub);
                });
                binding.chipGroupSubcategory.addView(chip);
            }
        }

        // Smoothly scroll to reveal the subcategory section
        binding.scrollContent.post(() ->
            binding.scrollContent.smoothScrollTo(0, binding.containerSubcategories.getTop() - 80)
        );
    }

    private void resetCategoryCards() {
        binding.cardRoadIssues.setStrokeWidth(2);
        binding.cardInfrastructure.setStrokeWidth(2);
        binding.cardSafety.setStrokeWidth(2);
        binding.cardWeather.setStrokeWidth(2);
        binding.cardEmergency.setStrokeWidth(2);
        binding.cardOther.setStrokeWidth(2);
        // Reset all to default stroke color
        int defaultColor = androidx.core.content.ContextCompat.getColor(this, R.color.glass_border);
        binding.cardRoadIssues.setStrokeColor(defaultColor);
        binding.cardInfrastructure.setStrokeColor(defaultColor);
        binding.cardSafety.setStrokeColor(defaultColor);
        binding.cardWeather.setStrokeColor(defaultColor);
        binding.cardEmergency.setStrokeColor(defaultColor);
        binding.cardOther.setStrokeColor(defaultColor);
    }

    private void highlightCard(String category) {
        // Active card gets a blue border and slightly tinted background
        int activeStrokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue);
        int strokeWidth = 4;
        if (ReportCategory.ROAD_ISSUES.equals(category)) {
            binding.cardRoadIssues.setStrokeColor(activeStrokeColor);
            binding.cardRoadIssues.setStrokeWidth(strokeWidth);
            binding.cardRoadIssues.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_panel));
        } else if (ReportCategory.INFRASTRUCTURE.equals(category)) {
            binding.cardInfrastructure.setStrokeColor(activeStrokeColor);
            binding.cardInfrastructure.setStrokeWidth(strokeWidth);
            binding.cardInfrastructure.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_panel));
        } else if (ReportCategory.SAFETY.equals(category)) {
            binding.cardSafety.setStrokeColor(activeStrokeColor);
            binding.cardSafety.setStrokeWidth(strokeWidth);
            binding.cardSafety.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_panel));
        } else if (ReportCategory.WEATHER.equals(category)) {
            binding.cardWeather.setStrokeColor(activeStrokeColor);
            binding.cardWeather.setStrokeWidth(strokeWidth);
            binding.cardWeather.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_panel));
        } else if (ReportCategory.EMERGENCY.equals(category)) {
            binding.cardEmergency.setStrokeColor(androidx.core.content.ContextCompat.getColor(this, R.color.danger_red));
            binding.cardEmergency.setStrokeWidth(strokeWidth);
            binding.cardEmergency.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.danger_red_light));
        } else if (ReportCategory.OTHER.equals(category)) {
            binding.cardOther.setStrokeColor(activeStrokeColor);
            binding.cardOther.setStrokeWidth(strokeWidth);
            binding.cardOther.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_panel));
        }
    }

    // ── SEVERITY ──────────────────────────────────────────────────────────────

    private int getSelectedSeverity() {
        int checkedId = binding.toggleSafetyLevel.getCheckedButtonId();
        if (checkedId == R.id.btn_severity_low) return 1;
        if (checkedId == R.id.btn_severity_high) return 5;
        return 3; // Medium default
    }

    // ── CAMERA ────────────────────────────────────────────────────────────────

    private void launchCamera() {
        try {
            // Create an empty file where the camera will save the full-resolution photo.
            // We use getExternalFilesDir so we don't need WRITE_EXTERNAL_STORAGE permission.
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File imageFile = File.createTempFile("REPORT_" + timeStamp, ".jpg", storageDir);

            // FileProvider converts the file path to a content:// URI the camera app can write to.
            // This is required since Android 7.0 — direct file:// URIs are blocked.
            cameraImageUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", imageFile
            );
            cameraLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Could not create image file.", Toast.LENGTH_SHORT).show();
        }
    }

    // ── VIEWMODEL OBSERVERS ───────────────────────────────────────────────────

    private void observeViewModel() {
        // ── Submission state (Firestore write) ────────────────────────────────
        viewModel.getSubmissionState().observe(this, state -> {
            switch (state) {
                case LOADING:
                    binding.containerLoading.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    binding.containerLoading.setVisibility(View.GONE);
                    showSuccessDialog();
                    break;
                case ERROR:
                    binding.containerLoading.setVisibility(View.GONE);
                    viewModel.resetState();
                    break;
                case IDLE:
                    binding.containerLoading.setVisibility(View.GONE);
                    break;
            }
        });

        // ── Error messages (validation or network failures) ───────────────────
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });

        // ── Photo upload in-flight: disable Submit, show overlay ──────────────
        // This is the KEY guard: prevents submitting before Cloudinary URL is ready.
        viewModel.getIsPhotoUploading().observe(this, uploading -> {
            if (uploading == null) return;

            if (uploading) {
                // Photo is still going up to Cloudinary — show the progress overlay
                // and disable the Submit button so the user can't jump the gun.
                binding.containerUploadProgress.setVisibility(View.VISIBLE);
                binding.btnSubmit.setAlpha(0.5f);
                binding.btnSubmit.setEnabled(false);
            } else {
                // Upload done (success or cancelled) — hide overlay + re-enable Submit
                binding.containerUploadProgress.setVisibility(View.GONE);
                binding.btnSubmit.setAlpha(1.0f);
                binding.btnSubmit.setEnabled(true);
            }
        });

        // ── Upload progress (0–100): update circular indicator ────────────────
        viewModel.getUploadProgress().observe(this, progress -> {
            if (progress == null) return;
            binding.progressUploadCircle.setProgress(progress, true);
            binding.tvUploadPercent.setText(progress + "%");
        });
    }

    // ── LOCATION ──────────────────────────────────────────────────────────────

    private void checkAndRequestLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            captureLocation();
        } else {
            locationPermLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void captureLocation() {
        binding.tvLocationAddress.setText("Locating…");
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentGeoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                reverseGeocode(location);
                updateMapPreview(location);
            } else {
                binding.tvLocationAddress.setText("Location unavailable — please refresh");
            }
        });
    }

    private void reverseGeocode(Location location) {
        // Convert lat/lng to a human-readable "City, State" string
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(
                location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String city  = addr.getLocality();
                String state = addr.getAdminArea();
                String result = "";
                if (city != null)  result += city;
                if (state != null) result += (city != null ? ", " : "") + state;
                if (result.isEmpty()) result = addr.getAddressLine(0);
                binding.tvLocationAddress.setText(result);
            } else {
                binding.tvLocationAddress.setText(
                    String.format(Locale.US, "%.4f, %.4f",
                        location.getLatitude(), location.getLongitude()));
            }
        } catch (Exception e) {
            binding.tvLocationAddress.setText(
                String.format(Locale.US, "%.4f, %.4f",
                    location.getLatitude(), location.getLongitude()));
        }
    }

    // ── GOOGLE MAP ────────────────────────────────────────────────────────────

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        this.googleMap.getUiSettings().setAllGesturesEnabled(false);
        // If location was already captured before the map was ready, draw it now
        if (currentGeoPoint != null) {
            Location loc = new Location("");
            loc.setLatitude(currentGeoPoint.getLatitude());
            loc.setLongitude(currentGeoPoint.getLongitude());
            updateMapPreview(loc);
        }
    }

    private void updateMapPreview(Location location) {
        if (googleMap == null) return;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        googleMap.clear();
        googleMap.addMarker(new MarkerOptions().position(latLng));
        googleMap.addCircle(new CircleOptions()
            .center(latLng)
            .radius(100)
            .strokeColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_blue_40))
            .fillColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_blue_10))
            .strokeWidth(2f));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
    }

    // ── SUCCESS DIALOG ────────────────────────────────────────────────────────

    /**
     * Shows a premium custom success dialog (green checkmark + styled buttons).
     * Much nicer than a stock AlertDialog — matches the app's design system.
     */
    private void showSuccessDialog() {
        // Inflate our custom dialog layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_report_success, null);

        // Build the dialog with a transparent background so our rounded corners show
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // "Done" → close the dialog and finish the Activity
        dialogView.findViewById(R.id.btn_dialog_done).setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        // "View My Reports" → close the dialog and finish the Activity
        // (The report list screen can be built later)
        dialogView.findViewById(R.id.btn_dialog_view_reports).setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }

    // ── MAPVIEW LIFECYCLE ─────────────────────────────────────────────────────
    // The MapView inside the form requires explicit lifecycle delegation.
    // Without these calls the map won't render or will leak resources.

    @Override protected void onResume()  { super.onResume();  binding.mapPreview.onResume(); }
    @Override protected void onStart()   { super.onStart();   binding.mapPreview.onStart(); }
    @Override protected void onStop()    { super.onStop();    binding.mapPreview.onStop(); }
    @Override protected void onPause()   { binding.mapPreview.onPause();   super.onPause(); }
    @Override protected void onDestroy() { binding.mapPreview.onDestroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); binding.mapPreview.onLowMemory(); }
}
