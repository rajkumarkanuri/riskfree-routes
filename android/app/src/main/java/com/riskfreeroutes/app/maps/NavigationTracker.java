package com.riskfreeroutes.app.maps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.riskfreeroutes.app.model.Route;

import java.util.List;

public class NavigationTracker {

    public interface NavigationUpdateListener {
        void onLocationUpdated(Location location, double speedMps, double distanceRemainingMeters, int etaSeconds, String nextInstruction, double nextInstructionDistanceMeters);
        void onLongStopDetected();
    }

    private final FusedLocationProviderClient fusedLocationClient;
    private final NavigationUpdateListener listener;
    private Route currentRoute;
    private LocationCallback locationCallback;
    private int currentStepIndex = 0;
    
    private long stoppedStartTime = 0;
    private static final long LONG_STOP_THRESHOLD_MS = 30000; // 30 seconds

    public NavigationTracker(Context context, NavigationUpdateListener listener) {
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.listener = listener;
    }

    public void setRoute(Route route) {
        this.currentRoute = route;
        this.currentStepIndex = 0;
    }

    @SuppressLint("MissingPermission")
    public void startTracking() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(3000); // 3 seconds
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    processLocation(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public void stopTracking() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void processLocation(Location location) {
        if (currentRoute == null || currentRoute.getDecodedPath() == null || currentRoute.getDecodedPath().isEmpty()) {
            if (listener != null) listener.onLocationUpdated(location, 0.0, 0.0, 0, null, 0.0);
            return;
        }

        double speed = location.hasSpeed() ? location.getSpeed() : 0.0;
        
        // Detect Long Stop
        if (speed < 0.5) { // less than 0.5 m/s is considered stopped
            if (stoppedStartTime == 0) {
                stoppedStartTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stoppedStartTime > LONG_STOP_THRESHOLD_MS) {
                if (listener != null) listener.onLongStopDetected();
            }
        } else {
            stoppedStartTime = 0; // reset
        }

        // Background thread calculation for distance/ETA to avoid main thread lag
        new Thread(() -> {
            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            List<LatLng> path = currentRoute.getDecodedPath();
            
            // 1. Find the nearest point on the route
            int nearestIndex = 0;
            double minDistance = Double.MAX_VALUE;
            
            for (int i = 0; i < path.size(); i++) {
                double dist = SphericalUtil.computeDistanceBetween(currentLatLng, path.get(i));
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestIndex = i;
                }
            }

            // 2. Sum the remaining distance
            double remainingDistance = minDistance; // distance to the path
            for (int i = nearestIndex; i < path.size() - 1; i++) {
                remainingDistance += SphericalUtil.computeDistanceBetween(path.get(i), path.get(i + 1));
            }

            // 3. Calculate ETA
            // If speed is near zero, assume a typical city driving speed (e.g. 5 m/s ~ 11 mph) to prevent infinite ETA
            double effectiveSpeed = speed < 1.0 ? 5.0 : speed;
            int etaSeconds = (int) (remainingDistance / effectiveSpeed);

            // 4. Calculate Step Instruction
            String nextInstruction = "Follow the route";
            double nextInstructionDistanceMeters = 0.0;
            if (currentRoute.getSteps() != null && !currentRoute.getSteps().isEmpty()) {
                if (currentStepIndex < currentRoute.getSteps().size()) {
                    Route.RouteStep currentStep = currentRoute.getSteps().get(currentStepIndex);
                    nextInstructionDistanceMeters = SphericalUtil.computeDistanceBetween(currentLatLng, currentStep.endLocation);
                    nextInstruction = currentStep.instruction;

                    // If we are very close to the end of the step (e.g. within 20 meters), advance to the next step
                    if (nextInstructionDistanceMeters < 20.0 && currentStepIndex < currentRoute.getSteps().size() - 1) {
                        currentStepIndex++;
                    }
                }
            }

            if (listener != null) {
                listener.onLocationUpdated(location, speed, remainingDistance, etaSeconds, nextInstruction, nextInstructionDistanceMeters);
            }
        }).start();
    }

    public void resetLongStopDetection() {
        stoppedStartTime = 0;
    }
}

