package com.riskfreeroutes.app.network;

import com.riskfreeroutes.app.network.dto.CompleteProfileRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * ApiService — The Retrofit Interface mapping to our Spring Boot backend.
 *
 * WHY THIS EXISTS:
 * Instead of manually writing HttpURLConnection code, managing input streams,
 * and parsing JSON strings, Retrofit lets us define our backend API as a simple
 * Java interface.
 * Retrofit automatically generates the implementation at runtime.
 */
public interface ApiService {

    /**
     * Completes the user's profile after they sign in with Google for the first time.
     * Endpoint: POST /api/v1/users/complete-profile
     * 
     * The @Body annotation tells Retrofit to serialize the CompleteProfileRequest
     * object into JSON and put it in the HTTP request body.
     *
     * Note: The AuthInterceptor will automatically attach the "Authorization: Bearer <token>"
     * header to this request.
     *
     * @param request The user's name and phone number.
     * @return A Call object representing the async HTTP request. Void indicates we don't
     *         expect a specific JSON body in the response, just a 200 OK status.
     */
    @POST("api/v1/users/complete-profile")
    Call<Void> completeProfile(@Body CompleteProfileRequest request);
}
