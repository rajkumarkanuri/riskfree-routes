/**
 * service package — Business Logic Layer
 *
 * WHY THIS PACKAGE EXISTS:
 * Services contain the CORE BUSINESS LOGIC of our application.
 * They sit between Controllers (HTTP layer) and Repositories (DB layer).
 *
 * RESPONSIBILITIES:
 * - Validate business rules (e.g., "email must be unique before registering")
 * - Orchestrate multiple repository calls into one transaction
 * - Apply the safety score algorithm
 * - Interact with Cloudinary for image uploads
 *
 * CLASSES PLANNED:
 * - AuthService        → Register, login, JWT generation
 * - UserService        → Get/update profile, avatar upload
 * - RouteService       → Save route history
 * - ReportService      → Submit/fetch incident reports
 * - ContactService     → Manage emergency contacts
 * - SosService         → Trigger SOS, notify contacts
 * - CloudinaryService  → Upload images to Cloudinary
 *
 * All added in Module 2 onwards.
 */
package com.riskfreeroutes.backend.service;
