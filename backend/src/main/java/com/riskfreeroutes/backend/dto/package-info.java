/**
 * dto package — Data Transfer Objects (Request/Response Shapes)
 *
 * WHY THIS PACKAGE EXISTS:
 * A DTO (Data Transfer Object) is a simple Java class that defines the
 * SHAPE of data sent to/from our REST API.
 *
 * WHY NOT USE ENTITY CLASSES DIRECTLY?
 * 1. Security: Our User entity has password_hash. We NEVER want to send
 *    the hash back to the Android app in a response.
 * 2. Flexibility: The request body for "register" doesn't match the User entity
 *    exactly (e.g., we receive 'password', not 'password_hash').
 * 3. Validation: DTOs carry @NotNull, @Email, @Size annotations for input validation.
 * 4. Versioning: We can change our entity without breaking the API contract.
 *
 * EXAMPLE:
 *   Entity: User (id, name, email, password_hash, phone, avatar_url, created_at)
 *   RegisterRequest DTO: (name, email, password, phone)  ← what client sends
 *   UserResponse DTO: (id, name, email, phone, avatar_url) ← what server returns
 *
 * CLASSES PLANNED:
 * - LoginRequest      → { email, password }
 * - RegisterRequest   → { name, email, password, phone }
 * - AuthResponse      → { token, userId, name, email }
 * - UserResponse      → { id, name, email, phone, avatarUrl, createdAt }
 * - RouteRequest      → { originLat, originLng, destLat, destLng }
 * - ReportRequest     → { latitude, longitude, category, description, severity }
 * - ContactRequest    → { name, phone, relationship }
 * - ApiResponse       → Generic { success, message, data } wrapper
 *
 * All created in Module 2 onwards.
 */
package com.riskfreeroutes.backend.dto;
