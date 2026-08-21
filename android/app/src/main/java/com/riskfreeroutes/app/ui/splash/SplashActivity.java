package com.riskfreeroutes.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.ui.auth.LoginActivity;
import com.riskfreeroutes.app.ui.home.HomeActivity;

/**
 * SplashActivity — Branded Launch Screen
 *
 * WHY THIS EXISTS:
 * This is the FIRST screen the user sees when they open the app.
 * It serves two purposes:
 *
 * 1. BRANDING — Shows our glass-card logo design (from Stitch) for 2 seconds.
 *    The animated blobs + frosted glass card creates a premium first impression.
 *
 * 2. AUTH CHECK — While the animation plays, we silently ask Firebase:
 *    "Is there already a logged-in user?"
 *    → YES (FirebaseUser is not null) → go straight to HomeActivity
 *    → NO  (FirebaseUser is null)     → go to LoginActivity
 *
 * This means users who have already logged in are NEVER shown the login
 * screen again — they go straight to the map. Just like WhatsApp, Swiggy, etc.
 *
 * DESIGN SOURCE: Matches your Google Stitch splash screen design exactly:
 * - #f9f9fb background
 * - 3 ambient coloured blob shapes (blue, lavender, mint)
 * - Semi-transparent frosted glass card center
 * - App icon (black rounded square) + "RiskFree Routes" + "SOPHISTICATED TRAVEL"
 * - 3 staggered pulsing dots at the bottom
 * - Content fades in with scale animation (0.95 → 1.0)
 *
 * ARCHITECTURE: This Activity has NO ViewModel because there is NO
 * complex data. It's purely UI (animations) + one Firebase auth check.
 */
public class SplashActivity extends AppCompatActivity {

    // ── CONSTANTS ──────────────────────────────────────────────────
    /**
     * Total time the splash screen is shown before navigating away.
     * 2200ms = 2.2 seconds — enough to see the animation complete.
     */
    private static final long SPLASH_DURATION_MS = 2200L;

    /**
     * Delay offsets for the staggered dot pulse animation.
     * Each dot starts its pulse animation this many ms after the previous.
     * Creates a "wave" effect: dot1 → dot2 → dot3.
     */
    private static final long DOT_PULSE_OFFSET_MS = 200L;

    // ── VIEW REFERENCES ────────────────────────────────────────────
    private View layoutSplashContent; // The main glass card (icon + name + tagline)
    private View dot1;                // Loading dot 1
    private View dot2;                // Loading dot 2
    private View dot3;                // Loading dot 3
    private View blobBlue;            // Blue ambient blob
    private View blobLavender;        // Lavender ambient blob
    private View blobMint;            // Mint green ambient blob
    private View imgLogo;             // App logo image (for subtle scale animation)

    /**
     * Called when the Activity is created.
     * We set up the layout and start all animations here.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Always call super first

        // Set the XML layout for this Activity.
        // R.layout.activity_splash → app/src/main/res/layout/activity_splash.xml
        setContentView(R.layout.activity_splash);

        // ── STEP 1: Find all views ───────────────────────────────
        // 'findViewById' searches the XML layout for a view with the given ID.
        // We store them in variables so we don't call findViewById repeatedly.
        layoutSplashContent = findViewById(R.id.layout_splash_content);
        dot1         = findViewById(R.id.dot_1);
        dot2         = findViewById(R.id.dot_2);
        dot3         = findViewById(R.id.dot_3);
        blobBlue     = findViewById(R.id.blob_blue);
        blobLavender = findViewById(R.id.blob_lavender);
        blobMint     = findViewById(R.id.blob_mint);
        imgLogo      = findViewById(R.id.img_logo);

        // ── STEP 2: Start all animations ─────────────────────────
        startContentAnimation();   // Fade-in + scale for the glass card
        startDotAnimations();      // Staggered pulse on the 3 dots
        startBlobAnimations();     // Gentle floating movement on blobs
        startLogoAnimation();      // Stitch logo subtle scale pulse (500ms delay)

        // ── STEP 3: Schedule navigation after SPLASH_DURATION_MS ─
        // After 2.2 seconds, decide where to go based on Firebase auth state.
        new Handler(Looper.getMainLooper()).postDelayed(
                this::checkAuthAndNavigate,
                SPLASH_DURATION_MS
        );
    }

    /**
     * Plays the main content entrance animation — matching the Stitch CSS:
     *
     *   @keyframes fadeInScale {
     *       from { opacity: 0; transform: scale(0.95); }
     *       to   { opacity: 1; transform: scale(1); }
     *   }
     *   animation: fadeInScale 1.2s cubic-bezier(0.16, 1, 0.3, 1);
     *
     * We load the animation from res/anim/splash_content_enter.xml
     * and apply it to the glass card LinearLayout.
     */
    private void startContentAnimation() {
        // Load our animation XML from the res/anim folder.
        // AnimationUtils.loadAnimation(context, animationResourceId)
        Animation contentEnter = AnimationUtils.loadAnimation(this, R.anim.splash_content_enter);

        // Apply the animation to the card.
        // The card will fade in from 0 → 1 alpha while scaling from 0.95 → 1.0
        layoutSplashContent.startAnimation(contentEnter);
    }

