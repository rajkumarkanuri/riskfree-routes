package com.riskfreeroutes.app.network;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AuthInterceptor — Automatically attaches authentication to API calls
 *
 * WHY THIS EXISTS:
 * Every time we call our Spring Boot backend (e.g., to save a route, update profile),
 * the server needs to know WHO is calling. It verifies this via a JWT token in
 * the "Authorization" HTTP header.
 *
 * Instead of manually adding this header to every single Retrofit API call,
 * this OkHttp Interceptor sits in the middle. It intercepts the outgoing request,
 * pauses it, fetches a fresh Firebase token, attaches it, and then sends the request.
 */
public class AuthInterceptor implements Interceptor {

    private final TokenProvider tokenProvider;

    public AuthInterceptor(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // 1. Fetch a fresh token from Firebase
        // Since intercept() runs on OkHttp's background thread, it is safe to block here.
        String token = tokenProvider.getFreshTokenSynchronously();
        android.util.Log.d("AuthInterceptor", "Token retrieved: " + token);

        // 2. If we have a token, attach it to the header
        if (token != null && !token.isEmpty()) {
            Request newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
            return chain.proceed(newRequest);
        }

        // 3. If no token (user logged out), proceed without it
        // The backend will likely return a 401 Unauthorized if the endpoint requires auth.
        return chain.proceed(originalRequest);
    }
}
