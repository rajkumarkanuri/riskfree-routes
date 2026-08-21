package com.riskfreeroutes.app.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * LocationTrackingService — Stub for Module 8
 *
 * This service will run in the foreground during active navigation,
 * continuously tracking the user's GPS position.
 * Full implementation in Module 8 (SOS) / Module 5 (Navigation).
 */
public class LocationTrackingService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Stub — implementation in Module 5/8
        return START_NOT_STICKY; // Don't restart service automatically if killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't support binding to this service from Activities
        return null;
    }
}
