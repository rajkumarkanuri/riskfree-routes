package com.riskfreeroutes.app.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.RiskFreeRoutesApp;

import java.util.HashMap;
import java.util.Map;

/**
 * CloudinaryUploadHelper.java — A robust wrapper around the Cloudinary Android SDK.
 */
public class CloudinaryUploadHelper {

    private static final String TAG = "CloudinaryUploadHelper";
    private static final String UPLOAD_PRESET = "riskfree_unsigned";
    private static final String FOLDER = "community_reports";

    public interface OnUploadListener {
        void onProgress(int percent);
        void onSuccess(String secureUrl);
        void onError(String errorMessage);
    }

    /**
     * Ensures MediaManager is initialized.
     */
    private static void ensureInitialized() {
        try {
            MediaManager.get();
        } catch (IllegalStateException e) {
            Context context = RiskFreeRoutesApp.getInstance();
            if (context != null) {
                try {
                    Map<String, Object> config = new HashMap<>();
                    config.put("cloud_name", context.getString(R.string.cloudinary_cloud_name));
                    config.put("secure", true);
                    MediaManager.init(context, config);
                } catch (Exception ex) {
                    Log.e(TAG, "Failed fallback MediaManager init", ex);
                }
            }
        }
    }

    /**
     * Uploads an image (for reports or profile) to Cloudinary and reports progress/results.
     */
    public static void uploadImage(Uri imageUri, OnUploadListener listener) {
        uploadImage(imageUri, FOLDER, listener);
    }

    /**
     * Uploads an image to a specific folder (e.g., 'profile_photos' or 'community_reports').
     */
    public static void uploadImage(Uri imageUri, String folderName, OnUploadListener listener) {
        if (imageUri == null) {
            if (listener != null) listener.onError("No image selected.");
            return;
        }

        try {
            ensureInitialized();

            MediaManager.get()
                .upload(imageUri)
                .unsigned(UPLOAD_PRESET)
                .option("folder", folderName != null ? folderName : FOLDER)
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Upload started: " + requestId);
                        if (listener != null) listener.onProgress(0);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        int percent = (totalBytes > 0) ? (int) (100L * bytes / totalBytes) : 0;
                        Log.d(TAG, "Upload progress: " + percent + "%");
                        if (listener != null) listener.onProgress(percent);
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        Log.d(TAG, "Upload success! URL: " + url);
                        if (listener != null) listener.onSuccess(url != null ? url : "");
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        String msg = error != null ? error.getDescription() : "Unknown error";
                        Log.e(TAG, "Upload error: " + msg);
                        if (listener != null) listener.onError(msg);
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        Log.w(TAG, "Upload rescheduled: " + (error != null ? error.getDescription() : ""));
                    }
                })
                .dispatch();

        } catch (Exception e) {
            Log.e(TAG, "Upload dispatch failed", e);
            if (listener != null) listener.onError("Upload failed: " + e.getMessage());
        }
    }
}
