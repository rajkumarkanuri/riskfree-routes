/**
 * controller package — REST API Endpoint Handlers
 *
 * WHY THIS PACKAGE EXISTS:
 * Controllers are the "front door" of our backend. They receive HTTP
 * requests from the Android app, delegate work to Service classes,
 * and return JSON responses.
 *
 * RULE: Controllers must be THIN — no business logic.
 * Every controller method should just call a service and return a response.
 *
 * CLASSES ADDED IN MODULE 2:
 * - AuthController    → POST /api/auth/login, POST /api/auth/register
 * - UserController    → GET/PUT /api/users/me
 * And more controllers in later modules.
 */
package com.riskfreeroutes.backend.controller;
