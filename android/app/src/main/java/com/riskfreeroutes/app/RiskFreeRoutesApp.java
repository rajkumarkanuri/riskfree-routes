package com.riskfreeroutes.app;

import android.app.Application;
import android.util.Log;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

/**
 * RiskFreeRoutesApp — Application Entry Point.
 */
public class RiskFreeRoutesApp extends Application {

    private static final String TAG = "RiskFreeRoutesApp";
    private static RiskFreeRoutesApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "Application started — Risk Free Routes");

        // Initialize Cloudinary MediaManager for profile & report photo uploads
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", getString(R.string.cloudinary_cloud_name));
            config.put("secure", true);
            MediaManager.init(this, config);
            Log.d(TAG, "Cloudinary MediaManager initialized successfully");
        } catch (Exception e) {
            Log.w(TAG, "Cloudinary MediaManager initialization warning: " + e.getMessage());
        }
    }

    public static RiskFreeRoutesApp getInstance() {
        return instance;
    }
}
