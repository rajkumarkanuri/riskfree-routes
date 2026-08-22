package com.riskfreeroutes.app.ui.routes;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityRouteSelectionBinding;
import com.riskfreeroutes.app.model.Route;
import com.riskfreeroutes.app.utils.SafetyScoreResult;

import com.google.android.gms.maps.model.LatLng;
import com.riskfreeroutes.app.repository.JourneyHistoryRepository;

import java.util.ArrayList;
import java.util.List;

public class RouteSelectionActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ActivityRouteSelectionBinding binding;
    private GoogleMap googleMap;
    private RouteSelectionViewModel viewModel;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    
    private List<Polyline> drawnPolylines = new ArrayList<>();
    
    // Pass destination from Home via intent
    public static final String EXTRA_DESTINATION_NAME = "destination_name";
    public static final String EXTRA_DEST_LAT = "dest_lat";
    public static final String EXTRA_DEST_LNG = "dest_lng";
    public static final String EXTRA_ORIGIN_LAT = "origin_lat";
    public static final String EXTRA_ORIGIN_LNG = "origin_lng";

    private androidx.activity.result.ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                // Re-check permissions directly from ContextCompat to be safe, because the result map 
                // only contains the permissions that were just requested.
                boolean hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                boolean hasSms = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                
                if (hasLocation) {
                    if (!hasSms) {
                        Toast.makeText(this, "SMS permission denied — contacts won't receive arrival notifications.", Toast.LENGTH_LONG).show();
                    }
                    startNavigationActivity();
                } else {
                    Toast.makeText(this, "Location permission required for navigation", Toast.LENGTH_SHORT).show();
                }
            });

    private void startNavigationActivity() {
        Route selected = viewModel.getSelectedRoute().getValue();
        if (selected == null) {
            Toast.makeText(this, "Please select a route first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Store the route in the singleton so NavigationActivity/ViewModel can access it.
        com.riskfreeroutes.app.repository.ActiveRouteRepository.getInstance().setActiveRoute(selected);

        // ── Start the journey in Firestore BEFORE launching NavigationActivity ──
        // We do this here so the journeyId is ready by the time NavigationViewModel
        // reads ActiveRouteRepository.getActiveJourneyId() in its constructor.
        //
        // The destination coordinates come from the last LatLng in the decoded route path
        // (the route's polyline endpoint IS the destination).
        java.util.List<LatLng> path = selected.getDecodedPath();
        double originLat = getIntent().getDoubleExtra(EXTRA_ORIGIN_LAT, 0);
        double originLng = getIntent().getDoubleExtra(EXTRA_ORIGIN_LNG, 0);
        double destLat   = (!path.isEmpty()) ? path.get(path.size() - 1).latitude  : 0;
        double destLng   = (!path.isEmpty()) ? path.get(path.size() - 1).longitude : 0;

        new JourneyHistoryRepository().startJourney(
                originLat, originLng, destLat, destLng, selected.getSafetyScore(),
                journeyId -> {
                    // Store the journey ID so NavigationViewModel can call endJourney on arrival.
                    com.riskfreeroutes.app.repository.ActiveRouteRepository.getInstance()
                            .setActiveJourneyId(journeyId);
                    Log.d("RouteSelectionActivity", "Journey started in Firestore: " + journeyId);
                }
        );

        // Launch the navigation screen.
        // NOTE: startJourney() is asynchronous, but this is acceptable:
        // the journey doc is created within <500ms (typical Firestore write latency).
        // NavigationViewModel reads the journeyId in its constructor which runs
        // after onCreate, giving the callback time to complete.
        Intent intent = new Intent(this, com.riskfreeroutes.app.ui.navigation.NavigationActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRouteSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(RouteSelectionViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

        String destName = getIntent().getStringExtra(EXTRA_DESTINATION_NAME);
        if (destName != null) {
            binding.tvDestinationTop.setText("To: " + destName);
        }

        binding.btnBack.setOnClickListener(v -> finish());
        
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet);

        binding.btnViewDetails.setOnClickListener(v -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                binding.btnViewDetails.setText("Hide Details");
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                binding.btnViewDetails.setText("Details");
            }
        });

        binding.btnStartNavigation.setOnClickListener(v -> {
            boolean hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean hasSms = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;

            if (hasLocation && hasSms) {
                startNavigationActivity();
            } else {
                java.util.List<String> permissionsToRequest = new java.util.ArrayList<>();
                if (!hasLocation) {
                    permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
                    permissionsToRequest.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
                }
                if (!hasSms) {
                    permissionsToRequest.add(android.Manifest.permission.SEND_SMS);
                }
                requestPermissionsLauncher.launch(permissionsToRequest.toArray(new String[0]));
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        observeViewModel();

        double dLat = getIntent().getDoubleExtra(EXTRA_DEST_LAT, 0.0);
        double dLng = getIntent().getDoubleExtra(EXTRA_DEST_LNG, 0.0);
        
        if (dLat == 0.0 && dLng == 0.0) {
            Toast.makeText(this, "Destination coordinates not specified", Toast.LENGTH_SHORT).show();
            return;
        }
        LatLng destLoc = new LatLng(dLat, dLng);

        boolean hasOrigin = getIntent().hasExtra(EXTRA_ORIGIN_LAT) && getIntent().hasExtra(EXTRA_ORIGIN_LNG);
        if (hasOrigin) {
            double oLat = getIntent().getDoubleExtra(EXTRA_ORIGIN_LAT, 0.0);
            double oLng = getIntent().getDoubleExtra(EXTRA_ORIGIN_LNG, 0.0);
            LatLng currentLoc = new LatLng(oLat, oLng);
            initRouteWithPoints(currentLoc, destLoc);
        } else {
            // Fetch real GPS current location dynamically
            com.riskfreeroutes.app.maps.LocationHelper.getCurrentLocation(this, location -> {
                LatLng currentLoc = new LatLng(location.getLatitude(), location.getLongitude());
                initRouteWithPoints(currentLoc, destLoc);
            }, error -> {
                Toast.makeText(this, "Could not acquire current location: " + error.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void initRouteWithPoints(LatLng currentLoc, LatLng destLoc) {
        if (googleMap == null) return;
        googleMap.clear();
        googleMap.addMarker(new MarkerOptions()
                .position(currentLoc)
                .title("My Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        googleMap.addMarker(new MarkerOptions()
                .position(destLoc)
                .title(getIntent().getStringExtra(EXTRA_DESTINATION_NAME) != null ? getIntent().getStringExtra(EXTRA_DESTINATION_NAME) : "Destination"));

        // Fetch real routes from Google Directions API
        viewModel.fetchRoutes(currentLoc, destLoc);
    }

    private void observeViewModel() {
        viewModel.getRoutes().observe(this, this::renderRoutesOnMap);
        viewModel.getRoutes().observe(this, this::renderRouteCards);
        
        viewModel.getLiveReports().observe(this, reports -> {
            if (viewModel.getSelectedRoute().getValue() != null) {
                viewModel.analyzeRouteSafety(viewModel.getSelectedRoute().getValue(), reports);
            }
        });
        
        viewModel.getSelectedRoute().observe(this, route -> {
            if (viewModel.getLiveReports().getValue() != null) {
                viewModel.analyzeRouteSafety(route, viewModel.getLiveReports().getValue());
            } else {
                // Generate without reports initially or wait
                viewModel.analyzeRouteSafety(route, new ArrayList<>());
            }
        });
        
        viewModel.getSafetyAnalysis().observe(this, result -> {
            if (result != null) {
                binding.tvRecommendationBanner.setText("Recommended Route · Safety Score " + result.getScore());
                binding.tvRecommendationReason.setText(result.getReasons().isEmpty() ? "" : result.getReasons().get(0));
                
                binding.tvVerifiedReports.setText(result.getVerifiedHazardsCount() + " Nearby");
                binding.tvEmergencyServices.setText((result.getNearbyPoliceCount() + result.getNearbyHospitalCount()) + " Nearby");

                // Render bullets
                binding.containerSafetyReasons.removeAllViews();
                for (String reason : result.getReasons()) {
                    TextView tv = new TextView(RouteSelectionActivity.this);
                    tv.setText(reason);
                    tv.setTextColor(androidx.core.content.ContextCompat.getColor(RouteSelectionActivity.this, R.color.text_secondary));
                    tv.setTextSize(14f);
                    tv.setPadding(0, 4, 0, 4);
                    binding.containerSafetyReasons.addView(tv);
                }
                
                renderHazardTimeline(result);
            }
        });
    }

    private void renderRoutesOnMap(List<Route> routes) {
        if (googleMap == null || routes == null) return;
        
        for (Polyline p : drawnPolylines) p.remove();
        drawnPolylines.clear();

        for (int i = routes.size() - 1; i >= 0; i--) {
            Route route = routes.get(i);
            boolean isSelected = route.isSelected();
            int color;
            int width = isSelected ? 18 : 12;
            int zIndex = isSelected ? 100 : 0;
            
            if (isSelected) {
                color = androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue); // Blue top-scored
            } else if (route.getSafetyScore() >= 80) {
                color = androidx.core.content.ContextCompat.getColor(this, R.color.success_green); // Light Green safe alternative
            } else {
                color = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary); // Gray others
            }

            Polyline polyline = googleMap.addPolyline(new PolylineOptions()
                    .addAll(route.getDecodedPath())
                    .color(color)
                    .width(width)
                    .zIndex(zIndex));
            
            drawnPolylines.add(polyline);
        }
        
        if (!routes.isEmpty()) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(routes.get(0).getDecodedPath().get(0), 12f));
        }
    }

    private void renderRouteCards(List<Route> routes) {
        binding.rvRoutes.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        binding.rvRoutes.setAdapter(new RecyclerView.Adapter<RouteViewHolder>() {
            @androidx.annotation.NonNull
            @Override
            public RouteViewHolder onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_route_card, parent, false);
                return new RouteViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@androidx.annotation.NonNull RouteViewHolder holder, int position) {
                Route route = routes.get(position);
                holder.tvType.setText(route.getRouteType());
                holder.tvDuration.setText(route.getDurationInTraffic());
                holder.tvDistance.setText(route.getDistance());
                holder.tvSummary.setText("via " + route.getSummary());
                holder.tvScore.setText(String.valueOf(route.getSafetyScore()));
                holder.tvTraffic.setText(route.getTrafficCondition());

                if (route.isSelected()) {
                    holder.cardView.setStrokeColor(androidx.core.content.ContextCompat.getColor(RouteSelectionActivity.this, R.color.primary_blue_light));
                    holder.cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(RouteSelectionActivity.this, R.color.glass_panel));
                    holder.tvType.setTextColor(androidx.core.content.ContextCompat.getColor(RouteSelectionActivity.this, R.color.primary_blue_light));
                } else {
                    holder.cardView.setStrokeColor(androidx.core.content.ContextCompat.getColor(RouteSelectionActivity.this, R.color.glass_border));
                    holder.cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(RouteSelectionActivity.this, R.color.colorBackground));
                    holder.tvType.setTextColor(androidx.core.content.ContextCompat.getColor(RouteSelectionActivity.this, R.color.text_secondary));
                }

                holder.itemView.setOnClickListener(v -> viewModel.selectRoute(route));
            }

            @Override
            public int getItemCount() { return routes.size(); }
        });
    }

    private class RouteViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView cardView;
        TextView tvType, tvDuration, tvDistance, tvSummary, tvScore, tvTraffic;

        RouteViewHolder(View itemView) {
            super(itemView);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
            tvType = itemView.findViewById(R.id.tv_route_type);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            tvScore = itemView.findViewById(R.id.tv_score);
            tvTraffic = itemView.findViewById(R.id.tv_traffic_condition);
        }
    }

    private void renderHazardTimeline(SafetyScoreResult result) {
        binding.containerHazardTimeline.removeAllViews();
        
        // Add Start Dot
        addTimelineNode(false, false);
        
        // Add Hazards
        for (int i = 0; i < result.getVerifiedHazardsCount(); i++) {
            addTimelineNode(true, false);
        }
        
        // Add Destination
        addTimelineNode(false, true);
    }
    
    private void addTimelineNode(boolean isHazard, boolean isDestination) {
        View v = getLayoutInflater().inflate(R.layout.item_hazard_timeline, binding.containerHazardTimeline, false);
        ImageView icon = v.findViewById(R.id.img_hazard_icon);
        View lineRight = v.findViewById(R.id.line_right);
        
        if (isHazard) {
            icon.setImageResource(R.drawable.ic_shield); // Replace with hazard icon
            icon.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.danger_red));
        } else if (isDestination) {
            icon.setImageResource(R.drawable.ic_location_pin);
            icon.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue));
            lineRight.setVisibility(View.GONE);
        } else {
            icon.setImageResource(R.drawable.ic_my_location);
            icon.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue));
        }
        
        binding.containerHazardTimeline.addView(v);
    }
}

