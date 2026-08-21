package com.riskfreeroutes.app.repository;

import android.content.Context;
import android.util.Log;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.Arrays;
import java.util.List;

public class PlacesRepository {
    private final PlacesClient placesClient;
    private final Context context;
    private AutocompleteSessionToken token;
    
    public PlacesRepository(Context context, String apiKey) {
        this.context = context.getApplicationContext();
        if (!Places.isInitialized()) {
            Places.initialize(context, apiKey);
        }
        placesClient = Places.createClient(context);
    }
    
    public LiveData<List<AutocompletePrediction>> getPredictions(String query) {
        MutableLiveData<List<AutocompletePrediction>> liveData = new MutableLiveData<>();
        
        if (query == null || query.isEmpty()) {
            liveData.setValue(null);
            return liveData;
        }

        if (token == null) {
            token = AutocompleteSessionToken.newInstance();
        }

        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(token)
                .setQuery(query)
                .build();

        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            liveData.postValue(response.getAutocompletePredictions());
        }).addOnFailureListener(exception -> {
            Log.e("PlacesRepository", "Place not found: " + exception.getMessage());
            new Handler(Looper.getMainLooper()).post(() -> 
                android.widget.Toast.makeText(context, "Places API Error: " + exception.getMessage(), android.widget.Toast.LENGTH_LONG).show()
            );
            liveData.postValue(null);
        });

        return liveData;
    }
    
    public LiveData<LatLng> fetchPlaceLatLng(String placeId) {
        MutableLiveData<LatLng> liveData = new MutableLiveData<>();
        
        List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG);
        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, placeFields)
                .setSessionToken(token)
                .build();
                
        placesClient.fetchPlace(request).addOnSuccessListener(response -> {
            Place place = response.getPlace();
            if (place.getLatLng() != null) {
                liveData.postValue(place.getLatLng());
            }
            token = null; // Reset token after a successful session
        }).addOnFailureListener(exception -> {
            Log.e("PlacesRepository", "Place not found: " + exception.getMessage());
            liveData.postValue(null);
        });
        
        return liveData;
    }
}
