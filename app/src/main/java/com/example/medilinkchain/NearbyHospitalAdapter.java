package com.example.medilinkchain;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NearbyHospitalAdapter extends RecyclerView.Adapter<NearbyHospitalAdapter.HospitalViewHolder> {

    public interface OnHospitalActionListener {
        void onHospitalSelected(OsmHospital hospital, int position);
        void onDirectionsClicked(OsmHospital hospital);
        void onCallClicked(OsmHospital hospital);
    }

    private final List<OsmHospital> originalList;
    private final List<OsmHospital> displayedList;
    private final OnHospitalActionListener actionListener;
    private int selectedPosition = -1;

    public NearbyHospitalAdapter(List<OsmHospital> hospitals, OnHospitalActionListener listener) {
        this.originalList = new ArrayList<>(hospitals);
        this.displayedList = new ArrayList<>(hospitals);
        this.actionListener = listener;
    }

    public void updateList(List<OsmHospital> newHospitals) {
        originalList.clear();
        originalList.addAll(newHospitals);
        displayedList.clear();
        displayedList.addAll(newHospitals);
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void filter(String query) {
        displayedList.clear();
        if (query == null || query.trim().isEmpty()) {
            displayedList.addAll(originalList);
        } else {
            String lower = query.toLowerCase().trim();
            for (OsmHospital h : originalList) {
                if (h.getName().toLowerCase().contains(lower) ||
                    h.getAddress().toLowerCase().contains(lower) ||
                    h.getSpecialty().toLowerCase().contains(lower) ||
                    h.getFacilityType().toLowerCase().contains(lower)) {
                    displayedList.add(h);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedHospitalId(long hospitalId) {
        for (int i = 0; i < displayedList.size(); i++) {
            if (displayedList.get(i).getId() == hospitalId) {
                int prev = selectedPosition;
                selectedPosition = i;
                if (prev != -1) notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                break;
            }
        }
    }

    public OsmHospital getItemAt(int position) {
        if (position >= 0 && position < displayedList.size()) {
            return displayedList.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public HospitalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearby_hospital, parent, false);
        return new HospitalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HospitalViewHolder holder, int position) {
        OsmHospital hospital = displayedList.get(position);

        holder.tvHospitalName.setText(hospital.getName());
        holder.tvHospitalAddress.setText(hospital.getAddress());
        holder.tvFacilityTag.setText(hospital.getFacilityType() + " · " + hospital.getSpecialty());
        holder.tvDistanceBadge.setText(hospital.getShortDistance());

        // Coordinates for transparency
        holder.tvCoordinates.setText(String.format(Locale.US, "GPS: %.4f, %.4f", hospital.getLatitude(), hospital.getLongitude()));

        // Phone if available
        if (hospital.getPhone() != null && !hospital.getPhone().trim().isEmpty()) {
            holder.layoutPhone.setVisibility(View.VISIBLE);
            holder.tvHospitalPhone.setText(hospital.getPhone());
            holder.layoutPhone.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onCallClicked(hospital);
                }
            });
        } else {
            holder.layoutPhone.setVisibility(View.GONE);
            holder.layoutPhone.setOnClickListener(null);
        }

        // Selection highlight
        if (selectedPosition == position) {
            holder.cardRoot.setStrokeColor(0xFF8E24AA); // Theme Purple/Blue highlight
            holder.cardRoot.setStrokeWidth(4);
            holder.cardRoot.setCardElevation(8f);
        } else {
            holder.cardRoot.setStrokeColor(0xFFE2E8F0);
            holder.cardRoot.setStrokeWidth(2);
            holder.cardRoot.setCardElevation(3f);
        }

        holder.cardRoot.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            if (prev != -1) notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            if (actionListener != null) {
                actionListener.onHospitalSelected(hospital, holder.getAdapterPosition());
            }
        });

        holder.btnDirections.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDirectionsClicked(hospital);
            }
        });
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    static class HospitalViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        TextView tvHospitalName;
        TextView tvHospitalAddress;
        TextView tvFacilityTag;
        TextView tvDistanceBadge;
        TextView tvHospitalPhone;
        TextView tvCoordinates;
        LinearLayout layoutPhone;
        MaterialButton btnDirections;

        public HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardHospitalRoot);
            tvHospitalName = itemView.findViewById(R.id.tvHospitalName);
            tvHospitalAddress = itemView.findViewById(R.id.tvHospitalAddress);
            tvFacilityTag = itemView.findViewById(R.id.tvFacilityTag);
            tvDistanceBadge = itemView.findViewById(R.id.tvDistanceBadge);
            tvHospitalPhone = itemView.findViewById(R.id.tvHospitalPhone);
            tvCoordinates = itemView.findViewById(R.id.tvCoordinates);
            layoutPhone = itemView.findViewById(R.id.layoutPhone);
            btnDirections = itemView.findViewById(R.id.btnDirections);
        }
    }
}
