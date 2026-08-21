package com.riskfreeroutes.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityProfileBinding;
import com.riskfreeroutes.app.model.User;
import com.riskfreeroutes.app.ui.auth.LoginActivity;
import com.riskfreeroutes.app.ui.contacts.EmergencyContactsActivity;
import com.riskfreeroutes.app.ui.journey.JourneyHistoryActivity;
import com.riskfreeroutes.app.ui.nearby.NearbyPlacesActivity;
import com.riskfreeroutes.app.ui.reports.ReportListActivity;
import com.riskfreeroutes.app.ui.settings.SettingsActivity;

/**
 * ProfileActivity — The user's profile screen.
 *
 * ─── ARCHITECTURE ────────────────────────────────────────────────────────────
 * ProfileActivity (View)
 *   → ProfileViewModel (holds LiveData, survives rotation)
 *     → UserRepository (Firestore snapshot listener)
 *
 * Why a snapshot listener instead of a one-time get()?
 * addSnapshotListener() sends updates to the UI AUTOMATICALLY whenever the
 * Firestore document changes — even if the change was made from another device
 * or by a cloud function. This means if totalJourneys increments in the
 * background while this screen is open, the counter updates instantly without
 * the user needing to close and reopen the screen.
 *
 * ─── DATA FLOW ───────────────────────────────────────────────────────────────
 * 1. onCreate → viewModel.loadAllData() kicks off Firestore listener
 * 2. Firestore responds → UserRepository posts to LiveData<User>
 * 3. ProfileViewModel observes UserRepository and re-posts to its own LiveData
 * 4. ProfileActivity observes ProfileViewModel LiveData → populates UI views
 *
 * ─── LOADING STATE ───────────────────────────────────────────────────────────
 * loadingContainer  = VISIBLE while fetch is in progress
 * contentContainer  = VISIBLE once data arrives
 * errorContainer    = VISIBLE if fetch fails (retry button shown)
 *
 * No view is ever left blank/empty — there's always a spinner OR content.
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    // ViewBinding: type-safe access to every view in activity_profile.xml
    private ActivityProfileBinding binding;

    // ViewModel: survives screen rotation, holds all LiveData for this screen
    private ProfileViewModel viewModel;

    /**
     * ActivityResultLauncher for EditProfileActivity.
     *
     * When we open EditProfileActivity and the user presses Save,
     * EditProfileActivity calls setResult(RESULT_OK) before finishing.
     * Our launcher receives that result and calls viewModel.refreshData()
     * so the latest name/photo shows up on this screen immediately.
     */
    private ActivityResultLauncher<Intent> editProfileLauncher;

    // ═══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Step 1: Inflate the layout and set it as the content view.
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.d(TAG, "ProfileActivity onCreate");

        // Step 2: Get the ViewModel. ViewModelProvider ensures we get the SAME
        // instance on rotation (not a fresh one). This keeps LiveData alive.
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        // Step 3: Register the launcher for EditProfileActivity BEFORE calling
        // any other setup methods. Android requires launchers to be registered
        // in onCreate (before the activity is resumed).
        editProfileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // User saved changes in EditProfileActivity.
                    // Firestore's snapshot listener will auto-update the UI,
                    // but calling refreshData() also ensures a clean reload.
                    Log.d(TAG, "EditProfile returned RESULT_OK — data will update via snapshot");
                }
            }
        );

        // Step 4: Wire up all click listeners.
        setupClickListeners();

        // Step 5: Start observing LiveData from the ViewModel.
        // When Firestore data arrives, these observers will update the UI.
        observeViewModel();

        // Step 6: Kick off the Firestore fetch.
        viewModel.loadAllData();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OBSERVE VIEWMODEL
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sets up all LiveData observers.
     *
     * Each observer is a lambda that runs ON THE MAIN THREAD whenever the
     * corresponding LiveData value changes. This is how Firestore data
     * reaches the UI without us having to do any thread management.
     */
    private void observeViewModel() {

        // ── Loading State ──────────────────────────────────────────────────────
        // isLoading is true while the first Firestore response hasn't arrived yet.
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                // Show spinner, hide content (never show a blank screen)
                binding.loadingContainer.setVisibility(View.VISIBLE);
                binding.contentContainer.setVisibility(View.GONE);
                binding.errorContainer.setVisibility(View.GONE);
                Log.d(TAG, "UI state: LOADING");
            } else {
                // Hide spinner; content or error will be shown by their own observers
                binding.loadingContainer.setVisibility(View.GONE);
                Log.d(TAG, "UI state: DONE LOADING");
            }
        });

        // ── Error State ────────────────────────────────────────────────────────
        // errorMessage is non-null if the Firestore query fails.
        viewModel.getErrorMessage().observe(this, errorMsg -> {
            if (errorMsg != null) {
                // Show error panel with the actual error message and a Retry button
                binding.errorContainer.setVisibility(View.VISIBLE);
                binding.contentContainer.setVisibility(View.GONE);
                binding.tvErrorMessage.setText(errorMsg);
                Log.e(TAG, "UI state: ERROR — " + errorMsg);
            } else {
                binding.errorContainer.setVisibility(View.GONE);
            }
        });

        // ── User Profile ───────────────────────────────────────────────────────
        // This is the main data observer. When user doc arrives from Firestore,
        // we populate all the visible views.
        viewModel.getUser().observe(this, user -> {
            if (user != null) {
                Log.d(TAG, "User data received: uid=" + user.getUid()
                    + " name=" + user.getFullName()
                    + " journeys=" + user.getTotalJourneys()
                    + " reports=" + user.getReportsSubmitted()
                    + " avgScore=" + user.getAvgSafetyScore()
                    + " badge=" + user.getBadge());

                // Show the content panel now that we have real data
                binding.contentContainer.setVisibility(View.VISIBLE);
                binding.loadingContainer.setVisibility(View.GONE);

                // Populate all views with the Firestore data
                populateUI(user);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POPULATE UI
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fills every visible UI view with data from the Firestore User document.
     *
     * This method is called every time the Firestore snapshot updates,
     * so the screen always shows the freshest data.
     *
     * @param user The User object deserialized from Firestore.
     */
    private void populateUI(User user) {

        // ── Name ───────────────────────────────────────────────────────────────
        // Prefer fullName (set during registration/edit). Fall back to name
        // (the older field name) for backward compatibility.
        String displayName = user.getFullName() != null && !user.getFullName().isEmpty()
                ? user.getFullName()
                : (user.getName() != null ? user.getName() : "User");
        binding.tvName.setText(displayName);

        // ── Email ─────────────────────────────────────────────────────────────
        binding.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "—");

        // ── Profile Photo ──────────────────────────────────────────────────────
        // Glide: handles downloading, caching, and decoding the image from the
        // Cloudinary URL. circleCrop() crops it into a perfect circle to match
        // the CircleImageView container.
        String imageUrl = user.getProfileImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_person)   // shown while downloading
                    .error(R.drawable.ic_person)         // shown if download fails
                    .circleCrop()
                    .into(binding.imgProfile);
        } else {
            // No photo yet — use the placeholder avatar
            binding.imgProfile.setImageResource(R.drawable.ic_person);
        }

        // ── Trusted Reporter Badge ─────────────────────────────────────────────
        // RULE: badge chip is ONLY shown if badge == "Trusted Reporter".
        // For a new user (badge == "None"), this stays GONE.
        String badge = user.getBadge();
        boolean isTrustedReporter = "Trusted Reporter".equalsIgnoreCase(badge)
                || user.isTrustedReporterBadge(); // also check legacy boolean field
        binding.badgeContainer.setVisibility(isTrustedReporter ? View.VISIBLE : View.GONE);
        Log.d(TAG, "Badge field='" + badge + "' → chip visible=" + isTrustedReporter);

        // ── Stat Cards ────────────────────────────────────────────────────────
        // totalJourneys: counter field on the user doc, incremented by cloud function
        binding.tvStatJourneys.setText(String.valueOf(user.getTotalJourneys()));

        // reportsSubmitted: counter incremented each time user submits a report
        binding.tvStatReports.setText(String.valueOf(user.getReportsSubmitted()));

        // avgSafetyScore: running average of safety scores across completed journeys
        double avgScore = user.getAvgSafetyScore();
        if (avgScore > 0) {
            // Show as integer (e.g., "87")
            binding.tvStatAvgScore.setText(String.valueOf((int) avgScore));
        } else {
            // No journeys completed yet
            binding.tvStatAvgScore.setText("—");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLICK LISTENERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Wires every tappable element to its destination or action.
     *
     * For each action, the comment explains:
     *   - What exists: does the target Activity already exist in the codebase?
     *   - What happens: does it navigate, or perform an in-place action?
     */
    private void setupClickListeners() {

        // ── Back Button ────────────────────────────────────────────────────────
        // Simple finish() pops this Activity off the back stack
        binding.btnBack.setOnClickListener(v -> {
            Log.d(TAG, "Back button tapped");
            finish();
            overridePendingTransition(0, 0); // match the smooth transition from home nav
        });

        // ── Edit Profile ───────────────────────────────────────────────────────
        // STATUS: EditProfileActivity EXISTS and is fully functional.
        // It loads current data, lets user edit name/phone/photo/safetyMode,
        // and saves via Firestore.update(). Snapshot listener auto-refreshes this screen.
        binding.btnEditProfile.setOnClickListener(v -> {
            Log.d(TAG, "Edit Profile tapped");
            editProfileLauncher.launch(new Intent(this, EditProfileActivity.class));
        });

        // ── Change Photo (avatar tap) → same as Edit Profile ─────────────────
        // Tapping the profile photo also opens EditProfileActivity (photo editing is there)
        binding.btnChangePhoto.setOnClickListener(v -> {
            Log.d(TAG, "Change Photo tapped");
            editProfileLauncher.launch(new Intent(this, EditProfileActivity.class));
        });

        // ── Journey History ────────────────────────────────────────────────────
        // STATUS: JourneyHistoryActivity EXISTS with RecyclerView from
        // users/{uid}/journey_history, ordered by startTimestamp DESC.
        binding.rowJourneyHistory.setOnClickListener(v -> {
            Log.d(TAG, "Journey History tapped");
            startActivity(new Intent(this, JourneyHistoryActivity.class));
        });

        // ── Trusted Contacts ───────────────────────────────────────────────────
        // STATUS: EmergencyContactsActivity EXISTS but is currently a basic stub.
        // It opens without crashing. Full contact management UI is in scope for
        // a future sprint but the navigation is correctly wired here.
        binding.rowTrustedContacts.setOnClickListener(v -> {
            Log.d(TAG, "Trusted Contacts tapped");
            startActivity(new Intent(this, EmergencyContactsActivity.class));
        });

        // ── Nearby Safe Places ─────────────────────────────────────────────────
        // STATUS: NearbyPlacesActivity EXISTS and is fully functional —
        // shows police stations, hospitals, and pharmacies near the user.
        binding.rowNearbySafePlaces.setOnClickListener(v -> {
            Log.d(TAG, "Nearby Safe Places tapped");
            startActivity(new Intent(this, NearbyPlacesActivity.class));
        });

        // ── My Reports ─────────────────────────────────────────────────────────
        // STATUS: ReportListActivity EXISTS — shows community_reports filtered
        // by reporterId == currentUid, ordered by timestamp DESC.
        binding.rowMyReports.setOnClickListener(v -> {
            Log.d(TAG, "My Reports tapped");
            startActivity(new Intent(this, ReportListActivity.class));
        });

        // ── Settings ───────────────────────────────────────────────────────────
        // STATUS: SettingsActivity EXISTS — toggles for notifications/SMS/heatmap,
        // reads/writes users/{uid}/settings/preferences via SettingsRepository.
        binding.rowSettings.setOnClickListener(v -> {
            Log.d(TAG, "Settings tapped");
            startActivity(new Intent(this, SettingsActivity.class));
        });

        // ── Leaderboard ────────────────────────────────────────────────────────
        // STATUS: LeaderboardActivity does NOT yet exist in this codebase.
        // Showing a "coming soon" toast rather than a crash.
        // TODO: build LeaderboardActivity in a future sprint.
        binding.rowLeaderboard.setOnClickListener(v -> {
            Log.d(TAG, "Leaderboard tapped — not yet built");
            Toast.makeText(this, "Leaderboard — coming soon!", Toast.LENGTH_SHORT).show();
        });

        // ── Logout ─────────────────────────────────────────────────────────────
        // Shows a confirmation dialog before signing out (prevents accidents).
        binding.rowLogout.setOnClickListener(v -> {
            Log.d(TAG, "Logout tapped");
            showLogoutConfirmationDialog();
        });

        // ── Retry Button (shown in the error state) ────────────────────────────
        binding.btnRetry.setOnClickListener(v -> {
            Log.d(TAG, "Retry tapped");
            viewModel.loadAllData();
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOGOUT DIALOG
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Shows a confirmation dialog before signing the user out.
     *
     * WHY A DIALOG?
     * Logout is irreversible in the sense that it clears the session and
     * the user must re-enter their password to get back in. A confirmation
     * step prevents accidental logouts.
     */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out", (dialog, which) -> {
                Log.d(TAG, "User confirmed logout");

                // Sign out from Firebase Auth (clears local session token)
                FirebaseAuth.getInstance().signOut();

                // Navigate to LoginActivity and CLEAR THE ENTIRE BACK STACK.
                // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK ensures that
                // pressing Back from LoginActivity doesn't return to a logged-out
                // Profile or Home screen.
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Cancel", null) // null = just dismiss dialog
            .show();
    }
}
