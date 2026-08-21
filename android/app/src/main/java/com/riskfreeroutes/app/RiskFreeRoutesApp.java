package com.riskfreeroutes.app;

import android.app.Application;
import android.util.Log;

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
    }

    public static RiskFreeRoutesApp getInstance() {
        return instance;
    }
}
