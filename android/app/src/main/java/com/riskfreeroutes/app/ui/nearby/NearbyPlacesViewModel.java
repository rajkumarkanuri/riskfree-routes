package com.riskfreeroutes.app.ui.nearby;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.riskfreeroutes.app.model.SafePlace;
import com.riskfreeroutes.app.repository.NearbyPlacesRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NearbyPlacesViewModel extends AndroidViewModel {

    private final NearbyPlacesRepository repository;
    private final MutableLiveData<String> currentFilter = new MutableLiveData<>("police");
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    
    // In-memory cache: Maps filter type ("police", "hospital", "pharmacy") to the list of places
    private final Map<String, List<SafePlace>> placesCache = new HashMap<>();
    
    private final MediatorLiveData<List<SafePlace>> safePlaces = new MediatorLiveData<>();
    
    private LatLng lastKnownLocation;

    public NearbyPlacesViewModel(@NonNull Application application) {
        super(application);
        String apiKey = getApiKey(application);
        repository = new NearbyPlacesRepository(apiKey);
        
        safePlaces.addSource(currentFilter, filter -> {
            fetchPlacesForFilter(filter);
        });
    }

    private String getApiKey(Application app) {
        try {
            ApplicationInfo ai = app.getPackageManager().getApplicationInfo(app.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            return bundle.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
            return "";
        }
    }

    public LiveData<List<SafePlace>> getSafePlaces() {
        return safePlaces;
    }

    public LiveData<String> getCurrentFilter() {
        return currentFilter;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void setFilter(String filterType) {
        if (!filterType.equals(currentFilter.getValue())) {
            currentFilter.setValue(filterType);
        }
    }

    public void setLocationAndFetch(LatLng location) {
        this.lastKnownLocation = location;
        placesCache.clear(); // Clear cache when location changes significantly
        fetchPlacesForFilter(currentFilter.getValue());
    }
    
    public LatLng getLastKnownLocation() {
        return lastKnownLocation;
    }

    public NearbyPlacesRepository getRepository() {
        return repository;
    }

    private void fetchPlacesForFilter(String filter) {
        if (lastKnownLocation == null) return;
        
        if (placesCache.containsKey(filter) && placesCache.get(filter) != null) {
            safePlaces.setValue(placesCache.get(filter));
            return;
        }

        isLoading.setValue(true);
        LiveData<List<SafePlace>> repoData = repository.fetchNearbyPlaces(lastKnownLocation, filter);
        
        safePlaces.addSource(repoData, places -> {
            safePlaces.removeSource(repoData);
            isLoading.setValue(false);
            if (places != null) {
                placesCache.put(filter, places);
                safePlaces.setValue(places);
            }
        });
    }
}
