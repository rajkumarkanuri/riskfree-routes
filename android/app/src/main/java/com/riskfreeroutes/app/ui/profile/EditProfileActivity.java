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
import com.riskfreeroutes.app.repository.UserRepository;
import com.riskfreeroutes.app.databinding.ActivityEditProfileBinding;
import com.riskfreeroutes.app.utils.CloudinaryUploadHelper;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    // ViewBinding object to access all views in the layout without findViewById
    private ActivityEditProfileBinding binding;
    
    // Store the uploaded image URL if the user changes their photo
    private String newProfileImageUrl = null;
    
    // Launcher for the gallery image picker
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize ViewBinding
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Setup the top toolbar
        setupToolbar();
        
        // Setup the dropdown spinner with options
        setupSpinner();
        
        // Setup the image picker to handle results from the gallery
        setupImagePicker();
        
        // Load the existing profile data to pre-fill the form
        loadUserProfile();
        
        // Setup click listeners for buttons
        setupClickListeners();
    }

    /**
     * Sets up the toolbar back button functionality.
     */
    private void setupToolbar() {
        // Set the toolbar navigation click to finish the activity (act as a back button)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    /**
     * Populates the Safety Mode spinner with the requested options.
     */
    private void setupSpinner() {
        String[] safetyModes = {"Standard", "Student", "Women", "Night Shift"};
        
        // Create an adapter for the spinner using a simple default layout
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, 
                android.R.layout.simple_spinner_item, 
                safetyModes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        binding.spinnerSafetyMode.setAdapter(adapter);
    }

    /**
     * Initializes the ActivityResultLauncher for picking images from the gallery.
     */
    private void setupImagePicker() {
        // We use the new ActivityResult API instead of onActivityResult for better lifecycle handling
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Get the URI of the selected image
                        Uri selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            // Immediately upload the new image to Cloudinary
                            uploadProfileImage(selectedImageUri);
                        }
                    }
                }
        );
    }

    /**
     * Loads the current user's profile and pre-fills the fields.
     */
    private void loadUserProfile() {
        // For demonstration, fetch directly from Firestore to ensure it works reliably 
        // given the custom requirements for updating the profile.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        showLoading(true);
        FirebaseFirestore.getInstance().collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        // Pre-fill the form fields from the database
                        String fullName = documentSnapshot.getString("fullName");
                        if (fullName == null) fullName = documentSnapshot.getString("name"); // fallback
                        
                        String phone = documentSnapshot.getString("phone");
                        String safetyMode = documentSnapshot.getString("safetyMode");
                        String profileImageUrl = documentSnapshot.getString("profileImageUrl");
                        
                        // Set text fields
                        binding.etFullName.setText(fullName);
                        binding.etPhone.setText(phone);
                        
                        // Select the correct safety mode in the spinner
                        if (safetyMode != null) {
                            ArrayAdapter<String> adapter = (ArrayAdapter<String>) binding.spinnerSafetyMode.getAdapter();
                            for (int i = 0; i < adapter.getCount(); i++) {
                                if (safetyMode.equals(adapter.getItem(i))) {
                                    binding.spinnerSafetyMode.setSelection(i);
                                    break;
                                }
                            }
                        }
                        
                        // Load profile image using Glide and apply circular crop
                        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_person)
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

    /**
     * Sets up click listeners for the interactive elements on the screen.
     */
    private void setupClickListeners() {
        // Launch gallery picker when the camera button is clicked
        binding.btnChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });
        
        // Handle saving the profile when the save button is clicked
        binding.btnSave.setOnClickListener(v -> saveProfile());
    }

    /**
     * Uploads the selected image to Cloudinary.
     */
    private void uploadProfileImage(Uri imageUri) {
        showLoading(true);
        
        // Show the image locally first for immediate user feedback
        Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(binding.ivProfilePhoto);
                
        // Upload the image using the provided Cloudinary upload helper
        CloudinaryUploadHelper.uploadImage(imageUri, new CloudinaryUploadHelper.OnUploadListener() {
            @Override
            public void onProgress(int percent) {
                // Future enhancement: could update a progress bar here
            }

            @Override
            public void onSuccess(String secureUrl) {
                showLoading(false);
                // Store the URL to be saved to Firestore later when the user clicks Save
                newProfileImageUrl = secureUrl;
                Toast.makeText(EditProfileActivity.this, "Photo uploaded successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                showLoading(false);
                Toast.makeText(EditProfileActivity.this, "Upload failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                
                // Revert to placeholder or previous image on error
                binding.ivProfilePhoto.setImageResource(R.drawable.ic_person);
            }
        });
    }

    /**
     * Validates input and saves the updated profile data to Firestore.
     */
    private void saveProfile() {
        // 1. Get input values
        String fullName = binding.etFullName.getText() != null ? binding.etFullName.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String safetyMode = binding.spinnerSafetyMode.getSelectedItem().toString();
        
        // 2. Validate input
        if (fullName.isEmpty()) {
            binding.etFullName.setError("Name cannot be empty");
            return;
        }
        
        // 3. Ensure user is logged in
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 4. Build the map of fields to update
        Map<String, Object> fieldsMap = new HashMap<>();
        fieldsMap.put("fullName", fullName);
        fieldsMap.put("name", fullName); // Saving to both as per requirements
        fieldsMap.put("phone", phone);
        fieldsMap.put("safetyMode", safetyMode);
        
        // Include the new profile image URL if it was changed
        if (newProfileImageUrl != null) {
            fieldsMap.put("profileImageUrl", newProfileImageUrl);
        }
        
        // 5. Save directly to Firestore since batch update is not available in UserRepository yet
        showLoading(true);
        FirebaseFirestore.getInstance().collection("users")
                .document(currentUser.getUid())
                .update(fieldsMap)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Helper method to toggle the loading state UI.
     * Shows the progress bar and disables the save button while loading.
     */
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
