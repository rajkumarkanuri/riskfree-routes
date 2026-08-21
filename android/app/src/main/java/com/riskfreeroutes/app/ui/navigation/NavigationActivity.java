package com.riskfreeroutes.app.ui.navigation;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

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
import com.google.android.gms.maps.model.PolylineOptions;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.model.Route;
import com.riskfreeroutes.app.repository.ActiveRouteRepository;

/**
 * NavigationActivity — The map screen shown during active turn-by-turn navigation.
 *
 * ── RESPONSIBILITIES ─────────────────────────────────────────────────────────
 * This Activity is a THIN VIEW LAYER. It:
 *   1. Renders the Google Map and draws the route polyline
 *   2. Moves the user's position marker as GPS updates arrive
 *   3. Displays ETA, remaining distance, and turn-by-turn instruction
 *   4. Shows the EmergencyCountdown dialog when a long stop is detected
 *   5. Shows the Safe Arrival overlay when the ViewModel signals arrival
 *   6. Finishes itself (returns to the previous screen) after arrival is dismissed
 *
 * ALL LOGIC lives in NavigationViewModel. The Activity only observes LiveData
 * and updates the UI accordingly. This is the MVVM pattern.
 *
 * ── SAFE ARRIVAL OVERLAY ─────────────────────────────────────────────────────
 * When `viewModel.getArrived()` emits true:
 *   - The safeArrivalOverlay FrameLayout becomes VISIBLE
 *   - A 4-second auto-dismiss timer starts (in case the user doesn't tap "Done")
 *   - The "contacts notified" line becomes visible if smsWasSent == true
 *   - Tapping "Done" or the timer expiring calls finish() to end the Activity
 */
