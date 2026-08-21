package com.riskfreeroutes.app.ui.routes;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.model.Route;
import com.riskfreeroutes.app.repository.DirectionsRepository;
import com.riskfreeroutes.app.repository.FirestoreReportsRepository;
import com.riskfreeroutes.app.utils.SafetyScoreCalculator;
import com.riskfreeroutes.app.utils.SafetyScoreResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteSelectionViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Route>> routesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Route> selectedRouteLiveData = new MutableLiveData<>();
    private final MutableLiveData<SafetyScoreResult> safetyAnalysisLiveData = new MutableLiveData<>();
    
    private final FirestoreReportsRepository reportsRepository;
    private final DirectionsRepository directionsRepository;

    public RouteSelectionViewModel(@NonNull Application application) {
        super(application);
        reportsRepository = new FirestoreReportsRepository();
        
        String apiKey = getApiKey(application);
        directionsRepository = new DirectionsRepository(apiKey);
    }
    
    private String getApiKey(Application app) {
        try {
            ApplicationInfo ai = app.getPackageManager().getApplicationInfo(app.getPackageName(), PackageManager.GET_META_DATA);
            return ai.metaData.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
            Log.e("RouteSelectionViewModel", "Failed to load meta-data, NameNotFound: " + e.getMessage());
        }
        return null;
    }

    public LiveData<List<Route>> getRoutes() { return routesLiveData; }
    public LiveData<Route> getSelectedRoute() { return selectedRouteLiveData; }
    public LiveData<SafetyScoreResult> getSafetyAnalysis() { return safetyAnalysisLiveData; }

    public void fetchRoutes(LatLng origin, LatLng destination) {
        directionsRepository.getRoutes(origin, destination).observeForever(routes -> {
            if (routes != null && !routes.isEmpty()) {
                // Determine route types (for simplicity based on order)
                for (int i = 0; i < routes.size(); i++) {
                    Route r = routes.get(i);
                    if (i == 0) r.setRouteType("Safest Route");
                    else if (i == 1) r.setRouteType("Fastest Route");
                    else r.setRouteType("Alternative Route");
                }
                routesLiveData.setValue(routes);
                selectRoute(routes.get(0));
            }
        });
    }
    
    public void selectRoute(Route route) {
        List<Route> currentRoutes = routesLiveData.getValue();
        if (currentRoutes != null) {
            for (Route r : currentRoutes) r.setSelected(false);
            route.setSelected(true);
            routesLiveData.setValue(currentRoutes);
        }
        
        selectedRouteLiveData.setValue(route);
    }
    
    public LiveData<List<CommunityReport>> getLiveReports() {
        return reportsRepository.getLiveReports();
    }

    public void analyzeRouteSafety(Route route, List<CommunityReport> reports) {
        if (route == null || reports == null) return;
        SafetyScoreResult result = SafetyScoreCalculator.calculateForRoute(route, reports);
        safetyAnalysisLiveData.postValue(result);
    }

    private Route createMockRoute(LatLng origin, LatLng dest, String type, String summary, String duration, String dist, int score, String traffic, boolean selected) {
        List<LatLng> path = new ArrayList<>();
        path.add(origin);
        
        // Randomize intermediate point a bit
        double midLat = (origin.latitude + dest.latitude) / 2 + (Math.random() - 0.5) * 0.01;
        double midLng = (origin.longitude + dest.longitude) / 2 + (Math.random() - 0.5) * 0.01;
        path.add(new LatLng(midLat, midLng));
        
        path.add(dest);
        
        Route r = new Route(summary, dist, duration, duration, traffic, path, new ArrayList<>());
        r.setRouteType(type);
        r.setSafetyScore(score);
        r.setSelected(selected);
        
        return r;
    }
}

