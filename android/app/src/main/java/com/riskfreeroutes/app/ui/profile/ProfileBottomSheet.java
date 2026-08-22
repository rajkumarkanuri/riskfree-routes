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
import com.google.firebase.firestore.FirebaseFirestore;
import com.riskfreeroutes.app.R;
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
     * Loads the user's name, email, and photo from Firestore and Firebase Auth.
     */
    private void setupUserProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String email = user.getEmail();
            binding.tvProfileEmail.setText(email != null ? email : "Anonymous");

            // Fetch name and photo from Firestore
            FirebaseFirestore.getInstance().collection("users")
                    .document(user.getUid())
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (binding == null) return;
                        String fullName = snap.exists() ? snap.getString("fullName") : null;
                        if (fullName == null && snap.exists()) fullName = snap.getString("name");
                        if (fullName == null && user.getDisplayName() != null) fullName = user.getDisplayName();

                        binding.tvProfileName.setText(fullName != null && !fullName.isEmpty() ? fullName : "Risk Free User");

                        String profileImageUrl = snap.exists() ? snap.getString("profileImageUrl") : null;
                        String photoToLoad = (profileImageUrl != null && !profileImageUrl.isEmpty())
                                ? profileImageUrl
                                : (user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : null);

                        if (photoToLoad != null) {
                            Glide.with(this)
                                    .load(photoToLoad)
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile)
                                    .circleCrop()
                                    .into(binding.imgProfileAvatar);
                        } else {
                            binding.imgProfileAvatar.setImageResource(R.drawable.ic_profile);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (binding == null) return;
                        binding.tvProfileName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Risk Free User");
                        if (user.getPhotoUrl() != null) {
                            Glide.with(this)
                                    .load(user.getPhotoUrl())
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile)
                                    .circleCrop()
                                    .into(binding.imgProfileAvatar);
                        } else {
                            binding.imgProfileAvatar.setImageResource(R.drawable.ic_profile);
                        }
                    });
        } else {
            binding.tvProfileName.setText("Guest");
            binding.tvProfileEmail.setText("Sign in to sync your preferences");
            binding.imgProfileAvatar.setImageResource(R.drawable.ic_profile);
        }
    }

    /**
     * Wires up the buttons (Sign Out, Set Home/Work, etc)
     */
    private void setupClickListeners() {
        binding.btnSignOut.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(getContext(), "Signed out successfully", Toast.LENGTH_SHORT).show();
            
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            dismiss();
        });

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
        binding = null;
    }
}
