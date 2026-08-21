package com.riskfreeroutes.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

/**
 * FirebaseTokenFilter — Intercepts every HTTP request and verifies the Firebase ID Token.
 *
 * Flow:
 * 1. Android sends: Authorization: Bearer <firebase_id_token>
 * 2. This filter extracts the token from the header.
 * 3. Calls Firebase Admin SDK to verify the token is valid and not expired.
 * 4. If valid, stores the Firebase UID in Spring Security's context so controllers can access it.
 * 5. If invalid/missing, the request continues without authentication (and protected endpoints return 401).
 */
public class FirebaseTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String idToken = authHeader.substring(7);
            try {
                // Verify the Firebase ID Token — this call contacts Firebase servers
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
                String uid = decodedToken.getUid();

                // Store the verified UID in Spring Security context
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(uid, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // Token is invalid — clear any existing auth and let Spring Security handle it
                System.err.println("Firebase Token Verification Failed: " + e.getMessage());
                e.printStackTrace();
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
