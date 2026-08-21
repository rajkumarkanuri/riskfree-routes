package com.riskfreeroutes.app;

import android.app.Application;
import android.util.Log;

/**
 * RiskFreeRoutesApp — The Application Class
 *
 * WHY THIS EXISTS:
 * Android creates ONE instance of this class when the app process starts —
 * before any Activity is shown. It lives for the ENTIRE lifetime of the app.
 *
 * We use it to:
 * 1. Initialize libraries that need an Application context (like Retrofit client setup).
 * 2. Store app-wide singletons (we'll add them in later modules).
 * 3. Set up crash reporting or logging.
 *
 * IMPORTANT: This class is registered in AndroidManifest.xml via:
 *   android:name=".RiskFreeRoutesApp"
 * Without that line, Android ignores this class entirely.
 *
 * Think of this class as the "app's main() method" — the very first
 * code that runs when your app is launched.
 */
public class RiskFreeRoutesApp extends Application {

    // A tag used in Logcat to identify log messages from this class.
    // In Android, Log.d(TAG, "message") prints to the Logcat console.
    private static final String TAG = "RiskFreeRoutesApp";

    // A static reference to the Application instance.
    // WHY: Some utility classes (like our RetrofitClient) need a Context
    // but don't have an Activity reference. We give them the Application context
    // via this static getter. Application context is safe to hold statically
    // because it lives as long as the app — unlike Activity context which
    // can cause memory leaks if held statically.
    private static RiskFreeRoutesApp instance;

    /**
     * Called by Android ONCE when the app process is created.
     * This runs before any Activity, Service, or BroadcastReceiver starts.
     */
    @Override
    public void onCreate() {
        super.onCreate(); // Always call super first

        // Store the singleton instance so other classes can access it.
        instance = this;

        Log.d(TAG, "Application started — Risk Free Routes v1.0");

        // Future modules will add initialization here, for example:
        // RetrofitClient.init(this);      ← Module 2 (networking)
        // RoomDatabase.init(this);        ← Module 3 (local DB)
    }

    /**
     * Returns the singleton Application instance.
     * Other classes call RiskFreeRoutesApp.getInstance() to get a Context
     * without needing an Activity reference.
     *
     * @return The single Application instance.
     */
    public static RiskFreeRoutesApp getInstance() {
        return instance;
    }
}
