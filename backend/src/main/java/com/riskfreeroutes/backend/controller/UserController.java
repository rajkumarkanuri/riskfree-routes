package com.riskfreeroutes.backend.controller;

import com.riskfreeroutes.backend.dto.CompleteProfileRequest;
import com.riskfreeroutes.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * UserController — REST endpoints for user profile management.
 *
 * The 'Authentication' parameter in each method is automatically
 * populated by Spring Security with the Firebase UID (set by FirebaseTokenFilter).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/v1/users/complete-profile
     * Called by RegisterActivity after Google Sign-In to save the user's name and phone.
     * Requires: Authorization: Bearer <firebase_id_token>
     */
    @PostMapping("/complete-profile")
    public ResponseEntity<?> completeProfile(@RequestBody CompleteProfileRequest request,
                                             Authentication authentication) {
        // The Firebase UID is extracted from the verified token by our FirebaseTokenFilter
        String firebaseUid = (String) authentication.getPrincipal();
        userService.completeProfile(firebaseUid, request);
        return ResponseEntity.ok().build();
    }
}

