package com.riskfreeroutes.app.ui.contacts;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityAddEditContactBinding;
import com.riskfreeroutes.app.model.TrustedContact;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AddEditContactActivity — Form for adding a new contact or editing an existing one.
 *
 * ── TWO MODES ────────────────────────────────────────────────────────────────
 * ADD  mode: opened with NO Intent extras. Form is blank. Save calls addContact().
 * EDIT mode: opened with contactId extra (and all field values). Form is pre-filled.
 *            Save calls updateContact(). Delete button is visible.
 *
 * ── HOW MODE IS DETECTED ──────────────────────────────────────────────────────
 * We check whether the Intent has a "contactId" extra.
 * If contactId is non-null → EDIT mode.
 * If contactId is null     → ADD mode.
 *
 * ── PHONE VALIDATION ──────────────────────────────────────────────────────────
 * We validate the phone number before saving using a simple regex.
 * Valid: digits, spaces, dashes, parentheses, optional leading "+".
 * Must be 7–15 characters (international numbers can be long).
 * Examples of VALID: "+91 98765 43210", "9876543210", "(98765) 43210", "+1-800-555-0199"
 * Examples of INVALID: "abc", "123", "not a phone"
 *
 * ── PRIMARY CONTACT LOGIC ─────────────────────────────────────────────────────
 * If the "Set as primary" switch is ON when Save is pressed:
 *   → ViewModel.setPrimaryContact(contactId) is called AFTER the add/update succeeds.
 *   → This uses a Firestore batch to atomically un-primary all others and set this one.
 *
 * If the switch is OFF:
 *   → No change to isPrimary field of any other contact.
 *   → The new/updated contact's isPrimary is stored as false.
 *
 * ── DELETE FLOW ───────────────────────────────────────────────────────────────
 * 1. User taps "Delete Contact" (red outlined button, visible in EDIT mode only)
 * 2. Confirmation AlertDialog appears ("Are you sure?")
 * 3. If confirmed → ViewModel.deleteContact(contactId) → Firestore deletes doc
 * 4. On success toast → Activity finishes → back to EmergencyContactsActivity
 *    where the snapshot listener auto-removes the card from the list
 */
public class AddEditContactActivity extends AppCompatActivity {

    private static final String TAG = "AddEditContactAct";

    // ── Intent extra keys (public so EmergencyContactsActivity can use them) ──
    public static final String EXTRA_CONTACT_ID           = "contact_id";
    public static final String EXTRA_CONTACT_NAME         = "contact_name";
    public static final String EXTRA_CONTACT_PHONE        = "contact_phone";
    public static final String EXTRA_CONTACT_RELATIONSHIP = "contact_relationship";
    public static final String EXTRA_CONTACT_IS_PRIMARY   = "contact_is_primary";

    // ── Phone validation regex ─────────────────────────────────────────────────
    // Matches: optional leading "+", then digits/spaces/dashes/parentheses, 7–15 chars total
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[\\d\\s\\-().]{7,15}$");

    // ── Relationship options (shown in the Spinner) ────────────────────────────
    private static final String[] RELATIONSHIPS =
            {"Parent", "Sibling", "Friend", "Spouse", "Roommate", "Colleague", "Other"};

    private ActivityAddEditContactBinding binding;
    private TrustedContactsViewModel viewModel;

    // In EDIT mode, this is the Firestore document ID. Null in ADD mode.
    private String contactId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAddEditContactBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get the ViewModel (shared with EmergencyContactsActivity via ViewModelProvider —
        // but since this is a DIFFERENT Activity, it gets its own ViewModel instance.
        // That's fine: we only need write operations here, not the list observer.)
        viewModel = new ViewModelProvider(this).get(TrustedContactsViewModel.class);

        // Determine mode from Intent extras
        contactId = getIntent().getStringExtra(EXTRA_CONTACT_ID);
        boolean isEditMode = contactId != null;
        Log.d(TAG, isEditMode ? "EDIT mode: contactId=" + contactId : "ADD mode");

        setupSpinner();
        setupUI(isEditMode);
        setupClickListeners(isEditMode);
        observeViewModel();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UI SETUP
    // ═════════════════════════════════════════════════════════════════════════

    /** Populates the Relationship spinner with the predefined options. */
    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                RELATIONSHIPS
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerRelationship.setAdapter(adapter);
    }

