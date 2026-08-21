package com.riskfreeroutes.app.utils;

/**
 * Constants — App-Wide Magic Number Eliminator
 *
 * WHY THIS EXISTS:
 * A "magic number" or "magic string" is a hardcoded value like "http://192.168.1.10:8080"
 * scattered throughout the code. If you need to change it (e.g., the server IP changes),
 * you'd have to find and update it in 20 different files — and miss some.
 *
 * Instead, we define every important value ONCE here as a constant.
 * Java 'static final' means:
 *   - 'static'  → belongs to the CLASS, not an object (no need to create an instance)
 *   - 'final'   → can never be changed after it's assigned (read-only)
 *
 * USAGE EXAMPLE:
 *   String url = Constants.BASE_URL;           // in any Java class
 *   int timeout = Constants.NETWORK_TIMEOUT;
 *
 * ARCHITECTURE NOTE:
 * This class is in the 'utils' package because it's a shared utility
 * used by ALL layers — network, UI, database, maps — without depending on any of them.
 */
public final class Constants {

    /*
     * Private constructor — prevents anyone from accidentally creating an
     * instance of this class with 'new Constants()'.
     * This is a utility class — all its members are static and should be
     * accessed directly: Constants.BASE_URL (not new Constants().BASE_URL).
     */
    private Constants() {
        // Not instantiable
    }

    // ============================================================
    // NETWORK CONFIGURATION
    // ============================================================

    /**
     * The root URL of our Spring Boot backend server.
     *
     * DEVELOPMENT (college WiFi / local PC): Use your PC's IP address.
     *   → Run 'ipconfig' (Windows) or 'ifconfig' (Mac/Linux) to find your IP.
     *   → Both your PC and Android phone MUST be on the same WiFi network.
     *
     * CHANGE THIS to match your PC's actual IP before testing on a device!
     * "10.0.2.2" is the special IP that Android Emulator uses to reach
     * your computer's localhost — use this when testing on the emulator.
     */
    // public static final String BASE_URL = "http://10.0.2.2:8080/"; // ← Android Emulator only
    public static final String BASE_URL = "http://192.168.1.3:8080/"; // ← Real device on WiFi

    /**
     * Connection timeout — how many seconds to wait when CONNECTING to the server.
     * If the server doesn't respond in 30 seconds, OkHttp throws a timeout error.
     */
    public static final int NETWORK_TIMEOUT_SECONDS = 30;

    /**
     * Read timeout — how many seconds to wait for the server to SEND a response.
     * After connection is established, if no data arrives in 30 seconds, timeout.
     */
    public static final int NETWORK_READ_TIMEOUT_SECONDS = 30;

    // ============================================================
    // SHARED PREFERENCES KEYS
    // SharedPreferences is Android's simple key-value storage —
    // like a tiny database that persists data between app sessions.
    // We use it to store the JWT token and basic user info.
    // ============================================================

    /**
     * The name of our SharedPreferences "file" (it's a virtual namespace).
     * All our app's preferences will be grouped under this name.
     */
    public static final String PREF_FILE_NAME = "risk_free_routes_prefs";

    /** Key for storing the JWT authentication token */
    public static final String PREF_KEY_JWT_TOKEN = "jwt_token";

    /** Key for storing the logged-in user's ID (long value) */
    public static final String PREF_KEY_USER_ID = "user_id";

    /** Key for storing the logged-in user's display name */
    public static final String PREF_KEY_USER_NAME = "user_name";

    /** Key for storing the logged-in user's email */
    public static final String PREF_KEY_USER_EMAIL = "user_email";

    /** Key for storing the user's avatar/profile picture URL (Cloudinary) */
    public static final String PREF_KEY_AVATAR_URL = "avatar_url";

    /** Key for storing the user's phone number */
    public static final String PREF_KEY_USER_PHONE = "user_phone";

    /**
     * Key for a boolean flag — "is the user logged in?"
     * We check this in SplashActivity to decide where to navigate:
     *   true  → go to HomeActivity
     *   false → go to LoginActivity
     */
    public static final String PREF_KEY_IS_LOGGED_IN = "is_logged_in";

    // ============================================================
    // API ENDPOINT PATHS
    // These are paths relative to BASE_URL.
    // Retrofit will combine: BASE_URL + ENDPOINT = full URL
    // e.g., "http://10.0.2.2:8080/" + "api/auth/login" = full URL
    // ============================================================

