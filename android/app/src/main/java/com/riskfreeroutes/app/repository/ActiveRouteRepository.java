package com.riskfreeroutes.app.repository;

import com.riskfreeroutes.app.model.Route;

/**
 * Singleton repository to hold the currently selected route AND the active journey ID in memory.
 *
 * WHY A SINGLETON?
 * Passing a large Route object (with hundreds of LatLng points) via Intent extras causes
 * TransactionTooLargeException. Instead, we store it here and let NavigationActivity read
 * it directly via getInstance().getActiveRoute().
 *
 * We also store journeyId here because:
 *   - journeyId is created by JourneyHistoryRepository.startJourney() in RouteSelectionActivity
 *   - NavigationViewModel needs it to call endJourney() when the trip finishes
 *   - We can't pass it via Intent because NavigationViewModel has no access to the Intent extras
 *   - Storing it here is the cleanest way to share it without coupling the two layers
 */
public class ActiveRouteRepository {

    private static ActiveRouteRepository instance;
    private Route activeRoute;

    /**
     * The Firestore document ID of the currently active journey.
     * Set by RouteSelectionActivity when navigation starts (after startJourney() returns).
     * Read by NavigationViewModel when the trip ends (endJourney, safe arrival flow).
     * Cleared in clear() when navigation stops.
     */
    private String activeJourneyId;
    private String activeShareUrl;
    private String activeShareToken;

    private ActiveRouteRepository() {}

    public static synchronized ActiveRouteRepository getInstance() {
        if (instance == null) {
            instance = new ActiveRouteRepository();
        }
        return instance;
    }

    public void setActiveRoute(Route route) {
        this.activeRoute = route;
    }

    public Route getActiveRoute() {
        return activeRoute;
    }

    /** Stores the Firestore journey document ID for the current trip. */
    public void setActiveJourneyId(String journeyId) {
        this.activeJourneyId = journeyId;
    }

    /** Returns the Firestore journey document ID, or null if not set. */
    public String getActiveJourneyId() {
        return activeJourneyId;
    }

    public String getActiveShareUrl() {
        return activeShareUrl;
    }

    public void setActiveShareUrl(String activeShareUrl) {
        this.activeShareUrl = activeShareUrl;
    }

    public String getActiveShareToken() {
        return activeShareToken;
    }

    public void setActiveShareToken(String activeShareToken) {
        this.activeShareToken = activeShareToken;
    }

    /** Called when navigation ends. Clears both the route and the journey ID. */
    public void clear() {
        this.activeRoute = null;
        this.activeJourneyId = null;
        this.activeShareUrl = null;
        this.activeShareToken = null;
    }
}
