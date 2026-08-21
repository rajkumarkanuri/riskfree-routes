/**
 * security package — Spring Security + JWT Configuration
 *
 * WHY THIS PACKAGE EXISTS:
 * This package contains all security-related classes.
 * Security in our app means:
 * 1. Only registered/logged-in users can call most API endpoints
 * 2. Passwords are always BCrypt-hashed before storage
 * 3. Every protected request must carry a valid JWT token in the header
 *
 * HOW JWT AUTH WORKS (simplified):
 * 1. User logs in → sends email + password
 * 2. AuthService verifies password against BCrypt hash in DB
 * 3. If correct → generate a JWT token and return it to Android app
 * 4. Android app saves the token in SharedPreferences
 * 5. For every subsequent request, Android adds header:
 *      Authorization: Bearer eyJhbGciOiJI...
 * 6. Our JwtAuthFilter intercepts EVERY request, extracts the token,
 *    validates it, and sets the user as "authenticated" in Spring Security
 * 7. If token is invalid/missing → Spring returns 401 Unauthorized
 *
 * CLASSES PLANNED:
 * - JwtUtil              → generates and validates JWT tokens
 * - JwtAuthFilter        → intercepts every request and validates JWT
 * - SecurityConfig       → defines which endpoints are public vs protected
 * - UserDetailsServiceImpl → loads user from DB by email (for Spring Security)
 *
 * All created in Module 2 (Auth Module).
 */
package com.riskfreeroutes.backend.security;
