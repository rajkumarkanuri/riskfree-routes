/**
 * model package — JPA Entity Classes (Database Table Mappings)
 *
 * WHY THIS PACKAGE EXISTS:
 * JPA Entities are plain Java classes annotated with @Entity.
 * Hibernate reads these annotations and:
 * 1. Creates the corresponding database table (via ddl-auto=update)
 * 2. Maps each Java field to a database column
 * 3. Manages INSERT/UPDATE/DELETE/SELECT operations
 *
 * Each entity class = one database table.
 *
 * CLASSES PLANNED:
 * - User              → maps to 'users' table
 * - Route             → maps to 'routes' table
 * - IncidentReport    → maps to 'incident_reports' table
 * - EmergencyContact  → maps to 'emergency_contacts' table
 * - SosEvent          → maps to 'sos_events' table
 *
 * KEY ANNOTATIONS USED:
 * @Entity          → "This class is a database table"
 * @Table(name="x") → "The table name is x"
 * @Id              → "This field is the primary key"
 * @GeneratedValue  → "The DB auto-generates this value (BIGSERIAL)"
 * @Column          → "Map this field to a specific column with constraints"
 * @ManyToOne       → "Many of these belong to one User" (foreign key)
 *
 * All entity classes are created in Module 2 onwards.
 */
package com.riskfreeroutes.backend.model;
