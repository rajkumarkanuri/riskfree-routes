package com.riskfreeroutes.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.riskfreeroutes.app.R;

import java.util.ArrayList;
import java.util.List;

public class PlaceSuggestionsAdapter extends RecyclerView.Adapter<PlaceSuggestionsAdapter.ViewHolder> {

    private List<AutocompletePrediction> predictions = new ArrayList<>();
    private final OnPlaceClickListener listener;

    public interface OnPlaceClickListener {
        void onPlaceClick(AutocompletePrediction prediction);
    }

    public PlaceSuggestionsAdapter(OnPlaceClickListener listener) {
        this.listener = listener;
    }

    public void setPredictions(List<AutocompletePrediction> predictions) {
        this.predictions = predictions == null ? new ArrayList<>() : predictions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_place_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AutocompletePrediction prediction = predictions.get(position);
        holder.tvPrimaryText.setText(prediction.getPrimaryText(null));
        holder.tvSecondaryText.setText(prediction.getSecondaryText(null));
        holder.itemView.setOnClickListener(v -> listener.onPlaceClick(prediction));
    }

    @Override
    public int getItemCount() {
        return predictions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPrimaryText;
        TextView tvSecondaryText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPrimaryText = itemView.findViewById(R.id.tv_primary_text);
            tvSecondaryText = itemView.findViewById(R.id.tv_secondary_text);
        }
    }
}
