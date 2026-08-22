package com.riskfreeroutes.app.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.riskfreeroutes.app.model.User;
import com.riskfreeroutes.app.repository.GuardianRepository;
import com.riskfreeroutes.app.repository.JourneyHistoryRepository;
import com.riskfreeroutes.app.repository.NotificationRepository;
import com.riskfreeroutes.app.repository.TrustedContactRepository;
import com.riskfreeroutes.app.repository.UserRepository;

/**
 * ProfileViewModel — Aggregates data from multiple repositories
 * and exposes it as LiveData for the Profile screen.
 *
 * Each section of the profile screen gets its own LiveData:
 *   - User profile (header)
 *   - Safety statistics (stat cards)
 *   - Guardian mode (status, last session, primary contact)
 *   - Journey history (count, last journey, avg safety)
 *   - SOS history (count, last time)
 *   - Notifications (total, unread)
 *   - Loading and error states (so the UI can show spinners or retry buttons)
 */
public class ProfileViewModel extends ViewModel {

    // ── REPOSITORIES ──────────────────────────────────────────────────────────
    private final UserRepository userRepository;
    private final GuardianRepository guardianRepository;
    private final JourneyHistoryRepository journeyHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final TrustedContactRepository trustedContactRepository;

    // ── USER PROFILE ──────────────────────────────────────────────────────────
    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();

    // ── GUARDIAN ───────────────────────────────────────────────────────────────
    private final MutableLiveData<Integer> guardianLogCount = new MutableLiveData<>(0);
    private final MutableLiveData<String> lastGuardianSession = new MutableLiveData<>("—");
    private final MutableLiveData<String> primaryContact = new MutableLiveData<>("—");

    // ── JOURNEY ───────────────────────────────────────────────────────────────
    private final MutableLiveData<String> lastJourneyDate = new MutableLiveData<>("—");
    private final MutableLiveData<Integer> completedJourneyCount = new MutableLiveData<>(0);

    // ── SOS ───────────────────────────────────────────────────────────────────
    private final MutableLiveData<Integer> sosCount = new MutableLiveData<>(0);
    private final MutableLiveData<String> lastSosTime = new MutableLiveData<>("—");

    // ── NOTIFICATIONS ─────────────────────────────────────────────────────────
    private final MutableLiveData<Integer> notifTotal = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> notifUnread = new MutableLiveData<>(0);

    // ── LOADING & ERROR STATE ─────────────────────────────────────────────────
    // isLoading: true while the initial user document is being fetched.
    // The Activity uses this to show/hide a full-screen loading spinner.
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);
    // errorMessage: non-null if the Firestore fetch fails (e.g., no internet).
    // The Activity uses this to show a retry button.
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    // ── CONSTRUCTOR ───────────────────────────────────────────────────────────
    public ProfileViewModel() {
        userRepository = new UserRepository();
        guardianRepository = new GuardianRepository();
        journeyHistoryRepository = new JourneyHistoryRepository();
        notificationRepository = new NotificationRepository();
        trustedContactRepository = new TrustedContactRepository();
    }

    // ── LOAD ALL DATA ─────────────────────────────────────────────────────────

    /** Call this from the Activity's onCreate to kick off all Firestore queries. */
    public void loadAllData() {
        android.util.Log.d("DIAGNOSTICS", "ProfileViewModel loadAllData called");
        isLoading.postValue(true);
        errorMessage.postValue(null);
        loadUserProfile();
        loadGuardianData();
        loadJourneyData();
        loadSosData();
        loadNotificationData();
    }

    /**
     * Call this when returning from EditProfileActivity or any screen
     * that may have changed user data. Re-fetches the user document.
     */
    public void refreshData() {
        loadAllData();
    }

    private void loadUserProfile() {
        // UserRepository.getCurrentUserProfile() returns a LiveData<User>
        // We observe it once internally and post to our own MutableLiveData.
        LiveData<User> source = userRepository.getCurrentUserProfile();
        source.observeForever(user -> {
            if (user != null) {
                android.util.Log.d("DIAGNOSTICS", "ProfileViewModel received user: " + user.getUid());
                userLiveData.postValue(user);
                isLoading.postValue(false);
                errorMessage.postValue(null);
            } else {
                android.util.Log.d("DIAGNOSTICS", "ProfileViewModel received null user");
                errorMessage.postValue("Failed to load profile data.");
                isLoading.postValue(false);
            }
        });
    }

    private void loadGuardianData() {
        guardianRepository.getGuardianLogCount(count -> guardianLogCount.postValue(count));

        guardianRepository.getLastGuardianTimestamp(timestamp -> {
            if (timestamp != null) {
                java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
                lastGuardianSession.postValue(sdf.format(timestamp.toDate()));
            }
        });

        trustedContactRepository.getPrimaryContact(name -> {
            if (name != null) {
                primaryContact.postValue(name);
            }
        });
    }

    private void loadJourneyData() {
        journeyHistoryRepository.getJourneyStats((lastDate, avg, completed) -> {
            if (lastDate != null) lastJourneyDate.postValue(lastDate);
            completedJourneyCount.postValue(completed);
        });
    }

    private void loadSosData() {
        guardianRepository.getSosCount(count -> sosCount.postValue(count));

        guardianRepository.getLastSosTimestamp(timestamp -> {
            if (timestamp != null) {
                java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
                lastSosTime.postValue(sdf.format(timestamp.toDate()));
            }
        });
    }

    private void loadNotificationData() {
        notificationRepository.getTotalCount(count -> notifTotal.postValue(count));
        notificationRepository.getUnreadCount(count -> notifUnread.postValue(count));
    }

    // ── GETTERS (LiveData exposed to the Activity) ────────────────────────────

    public LiveData<User> getUser() { return userLiveData; }

    public LiveData<Integer> getGuardianLogCount() { return guardianLogCount; }
    public LiveData<String> getLastGuardianSession() { return lastGuardianSession; }
    public LiveData<String> getPrimaryContact() { return primaryContact; }

    public LiveData<String> getLastJourneyDate() { return lastJourneyDate; }
    public LiveData<Integer> getCompletedJourneyCount() { return completedJourneyCount; }

    public LiveData<Integer> getSosCount() { return sosCount; }
    public LiveData<String> getLastSosTime() { return lastSosTime; }

    public LiveData<Integer> getNotifTotal() { return notifTotal; }
    public LiveData<Integer> getNotifUnread() { return notifUnread; }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
}
