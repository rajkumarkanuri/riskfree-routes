package com.riskfreeroutes.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.riskfreeroutes.app.databinding.FragmentProfileBottomSheetBinding;
import com.riskfreeroutes.app.ui.auth.LoginActivity;

/**
 * ProfileBottomSheet.java
 *
 * Displays the current user's profile information, saved places,
 * and application settings. Accessible via the profile icon on the Home Map.
 */
public class ProfileBottomSheet extends BottomSheetDialogFragment {

    private FragmentProfileBottomSheetBinding binding;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBottomSheetBinding.inflate(inflater, container, false);
        mAuth = FirebaseAuth.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupUserProfile();
        setupClickListeners();
    }

    /**
     * Loads the user's name, email, and photo from Firebase Auth.
     */
    private void setupUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            String email = user.getEmail();

            binding.tvProfileName.setText(name != null && !name.isEmpty() ? name : "Risk Free User");
            binding.tvProfileEmail.setText(email != null ? email : "Anonymous");

            // Load profile photo if it exists (e.g. from Google Sign In)
            if (user.getPhotoUrl() != null) {
                Glide.with(this)
                     .load(user.getPhotoUrl())
                     .into(binding.imgProfileAvatar);
            }
        } else {
            // Should not happen if they are logged in, but just in case
            binding.tvProfileName.setText("Guest");
            binding.tvProfileEmail.setText("Sign in to sync your preferences");
        }
    }

    /**
     * Wires up the buttons (Sign Out, Set Home/Work, etc)
     */
    private void setupClickListeners() {
        // Log out
        binding.btnSignOut.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getContext(), "Signed out successfully", Toast.LENGTH_SHORT).show();
            
            // Redirect to Login
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            dismiss();
        });

        // Placeholder for Home/Work addresses
        binding.btnSaveHome.setOnClickListener(v -> 
            Toast.makeText(getContext(), "Set Home Address (Coming Soon)", Toast.LENGTH_SHORT).show()
        );
        binding.btnSaveWork.setOnClickListener(v -> 
            Toast.makeText(getContext(), "Set Work Address (Coming Soon)", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leak
    }
}