    public static final String ENDPOINT_LOGIN    = "api/auth/login";
    public static final String ENDPOINT_REGISTER = "api/auth/register";
    public static final String ENDPOINT_PROFILE  = "api/users/me";
    public static final String ENDPOINT_ROUTES   = "api/routes";
    public static final String ENDPOINT_REPORTS  = "api/reports";
    public static final String ENDPOINT_CONTACTS = "api/contacts";
    public static final String ENDPOINT_SOS      = "api/sos";

    // ============================================================
    // INTENT EXTRA KEYS
    // When one Activity starts another, it passes data via Intent extras.
    // These keys identify what data is being passed.
    // Example: intent.putExtra(Constants.EXTRA_ROUTE_ID, routeId)
    // ============================================================

    public static final String EXTRA_ROUTE_ID      = "extra_route_id";
    public static final String EXTRA_REPORT_ID     = "extra_report_id";
    public static final String EXTRA_ORIGIN_LAT    = "extra_origin_lat";
    public static final String EXTRA_ORIGIN_LNG    = "extra_origin_lng";
    public static final String EXTRA_DEST_LAT      = "extra_dest_lat";
    public static final String EXTRA_DEST_LNG      = "extra_dest_lng";
    public static final String EXTRA_ORIGIN_NAME   = "extra_origin_name";
    public static final String EXTRA_DEST_NAME     = "extra_dest_name";
    public static final String EXTRA_CONTACT_ID    = "extra_contact_id";

    // ============================================================
    // SAFETY SCORE THRESHOLDS
    // Used by the safety score engine to assign color labels.
    // Score 0–100: higher = safer
    // ============================================================

    /** Scores above this are considered SAFE (show green) */
    public static final int SCORE_SAFE_THRESHOLD    = 70;

    /** Scores above this (but below SAFE) are MODERATE (show amber) */
    public static final int SCORE_MODERATE_THRESHOLD = 40;

    /** Scores below MODERATE are DANGER (show red) */
    // Implicitly: anything below 40

    // ============================================================
    // LOCATION & MAP SETTINGS
    // ============================================================

    /** Default map zoom level when showing user's location (1=world, 21=building) */
    public static final float MAP_DEFAULT_ZOOM = 15f;

    /** Radius in meters to search for incidents near a route segment */
    public static final int INCIDENT_SEARCH_RADIUS_METERS = 500;

    /** Minimum distance in meters before location updates fire (saves battery) */
    public static final int LOCATION_MIN_DISTANCE_METERS = 10;

    /** Minimum time in milliseconds between location updates */
    public static final long LOCATION_UPDATE_INTERVAL_MS = 5000L; // 5 seconds

    // ============================================================
    // REQUEST CODES
    // Used with Android's startActivityForResult() and permission requests.
    // Each must be a unique integer to identify which request returned.
    // ============================================================

    public static final int REQUEST_CODE_LOCATION_PERMISSION = 1001;
    public static final int REQUEST_CODE_CAMERA_PERMISSION   = 1002;
    public static final int REQUEST_CODE_SMS_PERMISSION      = 1003;
    public static final int REQUEST_CODE_STORAGE_PERMISSION  = 1004;
    public static final int REQUEST_CODE_PICK_IMAGE          = 2001;
    public static final int REQUEST_CODE_TAKE_PHOTO          = 2002;
    public static final int REQUEST_CODE_NOTIFICATIONS_PERMISSION = 1005;

    // ============================================================
    // NOTIFICATION CHANNEL IDs
    // Android 8+ requires a "channel" for each type of notification.
    // ============================================================

    /** Channel for navigation-related notifications */
    public static final String NOTIFICATION_CHANNEL_NAVIGATION = "channel_navigation";

    /** Channel for SOS / emergency notifications */
    public static final String NOTIFICATION_CHANNEL_SOS = "channel_sos";

    // ============================================================
    // INCIDENT CATEGORIES
    // Mirror the enum values stored in the backend/database.
    // ============================================================

    public static final String CATEGORY_THEFT    = "THEFT";
    public static final String CATEGORY_ASSAULT  = "ASSAULT";
    public static final String CATEGORY_ACCIDENT = "ACCIDENT";
    public static final String CATEGORY_LIGHTING = "LIGHTING";
    public static final String CATEGORY_OTHER    = "OTHER";
}
