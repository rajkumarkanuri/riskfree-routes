package com.riskfreeroutes.app.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.riskfreeroutes.app.model.TrustedContact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TrustedContactRepository.java — The ONLY class that reads/writes Trusted Contacts in Firestore.
 *
 * ── WHY A SEPARATE REPOSITORY? ───────────────────────────────────────────────
 * In MVVM, data-access code belongs in repositories, not in Activities or ViewModels.
 * If we change from Firestore to a different database later, we only change this file.
 * Everything else (ViewModel, Activity) stays the same.
 *
 * ── FIRESTORE STRUCTURE ───────────────────────────────────────────────────────
 * users/
 *   {uid}/
 *     trusted_contacts/          ← subcollection
 *       {contactId}/             ← auto-generated document ID
 *         name: "Mom"
 *         phone: "+91 9876543210"
 *         relationship: "Parent"
 *         isPrimary: true
 *         userId: "{uid}"
 *         createdAt: Timestamp
 *
 * ── REAL-TIME UPDATES (addSnapshotListener) ───────────────────────────────────
 * getContactsLiveData() returns a LiveData that is backed by Firestore's
 * addSnapshotListener. This means:
 *   - The first delivery happens immediately with the cached data (offline-first)
 *   - Whenever ANY document in trusted_contacts changes (add/edit/delete),
 *     the LiveData emits the new full list automatically
 *   - The Activity never needs to manually reload — it just observes LiveData
 *
 * ── PRIMARY CONTACT RULE ─────────────────────────────────────────────────────
 * Only ONE contact per user may have isPrimary == true at any time.
 * setPrimaryContact() uses a Firestore WriteBatch to atomically:
 *   1. Set isPrimary = false on ALL existing contacts
 *   2. Set isPrimary = true on the target contact
 * A batch is atomic — either ALL writes succeed or NONE do. This prevents
 * the "two contacts both marked primary" race condition.
 */
public class TrustedContactRepository {

    private static final String TAG = "TrustedContactRepo";
    private static final String COLLECTION_USERS = "users";
    private static final String SUBCOLLECTION_CONTACTS = "trusted_contacts";

    private final FirebaseFirestore db;

    // ── SNAPSHOT LISTENER ─────────────────────────────────────────────────────
    // We keep a reference to the active snapshot listener registration so
    // TrustedContactsViewModel can detach it when the ViewModel is destroyed
    // (preventing memory leaks and unnecessary Firestore reads).
    private ListenerRegistration snapshotRegistration;

    public TrustedContactRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. REAL-TIME LIST (used by TrustedContactsViewModel)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Returns a LiveData that emits the full contact list in real time.
     *
     * HOW IT WORKS:
     * We attach a Firestore snapshot listener to the trusted_contacts subcollection.
     * Every time a document changes (add/edit/delete), Firestore pushes the new
     * full snapshot to our listener. We convert it into a List<TrustedContact>
     * and post it to the MutableLiveData.
     *
     * The ViewModel observes this LiveData. The Activity observes the ViewModel.
     * Data flows: Firestore → Repository → ViewModel → Activity → UI.
     *
     * ORDERING (client-side):
     * We sort client-side rather than using a Firestore orderBy on isPrimary
     * because multi-field ordering (isPrimary desc, name asc) requires a
     * composite Firestore index on the subcollection, which must be created
     * in the Firebase console. Sorting 10–20 contacts in memory is instant.
     * Sort rule: primary contacts first, then alphabetically by name.
     *
     * @param uid The Firebase Auth UID of the current user.
     * @return LiveData that always emits the latest sorted contact list.
     */
    public LiveData<List<TrustedContact>> getContactsLiveData(String uid) {
        MutableLiveData<List<TrustedContact>> liveData = new MutableLiveData<>();

        // Start listening to ALL documents in the subcollection.
        // Query.Direction.ASCENDING on "name" gives Firestore a hint for the
        // initial sort, but we re-sort client-side after to put primary first.
        snapshotRegistration = db.collection(COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_CONTACTS)
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Snapshot listener error: " + error.getMessage());
                        liveData.postValue(new ArrayList<>()); // don't crash — emit empty list
                        return;
                    }

                    if (snapshot == null) {
                        liveData.postValue(new ArrayList<>());
                        return;
                    }

