package com.riskfreeroutes.app.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.riskfreeroutes.app.model.SafePlace;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NearbyPlacesRepository {
    private static final String TAG = "NearbyPlacesRepository";
    private final String apiKey;
    private final OkHttpClient client;
    private final Handler mainHandler;

    public NearbyPlacesRepository(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public LiveData<List<SafePlace>> fetchNearbyPlaces(LatLng currentLoc, String placeType) {
        MutableLiveData<List<SafePlace>> liveData = new MutableLiveData<>();

        // Places API Nearby Search (Legacy but standard for rankby=distance)
        // https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=LAT,LNG&type=TYPE&rankby=distance&key=API_KEY
        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + currentLoc.latitude + "," + currentLoc.longitude +
                "&type=" + placeType +
                "&rankby=distance" +
                "&key=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Places API request failed", e);
                mainHandler.post(() -> liveData.setValue(new ArrayList<>()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    mainHandler.post(() -> liveData.setValue(new ArrayList<>()));
                    return;
                }

                try {
                    String json = response.body().string();
                    JSONObject root = new JSONObject(json);
                    JSONArray results = root.optJSONArray("results");

                    List<SafePlace> places = new ArrayList<>();
                    if (results != null) {
                        // Limit to top 10 as requested
                        int limit = Math.min(results.length(), 10);
                        for (int i = 0; i < limit; i++) {
                            JSONObject placeJson = results.getJSONObject(i);

                            String id = placeJson.optString("place_id");
                            String name = placeJson.optString("name", "Unknown Place");
                            String address = placeJson.optString("vicinity", "No address");
                            
                            JSONObject geometry = placeJson.optJSONObject("geometry");
                            LatLng location = null;
                            if (geometry != null) {
                                JSONObject locJson = geometry.optJSONObject("location");
                                if (locJson != null) {
                                    location = new LatLng(locJson.optDouble("lat"), locJson.optDouble("lng"));
                                }
                            }

                            // Calculate distance
                            double distance = 0;
                            if (location != null) {
                                distance = SphericalUtil.computeDistanceBetween(currentLoc, location);
                            }

                            // Optional fields
                            Boolean isOpenNow = null;
                            JSONObject openingHours = placeJson.optJSONObject("opening_hours");
                            if (openingHours != null && openingHours.has("open_now")) {
                                isOpenNow = openingHours.optBoolean("open_now");
                            }

                            // Note: basic nearby search doesn't return formatted_phone_number. 
                            // We would need Place Details for that, but we keep it null here for now
                            // to avoid N+1 API calls.
                            
                            if (location != null) {
                                places.add(new SafePlace(id, name, address, location, distance, null, isOpenNow, placeType));
                            }
                        }
                    }

                    mainHandler.post(() -> liveData.setValue(places));

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing places JSON", e);
                    mainHandler.post(() -> liveData.setValue(new ArrayList<>()));
                }
            }
        });

        return liveData;
    }

    public void fetchPlacePhone(String placeId, Callback callback) {
        String url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=" + placeId +
                "&fields=formatted_phone_number" +
                "&key=" + apiKey;

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(callback);
    }
}
