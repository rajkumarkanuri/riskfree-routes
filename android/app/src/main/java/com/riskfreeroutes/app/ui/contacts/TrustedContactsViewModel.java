package com.riskfreeroutes.app.ui.contacts;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.riskfreeroutes.app.model.TrustedContact;
import com.riskfreeroutes.app.repository.TrustedContactRepository;

import java.util.List;
import java.util.Map;

/**
 * TrustedContactsViewModel — Sits between the Repository and the Activity.
 *
 * ── WHY A VIEWMODEL? ─────────────────────────────────────────────────────────
 * Android can destroy and recreate an Activity at any moment (e.g., on screen
 * rotation). If we stored our Firestore listener directly in the Activity, it
 * would be destroyed on rotation and we'd have to restart the network request.
 *
 * A ViewModel survives configuration changes (rotations, locale switches, etc.).
 * Its lifecycle is tied to the SCREEN (not the Activity instance), so:
 *   - The Firestore snapshot listener stays alive through rotations
 *   - The LiveData already has the latest data when the new Activity instance
 *     subscribes — no flicker, no loading spinner on rotation
 *
 * ── LIFECYCLE ────────────────────────────────────────────────────────────────
 * 1. Activity creates ViewModel via ViewModelProvider in onCreate()
 * 2. ViewModel starts Firestore listener in loadContacts()
 * 3. On rotation: Activity is destroyed, ViewModel SURVIVES
 * 4. New Activity instance subscribes to the same LiveData, gets latest data
 * 5. When user navigates away permanently: ViewModel.onCleared() fires →
 *    we detach the Firestore listener to stop unnecessary network traffic
 */
public class TrustedContactsViewModel extends ViewModel {

    private static final String TAG = "TrustedContactsVM";

    // ── Repository ────────────────────────────────────────────────────────────
    private final TrustedContactRepository repository;

    // ── LiveData exposed to the Activity ─────────────────────────────────────

    /**
     * The live list of contacts. Emits every time Firestore delivers a new snapshot.
     * Primary contacts are sorted first; rest alphabetical.
     */
    private LiveData<List<TrustedContact>> contactsLiveData;

    /**
     * True while a one-shot write (add/update/delete/setPrimary) is in progress.
     * The Activity uses this to show a progress indicator and disable the Save button.
     * Note: the snapshot listener itself doesn't set isLoading — it's always running.
     */
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    /**
     * Non-null when a write operation fails. The Activity shows a Toast/Snackbar.
     * Reset to null after the Activity reads it.
     */
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

    /**
     * Non-null when a write operation completes successfully (used to dismiss
     * AddEditContactActivity automatically after a successful save).
     */
    private final MutableLiveData<String> successMessage = new MutableLiveData<>(null);

    // ── UID ───────────────────────────────────────────────────────────────────
    private final String uid;

    // ── CONSTRUCTOR ───────────────────────────────────────────────────────────
    public TrustedContactsViewModel() {
        repository = new TrustedContactRepository();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = (user != null) ? user.getUid() : null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // LOAD CONTACTS (start the real-time listener)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Starts the Firestore snapshot listener and returns the LiveData.
     *
     * IMPORTANT: Call this only ONCE from the Activity's onCreate().
     * Calling it a second time would create a second listener and duplicate data.
     *
     * We use lazy initialization (contactsLiveData is null until first call)
     * to ensure the listener is only attached once per ViewModel lifecycle.
     */
    public LiveData<List<TrustedContact>> getContacts() {
        if (uid == null) {
            Log.w(TAG, "getContacts called but user is not logged in");
            MutableLiveData<List<TrustedContact>> empty = new MutableLiveData<>();
            empty.setValue(new java.util.ArrayList<>());
            return empty;
        }

        // Lazy init: only start the listener once
        if (contactsLiveData == null) {
            Log.d(TAG, "Starting Firestore snapshot listener for uid=" + uid);
            contactsLiveData = repository.getContactsLiveData(uid);
        }
        return contactsLiveData;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // WRITE OPERATIONS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Adds a new contact. Shows loading state while the write is in flight.
     * On success, the snapshot listener automatically delivers the updated list.
     */
    public void addContact(TrustedContact contact) {
        if (uid == null) { errorMessage.setValue("Not logged in"); return; }
        isLoading.setValue(true);

        repository.addContact(uid, contact, new TrustedContactRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "addContact ViewModel: success");
                isLoading.postValue(false);
                successMessage.postValue("Contact saved");
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "addContact ViewModel: failure", e);
                isLoading.postValue(false);
                errorMessage.postValue("Failed to save contact: " + e.getMessage());
            }
        });
    }

    /**
     * Updates an existing contact's fields.
     */
    public void updateContact(String contactId, Map<String, Object> fields) {
        if (uid == null) { errorMessage.setValue("Not logged in"); return; }
        isLoading.setValue(true);

        repository.updateContact(uid, contactId, fields, new TrustedContactRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "updateContact ViewModel: success for " + contactId);
                isLoading.postValue(false);
                successMessage.postValue("Contact updated");
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "updateContact ViewModel: failure", e);
                isLoading.postValue(false);
                errorMessage.postValue("Failed to update: " + e.getMessage());
            }
        });
    }

    /**
     * Deletes a contact permanently from Firestore.
     */
    public void deleteContact(String contactId) {
        if (uid == null) { errorMessage.setValue("Not logged in"); return; }
        isLoading.setValue(true);

        repository.deleteContact(uid, contactId, new TrustedContactRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "deleteContact ViewModel: success for " + contactId);
                isLoading.postValue(false);
                successMessage.postValue("Contact deleted");
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "deleteContact ViewModel: failure", e);
                isLoading.postValue(false);
                errorMessage.postValue("Failed to delete: " + e.getMessage());
            }
        });
    }

    /**
     * Sets one contact as primary (atomically un-primaries all others).
     */
    public void setPrimaryContact(String contactId) {
        if (uid == null) { errorMessage.setValue("Not logged in"); return; }
        isLoading.setValue(true);

        repository.setPrimaryContact(uid, contactId, new TrustedContactRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "setPrimaryContact ViewModel: success for " + contactId);
                isLoading.postValue(false);
            }
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "setPrimaryContact ViewModel: failure", e);
                isLoading.postValue(false);
                errorMessage.postValue("Failed to set primary: " + e.getMessage());
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GETTERS (LiveData exposed to the Activity)
    // ═════════════════════════════════════════════════════════════════════════

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<String> getSuccessMessage() { return successMessage; }

    /** Called by the Activity after reading the error, to avoid re-showing it on rotation. */
    public void clearError() { errorMessage.setValue(null); }
    public void clearSuccess() { successMessage.setValue(null); }

    // ═════════════════════════════════════════════════════════════════════════
    // CLEANUP (called by Android when screen is permanently gone)
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCleared() {
        super.onCleared();
        // Detach the Firestore snapshot listener to stop receiving updates
        // and free the associated network connection.
        repository.detachListener();
        Log.d(TAG, "ViewModel cleared — snapshot listener detached");
    }
}
