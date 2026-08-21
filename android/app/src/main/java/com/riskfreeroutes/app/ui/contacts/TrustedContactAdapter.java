package com.riskfreeroutes.app.ui.contacts;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.riskfreeroutes.app.databinding.ItemTrustedContactBinding;
import com.riskfreeroutes.app.model.TrustedContact;

import java.util.List;

/**
 * TrustedContactAdapter — Binds a List<TrustedContact> to the RecyclerView.
 *
 * ── HOW RECYCLERVIEW ADAPTERS WORK ───────────────────────────────────────────
 * A RecyclerView reuses a fixed pool of "ViewHolder" objects (one per visible row).
 * As the user scrolls, off-screen ViewHolders are recycled and rebound with new data
 * instead of inflating new views. This is far more efficient than creating one View
 * per list item (which is what ListView did).
 *
 * Our Adapter does three jobs:
 *   1. onCreateViewHolder — inflate item_trusted_contact.xml and wrap it in a ViewHolder
 *   2. onBindViewHolder   — fill the ViewHolder's views with one TrustedContact's data
 *   3. getItemCount        — tell RecyclerView how many items exist
 *
 * ── CLICK LISTENERS ──────────────────────────────────────────────────────────
 * We use two callback interfaces instead of setting click listeners directly inside
 * the Adapter. This keeps the Adapter "dumb" (it only displays data) and moves
 * the navigation/deletion logic into the Activity (which knows about Intents and
 * ViewModels). This is the standard pattern.
 */
public class TrustedContactAdapter extends RecyclerView.Adapter<TrustedContactAdapter.ContactViewHolder> {

    // ── Callback interfaces ───────────────────────────────────────────────────

    /** Called when the user taps a contact card (opens Edit mode) */
    public interface OnContactClickListener {
        void onContactClick(TrustedContact contact);
    }

    /** Called when the user taps the delete icon on a contact card */
    public interface OnDeleteClickListener {
        void onDeleteClick(TrustedContact contact);
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    private List<TrustedContact> contacts;
    private final OnContactClickListener onContactClick;
    private final OnDeleteClickListener onDeleteClick;

    // A set of background colors for the initials avatars.
    // We pick one based on the hash of the contact's name so each
    // contact always gets the same color (consistent across sessions).
    private static final int[] AVATAR_COLORS = {
        Color.parseColor("#2563EB"), // blue
        Color.parseColor("#7C3AED"), // purple
        Color.parseColor("#DB2777"), // pink
        Color.parseColor("#059669"), // green
        Color.parseColor("#D97706"), // amber
        Color.parseColor("#DC2626"), // red
        Color.parseColor("#0891B2"), // cyan
    };

    // ── CONSTRUCTOR ───────────────────────────────────────────────────────────
    public TrustedContactAdapter(List<TrustedContact> contacts,
                                  OnContactClickListener onContactClick,
                                  OnDeleteClickListener onDeleteClick) {
        this.contacts = contacts;
        this.onContactClick = onContactClick;
        this.onDeleteClick = onDeleteClick;
    }

    /** Call this to replace the list data and refresh all visible rows */
    public void updateContacts(List<TrustedContact> newList) {
        this.contacts = newList;
        notifyDataSetChanged(); // simple full refresh; fine for ≤50 contacts
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ADAPTER METHODS (required by RecyclerView.Adapter)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Called by RecyclerView when it needs a new ViewHolder.
     * We inflate item_trusted_contact.xml using ViewBinding (type-safe, no findViewById).
     */
    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTrustedContactBinding binding = ItemTrustedContactBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ContactViewHolder(binding);
    }

    /**
     * Called by RecyclerView to fill a ViewHolder with data from position.
     * This is called once per visible item on initial load, and again when
     * the user scrolls a recycled ViewHolder into view.
     */
    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        TrustedContact contact = contacts.get(position);
        holder.bind(contact);
    }

    @Override
    public int getItemCount() {
        return contacts != null ? contacts.size() : 0;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VIEW HOLDER
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * ViewHolder: holds references to all the views in one item row.
     * Using ViewBinding means we don't call findViewById() at all —
     * the binding object has typed references to each view.
     */
    class ContactViewHolder extends RecyclerView.ViewHolder {

        private final ItemTrustedContactBinding b;

        public ContactViewHolder(ItemTrustedContactBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        /**
         * Fills this row's views with data from one TrustedContact.
         *
         * @param contact The contact to display.
         */
        public void bind(TrustedContact contact) {
            // ── Name ─────────────────────────────────────────────────────────
            String name = contact.getName() != null ? contact.getName() : "Unknown";
            b.tvContactName.setText(name);

            // ── Initials Avatar ───────────────────────────────────────────────
            // Extract the first letter of the name (uppercase) for the avatar.
            String initial = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
            b.tvInitials.setText(initial);

            // Pick a color based on the hash of the name so it's consistent.
            // Math.abs handles negative hash codes; % ensures we stay in range.
            int colorIndex = Math.abs(name.hashCode()) % AVATAR_COLORS.length;
            b.tvInitials.getBackground().setTint(AVATAR_COLORS[colorIndex]);

            // ── Relationship ──────────────────────────────────────────────────
            String relationship = contact.getRelationship();
            b.tvRelationship.setText(
                    (relationship != null && !relationship.isEmpty()) ? relationship : "Contact"
            );

            // ── Phone ─────────────────────────────────────────────────────────
            b.tvPhone.setText(contact.getPhone() != null ? contact.getPhone() : "—");

            // ── Primary Badge ─────────────────────────────────────────────────
            // Show the "PRIMARY" chip only if isPrimary is true.
            b.chipPrimary.setVisibility(contact.isPrimary()
                    ? android.view.View.VISIBLE
                    : android.view.View.GONE);

            // ── Tap entire card → Edit ─────────────────────────────────────────
            b.getRoot().setOnClickListener(v -> {
                if (onContactClick != null) onContactClick.onContactClick(contact);
            });

            // ── Tap delete icon → Confirm dialog ──────────────────────────────
            b.btnDelete.setOnClickListener(v -> {
                Context ctx = v.getContext();
                new AlertDialog.Builder(ctx)
                        .setTitle("Delete Contact")
                        .setMessage("Remove " + name + " from your trusted contacts?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            if (onDeleteClick != null) onDeleteClick.onDeleteClick(contact);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }
    }
}
