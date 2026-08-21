-- ============================================================
-- schema.sql — PostgreSQL Database Schema for Risk Free Routes
--
-- WHY THIS FILE EXISTS:
-- Before our Spring Boot backend can store any data, we need to
-- create the database and its tables in PostgreSQL.
-- This file contains all the SQL commands to set up the entire
-- database structure from scratch.
--
-- HOW TO RUN THIS:
-- Option 1 (pgAdmin): Open pgAdmin → Query Tool → paste this file → Run
-- Option 2 (terminal):
--   psql -U postgres
--   \i /path/to/schema.sql
-- Option 3: Spring Boot with ddl-auto=update will create tables
--   automatically from @Entity classes — but this file is your
--   reference for the exact schema design.
--
-- NOTE: Hibernate's ddl-auto=update will create tables from our
-- Java @Entity classes. This SQL file is for:
--   1. Manual database setup and verification
--   2. Documentation of our exact data model
--   3. Creating indexes that Hibernate won't auto-create
-- ============================================================


-- ============================================================
-- STEP 1: CREATE THE DATABASE
-- Run this command ONCE, before running the rest of the script.
-- If the database already exists, this will give an error — that's fine.
-- ============================================================
-- CREATE DATABASE riskfreeroutes_db;

-- Connect to our database before creating tables
-- \c riskfreeroutes_db;


-- ============================================================
-- STEP 2: DROP TABLES (if rebuilding from scratch)
-- We drop in REVERSE order of creation to avoid foreign key violations.
-- A foreign key says "this column references another table's row" —
-- you can't drop a table that another table points to.
-- CAUTION: Only run these drops if you want to DELETE ALL DATA.
-- ============================================================
-- DROP TABLE IF EXISTS sos_events CASCADE;
-- DROP TABLE IF EXISTS emergency_contacts CASCADE;
-- DROP TABLE IF EXISTS incident_reports CASCADE;
-- DROP TABLE IF EXISTS routes CASCADE;
-- DROP TABLE IF EXISTS users CASCADE;


-- ============================================================
-- TABLE: users
-- Stores registered user accounts.
--
-- WHY BIGSERIAL for id?
--   BIGSERIAL = auto-incrementing 64-bit integer.
--   We use BIGINT (not INT) because INT runs out at ~2 billion.
-- Stores app-level user profile data LINKED to Firebase identity.
--
-- WHY NO password_hash HERE?
-- Firebase Authentication owns identity (email, password, Google account).
-- We trust Firebase to verify the user. Spring Boot receives a Firebase
-- ID Token and calls Firebase Admin SDK to verify it — if valid, we get
-- the firebase_uid and know exactly who the user is.
-- PostgreSQL stores APP DATA (profile, avatar, phone) not auth credentials.
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    -- Firebase UID is the primary key — a 28-char string like "abc123XYZ..."
    -- Firebase generates this when the user registers. It NEVER changes
    -- even if the user updates their email or password.
    firebase_uid   VARCHAR(128)    PRIMARY KEY,

    -- User's display name (synced from Firebase or set by user)
    name           VARCHAR(100)    NOT NULL,

    -- Email address (synced from Firebase — read-only here)
    email          VARCHAR(150)    NOT NULL UNIQUE,

    -- Phone number for SOS SMS alerts (set by user in app, not Firebase)
    phone          VARCHAR(20),

    -- URL to the user's profile picture on Cloudinary
    -- (separate from Firebase photo URL — user may upload a custom one)
    avatar_url     TEXT,

    -- When was this profile record first created in our DB?
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- When was the profile last updated?
    updated_at     TIMESTAMP
);

-- Index on email for fast lookup
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

COMMENT ON TABLE users IS 'App-level user profiles. Identity is owned by Firebase; this table stores app data keyed by firebase_uid';
COMMENT ON COLUMN users.firebase_uid IS 'Firebase UID — received from verified Firebase ID Token. Never store passwords here.';


