/**
 * config package — Spring Configuration Classes
 *
 * WHY THIS PACKAGE EXISTS:
 * Configuration classes set up infrastructure components that
 * Spring Boot can't auto-configure without our custom settings.
 *
 * CLASSES PLANNED:
 * - CorsConfig         → Allows our Android app to call the API
 *                        (CORS = Cross-Origin Resource Sharing)
 * - CloudinaryConfig   → Sets up the Cloudinary SDK with our API credentials
 * - OpenApiConfig      → Sets up Swagger/OpenAPI docs (optional, helpful for testing)
 *
 * All created in Module 2 onwards.
 */
package com.riskfreeroutes.backend.config;
