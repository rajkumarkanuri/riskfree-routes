package com.riskfreeroutes.backend.service;

import com.riskfreeroutes.backend.dto.CompleteProfileRequest;
import com.riskfreeroutes.backend.model.AppUser;
import com.riskfreeroutes.backend.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

/**
 * UserService — Business logic for user profile operations.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates or updates a user's profile after Google Sign-In.
     * @param firebaseUid The verified Firebase UID from the token
     * @param request The profile data (name, phone, safetyMode) from the Android app
     */
    public AppUser completeProfile(String firebaseUid, CompleteProfileRequest request) {
        // Find existing user or create a new one
        AppUser user = userRepository.findByFirebaseUid(firebaseUid)
                .orElse(new AppUser());

        user.setFirebaseUid(firebaseUid);
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        if (request.getSafetyMode() != null) {
            user.setSafetyMode(request.getSafetyMode());
        }

        // Try to get email from Firebase if not already set
        if (user.getEmail() == null) {
            try {
                String email = FirebaseAuth.getInstance().getUser(firebaseUid).getEmail();
                user.setEmail(email != null ? email : firebaseUid + "@firebase.user");
            } catch (Exception e) {
                user.setEmail(firebaseUid + "@firebase.user");
            }
        }

        return userRepository.save(user);
    }
}