    /**
     * Configures the screen title and pre-fills fields in EDIT mode.
     *
     * @param isEditMode true = EDIT mode, false = ADD mode
     */
    private void setupUI(boolean isEditMode) {
        if (isEditMode) {
            binding.tvTitle.setText("Edit Contact");

            // Pre-fill all fields from Intent extras
            String name         = getIntent().getStringExtra(EXTRA_CONTACT_NAME);
            String phone        = getIntent().getStringExtra(EXTRA_CONTACT_PHONE);
            String relationship = getIntent().getStringExtra(EXTRA_CONTACT_RELATIONSHIP);
            boolean isPrimary   = getIntent().getBooleanExtra(EXTRA_CONTACT_IS_PRIMARY, false);

            binding.etName.setText(name);
            binding.etPhone.setText(phone);
            binding.switchIsPrimary.setChecked(isPrimary);

            // Select the matching spinner item
            if (relationship != null) {
                for (int i = 0; i < RELATIONSHIPS.length; i++) {
                    if (RELATIONSHIPS[i].equalsIgnoreCase(relationship)) {
                        binding.spinnerRelationship.setSelection(i);
                        break;
                    }
                }
            }

            // Show the Delete button (hidden in ADD mode)
            binding.btnDelete.setVisibility(View.VISIBLE);
        } else {
            binding.tvTitle.setText("Add Contact");
            binding.btnDelete.setVisibility(View.GONE);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CLICK LISTENERS
    // ═════════════════════════════════════════════════════════════════════════

    private void setupClickListeners(boolean isEditMode) {
        // Back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Save button
        binding.btnSave.setOnClickListener(v -> {
            if (validateInput()) {
                saveContact(isEditMode);
            }
        });

        // Delete button (EDIT mode only)
        binding.btnDelete.setOnClickListener(v -> showDeleteConfirmation());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Validates all required fields before allowing a save.
     *
     * WHY VALIDATE HERE (not in the ViewModel)?
     * Input validation is a UI concern — it knows about text fields,
     * error display, and the keyboard. The ViewModel only knows about data.
     * Putting validation in the Activity keeps each layer focused on its job.
     *
     * @return true if all fields pass, false if any error is shown.
     */
    private boolean validateInput() {
        String name  = getNameText();
        String phone = getPhoneText();

        // Clear previous errors
        binding.layoutName.setError(null);
        binding.layoutPhone.setError(null);

        // ── Name: required ────────────────────────────────────────────────────
        if (TextUtils.isEmpty(name)) {
            binding.layoutName.setError("Name is required");
            binding.etName.requestFocus();
            return false;
        }

        // ── Phone: required and must match regex ──────────────────────────────
        if (TextUtils.isEmpty(phone)) {
            binding.layoutPhone.setError("Phone number is required");
            binding.etPhone.requestFocus();
            return false;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            binding.layoutPhone.setError("Enter a valid phone number (e.g. +91 98765 43210)");
            binding.etPhone.requestFocus();
            return false;
        }

        return true; // all checks passed
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SAVE CONTACT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Performs the Firestore write (add or update) and then handles the primary toggle.
     *
     * ── ADD flow ─────────────────────────────────────────────────────────────
     * 1. Build a TrustedContact object with form values
     * 2. Call viewModel.addContact(contact)
     * 3. If isPrimary switch is ON → call viewModel.setPrimaryContact(newContactId)
     *    (we need the new auto-generated contactId, which the repository provides
     *    via the contact object after the write — see TrustedContactRepository.addContact())
     * 4. Observe successMessage → finish()
     *
     * ── EDIT flow ────────────────────────────────────────────────────────────
     * 1. Build a Map of changed fields
     * 2. Call viewModel.updateContact(contactId, fields)
     * 3. If isPrimary switch is ON → call viewModel.setPrimaryContact(contactId)
     * 4. Observe successMessage → finish()
     */
    private void saveContact(boolean isEditMode) {
        String name         = getNameText();
        String phone        = getPhoneText();
        String relationship = binding.spinnerRelationship.getSelectedItem().toString();
        boolean isPrimary   = binding.switchIsPrimary.isChecked();

        showLoading(true);

        if (isEditMode) {
            // ── EDIT: update only the changed fields ──────────────────────────
            Map<String, Object> fields = new HashMap<>();
            fields.put("name", name);
            fields.put("phone", phone);
            fields.put("relationship", relationship);
            // Note: isPrimary is handled separately by setPrimaryContact below
            // to ensure the atomic batch (un-primary others) runs correctly.

            viewModel.updateContact(contactId, fields);

            // If the switch is ON, also run the atomic primary-set operation
            if (isPrimary) {
                viewModel.setPrimaryContact(contactId);
            }

        } else {
            // ── ADD: create a new contact document ────────────────────────────
            TrustedContact newContact = new TrustedContact(
                    null,      // userId: set by Repository from FirebaseAuth.currentUser
                    name,
                    phone,
                    relationship,
                    false      // isPrimary: set separately after add so we have the new contactId
            );

            viewModel.addContact(newContact);

            // isPrimary handling for ADD:
            // After addContact() succeeds, the snapshot listener will fire and deliver
            // the new contact WITH its auto-generated contactId.
            // We call setPrimaryContact() AFTER the snapshot delivers the new contact
            // by observing successMessage (which fires after addContact succeeds).
            // The isPrimary flag is stored in the local var for use in the observer below.
            // See the viewModel.getSuccessMessage() observer.
            //
            // We store isPrimary in a local field so the observer can read it:
            this.pendingSetPrimary = isPrimary;
            this.pendingContact = newContact;
        }
    }

    // Helper fields for ADD mode primary handling (set in saveContact, read in observer)
    private boolean pendingSetPrimary = false;
    private TrustedContact pendingContact = null;

    // ═════════════════════════════════════════════════════════════════════════
    // DELETE CONTACT
    // ═════════════════════════════════════════════════════════════════════════

    private void showDeleteConfirmation() {
        String name = getNameText();
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Permanently remove " + name + " from your trusted contacts?\n\n"
                        + "They will no longer receive SOS alerts.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Log.d(TAG, "User confirmed delete for contactId=" + contactId);
                    showLoading(true);
                    viewModel.deleteContact(contactId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OBSERVE VIEWMODEL
    // ═════════════════════════════════════════════════════════════════════════

    private void observeViewModel() {

        // ── Loading state ──────────────────────────────────────────────────────
        viewModel.getIsLoading().observe(this, isLoading -> {
            showLoading(isLoading != null && isLoading);
        });

        // ── Success ────────────────────────────────────────────────────────────
        viewModel.getSuccessMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Log.d(TAG, "Write success: " + msg);

                // For ADD mode: if user toggled isPrimary ON, we now need to set it.
                // The new contact has been saved, but we need its contactId.
                // The pendingContact.getContactId() is set by Repository.addContact().
                if (pendingSetPrimary && pendingContact != null
                        && pendingContact.getContactId() != null) {
                    viewModel.setPrimaryContact(pendingContact.getContactId());
                    pendingSetPrimary = false; // prevent running again on rotation
                }

                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearSuccess();
                finish(); // close this screen, snapshot in list screen auto-updates
            }
        });

        // ── Error ──────────────────────────────────────────────────────────────
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Log.e(TAG, "Write error: " + error);
                showLoading(false);
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /** Gets trimmed name text, safe against null EditText content. */
    private String getNameText() {
        return binding.etName.getText() != null
                ? binding.etName.getText().toString().trim()
                : "";
    }

    /** Gets trimmed phone text, safe against null. */
    private String getPhoneText() {
        return binding.etPhone.getText() != null
                ? binding.etPhone.getText().toString().trim()
                : "";
    }

    /**
     * Toggles the loading UI: show/hide progress bar and enable/disable Save button.
     * This prevents the user from tapping Save twice while a write is in flight.
     */
    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
        binding.btnDelete.setEnabled(!loading);
    }
}
