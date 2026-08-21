package com.riskfreeroutes.app.model;

import com.google.firebase.Timestamp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReportCategory.java — All category/subcategory definitions and expiry rules.
 *
 * WHY THIS EXISTS:
 * We centralise every category string and its subcategories here so both the
 * UI (SubmitReportActivity) and the Firestore logic (ReportRepository) share
 * the exact same strings. This eliminates typos and makes adding a new category
 * a single-file change.
 *
 * EXPIRY MAPPING (from spec):
 *   Safety / Emergency  → 6 hours
 *   Weather             → 12 hours
 *   Road Issues / Infrastructure / Other → 3 days (72 hours)
 */
public class ReportCategory {

    // ── MAIN CATEGORIES ──────────────────────────────────────────────────────

    public static final String ROAD_ISSUES     = "Road Issues";
    public static final String INFRASTRUCTURE  = "Infrastructure";
    public static final String SAFETY          = "Safety";
    public static final String WEATHER         = "Weather";
    public static final String EMERGENCY       = "Emergency";
    public static final String OTHER           = "Other";

    /** Ordered list of main categories shown in the UI. */
    public static final List<String> ALL_CATEGORIES = Arrays.asList(
        ROAD_ISSUES, INFRASTRUCTURE, SAFETY, WEATHER, EMERGENCY, OTHER
    );

    // ── SUBCATEGORIES ─────────────────────────────────────────────────────────

    /** Maps mainCategory → list of subcategories. */
    public static final Map<String, List<String>> SUBCATEGORIES = new HashMap<String, List<String>>() {{
        put(ROAD_ISSUES, Arrays.asList(
            "Pothole", "Accident", "Road Closure", "Debris on Road",
            "Flooding on Road", "Missing Lane Markings"
        ));
        put(INFRASTRUCTURE, Arrays.asList(
            "Broken Streetlight", "Damaged Traffic Sign", "No Signal / Blackout",
            "Damaged Footpath", "Broken Barrier / Fence", "Water Leakage"
        ));
        put(SAFETY, Arrays.asList(
            "Suspicious Activity", "Theft", "Assault", "Harassment",
            "Unsafe Area (General)", "Drug Activity", "Vandalism"
        ));
        put(WEATHER, Arrays.asList(
            "Flood", "Dense Fog", "Ice / Black Ice", "Fallen Tree",
            "Strong Winds", "Landslide Risk"
        ));
        put(EMERGENCY, Arrays.asList(
            "Fire", "Medical Emergency", "Gas Leak", "Explosion", "Power Line Down"
        ));
        put(OTHER, Arrays.asList(
            "Noise Disturbance", "Animal Hazard", "Obstacle on Path", "Other"
        ));
    }};

    // ── ICON NAMES (mapped to drawable names) ─────────────────────────────────

    /** Maps mainCategory → drawable resource name (ic_xxx). */
    public static final Map<String, Integer> CATEGORY_ICONS = new HashMap<>();

    // ── SEVERITY DEFAULTS ─────────────────────────────────────────────────────

    /** Default severity for each main category (1=low, 5=critical). */
    public static final Map<String, Integer> DEFAULT_SEVERITY = new HashMap<String, Integer>() {{
        put(ROAD_ISSUES,    2);
        put(INFRASTRUCTURE, 2);
        put(SAFETY,         4);
        put(WEATHER,        3);
        put(EMERGENCY,      5);
        put(OTHER,          1);
    }};

    // ── EXPIRY LOGIC ──────────────────────────────────────────────────────────

    /**
     * Returns the expiry Timestamp for a given main category.
     *
     * Rule (from spec):
     *   Safety / Emergency  → 6 hours
     *   Weather             → 12 hours
     *   Everything else     → 72 hours (3 days)
     *
     * @param mainCategory The main category string.
     * @return A Firestore Timestamp representing when the report should expire.
     */
    public static Timestamp expiryFor(String mainCategory) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long hoursToAdd;

        if (SAFETY.equals(mainCategory) || EMERGENCY.equals(mainCategory)) {
            hoursToAdd = 6;
        } else if (WEATHER.equals(mainCategory)) {
            hoursToAdd = 12;
        } else {
            hoursToAdd = 72; // Road Issues, Infrastructure, Other → 3 days
        }

        return new Timestamp(nowSeconds + hoursToAdd * 3600, 0);
    }

    /**
     * Returns the default severity (int 1–5) for a main category,
     * used to pre-fill the severity field if the user doesn't specify one.
     */
    public static int defaultSeverity(String mainCategory) {
        Integer s = DEFAULT_SEVERITY.get(mainCategory);
        return s != null ? s : 2;
    }
}
