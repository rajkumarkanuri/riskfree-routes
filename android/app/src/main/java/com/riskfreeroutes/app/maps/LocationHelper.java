package com.riskfreeroutes.app.maps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

/**
 * LocationHelper — Abstracts away the Google Play Services Location API.
 *
 * WHY THIS EXISTS:
 * Getting the user's location involves checking permissions, creating a 
 * FusedLocationProviderClient, and handling async callbacks. 
 * Putting all this logic directly in HomeActivity makes the Activity huge and
 * messy. This class provides a clean, reusable interface to get the location.
 */
public class LocationHelper {

    /**
     * Checks if the user has granted us ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
     */
    public static boolean hasLocationPermission(Context context) {
        int fineStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        int coarseStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        
        return fineStatus == PackageManager.PERMISSION_GRANTED || coarseStatus == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Fetches the last known location of the device from the FusedLocationProviderClient.
     * 
     * @param context The activity/application context.
     * @param onSuccess Callback triggered when a location is successfully found.
     * @param onFailure Callback triggered if there's an error (e.g., GPS turned off).
     */
    @SuppressLint("MissingPermission") // We suppress this because we assume the caller checked hasLocationPermission() first.
    public static void getCurrentLocation(
            Context context, 
            OnSuccessListener<Location> onSuccess, 
            OnFailureListener onFailure) {
        
        // 1. Double check permission just to be absolutely safe (prevents SecurityException crash)
        if (!hasLocationPermission(context)) {
            onFailure.onFailure(new SecurityException("Location permission not granted."));
            return;
        }

        // 2. Get the Fused Location Client
        // 'Fused' means Google Play Services handles the hard work of combining GPS, 
        // Wi-Fi, and Cell Towers to figure out exactly where the phone is, while saving battery.
        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);

        // 3. Request the last known location (this is usually instantaneous)
        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        onSuccess.onSuccess(location);
                    } else {
                        // Sometimes last location is null if the phone just booted up or 
                        // GPS is disabled. We now request fresh Location Updates here.
                        com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create();
                        locationRequest.setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);
                        locationRequest.setNumUpdates(1);
                        
                        fusedClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationCallback() {
                            @Override
                            public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                                if (locationResult != null && locationResult.getLastLocation() != null) {
                                    onSuccess.onSuccess(locationResult.getLastLocation());
                                } else {
                                    onFailure.onFailure(new Exception("Could not fetch fresh location."));
                                }
                            }
                        }, android.os.Looper.getMainLooper());
                    }
                })
                .addOnFailureListener(onFailure);
    }
}
