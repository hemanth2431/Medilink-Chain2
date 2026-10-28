package com.example.medilinkchain;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * PrescriptionAdapter — RecyclerView Adapter for displaying stored prescriptions.
 */
public class PrescriptionAdapter extends RecyclerView.Adapter<PrescriptionAdapter.PrescriptionViewHolder> {

    public interface PrescriptionActionListener {
        void onView(PrescriptionModel item);
        void onDelete(PrescriptionModel item);
    }

    private final Context context;
    private List<PrescriptionModel> prescriptionList = new ArrayList<>();
    private final PrescriptionActionListener listener;

    public PrescriptionAdapter(Context context, PrescriptionActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPrescriptions(List<PrescriptionModel> list) {
        this.prescriptionList = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PrescriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_prescription_card, parent, false);
        return new PrescriptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PrescriptionViewHolder holder, int position) {
        PrescriptionModel item = prescriptionList.get(position);

        holder.prescriptionTitleText.setText(item.getTitle());
        holder.prescriptionDateText.setText("Date: " + item.getPrescriptionDate());
        holder.doctorNameText.setText(item.getDoctorName());
        holder.hospitalNameText.setText(item.getHospitalName());

        String fileType = item.getFileType();
        holder.prescriptionTypeBadge.setText(fileType);

        if (item.isPdf()) {
            holder.prescriptionTypeBadge.setBackgroundResource(R.drawable.bg_badge_active);
            holder.prescriptionTypeBadge.setTextColor(0xFF2E7D32);
        } else {
            holder.prescriptionTypeBadge.setBackgroundResource(R.drawable.bg_pill_time);
            holder.prescriptionTypeBadge.setTextColor(context.getResources().getColor(R.color.colorMedicalBlue));
        }

        if (item.getNotes() != null && !item.getNotes().trim().isEmpty()) {
            holder.prescriptionNotesText.setVisibility(View.VISIBLE);
            holder.prescriptionNotesText.setText("Notes: " + item.getNotes());
        } else {
            holder.prescriptionNotesText.setVisibility(View.GONE);
        }

        holder.btnViewPrescription.setOnClickListener(v -> {
            if (listener != null) listener.onView(item);
        });

        holder.btnDeletePrescription.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return prescriptionList.size();
    }

    static class PrescriptionViewHolder extends RecyclerView.ViewHolder {
        ImageView prescriptionIcon;
        TextView prescriptionTitleText, prescriptionDateText, prescriptionTypeBadge, doctorNameText, hospitalNameText, prescriptionNotesText;
        MaterialButton btnViewPrescription, btnDeletePrescription;

        public PrescriptionViewHolder(@NonNull View itemView) {
            super(itemView);
            prescriptionIcon = itemView.findViewById(R.id.prescriptionIcon);
            prescriptionTitleText = itemView.findViewById(R.id.prescriptionTitleText);
            prescriptionDateText = itemView.findViewById(R.id.prescriptionDateText);
            prescriptionTypeBadge = itemView.findViewById(R.id.prescriptionTypeBadge);
            doctorNameText = itemView.findViewById(R.id.doctorNameText);
            hospitalNameText = itemView.findViewById(R.id.hospitalNameText);
            prescriptionNotesText = itemView.findViewById(R.id.prescriptionNotesText);
            btnViewPrescription = itemView.findViewById(R.id.btnViewPrescription);
            btnDeletePrescription = itemView.findViewById(R.id.btnDeletePrescription);
        }
    }
}