    /**
     * Starts the pulsing alpha animations on the 3 loading dots.
     *
     * WHY STAGGERED?
     * If all 3 dots pulsed at the same time, it would look like a single blinking
     * blob. Staggering by 200ms creates a flowing "wave" effect — exactly what
     * the Stitch design shows (Tailwind animate-pulse with sequential delays).
     *
     * We load the same animation for each dot, then set a different startOffset
     * (delay) before starting it:
     *   Dot 1: starts at 0ms    (immediately)
     *   Dot 2: starts at 200ms  (after 200ms)
     *   Dot 3: starts at 400ms  (after 400ms)
     */
    private void startDotAnimations() {
        startPulseWithDelay(dot1, 0L);                         // No delay
        startPulseWithDelay(dot2, DOT_PULSE_OFFSET_MS);        // 200ms delay
        startPulseWithDelay(dot3, DOT_PULSE_OFFSET_MS * 2);    // 400ms delay
    }

    /**
     * Helper — loads the dot_pulse animation, sets a start offset, and starts it.
     *
     * @param dot         The View (small circle) to animate.
     * @param offsetMs    How many milliseconds to delay before starting the pulse.
     */
    private void startPulseWithDelay(View dot, long offsetMs) {
        // Load the AlphaAnimation from res/anim/dot_pulse.xml
        AlphaAnimation pulse = (AlphaAnimation) AnimationUtils.loadAnimation(this, R.anim.dot_pulse);

        // setStartOffset delays the animation — the view stays at full alpha
        // until offsetMs has passed, then the pulse cycle begins.
        pulse.setStartOffset(offsetMs);

        // Start the animation on this dot view.
        dot.startAnimation(pulse);
    }

    /**
     * Animates the ambient blob shapes with a gentle floating motion.
     *
     * WHY THIS?
     * The Stitch design animates the blobs with CSS:
     *   @keyframes moveBlob {
     *       0%   { transform: translate(0, 0) scale(1); }
     *       50%  { transform: translate(50px, 100px) scale(1.1); }
     *       100% { transform: translate(-50px, -50px) scale(0.9); }
     *   }
     *   animation: moveBlob 25s infinite alternate;
     *
     * We replicate this using Android's ObjectAnimator — animating the
     * translationX and translationY properties of each blob view.
     *
     * NOTE: For API 26+ (our minSdk), ObjectAnimator works perfectly.
     * We use ValueAnimator to keep it compatible without importing
     * the full android.animation package set.
     */
    private void startBlobAnimations() {
        // Blob 1 (Blue): moves gently to the right and down
        animateBlob(blobBlue, -80f, -40f, -80f + 30f, -80f + 50f, 20000);

        // Blob 2 (Lavender): moves gently to the left and up
        animateBlob(blobLavender, 80f, 100f, 80f - 20f, 100f - 40f, 25000);

        // Blob 3 (Mint): subtle right-to-left drift
        animateBlob(blobMint, 60f, 0f, 60f - 25f, 10f, 18000);
    }

