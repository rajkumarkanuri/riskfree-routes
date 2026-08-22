package com.riskfreeroutes.app.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityEditProfileBinding;
import com.riskfreeroutes.app.utils.CloudinaryUploadHelper;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private String newProfileImageUrl = null;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupSpinner();
        setupImagePicker();
        loadUserProfile();
        setupClickListeners();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupSpinner() {
        String[] safetyModes = {"Standard", "Student", "Women", "Night Shift"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, 
                android.R.layout.simple_spinner_item, 
                safetyModes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSafetyMode.setAdapter(adapter);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            uploadProfileImage(selectedImageUri);
                        }
                    }
                }
        );
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        showLoading(true);
        FirebaseFirestore.getInstance().collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        if (fullName == null) fullName = documentSnapshot.getString("name");
                        if (fullName == null && currentUser.getDisplayName() != null) fullName = currentUser.getDisplayName();

                        String phone = documentSnapshot.getString("phone");
                        String safetyMode = documentSnapshot.getString("safetyMode");
                        String profileImageUrl = documentSnapshot.getString("profileImageUrl");

                        if (fullName != null) binding.etFullName.setText(fullName);
                        if (phone != null) binding.etPhone.setText(phone);

                        if (safetyMode != null) {
                            ArrayAdapter<String> adapter = (ArrayAdapter<String>) binding.spinnerSafetyMode.getAdapter();
                            if (adapter != null) {
                                for (int i = 0; i < adapter.getCount(); i++) {
                                    if (safetyMode.equals(adapter.getItem(i))) {
                                        binding.spinnerSafetyMode.setSelection(i);
                                        break;
                                    }
                                }
                            }
                        }

                        // Load profile image from Cloudinary or Firebase Google Auth
                        String photoToLoad = (profileImageUrl != null && !profileImageUrl.isEmpty())
                                ? profileImageUrl
                                : (currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : null);

                        if (photoToLoad != null) {
                            Glide.with(this)
                                    .load(photoToLoad)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .circleCrop()
                                    .into(binding.ivProfilePhoto);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupClickListeners() {
        binding.btnChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        binding.btnSave.setOnClickListener(v -> saveProfile());
    }

    private void uploadProfileImage(Uri imageUri) {
        showLoading(true);

        // Show immediate local preview
        Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivProfilePhoto);

        CloudinaryUploadHelper.uploadImage(imageUri, "profile_photos", new CloudinaryUploadHelper.OnUploadListener() {
            @Override
            public void onProgress(int percent) {
                // Upload in progress
            }

            @Override
            public void onSuccess(String secureUrl) {
                showLoading(false);
                newProfileImageUrl = secureUrl;
                Toast.makeText(EditProfileActivity.this, "Profile photo updated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                showLoading(false);
                Toast.makeText(EditProfileActivity.this, "Photo upload failed: " + errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfile() {
        String fullName = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String safetyMode = binding.spinnerSafetyMode.getSelectedItem() != null 
                ? binding.spinnerSafetyMode.getSelectedItem().toString() 
                : "Standard";

        if (fullName.isEmpty()) {
            binding.etFullName.setError("Name cannot be empty");
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> fieldsMap = new HashMap<>();
        fieldsMap.put("fullName", fullName);
        fieldsMap.put("name", fullName);
        fieldsMap.put("phone", phone);
        fieldsMap.put("safetyMode", safetyMode);

        if (newProfileImageUrl != null && !newProfileImageUrl.isEmpty()) {
            fieldsMap.put("profileImageUrl", newProfileImageUrl);
        }

        showLoading(true);
        FirebaseFirestore.getInstance().collection("users")
                .document(currentUser.getUid())
                .update(fieldsMap)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.btnSave.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnSave.setEnabled(true);
        }
    }
}