                    // Deserialize each Firestore document into a TrustedContact object.
                    // toObject(TrustedContact.class) maps field names to Java field names
                    // automatically (Firestore uses reflection + the empty constructor).
                    List<TrustedContact> contacts = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        TrustedContact contact = doc.toObject(TrustedContact.class);
                        if (contact != null) {
                            // Attach the document ID so the Activity can pass it
                            // back to updateContact() or deleteContact()
                            contact.setContactId(doc.getId());
                            contacts.add(contact);
                        }
                    }

                    // Client-side sort: primary first, then alphabetical by name
                    Collections.sort(contacts, (a, b) -> {
                        if (a.isPrimary() && !b.isPrimary()) return -1; // a goes first
                        if (!a.isPrimary() && b.isPrimary()) return 1;  // b goes first
                        // Both same priority → compare by name alphabetically
                        String nameA = a.getName() != null ? a.getName() : "";
                        String nameB = b.getName() != null ? b.getName() : "";
                        return nameA.compareToIgnoreCase(nameB);
                    });

                    Log.d(TAG, "Snapshot delivered " + contacts.size() + " contacts");
                    liveData.postValue(contacts);
                });

        return liveData;
    }

    /**
     * Detaches the snapshot listener. Call this from ViewModel.onCleared() to
     * prevent Firestore from pushing updates to a destroyed ViewModel.
     *
     * If you don't call this, the listener stays alive until the process dies,
     * consuming network bandwidth and memory.
     */
    public void detachListener() {
        if (snapshotRegistration != null) {
            snapshotRegistration.remove();
            snapshotRegistration = null;
            Log.d(TAG, "Snapshot listener detached");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. ADD CONTACT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Writes a new TrustedContact to Firestore.
     *
     * Firestore generates an auto-ID for the document (we don't pick the ID).
     * Auto-IDs are random strings like "aB3kZ7..." which are guaranteed unique
     * across all documents in the collection, globally.
     *
     * After saving, the snapshot listener in getContactsLiveData() will
     * automatically emit the new list — no extra reload needed.
     *
     * @param uid      Firebase Auth UID of the current user.
     * @param contact  The TrustedContact object to save.
     * @param callback Called on success or failure so the Activity can
     *                 show a Toast or dismiss the loading spinner.
     */
    public void addContact(String uid, TrustedContact contact, WriteCallback callback) {
        // Stamp userId and createdAt right before writing
        contact.setUserId(uid);
        contact.setCreatedAt(Timestamp.now());

        db.collection(COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_CONTACTS)
                .add(contact)                          // Firestore picks the auto-ID
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "addContact success: " + docRef.getId());
                    // Stamp the contactId back onto the object so the caller has it.
                    // The snapshot listener will also deliver the new doc with its ID.
                    contact.setContactId(docRef.getId());
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "addContact failed", e);
                    if (callback != null) callback.onFailure(e);
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. UPDATE CONTACT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Updates specific fields of an existing contact document.
     *
     * We use update() (not set()) because:
     *   - update() only touches the fields you specify
     *   - set() would overwrite the ENTIRE document, wiping any field we forget
     *
     * @param uid       Firebase Auth UID.
     * @param contactId The Firestore document ID of the contact to update.
     * @param fields    Map of fieldName → newValue pairs. Only these fields change.
     * @param callback  Called when done.
     */
    public void updateContact(String uid, String contactId,
                              Map<String, Object> fields, WriteCallback callback) {
        db.collection(COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_CONTACTS).document(contactId)
                .update(fields)
                .addOnSuccessListener(v -> {
                    Log.d(TAG, "updateContact success: " + contactId);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "updateContact failed: " + contactId, e);
                    if (callback != null) callback.onFailure(e);
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. DELETE CONTACT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Permanently deletes a contact document from Firestore.
     *
     * After deletion, the snapshot listener fires and the list in the UI
     * automatically shrinks — no extra reload needed.
     *
     * @param uid       Firebase Auth UID.
     * @param contactId Firestore document ID of the contact to delete.
     * @param callback  Called when done.
     */
    public void deleteContact(String uid, String contactId, WriteCallback callback) {
        db.collection(COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_CONTACTS).document(contactId)
                .delete()
                .addOnSuccessListener(v -> {
                    Log.d(TAG, "deleteContact success: " + contactId);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "deleteContact failed: " + contactId, e);
                    if (callback != null) callback.onFailure(e);
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. SET PRIMARY CONTACT (atomic batch)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Sets one contact as primary and unsets all others — atomically.
     *
     * WHY A BATCH?
     * If we first set A.isPrimary = false and then set B.isPrimary = true in two
     * separate writes, and the app crashes between them, we'd end up with ZERO
     * primary contacts (or worse, two primary contacts if we do it in reverse order).
     *
     * A Firestore WriteBatch solves this: all writes in the batch are committed
     * at the same instant. Either ALL succeed or NONE do. No partial states.
     *
     * STEPS:
     * 1. Fetch all contacts (one-time get, not snapshot — we just need the current IDs)
     * 2. For each contact: add "set isPrimary = false" to the batch
     * 3. For the target contact: add "set isPrimary = true" to the batch
     * 4. Commit the batch
     *
     * @param uid       Firebase Auth UID.
     * @param contactId Document ID of the contact that should become primary.
     * @param callback  Called when done.
     */
    public void setPrimaryContact(String uid, String contactId, WriteCallback callback) {
        // Step 1: fetch all contacts to get their document IDs
        db.collection(COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_CONTACTS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Step 2 & 3: build the batch
                    WriteBatch batch = db.batch();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        // Determine the new isPrimary value for this document:
                        // true if this IS the target contact, false for all others.
                        boolean shouldBePrimary = doc.getId().equals(contactId);

                        batch.update(
                            db.collection(COLLECTION_USERS).document(uid)
                              .collection(SUBCOLLECTION_CONTACTS).document(doc.getId()),
                            "isPrimary", shouldBePrimary
                        );
                    }

                    // Step 4: commit all writes atomically
                    batch.commit()
                            .addOnSuccessListener(v -> {
                                Log.d(TAG, "setPrimaryContact success: " + contactId);
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "setPrimaryContact batch failed", e);
                                if (callback != null) callback.onFailure(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "setPrimaryContact: failed to fetch contacts", e);
                    if (callback != null) callback.onFailure(e);
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6. BACKWARD-COMPATIBLE METHODS (used by ProfileViewModel & GuardianRepository)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Returns the name of the primary contact (or the first contact if none is marked primary).
     * Used by ProfileViewModel to display "Primary Contact: Mom" on the Profile screen.
     *
     * Reads from the SAME users/{uid}/trusted_contacts collection as everything else.
     */
    public interface ContactCallback {
        void onResult(String contactName);
    }

    public void getPrimaryContact(ContactCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { if (callback != null) callback.onResult(null); return; }
        String uid = user.getUid();

        // First try to find the primary contact
        db.collection(COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_CONTACTS)
                .whereEqualTo("isPrimary", true)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        String name = snap.getDocuments().get(0).getString("name");
                        if (callback != null) callback.onResult(name);
                    } else {
                        // No primary set — fall back to first contact alphabetically
                        db.collection(COLLECTION_USERS).document(uid)
                                .collection(SUBCOLLECTION_CONTACTS)
                                .orderBy("name")
                                .limit(1)
                                .get()
                                .addOnSuccessListener(snap2 -> {
                                    if (!snap2.isEmpty()) {
                                        String name = snap2.getDocuments().get(0).getString("name");
                                        if (callback != null) callback.onResult(name);
                                    } else {
                                        if (callback != null) callback.onResult(null);
                                    }
                                })
                                .addOnFailureListener(e -> { if (callback != null) callback.onResult(null); });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getPrimaryContact failed", e);
                    if (callback != null) callback.onResult(null);
                });
    }

    /**
     * Returns all phone numbers from trusted_contacts.
     * Used by GuardianRepository.fetchContactsAndTriggerSOS() to send SMS alerts.
     *
     * This is a one-time get() (not a snapshot listener) because SOS is a
     * one-shot operation — we just need the current phone list at the moment
     * SOS is triggered.
     */
    public interface ContactListCallback {
        void onResult(List<String> phoneNumbers);
    }

    public void getAllContactPhones(ContactListCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onResult(new ArrayList<>());
            return;
        }
        String uid = user.getUid();

        db.collection(COLLECTION_USERS).document(uid)
                .collection(SUBCOLLECTION_CONTACTS)
                .get()
                .addOnSuccessListener(snap -> {
                    List<String> phones = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String phone = doc.getString("phone");
                        if (phone != null && !phone.trim().isEmpty()) {
                            phones.add(phone.trim());
                        }
                    }
                    Log.d(TAG, "getAllContactPhones: found " + phones.size() + " numbers");
                    if (callback != null) callback.onResult(phones);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "getAllContactPhones failed", e);
                    if (callback != null) callback.onResult(new ArrayList<>());
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Generic callback for write operations (add, update, delete, setPrimary).
     * The Activity uses this to show a loading spinner while the write is in
     * progress and a Toast when it completes.
     */
    public interface WriteCallback {
        /** Called when the Firestore write succeeded. */
        void onSuccess();
        /** Called when the write failed. @param e The exception to show/log. */
        void onFailure(Exception e);
    }
}