    /**
     * Animates a single blob view back and forth between two positions.
     *
     * We use animate() which is the simplest ViewPropertyAnimator API in Android.
     * It creates a smooth, looping translation animation.
     *
     * @param blob          The blob View to animate
     * @param fromX         Starting X translation (in dp equivalent pixels)
     * @param fromY         Starting Y translation
     * @param toX           Ending X translation
     * @param toY           Ending Y translation
     * @param durationMs    Duration of one animation cycle in milliseconds
     */
    private void animateBlob(View blob, float fromX, float fromY,
                              float toX, float toY, long durationMs) {
        // Set initial position
        blob.setTranslationX(fromX);
        blob.setTranslationY(fromY);

        // Animate to the target position.
        // withEndAction creates a loop: when one animation ends, it starts going back.
        blob.animate()
                .translationX(toX)
                .translationY(toY)
                .setDuration(durationMs)
                // withEndAction runs when this animation finishes.
                // We reverse the direction to create an infinite back-and-forth loop.
                .withEndAction(() -> blob.animate()
                        .translationX(fromX)
                        .translationY(fromY)
                        .setDuration(durationMs)
                        .withEndAction(() -> startBlobAnimations()) // restart cycle
                        .start())
                .start();
    }

    /**
     * Checks Firebase Authentication state and navigates to the correct screen.
     *
     * FIREBASE AUTH CHECK:
     * FirebaseAuth.getInstance().getCurrentUser() returns:
     *   → A FirebaseUser object if the user is signed in (session is active)
     *   → null if no user is signed in
     *
     * Firebase automatically handles token refresh — if a user signed in 30 days
     * ago and the token expired, Firebase renews it silently. We don't need to
     * manually manage token expiry like we would with custom JWT.
     *
     * NAVIGATION:
     *   Signed in  → HomeActivity (skip login, go straight to the map)
     *   Not signed → LoginActivity (show auth screens)
     */
    private void checkAuthAndNavigate() {
        // Get the currently signed-in Firebase user.
        // This is a LOCAL check (no network call) — it reads from the device cache.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Determine destination: if user exists, go to Home; otherwise, go to Login.
        Class<?> destination = (currentUser != null)
                ? HomeActivity.class
                : LoginActivity.class;

        // Create Intent to start the destination Activity.
        Intent intent = new Intent(SplashActivity.this, destination);

        // Start the new Activity.
        startActivity(intent);

        // Finish SplashActivity so pressing Back doesn't return to the splash.
        finish();
    }

    /**
     * Replicates the Stitch JavaScript logo animation:
     *
     *   setTimeout(() => {
     *       logo.style.transition = 'transform 2s cubic-bezier(0.16, 1, 0.3, 1)';
     *       logo.style.transform = 'scale(1.05)';     // breathes out
     *       setTimeout(() => {
     *           logo.style.transform = 'scale(1)';    // breathes back in
     *       }, 2000);
     *   }, 500);
     *
     * Gives the logo a gentle "breathing" pulse — starts 500ms after launch,
     * scales up to 105% over 2 seconds, then returns to 100%.
     */
    private void startLogoAnimation() {
        if (imgLogo == null) return;

        // Wait 500ms then do the scale-up
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            imgLogo.animate()
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(2000)
                    // After scale-up completes, scale back down
                    .withEndAction(() -> imgLogo.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(2000)
                            .start())
                    .start();
        }, 500);
    }

    /**
     * Called when the Activity is destroyed (e.g., user presses Back quickly).
     * We cancel all animations to prevent memory leaks.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (layoutSplashContent != null) layoutSplashContent.clearAnimation();
        if (dot1 != null) dot1.clearAnimation();
        if (dot2 != null) dot2.clearAnimation();
        if (dot3 != null) dot3.clearAnimation();
        if (blobBlue != null) blobBlue.animate().cancel();
        if (blobLavender != null) blobLavender.animate().cancel();
        if (blobMint != null) blobMint.animate().cancel();
        if (imgLogo != null) imgLogo.animate().cancel();
    }
}

