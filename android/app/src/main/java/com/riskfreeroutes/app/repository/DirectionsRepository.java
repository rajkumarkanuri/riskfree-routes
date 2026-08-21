package com.riskfreeroutes.app.repository;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;
import com.riskfreeroutes.app.model.Route;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles calling the Google Maps Directions API to fetch route options.
 */
public class DirectionsRepository {
    private static final String TAG = "DirectionsRepository";
    private final String apiKey;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public DirectionsRepository(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Fetches routes from the Google Maps Directions API.
     * Uses core Java HttpURLConnection to keep it simple without Retrofit.
     */
    public LiveData<List<Route>> getRoutes(LatLng origin, LatLng destination) {
        MutableLiveData<List<Route>> routesLiveData = new MutableLiveData<>();

        executorService.execute(() -> {
            try {
                // Build the URL for Directions API
                String urlString = "https://maps.googleapis.com/maps/api/directions/json"
                        + "?origin=" + origin.latitude + "," + origin.longitude
                        + "&destination=" + destination.latitude + "," + destination.longitude
                        + "&alternatives=true"
                        + "&departure_time=now"
                        + "&key=" + apiKey;

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Parse JSON
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray routesArray = jsonResponse.getJSONArray("routes");

                    List<Route> routesList = new ArrayList<>();

                    for (int i = 0; i < routesArray.length(); i++) {
                        JSONObject routeObj = routesArray.getJSONObject(i);
                        String summary = routeObj.getString("summary");
                        
                        JSONObject overviewPolyline = routeObj.getJSONObject("overview_polyline");
                        String encodedPath = overviewPolyline.getString("points");
                        List<LatLng> decodedPath = PolyUtil.decode(encodedPath);

                        JSONArray legsArray = routeObj.getJSONArray("legs");
                        JSONObject firstLeg = legsArray.getJSONObject(0);
                        
                        JSONObject distanceObj = firstLeg.getJSONObject("distance");
                        String distanceText = distanceObj.getString("text");
                        
                        JSONObject durationObj = firstLeg.getJSONObject("duration");
                        String durationText = durationObj.getString("text");
                        int durationValue = durationObj.getInt("value");
                        
                        String durationInTrafficText = durationText;
                        String trafficCondition = "low traffic";
                        
                        if (firstLeg.has("duration_in_traffic")) {
                            JSONObject durationInTrafficObj = firstLeg.getJSONObject("duration_in_traffic");
                            durationInTrafficText = durationInTrafficObj.getString("text");
                            int trafficValue = durationInTrafficObj.getInt("value");
                            
                            if (trafficValue > durationValue * 1.2) {
                                trafficCondition = "heavy traffic";
                            } else if (trafficValue > durationValue * 1.1) {
                                trafficCondition = "moderate traffic";
                            }
                        }
                        
                        List<Route.RouteStep> stepsList = new ArrayList<>();
                        if (firstLeg.has("steps")) {
                            JSONArray stepsArray = firstLeg.getJSONArray("steps");
                            for (int j = 0; j < stepsArray.length(); j++) {
                                JSONObject stepObj = stepsArray.getJSONObject(j);
                                String htmlInstruction = stepObj.getString("html_instructions");
                                // Strip HTML tags safely
                                String plainInstruction = android.text.Html.fromHtml(htmlInstruction, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                                
                                JSONObject endLocObj = stepObj.getJSONObject("end_location");
                                LatLng endLoc = new LatLng(endLocObj.getDouble("lat"), endLocObj.getDouble("lng"));
                                stepsList.add(new Route.RouteStep(plainInstruction, endLoc));
                            }
                        }

                        routesList.add(new Route(summary, distanceText, durationText, durationInTrafficText, trafficCondition, decodedPath, stepsList));
                    }

                    // Post back to main thread
                    mainThreadHandler.post(() -> routesLiveData.setValue(routesList));
                } else {
                    Log.e(TAG, "Directions API call failed with code: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching directions", e);
            }
        });

        return routesLiveData;
    }
}

