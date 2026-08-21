package com.riskfreeroutes.app.ui.nearby;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.riskfreeroutes.app.R;
import com.riskfreeroutes.app.model.SafePlace;
import com.riskfreeroutes.app.ui.routes.RouteSelectionActivity;

import java.util.ArrayList;
import java.util.List;

public class SafePlacesAdapter extends RecyclerView.Adapter<SafePlacesAdapter.ViewHolder> {

    private List<SafePlace> places = new ArrayList<>();
    private final NearbyPlacesViewModel viewModel;

    public SafePlacesAdapter(NearbyPlacesViewModel viewModel) {
        this.viewModel = viewModel;
    }

    public void submitList(List<SafePlace> newPlaces) {
        this.places = newPlaces;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_safe_place, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SafePlace place = places.get(position);
        
        holder.tvName.setText(place.getName());
        holder.tvAddress.setText(place.getAddress());
        
        // Format distance
        String distanceStr;
        if (place.getDistanceMeters() >= 1000) {
            distanceStr = String.format("%.1f km", place.getDistanceMeters() / 1000.0);
        } else {
            distanceStr = String.format("%d m", (int) place.getDistanceMeters());
        }
        holder.tvDistance.setText(distanceStr);

        // Set Icon
        if ("police".equals(place.getType())) {
            holder.imgIcon.setImageResource(R.drawable.ic_shield);
            holder.imgIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.primary_blue_light)); // Blue
        } else if ("hospital".equals(place.getType())) {
            holder.imgIcon.setImageResource(android.R.drawable.ic_menu_add); // Using placeholder for cross
            holder.imgIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.danger_red)); // Red
        } else if ("pharmacy".equals(place.getType())) {
            holder.imgIcon.setImageResource(android.R.drawable.ic_menu_myplaces); // Using placeholder
            holder.imgIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.success_green)); // Green
        }

        // Handle Call button
        holder.btnCall.setOnClickListener(v -> {
            // First check if we have the phone number
            if (place.getPhoneNumber() != null && !place.getPhoneNumber().isEmpty()) {
                dialNumber(v, place.getPhoneNumber());
            } else {
                // Fetch it dynamically
                viewModel.getRepository().fetchPlacePhone(place.getId(), new okhttp3.Callback() {
                    @Override
                    public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                        v.post(() -> Toast.makeText(v.getContext(), "Failed to get phone number", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                        try {
                            String json = response.body().string();
                            org.json.JSONObject root = new org.json.JSONObject(json);
                            org.json.JSONObject result = root.optJSONObject("result");
                            if (result != null) {
                                String phone = result.optString("formatted_phone_number", null);
                                if (phone != null && !phone.isEmpty()) {
                                    v.post(() -> dialNumber(v, phone));
                                } else {
                                    v.post(() -> Toast.makeText(v.getContext(), "No phone number available", Toast.LENGTH_SHORT).show());
                                }
                            }
                        } catch (Exception e) {
                            v.post(() -> Toast.makeText(v.getContext(), "Error parsing phone number", Toast.LENGTH_SHORT).show());
                        }
                    }
                });
            }
        });

        // Handle Navigate button
        holder.btnNavigate.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), RouteSelectionActivity.class);
            // Pass the destination location
            intent.putExtra("DEST_LAT", place.getLocation().latitude);
            intent.putExtra("DEST_LNG", place.getLocation().longitude);
            intent.putExtra("DEST_NAME", place.getName());
            v.getContext().startActivity(intent);
        });
    }
    
    private void dialNumber(View v, String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phoneNumber));
        v.getContext().startActivity(intent);
    }

    @Override
    public int getItemCount() {
        return places.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView tvName;
        TextView tvAddress;
        TextView tvDistance;
        Button btnCall;
        Button btnNavigate;

        ViewHolder(View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_place_icon);
            tvName = itemView.findViewById(R.id.tv_place_name);
            tvAddress = itemView.findViewById(R.id.tv_place_address);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            btnCall = itemView.findViewById(R.id.btn_call);
            btnNavigate = itemView.findViewById(R.id.btn_navigate);
        }
    }
}
