package com.riskfreeroutes.app.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.model.User;

import java.util.Map;

/**
 * UserRepository.java — The ONLY class that interacts with Firebase for user data.
 *
 * WHY A REPOSITORY?
 * In MVVM, the rule is:
 *   Activity (View) → ViewModel → Repository → Firebase/Data source
 *
 * If we put FirebaseFirestore calls directly inside an Activity or ViewModel,
 * we'd repeat the same database code everywhere, making it hard to change.
 *
 * This class centralises ALL Firestore operations related to users:
 *   - Creating a new user document after registration
 *   - Fetching the currently logged-in user's profile
 *   - Updating user fields (e.g., phone number, profile photo URL)
 *
 * USAGE EXAMPLE (inside a ViewModel):
 *   UserRepository repo = new UserRepository();
 *   repo.getCurrentUserProfile().observe(lifecycleOwner, user -> {
 *       textView.setText(user.getName());
 *   });
 */
public class UserRepository {

    private static final String TAG = "UserRepository";

    // "users" is the name of our top-level Firestore collection.
    private static final String COLLECTION_USERS = "users";

    // db is our entry point to Firestore. Every operation starts here.
    private final FirebaseFirestore db;

    // auth lets us find out who is currently logged in.
    private final FirebaseAuth auth;

    // ── CONSTRUCTOR ─────────────────────────────────────────────────────
    public UserRepository() {
        // FirebaseFirestore.getInstance() returns a shared singleton —
        // calling this multiple times is safe and free.
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Writes a new User document to Firestore after successful registration.
     *
     * HOW IT WORKS:
     * We use the user's Firebase Auth UID as the document ID.
     * This means every user has exactly ONE document, and we can look it up
     * instantly by UID (which we always have from FirebaseAuth.getCurrentUser()).
     *
     * db.collection("users")           → navigate to the "users" collection
     *   .document(user.getUid())       → address the specific document for this user
     *   .set(user)                     → write all fields from our User POJO
     *
     * @param user   The User object to save. Built from registration form data.
     * @param onSuccess Runnable to execute when the write succeeds (navigate to Home).
     * @param onFailure Runnable to execute if something goes wrong (show error).
     */
    public void createUserProfile(User user, Runnable onSuccess, Runnable onFailure) {
        Log.d(TAG, "Creating Firestore profile for UID: " + user.getUid());

        db.collection(COLLECTION_USERS)
                .document(user.getUid())    // use UID as the document ID
                .set(user)                  // write the User POJO as a Firestore document
                .addOnSuccessListener(aVoid -> {
                    // .set() succeeded — the document is now in Firestore
                    Log.d(TAG, "Firestore profile created successfully");
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    // Something went wrong — log it and tell the Activity
                    Log.e(TAG, "Failed to create Firestore profile", e);
                    onFailure.run();
                });
    }

    /**
     * Fetches the currently logged-in user's profile from Firestore.
     *
     * Returns a LiveData<User> — the Activity/ViewModel can OBSERVE this.
     * When Firestore responds, the LiveData updates and the UI reacts automatically.
     *
     * @return LiveData that emits the User object (or null if not found/error).
     */
    public LiveData<User> getCurrentUserProfile() {
        MutableLiveData<User> liveData = new MutableLiveData<>();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            // No one is logged in — return null immediately
            liveData.setValue(null);
            return liveData;
        }

        String userId = currentUser.getUid();
        db.collection(COLLECTION_USERS)
                .document(userId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        android.util.Log.d("DIAGNOSTICS", "UserRepository fetch error: " + error.getMessage());
                        Log.e(TAG, "Listen failed.", error);
                        liveData.setValue(null);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        android.util.Log.d("DIAGNOSTICS", "UserRepository snapshot exists: " + snapshot.getData());
                        User user = snapshot.toObject(User.class);
                        liveData.setValue(user);
                    } else {
                        android.util.Log.d("DIAGNOSTICS", "UserRepository snapshot null or empty. Creating fallback profile.");
                        // Fallback: If document doesn't exist, create it with basic auth details so UI doesn't break
                        User fallbackUser = new User(userId, "User", currentUser.getEmail(), "");
                        createUserProfile(fallbackUser, 
                            () -> liveData.setValue(fallbackUser), 
                            () -> liveData.setValue(null)
                        );
                    }
                });

        return liveData;
    }

    /**
     * Updates a single field in the user's Firestore document.
     * Used when the user changes their phone number or profile photo URL.
     *
     * @param field  The Firestore field name to update (e.g., "profileImageUrl")
     * @param value  The new value
     */
    public void updateUserField(String field, Object value) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        db.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .update(field, value)   // update() only changes the specified field
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Updated field '" + field + "' successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update field '" + field + "'", e));
    }

    // ── PROFILE UPDATE (batch) ───────────────────────────────────────────────

    /**
     * Callback for profile update operations.
     */
    public interface UpdateCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    /**
     * Updates multiple fields on the user's Firestore document in a single call.
     * Used by EditProfileActivity when saving profile changes.
     *
     * @param fields   A Map of field names → new values (e.g., "fullName" → "John Doe")
     * @param callback Called on success or failure.
     */
    public void updateProfile(Map<String, Object> fields, UpdateCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            if (callback != null) callback.onFailure(new Exception("Not logged in"));
            return;
        }

        db.collection(COLLECTION_USERS)
                .document(currentUser.getUid())
                .update(fields)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile updated successfully");
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update profile", e);
                    if (callback != null) callback.onFailure(e);
                });
    }
}
