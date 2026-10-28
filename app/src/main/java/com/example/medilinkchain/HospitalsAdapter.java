package com.example.medilinkchain;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class HospitalsAdapter extends RecyclerView.Adapter<HospitalsAdapter.HospitalViewHolder> {

    private List<Hospital> hospitals;

    public HospitalsAdapter(List<Hospital> hospitals) {
        this.hospitals = hospitals;
    }

    @NonNull
    @Override
    public HospitalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_nearest_hospital, parent, false);
        return new HospitalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HospitalViewHolder holder, int position) {
        Hospital hospital = hospitals.get(position);
        holder.name.setText(hospital.getName());
        holder.distance.setText("Distance: " + hospital.getDistance());

        holder.callBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + hospital.getPhone()));
            v.getContext().startActivity(intent);
        });

        holder.navigateBtn.setOnClickListener(v -> {
            GoogleMapsHelper.openSearch(v.getContext(), hospital.getName());
        });
    }

    @Override
    public int getItemCount() {
        return hospitals.size();
    }

    static class HospitalViewHolder extends RecyclerView.ViewHolder {
        TextView name, distance;
        FloatingActionButton callBtn, navigateBtn;

        public HospitalViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.hospitalName);
            distance = itemView.findViewById(R.id.hospitalDistance);
            callBtn = itemView.findViewById(R.id.callHospitalBtn);
            navigateBtn = itemView.findViewById(R.id.navigateBtn);
        }
    }
}
