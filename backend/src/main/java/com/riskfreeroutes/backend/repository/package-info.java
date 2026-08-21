/**
 * repository package — Data Access Layer (JPA Repositories)
 *
 * WHY THIS PACKAGE EXISTS:
 * Repositories are the ONLY layer that talks to the database.
 * Services call repositories to read/write data.
 *
 * We use Spring Data JPA's JpaRepository<Entity, ID> interface.
 * By simply extending JpaRepository, we get:
 * - save(entity)         → INSERT or UPDATE
 * - findById(id)         → SELECT by primary key
 * - findAll()            → SELECT all rows
 * - deleteById(id)       → DELETE
 * - count()              → COUNT(*)
 * ...and many more — without writing any SQL!
 *
 * For custom queries, we use:
 * - Method name conventions: findByEmail(String email) → Spring auto-generates the SQL
 * - @Query annotation: for complex queries we write JPQL (Java-style SQL)
 *
 * CLASSES PLANNED:
 * - UserRepository           → CRUD for users table
 * - RouteRepository          → CRUD + history queries for routes table
 * - IncidentReportRepository → CRUD + geospatial queries for incident_reports
 * - EmergencyContactRepository → CRUD for emergency_contacts
 * - SosEventRepository       → CRUD for sos_events
 *
 * All added in Module 2 onwards.
 */
package com.riskfreeroutes.backend.repository;
