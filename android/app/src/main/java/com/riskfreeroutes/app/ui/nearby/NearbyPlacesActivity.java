package com.riskfreeroutes.app.ui.nearby;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.maps.LocationHelper;
import com.riskfreeroutes.app.model.SafePlace;

import java.util.List;

public class NearbyPlacesActivity extends AppCompatActivity implements OnMapReadyCallback {

    private NearbyPlacesViewModel viewModel;
    private SafePlacesAdapter adapter;
    private GoogleMap googleMap;
    
    private MapView mapView;
    private RecyclerView rvPlaces;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private MaterialButtonToggleGroup toggleGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.util.Log.d("DIAGNOSTICS", "NearbyPlacesActivity onCreate reached");
        setContentView(R.layout.activity_nearby_places);

        viewModel = new ViewModelProvider(this).get(NearbyPlacesViewModel.class);

        // UI References
        mapView = findViewById(R.id.map_view);
        rvPlaces = findViewById(R.id.rv_places);
        progressBar = findViewById(R.id.progress_bar);
        emptyState = findViewById(R.id.empty_state);
        toggleGroup = findViewById(R.id.toggle_filters);
        ImageView btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        // Initialize MapView
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Setup RecyclerView
        adapter = new SafePlacesAdapter(viewModel);
        rvPlaces.setLayoutManager(new LinearLayoutManager(this));
        rvPlaces.setAdapter(adapter);

        // Filter Toggle Listener
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_filter_police) {
                    viewModel.setFilter("police");
                } else if (checkedId == R.id.btn_filter_hospitals) {
                    viewModel.setFilter("hospital");
                } else if (checkedId == R.id.btn_filter_pharmacies) {
                    viewModel.setFilter("pharmacy");
                }
            }
        });

        // Observers
        viewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                rvPlaces.setVisibility(View.GONE);
                emptyState.setVisibility(View.GONE);
            }
        });

        viewModel.getSafePlaces().observe(this, places -> {
            if (places == null || places.isEmpty()) {
                if (Boolean.FALSE.equals(viewModel.getIsLoading().getValue())) {
                    rvPlaces.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                    updateMapMarkers(null);
                }
            } else {
                rvPlaces.setVisibility(View.VISIBLE);
                emptyState.setVisibility(View.GONE);
                adapter.submitList(places);
                updateMapMarkers(places);
            }
        });

        // Request Location
        LocationHelper.getCurrentLocation(this, location -> {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            viewModel.setLocationAndFetch(latLng);
            if (googleMap != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
                googleMap.addMarker(new MarkerOptions().position(latLng).title("You are here")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            }
        }, e -> {
            // Handle error silently or show toast
            android.widget.Toast.makeText(this, "Failed to get location: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            progressBar.setVisibility(android.view.View.GONE);
            emptyState.setVisibility(android.view.View.VISIBLE);
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setAllGesturesEnabled(false); // static preview map
    }

    private void updateMapMarkers(List<SafePlace> places) {
        if (googleMap == null) return;
        
        googleMap.clear();
        
        // Re-add user location marker
        LocationHelper.getCurrentLocation(this, location -> {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            googleMap.addMarker(new MarkerOptions().position(latLng).title("You are here")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }, e -> {});

        if (places != null) {
            for (SafePlace place : places) {
                float markerColor = BitmapDescriptorFactory.HUE_RED;
                if ("police".equals(place.getType())) markerColor = BitmapDescriptorFactory.HUE_BLUE;
                else if ("pharmacy".equals(place.getType())) markerColor = BitmapDescriptorFactory.HUE_GREEN;

                googleMap.addMarker(new MarkerOptions()
                        .position(place.getLocation())
                        .title(place.getName())
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));
            }
        }
    }

    // MapView requires forwarding lifecycle events
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }
    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