-- ============================================================
-- TABLE: routes
-- Stores route history — every time a user requests routes,
-- we log the origin, destination, and the safety score we computed.
-- This builds a personal "journey history" viewable in the app.
-- ============================================================
CREATE TABLE IF NOT EXISTS routes (
    id              BIGSERIAL       PRIMARY KEY,

    -- Firebase UID of the user who requested this route.
    firebase_uid    VARCHAR(128)    NOT NULL REFERENCES users(firebase_uid) ON DELETE CASCADE,

    origin_lat      DECIMAL(10,7)   NOT NULL,
    origin_lng      DECIMAL(10,7)   NOT NULL,
    dest_lat        DECIMAL(10,7)   NOT NULL,
    dest_lng        DECIMAL(10,7)   NOT NULL,
    origin_name     VARCHAR(255),
    dest_name       VARCHAR(255),
    safety_score    DECIMAL(5,2),
    distance_km     DECIMAL(8,3),
    duration_min    INT,
    route_type      VARCHAR(20),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_routes_firebase_uid ON routes(firebase_uid);
CREATE INDEX IF NOT EXISTS idx_routes_created_at ON routes(created_at);

COMMENT ON TABLE routes IS 'History of route requests keyed by firebase_uid';



-- ============================================================
-- TABLE: incident_reports
-- Stores crowd-sourced road hazard reports submitted by users.
-- These are the primary INPUT to our safety score algorithm —
-- more reports near a route → lower safety score.
-- ============================================================
CREATE TABLE IF NOT EXISTS incident_reports (
    id           BIGSERIAL       PRIMARY KEY,

    -- Firebase UID of who reported this (nullable for future anonymous reports)
    firebase_uid VARCHAR(128)    REFERENCES users(firebase_uid) ON DELETE SET NULL,

    -- Exact GPS coordinates of the incident location.
    -- DECIMAL(10,7) gives precision to ~1.1cm — more than enough for maps.
    latitude     DECIMAL(10,7)   NOT NULL,
    longitude    DECIMAL(10,7)   NOT NULL,

    -- What TYPE of incident is this?
    -- CHECK constraint enforces only valid values — prevents typos.
    category     VARCHAR(50)     NOT NULL
                 CHECK (category IN ('THEFT', 'ASSAULT', 'ACCIDENT', 'LIGHTING', 'OTHER')),

    -- Free-text description of what happened.
    description  TEXT,

    -- URL to a photo taken at the scene (uploaded to Cloudinary).
    image_url    TEXT,

    -- How serious is this incident? 1 = minor, 5 = critical
    -- Our safety score algorithm weights HIGH severity incidents more.
    severity     INT             NOT NULL DEFAULT 3
                 CHECK (severity BETWEEN 1 AND 5),

    -- Has a moderator verified this report is genuine?
    -- Unverified reports still count but with lower weight in the score algorithm.
    verified     BOOLEAN         NOT NULL DEFAULT FALSE,

    -- When was this incident reported?
    created_at   TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Spatial-ish index: speeds up "find all incidents within X km of lat/lng"
-- queries used by the safety score engine.
-- Note: For true spatial queries, PostgreSQL's PostGIS extension is better,
-- but for our diploma project, a composite index on lat/lng is sufficient.
CREATE INDEX IF NOT EXISTS idx_reports_location ON incident_reports(latitude, longitude);

-- Index: filter reports by category (e.g., show only THEFT reports on map)
CREATE INDEX IF NOT EXISTS idx_reports_category ON incident_reports(category);

-- Index: filter verified reports only
CREATE INDEX IF NOT EXISTS idx_reports_verified ON incident_reports(verified);

-- Index: get recent reports first
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON incident_reports(created_at DESC);

COMMENT ON TABLE incident_reports IS 'Crowd-sourced hazard reports keyed by firebase_uid';
COMMENT ON COLUMN incident_reports.severity IS '1=minor, 2=low, 3=moderate, 4=high, 5=critical';
COMMENT ON COLUMN incident_reports.verified IS 'Set to TRUE by admin after verification — verified reports have higher weight in score';


-- ============================================================
-- TABLE: emergency_contacts
-- Each user can save multiple trusted contacts.
-- When SOS is triggered, we fetch these contacts and send SMS alerts.
-- ============================================================
CREATE TABLE IF NOT EXISTS emergency_contacts (
    id             BIGSERIAL       PRIMARY KEY,

    -- Which user owns this contact?
    firebase_uid   VARCHAR(128)    NOT NULL REFERENCES users(firebase_uid) ON DELETE CASCADE,

    -- Contact's full name (shown in the SOS alert message)
    name           VARCHAR(100)    NOT NULL,

    -- Phone number to send SMS alert to.
    -- Stored with country code: e.g., "+919876543210"
    phone          VARCHAR(20)     NOT NULL,

    -- Relationship label: "Father", "Mother", "Friend", "Police" etc.
    relationship   VARCHAR(50),

    -- When was this contact added?
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Index: "get all contacts for user X" — used every time SOS is triggered
CREATE INDEX IF NOT EXISTS idx_contacts_firebase_uid ON emergency_contacts(firebase_uid);

COMMENT ON TABLE emergency_contacts IS 'Emergency contacts keyed by firebase_uid';


-- ============================================================
-- TABLE: sos_events
-- Logs every SOS event — when it was triggered, from where,
-- and whether it was resolved.
-- This creates an audit trail for safety analysis.
-- ============================================================
CREATE TABLE IF NOT EXISTS sos_events (
    id             BIGSERIAL       PRIMARY KEY,

    -- Which user triggered SOS?
    firebase_uid   VARCHAR(128)    NOT NULL REFERENCES users(firebase_uid) ON DELETE CASCADE,

    -- User's GPS location at the moment SOS was triggered.
    -- This is the location sent to emergency contacts.
    latitude       DECIMAL(10,7)   NOT NULL,
    longitude      DECIMAL(10,7)   NOT NULL,

    -- When was SOS triggered?
    triggered_at   TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- Has the emergency been resolved (user confirmed they're safe)?
    -- Default FALSE — must be explicitly marked resolved.
    resolved       BOOLEAN         NOT NULL DEFAULT FALSE,

    -- When was it resolved? (NULL until resolved=TRUE)
    resolved_at    TIMESTAMP
);

-- Index: "show all SOS events for user X" — for SOS history screen
CREATE INDEX IF NOT EXISTS idx_sos_firebase_uid ON sos_events(firebase_uid);

-- Index: find unresolved SOS events — useful for admin dashboard later
CREATE INDEX IF NOT EXISTS idx_sos_resolved ON sos_events(resolved) WHERE resolved = FALSE;

COMMENT ON TABLE sos_events IS 'Audit log of SOS events keyed by firebase_uid';


-- ============================================================
-- SEED DATA: Default Admin User (optional — for testing)
-- Password: 'admin123' (BCrypt hash shown below)
-- You can log in with admin@riskfreeroutes.com / admin123 to test APIs.
-- BCrypt hash generated for 'admin123' with strength 10.
-- ============================================================
-- INSERT INTO users (name, email, password_hash, phone)
-- VALUES (
--     'Admin User',
--     'admin@riskfreeroutes.com',
--     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
--     '+911234567890'
-- ) ON CONFLICT (email) DO NOTHING;


-- ============================================================
-- SEED DATA: Sample Incident Reports (uncomment to use for demos)
-- Replace lat/lng with coordinates near YOUR demo location.
-- These will make the safety score algorithm show meaningful results.
-- ============================================================
-- INSERT INTO incident_reports (latitude, longitude, category, description, severity, verified)
-- VALUES
-- (18.5204, 73.8567, 'THEFT',    'Mobile phone snatching near bus stop',    4, true),
-- (18.5220, 73.8590, 'LIGHTING', 'Streetlights broken for past 2 weeks',    2, true),
-- (18.5180, 73.8540, 'ACCIDENT', 'Road accident at junction, poor signage', 3, true),
-- (18.5240, 73.8610, 'ASSAULT',  'Harassment reported near market area',    5, true),
-- (18.5190, 73.8555, 'OTHER',    'Waterlogging making road dangerous',      2, false);


-- ============================================================
-- VERIFICATION: Run these queries to confirm tables were created
-- ============================================================
-- SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
-- SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'users';
-- SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'incident_reports';
