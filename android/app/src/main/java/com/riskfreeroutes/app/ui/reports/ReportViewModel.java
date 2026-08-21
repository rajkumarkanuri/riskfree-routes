package com.riskfreeroutes.app.ui.reports;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.GeoPoint;
import com.riskfreeroutes.app.model.CommunityReport;
import com.riskfreeroutes.app.model.ReportCategory;
import com.riskfreeroutes.app.repository.ReportRepository;
import com.riskfreeroutes.app.utils.CloudinaryUploadHelper;

/**
 * ReportViewModel.java — ViewModel for SubmitReportActivity.
 *
 * WHY THIS EXISTS (MVVM pattern):
 * The Activity is responsible ONLY for showing the UI and reading LiveData.
 * This ViewModel holds the submission state (loading/success/error) and
 * calls the ReportRepository to do the actual network work.
 *
 * This means:
 * - If the screen rotates mid-upload, the upload CONTINUES (ViewModel survives rotation)
 * - The Activity just reattaches to the same ViewModel and sees the current state
 *
 * SUBMISSION STATES:
 *   IDLE    → user is filling the form
 *   LOADING → Firestore write in progress (photo upload is tracked separately)
 *   SUCCESS → submitted successfully
 *   ERROR   → something failed (shown as error message)
 *
 * PHOTO UPLOAD:
 * Photo upload state is tracked separately with isPhotoUploading LiveData.
 * This allows the submit button to be disabled ONLY during upload,
 * without blocking the whole form.
 */
public class ReportViewModel extends AndroidViewModel {

    public enum State { IDLE, LOADING, SUCCESS, ERROR }

    // What the UI observes to know what to show during Firestore submission
    private final MutableLiveData<State> submissionState = new MutableLiveData<>(State.IDLE);
    private final MutableLiveData<String> errorMessage    = new MutableLiveData<>();

    // ── PHOTO UPLOAD STATE ────────────────────────────────────────────────────
    // Separate from submissionState so we can disable Submit during upload
    // without showing the full-screen spinner.

    /** true while a photo is being uploaded to Cloudinary, false otherwise. */
    private final MutableLiveData<Boolean> isPhotoUploading = new MutableLiveData<>(false);

    /** 0–100 progress of the current Cloudinary upload. */
    private final MutableLiveData<Integer> uploadProgress = new MutableLiveData<>(0);

    /** The Cloudinary HTTPS URL once upload completes. null = no photo or upload pending. */
    private final MutableLiveData<String> uploadedImageUrl = new MutableLiveData<>(null);

    // Selected category values (set by SubmitReportActivity as user picks them)
    private String selectedMainCategory;
    private String selectedSubCategory;

    private final ReportRepository repository;

    public ReportViewModel(@NonNull Application application) {
        super(application);
        repository = new ReportRepository();
    }

    // ── SETTERS called from UI ────────────────────────────────────────────────

    public void setMainCategory(String cat)  { this.selectedMainCategory = cat; }
    public void setSubCategory(String sub)   { this.selectedSubCategory = sub; }
    public String getSelectedMainCategory()  { return selectedMainCategory; }
    public String getSelectedSubCategory()   { return selectedSubCategory; }

    // ── LIVEDATA GETTERS ──────────────────────────────────────────────────────

    public LiveData<State>   getSubmissionState()  { return submissionState; }
    public LiveData<String>  getErrorMessage()     { return errorMessage; }
    public LiveData<Integer> getUploadProgress()   { return uploadProgress; }
    public LiveData<String>  getUploadedImageUrl() { return uploadedImageUrl; }
    public LiveData<Boolean> getIsPhotoUploading() { return isPhotoUploading; }

    // ─────────────────────────────────────────────────────────────────────────
    // PHOTO UPLOAD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Uploads a photo to Cloudinary via CloudinaryUploadHelper.
     * Updates isPhotoUploading (true while in flight) and uploadProgress (0–100).
     * On success, stores the Cloudinary HTTPS URL in uploadedImageUrl.
     *
     * The Activity observes isPhotoUploading to disable/enable the Submit button.
     * This prevents the user from submitting before the URL is ready.
     *
     * @param imageUri Local file URI from camera or gallery picker.
     */
    public void uploadPhoto(Uri imageUri) {
        // Signal to the UI: "a photo is uploading — disable the Submit button"
        isPhotoUploading.setValue(true);
        uploadProgress.setValue(0);

        CloudinaryUploadHelper.uploadImage(imageUri, new CloudinaryUploadHelper.OnUploadListener() {
            @Override
            public void onProgress(int percent) {
                // postValue() is safe from background threads (SDK may call this off main)
                uploadProgress.postValue(percent);
            }

            @Override
            public void onSuccess(String secureUrl) {
                // Store the URL — submitReport() will read this when building the Firestore doc
                uploadedImageUrl.postValue(secureUrl);
                isPhotoUploading.postValue(false);
                uploadProgress.postValue(100);
            }

            @Override
            public void onError(String errorMsg) {
                // Upload failed — let the user see the error and optionally retry or submit without photo
                errorMessage.postValue("Photo upload failed: " + errorMsg + "\nYou can still submit without a photo.");
                isPhotoUploading.postValue(false);
                uploadedImageUrl.postValue(null); // clear any stale URL
                uploadProgress.postValue(0);
            }
        });
    }

    /**
     * Clears the uploaded photo (when user taps the × remove button).
     * Resets all photo-related state back to initial.
     */
    public void removePhoto() {
        uploadedImageUrl.setValue(null);
        uploadProgress.setValue(0);
        isPhotoUploading.setValue(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBMIT REPORT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the CommunityReport object and submits it to Firestore.
     *
     * Guards:
     * - Won't submit if category/subcategory aren't selected
     * - Won't submit if description is empty
     * - Won't submit if location is not yet available
     * - Won't submit if a photo upload is still in progress
     *
     * @param description  Text description from the user.
     * @param location     GeoPoint of the user's current location.
     * @param severity     1 = Low, 3 = Medium, 5 = High.
     */
    public void submitReport(String description, GeoPoint location, int severity) {
        // ── VALIDATION ────────────────────────────────────────────────────────
        if (selectedMainCategory == null || selectedSubCategory == null) {
            errorMessage.setValue("Please select a category and subcategory.");
            return;
        }
        if (description == null || description.trim().isEmpty()) {
            errorMessage.setValue("Please add a description.");
            return;
        }
        if (location == null) {
            errorMessage.setValue("Location not available. Please wait or refresh.");
            return;
        }
        // Guard: prevent submitting while photo is still uploading
        if (Boolean.TRUE.equals(isPhotoUploading.getValue())) {
            errorMessage.setValue("Please wait for the photo to finish uploading.");
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
            : "anonymous";

        // imageUrl is null if no photo was selected — Firestore accepts null fine
        String imageUrl = uploadedImageUrl.getValue();

        CommunityReport report = new CommunityReport(
            uid,
            selectedMainCategory,
            selectedSubCategory,
            description.trim(),
            location,
            severity,
            imageUrl,
            ReportCategory.expiryFor(selectedMainCategory)
        );

        submissionState.setValue(State.LOADING);

        repository.submitReport(report, new ReportRepository.SubmitCallback() {
            @Override
            public void onSuccess(String documentId) {
                submissionState.postValue(State.SUCCESS);
            }

            @Override
            public void onFailure(Exception e) {
                errorMessage.postValue("Submission failed: " + e.getMessage());
                submissionState.postValue(State.ERROR);
            }
        });
    }

    /** Resets to IDLE (e.g., after showing an error toast). */
    public void resetState() {
        submissionState.setValue(State.IDLE);
    }
}
