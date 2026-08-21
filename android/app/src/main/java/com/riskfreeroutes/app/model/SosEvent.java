package com.riskfreeroutes.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * SosEvent.java — A record of an SOS alert being triggered.
 *
 * Firestore path: users/{uid}/sos_history/{sosId}
 *
 * WHY UNDER users/{uid}/?
 * SOS history is private — only the triggering user (and potentially
 * emergency services/contacts) should read it. Storing it under
 * users/{uid}/sos_history means our security rules can block any
 * other user from reading it with a single line.
 *
 * LIFECYCLE:
 * 1. User taps SOS button → document created with:
 *      status = "active", triggeredAt = now(), resolvedAt = null
 * 2. SMS/WhatsApp sent to all trusted contacts → their phone numbers
 *    are recorded in contactsNotified (for audit trail)
 * 3. User taps "Cancel SOS" → document updated with:
 *      status = "resolved", resolvedAt = now()
 *
 * FIELD MAPPING (Firestore → Java):
 *   location          → location  (GeoPoint)
 *   triggeredAt       → triggeredAt  (Timestamp)
 *   resolvedAt        → resolvedAt  (Timestamp, null if still active)
 *   contactsNotified  → contactsNotified  (List<String> of phone numbers/uids)
 *   status            → status  (String: "active" | "resolved")
 */
public class SosEvent {

    // ── LOCATION ──────────────────────────────────────────────────────────────
    // Where on the map was SOS triggered?
    // Stored as a GeoPoint so it can be plotted on a map later.
    private GeoPoint location;

    // ── TIMING ───────────────────────────────────────────────────────────────
    private Timestamp triggeredAt;

    // null while SOS is still active; set when user cancels
    private Timestamp resolvedAt;

    // ── WHO WAS NOTIFIED ─────────────────────────────────────────────────────
    // A list of phone numbers (or contact document IDs) that received an
    // alert message. We record this so:
    //   - We can show "your 3 contacts were notified" in the UI
    //   - We have an audit trail in case of disputes
    private List<String> contactsNotified;

    // ── STATUS ────────────────────────────────────────────────────────────────
    // "active"   → SOS is currently in progress (contacts were notified)
    // "resolved" → User cancelled the alert (they are safe)
    private String status;

    // ── LEGACY FIELD — kept for backward compat ───────────────────────────────
    // Old code stored the userId inside the document. We keep the field so
    // existing Firestore documents that have this field still deserialize correctly.
    private String userId;

    // ── REQUIRED EMPTY CONSTRUCTOR ────────────────────────────────────────────
    public SosEvent() {}

    // ── CONSTRUCTOR ───────────────────────────────────────────────────────────
    public SosEvent(String userId, GeoPoint location) {
        this.userId = userId;
        this.location = location;
        this.triggeredAt = Timestamp.now();
        this.resolvedAt = null;
        this.contactsNotified = new ArrayList<>(); // will be populated after SMS is sent
        this.status = "active";
    }

    // ── GETTERS & SETTERS ─────────────────────────────────────────────────────

    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }

    public Timestamp getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(Timestamp triggeredAt) { this.triggeredAt = triggeredAt; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    public List<String> getContactsNotified() { return contactsNotified; }
    public void setContactsNotified(List<String> contactsNotified) {
        this.contactsNotified = contactsNotified;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Legacy getter/setter for userId — existing code still compiles
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
