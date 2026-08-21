package com.riskfreeroutes.app.utils;

import android.net.Uri;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.util.Map;

/**
 * CloudinaryUploadHelper.java — A clean wrapper around the Cloudinary Android SDK.
 *
 * WHY THIS EXISTS:
 * Instead of writing upload code directly in Activities or Repositories,
 * this helper class keeps all Cloudinary-specific code in one place.
 * If we ever switch image hosting services, we only change this file.
 *
 * HOW UNSIGNED UPLOADS WORK:
 * 1. The user picks a photo from camera or gallery → we get a local file Uri
 * 2. We send that file directly to Cloudinary's servers (no backend required)
 * 3. Cloudinary stores it and gives us back a public HTTPS URL (secure_url)
 * 4. We store that URL in Firestore as the imageUrl field of the report
 * 5. The map screen reads that URL and loads the image using Glide/Picasso
 *
 * SETUP REQUIRED (one-time in Cloudinary dashboard):
 * Settings → Upload → Upload Presets → Add preset → Mode = Unsigned
 * Name it exactly: "riskfree_unsigned"
 */
public class CloudinaryUploadHelper {

    private static final String TAG = "CloudinaryUploadHelper";

    /**
     * The unsigned upload preset name.
     * Create this once in your Cloudinary dashboard:
     *   Settings → Upload → Upload Presets → Add Upload Preset
     *   Set "Signing Mode" = Unsigned, name = "riskfree_unsigned"
     */
    private static final String UPLOAD_PRESET = "riskfree_unsigned";

    /**
     * The Cloudinary folder where all community report images are stored.
     * This keeps your Cloudinary media library organized.
     */
    private static final String FOLDER = "community_reports";

    // ── CALLBACK INTERFACE ────────────────────────────────────────────────────

    /**
     * Interface that the caller implements to receive upload results.
     * Called on the main (UI) thread by the Cloudinary SDK.
     */
    public interface OnUploadListener {
        /**
         * Called periodically during upload (0–100).
         * Use this to animate a progress bar.
         */
        void onProgress(int percent);

        /**
         * Called when the photo has been successfully uploaded to Cloudinary.
         * @param secureUrl The full HTTPS URL of the uploaded image.
         *                  Store this in Firestore as imageUrl.
         *                  Looks like: https://res.cloudinary.com/YOUR_CLOUD/image/upload/...
         */
        void onSuccess(String secureUrl);

        /**
         * Called if the upload fails for any reason.
         * @param errorMessage A human-readable description of what went wrong.
         *                     Show this to the user so they can retry.
         */
        void onError(String errorMessage);
    }

    // ── UPLOAD METHOD ─────────────────────────────────────────────────────────

    /**
     * Uploads a local image Uri to Cloudinary and reports progress + result.
     *
     * @param imageUri The Uri of the image to upload.
     *                 Can come from camera (FileProvider Uri) or gallery picker.
     * @param listener Callback for progress / success / error.
     */
    public static void uploadImage(Uri imageUri, OnUploadListener listener) {
        if (imageUri == null) {
            listener.onError("No image selected.");
            return;
        }

        try {
            // MediaManager.get() returns the singleton Cloudinary client.
            // It was initialized in RiskFreeRoutesApp.onCreate() with our cloud name.
            //
            // .upload(imageUri)    → sets the file to upload
            // .unsigned(PRESET)    → uses unsigned upload (no backend needed)
            // .option("folder", …) → organizes images in Cloudinary media library
            // .callback(…)         → where results come back
            // .dispatch()          → starts the background upload
            MediaManager.get()
                .upload(imageUri)
                .unsigned(UPLOAD_PRESET)
                .option("folder", FOLDER)
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started: " + requestId);
                        listener.onProgress(0);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Calculate what percentage of the file has been sent
                        int percent = (totalBytes > 0)
                            ? (int) (100L * bytes / totalBytes)
                            : 0;
                        Log.d(TAG, "Upload progress: " + percent + "%");
                        listener.onProgress(percent);
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // "secure_url" is always HTTPS — safe to embed in apps
                        String url = (String) resultData.get("secure_url");
                        Log.d(TAG, "Upload success! URL: " + url);
                        listener.onSuccess(url != null ? url : "");
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        String msg = error != null ? error.getDescription() : "Unknown error";
                        Log.e(TAG, "Upload error: " + msg);
                        listener.onError(msg);
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Cloudinary automatically retries failed uploads on poor connections.
                        // We treat this as still-in-progress.
                        Log.w(TAG, "Upload rescheduled (poor connection?): "
                            + (error != null ? error.getDescription() : ""));
                    }
                })
                .dispatch();

        } catch (Exception e) {
            // Catches crashes like "MediaManager not initialized" or null URI
            Log.e(TAG, "Upload dispatch failed", e);
            listener.onError("Upload failed: " + e.getMessage());
        }
    }
}
