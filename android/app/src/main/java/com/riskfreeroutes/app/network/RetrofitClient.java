package com.riskfreeroutes.app.network;

import com.riskfreeroutes.app.utils.Constants;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * RetrofitClient — The networking engine for the app.
 *
 * WHY THIS EXISTS:
 * This is a Singleton class that sets up and provides the Retrofit instance.
 * We only want ONE instance of Retrofit running in our app to save memory
 * and efficiently pool HTTP connections.
 */
public class RetrofitClient {

    private static RetrofitClient instance = null;
    private ApiService apiService;

    // Private constructor ensures no other class can instantiate this.
    private RetrofitClient() {
        // 1. Setup the Logging Interceptor (helps debug network calls in Logcat)
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // Logs request & response bodies

        // 2. Setup the Auth Interceptor (attaches Firebase token)
        TokenProvider tokenProvider = new TokenProvider();
        AuthInterceptor authInterceptor = new AuthInterceptor(tokenProvider);

        // 3. Build the OkHttpClient with our interceptors and timeouts
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor) // Remove logging in production!
                .connectTimeout(Constants.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.NETWORK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();

        // 4. Build Retrofit
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create()) // Use Gson to parse JSON
                .build();

        // 5. Create the API Service implementation
        apiService = retrofit.create(ApiService.class);
    }

    /**
     * Gets the Singleton instance of RetrofitClient.
     */
    public static synchronized RetrofitClient getInstance() {
        if (instance == null) {
            instance = new RetrofitClient();
        }
        return instance;
    }

    /**
     * Gets the ApiService to make backend calls.
     * Example usage: RetrofitClient.getInstance().getApi().completeProfile(...)
     */
    public ApiService getApi() {
        return apiService;
    }
}
