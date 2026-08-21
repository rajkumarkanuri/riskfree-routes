package com.riskfreeroutes.app.network;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;

/**
 * TokenProvider — Manages Firebase Authentication Tokens
 *
 * WHY THIS EXISTS:
 * Firebase ID tokens expire every hour. If we cache them in SharedPreferences,
 * API calls will start failing after an hour.
 * This class ensures we ALWAYS get a fresh, valid token directly from the
 * Firebase SDK before making an API call to our Spring Boot backend.
 */
public class TokenProvider {

    /**
     * Synchronously fetches a fresh Firebase ID token.
     * 
     * IMPORTANT: This method uses Tasks.await() which blocks the thread until
     * the token is retrieved over the network. It MUST NOT be called on the
     * Main (UI) thread, otherwise the app will crash with a NetworkOnMainThreadException.
     * It is safe to call inside an OkHttp Interceptor because Retrofit executes
     * HTTP requests on background threads.
     *
     * @return The JWT token string, or null if the user is not logged in or fetching failed.
     */
    public String getFreshTokenSynchronously() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            android.util.Log.e("TokenProvider", "getCurrentUser() is NULL!");
            return null; // User is not logged in
        }

        try {
            // getIdToken(true) forces a refresh.
            Task<GetTokenResult> task = user.getIdToken(true);
            
            // Block the current background thread with a 10 second timeout
            GetTokenResult result = Tasks.await(task, 10, java.util.concurrent.TimeUnit.SECONDS);
            
            return result.getToken();
        } catch (java.util.concurrent.ExecutionException e) {
            android.util.Log.e("TokenProvider", "ExecutionException: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            android.util.Log.e("TokenProvider", "InterruptedException: " + e.getMessage(), e);
        } catch (java.util.concurrent.TimeoutException e) {
            android.util.Log.e("TokenProvider", "TimeoutException: " + e.getMessage(), e);
        } catch (Exception e) {
            android.util.Log.e("TokenProvider", "Unknown Exception: " + e.getMessage(), e);
        }
        return null;
    }
}
