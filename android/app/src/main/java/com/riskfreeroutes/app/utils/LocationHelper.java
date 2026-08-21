package com.riskfreeroutes.app.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * A helper class to manage location requests cleanly outside of UI code.
 * It uses the FusedLocationProviderClient to get real-time location and exposes it as LiveData.
 */
public class LocationHelper {
    private final FusedLocationProviderClient fusedLocationClient;
    private final MutableLiveData<Location> currentLocation = new MutableLiveData<>();
    private LocationCallback locationCallback;

    public LocationHelper(Context context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Exposes the location stream as LiveData so ViewModels can observe it.
     */
    public LiveData<Location> getCurrentLocation() {
        return currentLocation;
    }

    /**
     * Starts requesting location updates. 
     * IMPORTANT: The caller (Activity/Fragment) must check location permissions before calling this!
     */
    @SuppressLint("MissingPermission") // Caller is responsible for permission checks
    public void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    currentLocation.postValue(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        
        // Also grab the last known location for a quick first result
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation.postValue(location);
            }
        });
    }

    /**
     * Stops requesting location updates to save battery.
     */
    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
