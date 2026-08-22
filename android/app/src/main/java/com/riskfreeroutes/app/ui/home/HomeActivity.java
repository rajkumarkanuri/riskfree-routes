package com.riskfreeroutes.app.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityHomeBinding;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.model.CommunityReportItem;
import com.riskfreeroutes.app.model.Route;
import com.riskfreeroutes.app.ui.profile.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ActivityHomeBinding binding;
    private GoogleMap googleMap;
    private HomeViewModel viewModel;
    private ActivityResultLauncher<String[]> locationPermissionRequest;

    private final List<Polyline> drawnPolylines = new ArrayList<>();
    private Marker safetyBadgeMarker;
    private SharedPreferences prefs;
    private ClusterManager<CommunityReportItem> clusterManager;

    private PlaceSuggestionsAdapter suggestionsAdapter;
    private BottomSheetBehavior bottomSheetBehavior;

    private enum HomeState { IDLE, SEARCH, ROUTE_SELECTION, NAVIGATION }
    private HomeState currentState = HomeState.IDLE;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable restoreUIRunnable = this::restoreUIElements;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupWindowInsets();
        prefs = getSharedPreferences("MapPrefs", MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        setupPermissionLauncher();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupPlaces();
        setupClickListeners();
        updateUIState(HomeState.IDLE);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.interactionOverlayContainer.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });
    }

    private void setupPlaces() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet);
        bottomSheetBehavior.setSkipCollapsed(false);

        suggestionsAdapter = new PlaceSuggestionsAdapter(prediction -> {
            hideKeyboard();
            binding.etSearchExpanded.setText("");
            String name = prediction.getPrimaryText(null).toString();

            if (binding.tvDestinationName != null) {
                binding.tvDestinationName.setText(name);
            }

            viewModel.fetchPlaceLatLng(prediction.getPlaceId()).observe(this, latLng -> {
                if (latLng != null) {
                    Intent intent = new Intent(HomeActivity.this, com.riskfreeroutes.app.ui.routes.RouteSelectionActivity.class);
                    intent.putExtra(com.riskfreeroutes.app.ui.routes.RouteSelectionActivity.EXTRA_DESTINATION_NAME, name);
                    intent.putExtra(com.riskfreeroutes.app.ui.routes.RouteSelectionActivity.EXTRA_DEST_LAT, latLng.latitude);
                    intent.putExtra(com.riskfreeroutes.app.ui.routes.RouteSelectionActivity.EXTRA_DEST_LNG, latLng.longitude);
                    
                    android.location.Location currLoc = viewModel.getCurrentLocation().getValue();
                    if (currLoc != null) {
                        intent.putExtra(com.riskfreeroutes.app.ui.routes.RouteSelectionActivity.EXTRA_ORIGIN_LAT, currLoc.getLatitude());
                        intent.putExtra(com.riskfreeroutes.app.ui.routes.RouteSelectionActivity.EXTRA_ORIGIN_LNG, currLoc.getLongitude());
                    }
                    
                    startActivity(intent);
                    updateUIState(HomeState.IDLE);
                }
            });
        });

        binding.rvSuggestions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSuggestions.setAdapter(suggestionsAdapter);

        binding.etSearchExpanded.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
                binding.rvSuggestions.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupClickListeners() {
        binding.searchPillCard.setOnClickListener(v -> updateUIState(HomeState.SEARCH));
        binding.btnSearchBack.setOnClickListener(v -> {
            hideKeyboard();
            updateUIState(HomeState.IDLE);
        });
        
        binding.btnRouteClose.setOnClickListener(v -> {
            for (Polyline p : drawnPolylines) p.remove();
            drawnPolylines.clear();
            if (safetyBadgeMarker != null) safetyBadgeMarker.remove();
            updateUIState(HomeState.IDLE);
        });

        if (binding.btnLayerMapType != null) {
            binding.btnLayerMapType.setOnClickListener(v -> showMapTypeDialog());
        }

        binding.btnStartNavigation.setOnClickListener(v -> updateUIState(HomeState.NAVIGATION));
        binding.btnExitNavigation.setOnClickListener(v -> {
            for (Polyline p : drawnPolylines) p.remove();
            drawnPolylines.clear();
            if (safetyBadgeMarker != null) safetyBadgeMarker.remove();
            updateUIState(HomeState.IDLE);
        });
        
        binding.btnLayerRecenter.setOnClickListener(v -> recenterCamera());
        
        // SOS
        binding.btnSos.setOnClickListener(v -> triggerManualSOS());

        // Bottom Nav
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
                return false;
            } else if (item.getItemId() == R.id.nav_reports) {
                Intent intent = new Intent(this, com.riskfreeroutes.app.ui.reports.SubmitReportActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return false;
            } else if (item.getItemId() == R.id.nav_nearby) {
                startActivity(new Intent(this, com.riskfreeroutes.app.ui.nearby.NearbyPlacesActivity.class));
                overridePendingTransition(0, 0);
                return false;
            }
            return true; // We don't implement other tabs here for now
        });
        
        // Profile Avatar
        loadProfileAvatar();
        binding.imgProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void loadProfileAvatar() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            binding.imgProfile.setImageResource(R.drawable.ic_profile);
            return;
        }

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snap -> {
                    if (isFinishing() || isDestroyed()) return;
                    String profileImageUrl = snap.exists() ? snap.getString("profileImageUrl") : null;
                    String photoToLoad = (profileImageUrl != null && !profileImageUrl.isEmpty())
                            ? profileImageUrl
                            : (user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);

                    if (photoToLoad != null && !photoToLoad.isEmpty()) {
                        Glide.with(this)
                                .load(photoToLoad)
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .circleCrop()
                                .into(binding.imgProfile);
                    } else {
                        binding.imgProfile.setImageResource(R.drawable.ic_profile);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (user.getPhotoUrl() != null) {
                        Glide.with(this)
                                .load(user.getPhotoUrl())
                                .placeholder(R.drawable.ic_profile)
                                .error(R.drawable.ic_profile)
                                .circleCrop()
                                .into(binding.imgProfile);
                    } else {
                        binding.imgProfile.setImageResource(R.drawable.ic_profile);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileAvatar();
    }

    private void triggerManualSOS() {
        com.riskfreeroutes.app.ui.emergency.EmergencyCountdownDialog.newInstance(
            new com.riskfreeroutes.app.ui.emergency.EmergencyCountdownDialog.CountdownListener() {
                @Override
                public void onSafeClicked() {
                    Toast.makeText(HomeActivity.this, "SOS Cancelled", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onEmergencyNowClicked() {
                    executeManualSOS();
                }

                @Override
                public void onCountdownFinished() {
                    executeManualSOS();
                }

                @Override
                public void onCancelClicked() {
                    Toast.makeText(HomeActivity.this, "SOS Cancelled", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFindNearbyHelpClicked() {
                    startActivity(new android.content.Intent(HomeActivity.this, com.riskfreeroutes.app.ui.nearby.NearbyPlacesActivity.class));
                }
            }, 10).show(getSupportFragmentManager(), "ManualSOSDialog");
    }

    private void executeManualSOS() {
        com.riskfreeroutes.app.maps.LocationHelper.getCurrentLocation(this, location -> {
            String userName = "User";
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                userName = user.getDisplayName();
            }

            com.riskfreeroutes.app.repository.GuardianRepository guardianRepo = new com.riskfreeroutes.app.repository.GuardianRepository();
            
            // 1. Fetch contacts, send SMS, and create sos_history
            guardianRepo.fetchContactsAndTriggerSOS(this, userName, location, sosDocId -> {
                if (sosDocId != null) {
                    runOnUiThread(() -> Toast.makeText(this, "Emergency SMS Sent Successfully", Toast.LENGTH_LONG).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to trigger SOS", Toast.LENGTH_SHORT).show());
                }
            });
            
            // 2. Log to guardian_logs
            String journeyId = null;
            if (currentState == HomeState.NAVIGATION) {
                journeyId = viewModel.getCurrentJourneyId();
            }
            guardianRepo.logGuardianEvent("SOS Triggered", location, "active", journeyId);
        }, e -> {
            runOnUiThread(() -> Toast.makeText(this, "Failed to get location for SOS", Toast.LENGTH_SHORT).show());
        });
    }

    private void updateUIState(HomeState state) {
        currentState = state;
        
        binding.containerSearch.setVisibility(View.GONE);
        binding.containerRouteSelection.setVisibility(View.GONE);
        binding.containerNavigationMode.setVisibility(View.GONE);
        binding.searchPillCard.setVisibility(View.GONE);
        binding.guardianModePill.setVisibility(View.GONE);
        binding.bottomNavigation.setVisibility(View.GONE);
        
        // Always show SOS unless we specifically want to hide it
        binding.btnSos.setVisibility(View.VISIBLE);

        switch (state) {
            case IDLE:
                binding.searchPillCard.setVisibility(View.VISIBLE);
                binding.bottomNavigation.setVisibility(View.VISIBLE);
                bottomSheetBehavior.setPeekHeight(0, true);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                refreshMarkers(); // Hide markers
                break;
                
            case SEARCH:
                binding.containerSearch.setVisibility(View.VISIBLE);
                bottomSheetBehavior.setPeekHeight(dpToPx(600), true);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                binding.etSearchExpanded.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(binding.etSearchExpanded, InputMethodManager.SHOW_IMPLICIT);
                refreshMarkers();
                break;
                
            case ROUTE_SELECTION:
                binding.containerRouteSelection.setVisibility(View.VISIBLE);
                bottomSheetBehavior.setPeekHeight(dpToPx(380), true);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                refreshMarkers(); // Show markers
                break;
                
            case NAVIGATION:
                binding.containerNavigationMode.setVisibility(View.VISIBLE);
                binding.guardianModePill.setVisibility(View.VISIBLE);
                bottomSheetBehavior.setPeekHeight(dpToPx(240), true);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                refreshMarkers();
                break;
        }
    }

    private void refreshMarkers() {
        if (clusterManager == null || viewModel.getCommunityReports().getValue() == null) return;
        clusterManager.clearItems();
        
        // Always show reports
        for (CommunityReport report : viewModel.getCommunityReports().getValue()) {
            if (report.getLocation() != null) {
                clusterManager.addItem(new CommunityReportItem(report));
            }
        }
        clusterManager.cluster();
    }

    private void hideUIElementsOnInteract() {
        uiHandler.removeCallbacks(restoreUIRunnable);
        
        binding.bottomSheet.animate().cancel();
        binding.bottomNavigation.animate().cancel();
        binding.searchPillCard.animate().cancel();
        binding.guardianModePill.animate().cancel();
        if (binding.btnLayerMapType != null) binding.btnLayerMapType.animate().cancel();

        binding.bottomSheet.animate().translationY(binding.bottomSheet.getHeight()).setDuration(200).start();
        binding.bottomNavigation.animate().translationY(binding.bottomNavigation.getHeight()).setDuration(200).start();
        binding.searchPillCard.animate().translationY(-300).setDuration(200).start();
        binding.guardianModePill.animate().translationY(-300).setDuration(200).start();
        if (binding.btnLayerMapType != null) binding.btnLayerMapType.animate().translationX(300).setDuration(200).start();
    }

    private void restoreUIElements() {
        binding.bottomSheet.animate().cancel();
        binding.bottomNavigation.animate().cancel();
        binding.searchPillCard.animate().cancel();
        binding.guardianModePill.animate().cancel();
        if (binding.btnLayerMapType != null) binding.btnLayerMapType.animate().cancel();

        binding.bottomSheet.animate().translationY(0).setDuration(200).start();
        binding.bottomNavigation.animate().translationY(0).setDuration(200).start();
        binding.searchPillCard.animate().translationY(0).setDuration(200).start();
        binding.guardianModePill.animate().translationY(0).setDuration(200).start();
        if (binding.btnLayerMapType != null) binding.btnLayerMapType.animate().translationX(0).setDuration(200).start();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupPermissionLauncher() {
        locationPermissionRequest = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLoc = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLoc = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (fineLoc != null && fineLoc || coarseLoc != null && coarseLoc) {
                        enableMapLocation();
                        viewModel.startLocationUpdates();
                    } else {
                        Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showMapTypeDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_map_layers, null);
        dialog.setContentView(dialogView);
        
        try {
            ((View) dialogView.getParent()).setBackgroundColor(Color.TRANSPARENT);
        } catch (Exception e) {
            Log.e("HomeActivity", "Could not set dialog background transparent", e);
        }

        dialogView.findViewById(R.id.btn_type_normal).setOnClickListener(v -> {
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            prefs.edit().putInt("map_type", GoogleMap.MAP_TYPE_NORMAL).apply();
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.btn_type_satellite).setOnClickListener(v -> {
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            prefs.edit().putInt("map_type", GoogleMap.MAP_TYPE_SATELLITE).apply();
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.btn_type_hybrid).setOnClickListener(v -> {
            googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            prefs.edit().putInt("map_type", GoogleMap.MAP_TYPE_HYBRID).apply();
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.btn_type_terrain).setOnClickListener(v -> {
            googleMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
            prefs.edit().putInt("map_type", GoogleMap.MAP_TYPE_TERRAIN).apply();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMapLocation();
            viewModel.startLocationUpdates();
        } else {
            locationPermissionRequest.launch(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION });
        }
    }

    @SuppressLint("MissingPermission")
    private void enableMapLocation() {
        if (googleMap != null) {
            googleMap.setMyLocationEnabled(true);
        }
    }

    private void recenterCamera() {
        if (googleMap != null && viewModel.getCurrentLocation().getValue() != null) {
            android.location.Location loc = viewModel.getCurrentLocation().getValue();
            CameraPosition cp = new CameraPosition.Builder()
                    .target(new LatLng(loc.getLatitude(), loc.getLongitude()))
                    .zoom(16f).tilt(60f).bearing(0f).build();
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cp));
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.setBuildingsEnabled(true);
        googleMap.setIndoorEnabled(true);
        
        int mapType = prefs.getInt("map_type", GoogleMap.MAP_TYPE_NORMAL);
        googleMap.setMapType(mapType);

        clusterManager = new ClusterManager<>(this, googleMap);
        clusterManager.setRenderer(new ReportClusterRenderer(this, googleMap, clusterManager));
        
        googleMap.setOnCameraIdleListener(() -> {
            clusterManager.onCameraIdle();
            uiHandler.postDelayed(restoreUIRunnable, 2000); // 2 second delay to restore UI
        });
        
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                hideUIElementsOnInteract();
            }
        });
        
        googleMap.setOnMarkerClickListener(marker -> {
            // Check if it's a hazard marker handled by cluster manager
            return clusterManager.onMarkerClick(marker);
        });
        
        checkLocationPermissionAndStart();
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getPlacePredictions().observe(this, predictions -> {
            suggestionsAdapter.setPredictions(predictions);
            binding.rvSuggestions.setVisibility(predictions != null && !predictions.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getCurrentLocation().observe(this, location -> {
            if (location != null && googleMap != null && drawnPolylines.isEmpty()) {
                recenterCamera();
            }
            
            String token = com.riskfreeroutes.app.repository.ActiveRouteRepository.getInstance().getActiveShareToken();
            if (token != null && location != null) {
                new com.riskfreeroutes.app.repository.LiveShareRepository().updateLiveLocation(token, location, 0.0, 0, null, 0.0, 100);
            }
        });

        viewModel.getCommunityReports().observe(this, reports -> {
            refreshMarkers();
        });

        viewModel.getRoutes().observe(this, routes -> {
            if (routes != null && !routes.isEmpty()) {
                drawRoutesOnMap(routes);
                populateRouteCards(routes);
                updateUIState(HomeState.ROUTE_SELECTION);
            }
        });
    }

    private void drawRoutesOnMap(List<Route> routes) {
        for (Polyline p : drawnPolylines) p.remove();
        drawnPolylines.clear();
        if (safetyBadgeMarker != null) safetyBadgeMarker.remove();

        for (int i = routes.size() - 1; i >= 0; i--) {
            Route route = routes.get(i);
            boolean isSelected = route.isSelected();
            int color = isSelected ? androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue_light) : androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary);
            int width = isSelected ? 16 : 10;
            int zIndex = isSelected ? 100 : 0;

            Polyline polyline = googleMap.addPolyline(new PolylineOptions()
                    .addAll(route.getDecodedPath())
                    .color(color)
                    .width(width)
                    .zIndex(zIndex));
            
            drawnPolylines.add(polyline);

            if (isSelected && !route.getDecodedPath().isEmpty()) {
                LatLng midpoint = route.getDecodedPath().get(route.getDecodedPath().size() / 2);
                Bitmap badgeBitmap = createBadgeBitmap(route.getSafetyScore());
                safetyBadgeMarker = googleMap.addMarker(new MarkerOptions()
                        .position(midpoint)
                        .icon(BitmapDescriptorFactory.fromBitmap(badgeBitmap))
                        .anchor(0.5f, 0.5f)
                        .zIndex(101));
            }
        }
        
        if (!routes.isEmpty() && !routes.get(0).getDecodedPath().isEmpty()) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                    com.google.android.gms.maps.model.LatLngBounds.builder()
                            .include(routes.get(0).getDecodedPath().get(0))
                            .include(routes.get(0).getDecodedPath().get(routes.get(0).getDecodedPath().size() - 1))
                            .build(), 150));
        }
    }

    private Bitmap createBadgeBitmap(int score) {
        View view = getLayoutInflater().inflate(R.layout.view_safety_badge, null);
        TextView tvScore = view.findViewById(R.id.tv_badge_score);
        tvScore.setText(String.valueOf(score));
        
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                     View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    private void populateRouteCards(List<Route> routes) {
        binding.containerRouteCards.removeAllViews();

        for (int i = 0; i < routes.size(); i++) {
            Route route = routes.get(i);
            com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) getLayoutInflater().inflate(R.layout.item_route_card, binding.containerRouteCards, false);
            
            TextView tvType = cardView.findViewById(R.id.tv_route_type);
            TextView tvDuration = cardView.findViewById(R.id.tv_duration);
            TextView tvDistance = cardView.findViewById(R.id.tv_distance);
            TextView tvSummary = cardView.findViewById(R.id.tv_summary);
            TextView tvScore = cardView.findViewById(R.id.tv_score);
            TextView tvTraffic = cardView.findViewById(R.id.tv_traffic_condition);
            
            tvType.setText(route.getRouteType());
            tvDuration.setText(route.getDurationInTraffic());
            tvDistance.setText(route.getDistance());
            tvSummary.setText("via " + route.getSummary());
            tvScore.setText(String.valueOf(route.getSafetyScore()));
            tvTraffic.setText(route.getTrafficCondition());

            if (route.isSelected()) {
                cardView.setStrokeColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue_light));
                cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_panel));
                tvType.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue_light));
            } else {
                cardView.setStrokeColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_border));
                cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorBackground));
                tvType.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));
            }

            final int index = i;
            cardView.setOnClickListener(v -> {
                for (Route r : routes) r.setSelected(false);
                routes.get(index).setSelected(true);
                drawRoutesOnMap(routes);
                populateRouteCards(routes);
                
                // Update safety summary card based on selection
                binding.tvSummaryScore.setText(route.getSafetyScore() + " (" + route.getTrafficCondition() + ")");
                if (route.getSafetyScore() >= 90) {
                    binding.tvSummaryScore.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.success_green));
                } else if (route.getSafetyScore() >= 70) {
                    binding.tvSummaryScore.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.warning_amber));
                } else {
                    binding.tvSummaryScore.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.danger_red));
                }
            });

            binding.containerRouteCards.addView(cardView);
        }
        
        // Setup initial summary score based on first route
        if (!routes.isEmpty()) {
            Route firstRoute = routes.get(0);
            binding.tvSummaryScore.setText(firstRoute.getSafetyScore() + " (" + firstRoute.getTrafficCondition() + ")");
            if (firstRoute.getSafetyScore() >= 90) {
                binding.tvSummaryScore.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.success_green));
            } else if (firstRoute.getSafetyScore() >= 70) {
                binding.tvSummaryScore.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.warning_amber));
            } else {
                binding.tvSummaryScore.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.danger_red));
            }
        }
    }

    private class ReportClusterRenderer extends DefaultClusterRenderer<CommunityReportItem> {
        public ReportClusterRenderer(android.content.Context context, GoogleMap map, ClusterManager<CommunityReportItem> clusterManager) {
            super(context, map, clusterManager);
        }

        @Override
        protected void onBeforeClusterItemRendered(@androidx.annotation.NonNull CommunityReportItem item, @androidx.annotation.NonNull MarkerOptions markerOptions) {
            float hue = BitmapDescriptorFactory.HUE_RED;
            if (item.getReport().getSeverity() <= 2) hue = BitmapDescriptorFactory.HUE_BLUE;
            else if (item.getReport().getSeverity() == 3) hue = BitmapDescriptorFactory.HUE_ORANGE;
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(hue));
            markerOptions.title(item.getTitle());
            markerOptions.snippet(item.getSnippet());
            super.onBeforeClusterItemRendered(item, markerOptions);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        viewModel.stopLocationUpdates();
    }
}

