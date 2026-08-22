package com.riskfreeroutes.app.ui.nearby;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
        this.places = (newPlaces != null) ? newPlaces : new ArrayList<>();
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
        Context ctx = holder.itemView.getContext();
        
        holder.tvName.setText(place.getName() != null ? place.getName() : "Safe Haven");
        holder.tvAddress.setText(place.getAddress() != null ? place.getAddress() : "Nearby location");
        
        // Format distance
        String distanceStr;
        if (place.getDistanceMeters() >= 1000) {
            distanceStr = String.format("%.1f km", place.getDistanceMeters() / 1000.0);
        } else {
            distanceStr = String.format("%d m", (int) place.getDistanceMeters());
        }
        holder.tvDistance.setText(distanceStr);

        // Set Dedicated Symbols and Soft Pill Colors
        String type = place.getType() != null ? place.getType().toLowerCase() : "";
        if ("police".equals(type)) {
            holder.imgIcon.setImageResource(R.drawable.ic_police);
            holder.imgIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_blue)));
            holder.containerIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_blue_subtle)));
            holder.tvType.setText("Police Station · 24/7 Haven");
            holder.tvType.setTextColor(ContextCompat.getColor(ctx, R.color.primary_blue));
        } else if ("hospital".equals(type)) {
            holder.imgIcon.setImageResource(R.drawable.ic_hospital);
            holder.imgIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.danger_red)));
            holder.containerIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.danger_red_subtle)));
            holder.tvType.setText("Hospital · Emergency Medical");
            holder.tvType.setTextColor(ContextCompat.getColor(ctx, R.color.danger_red));
        } else if ("pharmacy".equals(type)) {
            holder.imgIcon.setImageResource(R.drawable.ic_pharmacy);
            holder.imgIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.success_green)));
            holder.containerIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.success_green_subtle)));
            holder.tvType.setText("Pharmacy · First Aid & Medicine");
            holder.tvType.setTextColor(ContextCompat.getColor(ctx, R.color.success_green));
        } else {
            holder.imgIcon.setImageResource(R.drawable.ic_shield);
            holder.imgIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_blue)));
            holder.containerIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_blue_subtle)));
            holder.tvType.setText("Verified Safe Place");
            holder.tvType.setTextColor(ContextCompat.getColor(ctx, R.color.primary_blue));
        }

        // Handle Call button
        holder.btnCall.setOnClickListener(v -> {
            if (place.getPhoneNumber() != null && !place.getPhoneNumber().isEmpty()) {
                dialNumber(v, place.getPhoneNumber());
            } else {
                viewModel.getRepository().fetchPlacePhone(place.getId(), new okhttp3.Callback() {
                    @Override
                    public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                        v.post(() -> Toast.makeText(v.getContext(), "No direct phone line listed", Toast.LENGTH_SHORT).show());
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
                                    v.post(() -> Toast.makeText(v.getContext(), "No phone number available for this location", Toast.LENGTH_SHORT).show());
                                }
                            }
                        } catch (Exception e) {
                            v.post(() -> Toast.makeText(v.getContext(), "Unable to retrieve phone number", Toast.LENGTH_SHORT).show());
                        }
                    }
                });
            }
        });

        // Handle Navigate button with correct Intent extras
        holder.btnNavigate.setOnClickListener(v -> {
            if (place.getLocation() == null) {
                Toast.makeText(v.getContext(), "Location coordinates unavailable", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(v.getContext(), RouteSelectionActivity.class);
            intent.putExtra(RouteSelectionActivity.EXTRA_DEST_LAT, place.getLocation().latitude);
            intent.putExtra(RouteSelectionActivity.EXTRA_DEST_LNG, place.getLocation().longitude);
            intent.putExtra(RouteSelectionActivity.EXTRA_DESTINATION_NAME, place.getName());
            
            // Pass the user's real GPS current location as origin
            if (viewModel.getLastKnownLocation() != null) {
                intent.putExtra(RouteSelectionActivity.EXTRA_ORIGIN_LAT, viewModel.getLastKnownLocation().latitude);
                intent.putExtra(RouteSelectionActivity.EXTRA_ORIGIN_LNG, viewModel.getLastKnownLocation().longitude);
            }
            v.getContext().startActivity(intent);
        });
    }
    
    private void dialNumber(View v, String phoneNumber) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phoneNumber));
            v.getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(v.getContext(), "Could not open dialer", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return places.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout containerIcon;
        ImageView imgIcon;
        TextView tvName;
        TextView tvType;
        TextView tvAddress;
        TextView tvDistance;
        Button btnCall;
        Button btnNavigate;

        ViewHolder(View itemView) {
            super(itemView);
            containerIcon = itemView.findViewById(R.id.containerIcon);
            imgIcon = itemView.findViewById(R.id.img_place_icon);
            tvName = itemView.findViewById(R.id.tv_place_name);
            tvType = itemView.findViewById(R.id.tv_place_type);
            tvAddress = itemView.findViewById(R.id.tv_place_address);
            tvDistance = itemView.findViewById(R.id.tv_distance);
            btnCall = itemView.findViewById(R.id.btn_call);
            btnNavigate = itemView.findViewById(R.id.btn_navigate);
        }
    }
}
