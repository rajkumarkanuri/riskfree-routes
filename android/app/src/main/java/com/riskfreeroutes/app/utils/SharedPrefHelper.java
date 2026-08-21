package com.riskfreeroutes.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPrefHelper — A Wrapper Around Android's SharedPreferences
 *
 * WHY THIS EXISTS:
 * Android's SharedPreferences is the simplest way to save small amounts
 * of data that must persist between app sessions (even after the user
 * closes and reopens the app).
 *
 * We use it specifically to store:
 *   1. The JWT token → so the user stays logged in
 *   2. Basic user info (name, email, avatar URL) → for quick display
 *      without a network request every time
 *   3. The "is logged in" flag → so SplashActivity knows where to navigate
 *
 * WHY A WRAPPER CLASS?
 * Without this wrapper, every Activity would need to write:
 *   SharedPreferences prefs = context.getSharedPreferences("risk_free_routes_prefs", Context.MODE_PRIVATE);
 *   SharedPreferences.Editor editor = prefs.edit();
 *   editor.putString("jwt_token", token);
 *   editor.apply();
 *
 * That's 4 lines every time. With this wrapper, it's just:
 *   SharedPrefHelper.getInstance(context).saveToken(token);
 *
 * Much cleaner, and all key names are safely in Constants.java.
 *
 * ARCHITECTURE: This is a utility class used by the Repository layer.
 * Repositories save the JWT token after a successful login response.
 */
public class SharedPrefHelper {

    // The SharedPreferences object — this is Android's actual storage mechanism.
    // We hold one reference and reuse it.
    private final SharedPreferences prefs;

    // Singleton instance — we only need ONE SharedPrefHelper in the entire app.
    private static SharedPrefHelper instance;

    /**
     * Private constructor — only called once inside getInstance().
     * Gets (or creates) our app's SharedPreferences file.
     *
     * @param context An Android Context — needed to access SharedPreferences.
     *                We use Application context to avoid memory leaks.
     */
    private SharedPrefHelper(Context context) {
        // getSharedPreferences() creates a named XML file on the device.
        // MODE_PRIVATE means only OUR app can read/write this file.
        prefs = context.getApplicationContext()
                .getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Returns the single SharedPrefHelper instance (creates it if needed).
     *
     * WHY SINGLETON?
     * SharedPreferences is backed by a file on disk. Creating multiple instances
     * could cause race conditions where one instance overwrites another's data.
     * A singleton guarantees there's always exactly one instance.
     *
     * Thread-safety note: 'synchronized' ensures that if two threads call this
     * at the same moment, only ONE will create the instance. The other waits.
     *
     * @param context Any Android Context (Activity, Service, Application — all work).
     * @return The single SharedPrefHelper instance.
     */
    public static synchronized SharedPrefHelper getInstance(Context context) {
        if (instance == null) {
            // First call — create the instance
            instance = new SharedPrefHelper(context);
        }
        return instance;
    }

    // ============================================================
    // SESSION / AUTH METHODS
    // ============================================================

    /**
     * Saves the JWT authentication token received after login/register.
     * This token is sent with every subsequent API request as proof of identity.
     *
     * @param token The JWT string from the server (e.g., "eyJhbGciOiJIUzI1...")
     */
    public void saveToken(String token) {
        // edit() returns an Editor — think of it as "unlocking" the file for writing.
        // putString() queues the write.
        // apply() saves ASYNCHRONOUSLY (doesn't block the main thread — always prefer this).
        prefs.edit().putString(Constants.PREF_KEY_JWT_TOKEN, token).apply();
    }

    /**
     * Retrieves the stored JWT token.
     * Returns null if no token is stored (user not logged in).
     *
     * @return JWT token string, or null.
     */
    public String getToken() {
        // Second argument is the DEFAULT value returned if the key doesn't exist.
        return prefs.getString(Constants.PREF_KEY_JWT_TOKEN, null);
    }

    /**
     * Marks the user as logged in and saves their basic info.
     * Called after a successful login or registration API response.
     *
     * @param userId The user's numeric ID from the database.
     * @param name   The user's display name.
     * @param email  The user's email address.
     * @param phone  The user's phone number.
     * @param token  The JWT authentication token.
     */
    public void saveUserSession(long userId, String name, String email, String phone, String token) {
        // We chain multiple putXxx() calls on the SAME editor before calling apply() once.
        // This is more efficient than calling apply() after each individual put().
        prefs.edit()
                .putBoolean(Constants.PREF_KEY_IS_LOGGED_IN, true) // ← mark as logged in
                .putLong(Constants.PREF_KEY_USER_ID, userId)
                .putString(Constants.PREF_KEY_USER_NAME, name)
                .putString(Constants.PREF_KEY_USER_EMAIL, email)
                .putString(Constants.PREF_KEY_USER_PHONE, phone)
                .putString(Constants.PREF_KEY_JWT_TOKEN, token)
                .apply(); // ← save all changes atomically
    }

    /**
     * Checks whether a user is currently logged in.
     * Used by SplashActivity to decide which screen to open.
     *
     * @return true if the user has an active session, false otherwise.
     */
    public boolean isLoggedIn() {
        return prefs.getBoolean(Constants.PREF_KEY_IS_LOGGED_IN, false);
    }

    /**
     * Returns the stored user ID.
     * Returns -1 if no user is logged in (a safe "not found" sentinel value).
     *
     * @return User ID (long), or -1.
     */
    public long getUserId() {
        return prefs.getLong(Constants.PREF_KEY_USER_ID, -1L);
    }

    /**
     * Returns the stored user's display name.
     *
     * @return User's name, or empty string if not set.
     */
    public String getUserName() {
        return prefs.getString(Constants.PREF_KEY_USER_NAME, "");
    }

    /**
     * Returns the stored user's email.
     *
     * @return Email string, or empty string if not set.
     */
    public String getUserEmail() {
        return prefs.getString(Constants.PREF_KEY_USER_EMAIL, "");
    }

    /**
     * Returns the stored user's phone number.
     *
     * @return Phone string, or empty string if not set.
     */
    public String getUserPhone() {
        return prefs.getString(Constants.PREF_KEY_USER_PHONE, "");
    }

    /**
     * Returns the stored avatar URL (Cloudinary URL).
     *
     * @return Avatar URL string, or null if not set.
     */
    public String getAvatarUrl() {
        return prefs.getString(Constants.PREF_KEY_AVATAR_URL, null);
    }

    /**
     * Updates just the avatar URL (called after a profile picture upload).
     *
     * @param url The new Cloudinary URL of the uploaded avatar.
     */
    public void updateAvatarUrl(String url) {
        prefs.edit().putString(Constants.PREF_KEY_AVATAR_URL, url).apply();
    }

    /**
     * Updates the stored name (called after profile edit).
     *
     * @param name The updated display name.
     */
    public void updateUserName(String name) {
        prefs.edit().putString(Constants.PREF_KEY_USER_NAME, name).apply();
    }

    /**
     * Clears ALL stored data — called when the user logs out.
     * After this, isLoggedIn() returns false, getToken() returns null,
     * and SplashActivity will route to LoginActivity.
     */
    public void clearSession() {
        // clear() removes ALL keys from this SharedPreferences file.
        prefs.edit().clear().apply();
    }
}
