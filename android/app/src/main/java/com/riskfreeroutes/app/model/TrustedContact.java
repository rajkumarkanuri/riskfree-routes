package com.riskfreeroutes.app.model;

import com.google.firebase.Timestamp;

/**
 * TrustedContact.java — Represents one person the user trusts in an emergency.
 *
 * ── FIRESTORE LOCATION ──────────────────────────────────────────────────────
 * Each contact lives in a subcollection under the user's own document:
 *
 *   users/{uid}/trusted_contacts/{contactId}
 *
 * Using a subcollection instead of a top-level collection means:
 *   - Security rules are simpler: "only the authenticated user can read/write
 *     documents under their own UID path"
 *   - Queries never need a .whereEqualTo("userId", ...) filter —
 *     the path IS the filter
 *
 * ── FIELDS ──────────────────────────────────────────────────────────────────
 * contactId   — the Firestore document ID. We store it inside the document
 *               so we don't have to pass it around separately when updating
 *               or deleting. Populated by TrustedContactRepository after the
 *               document is created.
 *
 * name        — display name. Required, not null.
 *
 * phone       — phone number string exactly as the user typed it.
 *               Validated in AddEditContactActivity before saving.
 *               GuardianRepository reads this field when sending SOS SMS.
 *
 * relationship — "Parent", "Sibling", "Friend", etc. Optional, for display only.
 *
 * isPrimary   — if true, this contact is shown first in the list and is
 *               labelled with a "PRIMARY" badge chip. Only ONE contact per user
 *               should have isPrimary == true at any time. This is enforced
 *               by TrustedContactRepository.setPrimaryContact() using a
 *               Firestore batch write.
 *
 * createdAt   — server timestamp set when the contact is first added.
 *               Used to resolve tie-breaking when sorting (if two contacts
 *               have the same name, the older one comes first).
 */
public class TrustedContact {

    // ── Firestore document ID ─────────────────────────────────────────────────
    // Firestore doesn't put the document ID inside the document by default.
    // We use @Exclude on the getter in theory, but since we set this field
    // programmatically after reading (not via Firestore deserialization),
    // we just store it as a normal field. Firestore will write it to Firestore
    // too, which is fine — it's a small redundancy that makes the code simpler.
    private String contactId;

    // ── Owner reference ───────────────────────────────────────────────────────
    // UID of the Firebase Auth user who created this contact.
    // Stored for debugging; the path users/{uid}/trusted_contacts already
    // encodes ownership, but having it in the document makes queries easier.
    private String userId;

    // ── Contact details ───────────────────────────────────────────────────────
    private String name;
    private String phone;
    private String relationship;

    // ── Primary flag ──────────────────────────────────────────────────────────
    // true  → this contact is shown first, gets a "PRIMARY" badge chip,
    //         and is the one ProfileViewModel.getPrimaryContact() returns.
    // false → normal contact (default).
    private boolean isPrimary;

    // ── Creation timestamp ────────────────────────────────────────────────────
    // Set by TrustedContactRepository.addContact() at write time.
    // Used for stable ordering (older contacts first when names are equal).
    private Timestamp createdAt;

    // ── REQUIRED EMPTY CONSTRUCTOR ────────────────────────────────────────────
    // Firestore's toObject(TrustedContact.class) uses this constructor to
    // instantiate the object before setting fields via reflection.
    // WITHOUT this constructor, deserialization will crash with a RuntimeException.
    public TrustedContact() {}

    // ── FULL CONSTRUCTOR ──────────────────────────────────────────────────────
    public TrustedContact(String userId, String name, String phone,
                          String relationship, boolean isPrimary) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
        this.isPrimary = isPrimary;
        this.createdAt = Timestamp.now();
    }

    // ── GETTERS & SETTERS ─────────────────────────────────────────────────────

    public String getContactId() { return contactId; }
    public void setContactId(String contactId) { this.contactId = contactId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
