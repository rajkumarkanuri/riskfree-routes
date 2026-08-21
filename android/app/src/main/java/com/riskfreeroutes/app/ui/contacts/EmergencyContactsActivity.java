package com.riskfreeroutes.app.ui.contacts;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.riskfreeroutes.app.databinding.ActivityEmergencyContactsBinding;
import com.riskfreeroutes.app.model.TrustedContact;

import java.util.ArrayList;

/**
 * EmergencyContactsActivity — Trusted Contacts list screen.
 *
 * ── WHAT THIS SCREEN DOES ─────────────────────────────────────────────────────
 * Shows all contacts stored in users/{uid}/trusted_contacts as glass cards.
 * Each card shows the contact's name, relationship, and phone number.
 * Contacts marked isPrimary == true show a "PRIMARY" badge and appear first.
 *
 * ── NAVIGATION ───────────────────────────────────────────────────────────────
 * Entry points:
 *   - Profile screen → "Trusted Contacts" row → this Activity
 *
 * From this screen:
 *   - "+" button at top right → AddEditContactActivity (ADD mode)
 *   - Tap a contact card → AddEditContactActivity (EDIT mode, passes contactId)
 *   - Tap delete icon on a card → confirmation dialog → ViewModel.deleteContact()
 *
 * ── DATA FLOW ────────────────────────────────────────────────────────────────
 * Firestore (snapshot) → TrustedContactRepository → TrustedContactsViewModel
 *   → LiveData<List<TrustedContact>> → this Activity → TrustedContactAdapter → RecyclerView
 *
 * Because we use addSnapshotListener in the Repository, any add/edit/delete
 * anywhere in the app instantly pushes a fresh list to the UI — no manual reload.
 *
 * ── STATES ───────────────────────────────────────────────────────────────────
 * LOADING  → spinner visible, list hidden, empty state hidden
 * EMPTY    → empty state visible (big call-to-action), list hidden
 * CONTENT  → list visible, others hidden
 */
public class EmergencyContactsActivity extends AppCompatActivity {

    private static final String TAG = "EmergencyContactsAct";

    private ActivityEmergencyContactsBinding binding;
    private TrustedContactsViewModel viewModel;
    private TrustedContactAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate — Trusted Contacts screen");

        binding = ActivityEmergencyContactsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Get the ViewModel (created once per screen lifecycle, survives rotation)
        viewModel = new ViewModelProvider(this).get(TrustedContactsViewModel.class);

        setupRecyclerView();
        setupClickListeners();
        observeViewModel();

        // Start the Firestore snapshot listener (no-op if already started)
        viewModel.getContacts();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RECYCLERVIEW SETUP
    // ═════════════════════════════════════════════════════════════════════════

    private void setupRecyclerView() {
        // LinearLayoutManager = vertical scrolling list (default)
        binding.recyclerContacts.setLayoutManager(new LinearLayoutManager(this));

        // Start with an empty list; the ViewModel will deliver real data via LiveData
        adapter = new TrustedContactAdapter(
                new ArrayList<>(),
                // OnContactClickListener: tap card → open Edit screen
                contact -> openAddEditScreen(contact),
                // OnDeleteClickListener: confirmation dialog already shown in Adapter;
                // if user confirms, we get here
                contact -> {
                    Log.d(TAG, "Delete confirmed for: " + contact.getName());
                    viewModel.deleteContact(contact.getContactId());
                }
        );
        binding.recyclerContacts.setAdapter(adapter);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CLICK LISTENERS
    // ═════════════════════════════════════════════════════════════════════════

    private void setupClickListeners() {
        // Back arrow → finish this Activity
        binding.btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        // "+" button in top-right → open Add Contact screen
        binding.btnAddContact.setOnClickListener(v -> openAddEditScreen(null));

        // "Add a Trusted Contact" button in the empty state
        binding.btnAddFirstContact.setOnClickListener(v -> openAddEditScreen(null));
    }

    /**
     * Opens AddEditContactActivity in the appropriate mode.
     *
     * @param contact null = ADD mode (blank form)
     *                non-null = EDIT mode (form pre-filled with this contact's data)
     */
    private void openAddEditScreen(TrustedContact contact) {
        Intent intent = new Intent(this, AddEditContactActivity.class);
        if (contact != null) {
            // EDIT mode: pass the contact's Firestore document ID and all fields
            // so AddEditContactActivity can pre-fill the form and call updateContact()
            intent.putExtra(AddEditContactActivity.EXTRA_CONTACT_ID, contact.getContactId());
            intent.putExtra(AddEditContactActivity.EXTRA_CONTACT_NAME, contact.getName());
            intent.putExtra(AddEditContactActivity.EXTRA_CONTACT_PHONE, contact.getPhone());
            intent.putExtra(AddEditContactActivity.EXTRA_CONTACT_RELATIONSHIP, contact.getRelationship());
            intent.putExtra(AddEditContactActivity.EXTRA_CONTACT_IS_PRIMARY, contact.isPrimary());
        }
        startActivity(intent);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OBSERVE VIEWMODEL
    // ═════════════════════════════════════════════════════════════════════════

    private void observeViewModel() {

        // ── Contact list ───────────────────────────────────────────────────────
        // This fires every time Firestore delivers a new snapshot.
        // We call getContacts() here which starts the listener if not already running.
        viewModel.getContacts().observe(this, contacts -> {
            Log.d(TAG, "Contact list updated: " + contacts.size() + " contacts");

            // Hide loading spinner (if still showing from initial load)
            binding.loadingContainer.setVisibility(View.GONE);

            if (contacts.isEmpty()) {
                // EMPTY state
                binding.recyclerContacts.setVisibility(View.GONE);
                binding.emptyContainer.setVisibility(View.VISIBLE);
            } else {
                // CONTENT state
                binding.emptyContainer.setVisibility(View.GONE);
                binding.recyclerContacts.setVisibility(View.VISIBLE);
                adapter.updateContacts(contacts);
            }
        });

        // ── Loading state (for write operations) ────────────────────────────────
        viewModel.getIsLoading().observe(this, isLoading -> {
            // Only the initial snapshot triggers the loadingContainer.
            // Write operations show a Toast, not a full-screen spinner.
        });

        // ── Error messages ─────────────────────────────────────────────────────
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        // ── Success messages ───────────────────────────────────────────────────
        viewModel.getSuccessMessage().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearSuccess();
            }
        });
    }
}
