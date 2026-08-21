package com.riskfreeroutes.app.ui.journey;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.databinding.ActivityJourneyHistoryBinding;
import com.riskfreeroutes.app.databinding.ItemJourneyBinding;
import com.riskfreeroutes.app.model.Journey;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JourneyHistoryActivity extends AppCompatActivity {

    // ViewBinding reference to access UI elements safely
    private ActivityJourneyHistoryBinding binding;
    private JourneyAdapter adapter;
    private List<Journey> journeyList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate layout using ViewBinding
        binding = ActivityJourneyHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup Back Button to close this Activity
        binding.btnBack.setOnClickListener(v -> finish());

        // Initialize RecyclerView with a LinearLayoutManager (vertical list)
        binding.recyclerJourneys.setLayoutManager(new LinearLayoutManager(this));
        adapter = new JourneyAdapter(journeyList);
        binding.recyclerJourneys.setAdapter(adapter);

        // Fetch data from Firestore
        loadJourneyHistory();
    }

    /**
     * Loads the user's journey history from Firestore and updates the UI.
     */
    private void loadJourneyHistory() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // No user logged in, hide progress and show empty state as fallback
            binding.progressBar.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
            return;
        }

        String uid = user.getUid();

        // Query Firestore: users/{uid}/journey_history ordered by startTimestamp DESCENDING
        FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("journey_history")
                .orderBy("startTimestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // Hide the progress bar since loading is complete
                    binding.progressBar.setVisibility(View.GONE);

                    // Convert Firestore documents into a list of Journey objects
                    List<Journey> journeys = querySnapshot.toObjects(Journey.class);

                    if (journeys.isEmpty()) {
                        // Show empty state if there are no journeys
                        binding.recyclerJourneys.setVisibility(View.GONE);
                        binding.emptyState.setVisibility(View.VISIBLE);
                    } else {
                        // Hide empty state and show RecyclerView
                        binding.emptyState.setVisibility(View.GONE);
                        binding.recyclerJourneys.setVisibility(View.VISIBLE);

                        // Add all data to our list and notify adapter
                        journeyList.clear();
                        journeyList.addAll(journeys);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    // In case of an error, hide progress and show empty state
                    binding.progressBar.setVisibility(View.GONE);
                    binding.emptyState.setVisibility(View.VISIBLE);
                });
    }

    /**
     * Inner Adapter class to bind Journey objects to the RecyclerView.
     */
    private class JourneyAdapter extends RecyclerView.Adapter<JourneyAdapter.JourneyViewHolder> {

        private List<Journey> dataList;

        public JourneyAdapter(List<Journey> dataList) {
            this.dataList = dataList;
        }

        @NonNull
        @Override
        public JourneyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Inflate the item layout using ViewBinding
            ItemJourneyBinding itemBinding = ItemJourneyBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new JourneyViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull JourneyViewHolder holder, int position) {
            Journey journey = dataList.get(position);
            holder.bind(journey);
        }

        @Override
        public int getItemCount() {
            return dataList.size();
        }

        /**
         * ViewHolder for a single journey item.
         */
        class JourneyViewHolder extends RecyclerView.ViewHolder {

            private ItemJourneyBinding itemBinding;

            public JourneyViewHolder(ItemJourneyBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }

            public void bind(Journey journey) {
                // Format Route: origin -> destination
                String origin = journey.getOriginAddress() != null ? journey.getOriginAddress() : "Unknown";
                String dest = journey.getDestinationAddress() != null ? journey.getDestinationAddress() : "Unknown";
                itemBinding.tvRoute.setText(origin + " \u2192 " + dest);

                // Format Date using SimpleDateFormat
                if (journey.getStartTimestamp() != null) {
                    Date date = journey.getStartTimestamp().toDate();
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                    itemBinding.tvDate.setText(sdf.format(date));
                } else {
                    itemBinding.tvDate.setText("Unknown Date");
                }

                // Setup Safety Score Badge
                int score = journey.getSafetyScore();
                itemBinding.tvSafetyScore.setText(score + "/100");

                // Set Badge Color based on score value
                int colorRes;
                if (score >= 80) {
                    colorRes = R.color.success_green;
                } else if (score >= 50) {
                    colorRes = R.color.warning_amber;
                } else {
                    colorRes = R.color.danger_red;
                }
                itemBinding.tvSafetyScore.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), colorRes));

                // Format Distance from meters to km
                double distKm = journey.getDistance() / 1000.0;
                itemBinding.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", distKm));

                // Format Duration from seconds to "Xh Ym" or "Ym"
                long durationSecs = journey.getEstimatedDuration();
                long hours = durationSecs / 3600;
                long minutes = (durationSecs % 3600) / 60;
                if (hours > 0) {
                    itemBinding.tvDuration.setText(hours + "h " + minutes + "m");
                } else {
                    itemBinding.tvDuration.setText(minutes + "m");
                }

                // Format and display the Status
                String status = journey.getStatus();
                if (status != null) {
                    // Make it look nice, e.g. "in_progress" -> "In progress"
                    String formattedStatus = status.replace("_", " ");
                    formattedStatus = formattedStatus.substring(0, 1).toUpperCase() + formattedStatus.substring(1);
                    itemBinding.tvStatus.setText(formattedStatus);
                } else {
                    itemBinding.tvStatus.setText("");
                }
            }
        }
    }
}