public class NavigationActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "NavigationActivity";

    // ── Map state ─────────────────────────────────────────────────────────────
    private GoogleMap googleMap;
    private NavigationViewModel viewModel;
    private Marker userMarker;

    // ── Navigation info TextViews (found by ID in onCreate) ──────────────────
    private TextView tvEta;
    private TextView tvDistanceRemaining;
    private TextView tvInstruction;

    // ── Safe Arrival overlay views ────────────────────────────────────────────
    private View safeArrivalOverlay;
    private TextView tvContactsNotified;

    // ── Permissions launcher ──────────────────────────────────────────────────
    private androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher;

    /**
     * Handler for the 4-second auto-dismiss timer on the Safe Arrival overlay.
     * We keep a reference so we can cancel it if the user taps "Done" early.
     */
    private final Handler autoFinishHandler = new Handler(Looper.getMainLooper());
    private Runnable autoFinishRunnable;
    private boolean isSosDialogShowing = false;
    
    private BroadcastReceiver voiceSosReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (com.riskfreeroutes.app.service.VoiceTriggerService.ACTION_VOICE_SOS_TRIGGERED.equals(intent.getAction())) {
                Toast.makeText(NavigationActivity.this, "Voice SOS Triggered!", Toast.LENGTH_SHORT).show();
                showEmergencyDialog();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
                voiceSosReceiver,
                new IntentFilter(com.riskfreeroutes.app.service.VoiceTriggerService.ACTION_VOICE_SOS_TRIGGERED)
        );
        setContentView(R.layout.activity_navigation);

        // ── ViewModel ─────────────────────────────────────────────────────────
        viewModel = new ViewModelProvider(this).get(NavigationViewModel.class);

        // ── Window insets (status bar padding) ───────────────────────────────
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout), (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, 0);
            return windowInsets;
        });

        // ── Find views ────────────────────────────────────────────────────────
        tvEta               = findViewById(R.id.tv_eta);
        tvDistanceRemaining = findViewById(R.id.tv_distance_remaining);
        tvInstruction       = findViewById(R.id.tv_instruction);
        safeArrivalOverlay  = findViewById(R.id.safeArrivalOverlay);
        tvContactsNotified  = findViewById(R.id.tvContactsNotified);

        // ── End Navigation button ─────────────────────────────────────────────
        // This is the user's MANUAL exit ("I want to stop navigating now").
        // It's different from arriving — the ViewModel records this as "ended_early".
        findViewById(R.id.btn_end_navigation).setOnClickListener(v -> {
            viewModel.stopNavigation();
            finish();
        });

        // ── "Done" button on the Safe Arrival overlay ─────────────────────────
        // Cancels the auto-dismiss timer and immediately finishes the Activity.
        findViewById(R.id.btnArrivalDone).setOnClickListener(v -> {
            cancelAutoFinish();
            finish();
        });

        // ── Map ───────────────────────────────────────────────────────────────
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map_navigation);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // ── SMS Permission ────────────────────────────────────────────────────
        // We request SEND_SMS so that the ViewModel can call SmsHelper on arrival.
        // If denied, SmsHelper will catch the SecurityException and log it gracefully.
        requestPermissionLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Toast.makeText(this,
                                "SMS permission denied — contacts won't receive arrival notifications.",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.SEND_SMS);
        }

        // ── Observe ViewModel ─────────────────────────────────────────────────
        observeViewModel();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VIEWMODEL OBSERVATION
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Sets up all LiveData observers. Each observer is called automatically by the
     * Android Lifecycle library whenever the observed LiveData value changes.
     *
     * IMPORTANT: All UI updates (setText, setVisibility, etc.) MUST happen here,
     * NOT inside the ViewModel. The ViewModel should never touch the UI directly.
     */
    private void observeViewModel() {

        // ── Location → move map marker ────────────────────────────────────────
        viewModel.getLocation().observe(this, location -> {
            if (googleMap == null) return;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            if (userMarker == null) {
                userMarker = googleMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("You")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            } else {
                userMarker.setPosition(latLng);
            }

            // Tilt the camera in navigation style (bird's-eye → forward-facing)
            float bearing = location.hasBearing() ? location.getBearing() : 0;
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(latLng)
                    .zoom(18f)
                    .bearing(bearing)
                    .tilt(45f)
                    .build();
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null);
        });

        // ── ETA seconds → "X min" text ────────────────────────────────────────
        viewModel.getEtaSeconds().observe(this, seconds -> {
            int mins = seconds / 60;
            tvEta.setText(mins + " min");
        });

        // ── Distance remaining → "X.X mi remaining" text ─────────────────────
        viewModel.getDistanceRemaining().observe(this, distanceMeters -> {
            double miles = distanceMeters * 0.000621371;
            tvDistanceRemaining.setText(String.format("%.1f mi remaining", miles));
        });

        // ── Turn instruction text ─────────────────────────────────────────────
        viewModel.getInstruction().observe(this, instruction -> {
            tvInstruction.setText(instruction);
        });

        // ── Long stop detected → emergency dialog ─────────────────────────────
        viewModel.getLongStopWarning().observe(this, warned -> {
            if (warned != null && warned) {
                showEmergencyDialog();
            }
        });

        // ── ARRIVAL DETECTED → show Safe Arrival overlay ──────────────────────
        // This is the core of the Safe Arrival feature.
        // The ViewModel sets this to true only ONCE per journey (guarded by hasArrived flag).
        viewModel.getArrived().observe(this, arrived -> {
            if (arrived != null && arrived) {
                Log.d(TAG, "Arrival confirmed by ViewModel — showing Safe Arrival overlay");
                showSafeArrivalOverlay();
            }
        });

        // ── SMS was sent → show "contacts notified" line ──────────────────────
        // This fires AFTER arrivedLiveData (once the SMS send attempt finishes).
        // We update the overlay's secondary text based on whether SMS was actually sent.
        viewModel.getSmsWasSent().observe(this, smsSent -> {
            if (smsSent != null && smsSent) {
                tvContactsNotified.setVisibility(View.VISIBLE);
                Log.d(TAG, "SMS was sent — showing 'contacts notified' line");
            } else {
                tvContactsNotified.setVisibility(View.GONE);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SAFE ARRIVAL OVERLAY
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Shows the Safe Arrival overlay and starts a 4-second auto-dismiss timer.
     *
     * WHY AUTO-DISMISS?
     * If the user is driving, they might not be able to tap "Done" immediately.
     * After 4 seconds the overlay disappears automatically and the Activity finishes,
     * returning them to the home/routes screen without requiring a tap.
     *
     * The auto-dismiss delay (4000ms) is intentionally short — the overlay is
     * confirmation, not a prompt for input. 4 seconds is enough to read it.
     */
    private void showSafeArrivalOverlay() {
        // Make the overlay visible (it was GONE before arrival)
        safeArrivalOverlay.setVisibility(View.VISIBLE);

        // Soft fade-in animation so it doesn't startle the user
        safeArrivalOverlay.setAlpha(0f);
        safeArrivalOverlay.animate()
                .alpha(1f)
                .setDuration(400)
                .start();

        // Auto-dismiss after 4 seconds
        autoFinishRunnable = this::finish;
        autoFinishHandler.postDelayed(autoFinishRunnable, 4000);
    }

    /** Cancels the auto-dismiss timer. Called when the user taps "Done" manually. */
    private void cancelAutoFinish() {
        if (autoFinishRunnable != null) {
            autoFinishHandler.removeCallbacks(autoFinishRunnable);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EMERGENCY DIALOG (Long Stop detection)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Shows the emergency countdown dialog when the user has been stationary
     * for more than 30 seconds. This is the ABNORMAL completion path (emergency),
     * as opposed to the Safe Arrival flow (normal completion path).
     */
    private void showEmergencyDialog() {
        com.riskfreeroutes.app.ui.emergency.EmergencyCountdownDialog dialog =
                com.riskfreeroutes.app.ui.emergency.EmergencyCountdownDialog.newInstance(
                        new com.riskfreeroutes.app.ui.emergency.EmergencyCountdownDialog.CountdownListener() {
                            @Override
                            public void onSafeClicked() {
                                Toast.makeText(NavigationActivity.this,
                                        "Guardian alert cancelled.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onEmergencyNowClicked() {
                                triggerEmergencySms();
                            }

                            @Override
                            public void onCountdownFinished() {
                                triggerEmergencySms();
                            }

                            @Override
                            public void onCancelClicked() {
                                Toast.makeText(NavigationActivity.this,
                                        "Guardian alert cancelled.", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFindNearbyHelpClicked() {
                                startActivity(new android.content.Intent(NavigationActivity.this,
                                        com.riskfreeroutes.app.ui.nearby.NearbyPlacesActivity.class));
                            }
                        }
                );
        dialog.show(getSupportFragmentManager(), "EmergencyCountdown");
    }

    /**
     * Sends the EMERGENCY SOS alert — different from the Safe Arrival notification.
     * This is the ABNORMAL path: user in distress, needs immediate help.
     */
    private void triggerEmergencySms() {
        android.location.Location loc = viewModel.getLocation().getValue();
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String name = user != null && user.getDisplayName() != null
                ? user.getDisplayName() : "A user";

        com.riskfreeroutes.app.repository.GuardianRepository repo =
                new com.riskfreeroutes.app.repository.GuardianRepository();
        repo.fetchContactsAndTriggerSOS(this, name, loc, sosDocId -> {
            if (sosDocId != null) {
                Toast.makeText(this, "✅ Emergency SMS Sent Successfully", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to send emergency alert.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();
        // Re-start GPS updates when the Activity comes back to the foreground.
        // (e.g. user switches apps and then returns)
        viewModel.startNavigation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // We stop location updates when the Activity is backgrounded.
        // NOTE: This does NOT record "ended_early" — stopNavigation() is only called
        // on manual "End" button tap. If the system pauses us, we'll resume on onResume().
        // A production app would use a foreground Service for background GPS — see LocationTrackingService stub.
        viewModel.stopNavigation();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceSosReceiver);
        super.onDestroy();
        // Always cancel the auto-dismiss timer when the Activity is destroyed.
        // If we don't, the Runnable holds a reference to `this` (NavigationActivity)
        // and calling finish() on a destroyed Activity would throw an exception.
        cancelAutoFinish();
        viewModel.stopNavigation();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // MAP READY
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;

        // Apply dark map style (matches the app's dark glass design system)
        try {
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark));
            if (!success) Log.e(TAG, "Style parsing failed.");
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find map style resource.", e);
        }

        // Draw the planned route as a blue polyline on the map
        Route route = ActiveRouteRepository.getInstance().getActiveRoute();
        if (route != null && route.getDecodedPath() != null) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(route.getDecodedPath())
                    .color(ContextCompat.getColor(this, R.color.brand_blue))
                    .width(18f)
                    .geodesic(true);
            googleMap.addPolyline(polylineOptions);

            // Center the camera on the starting point of the route
            if (!route.getDecodedPath().isEmpty()) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        route.getDecodedPath().get(0), 15f));
            }
        }
    }
}
