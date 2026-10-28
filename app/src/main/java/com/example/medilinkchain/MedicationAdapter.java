package com.example.medilinkchain;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * MedicationAdapter — RecyclerView Adapter for displaying active medicines in cards.
 */
public class MedicationAdapter extends RecyclerView.Adapter<MedicationAdapter.MedicationViewHolder> {

    public interface MedicationActionListener {
        void onTaken(MedicationModel med);
        void onSkipped(MedicationModel med);
        void onRefill(MedicationModel med);
        void onEdit(MedicationModel med);
        void onTogglePause(MedicationModel med);
        void onDelete(MedicationModel med);
    }

    private final Context context;
    private List<MedicationModel> medicationList = new ArrayList<>();
    private final MedicationActionListener listener;

    public MedicationAdapter(Context context, MedicationActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setMedications(List<MedicationModel> list) {
        this.medicationList = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MedicationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_medication_card, parent, false);
        return new MedicationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicationViewHolder holder, int position) {
        MedicationModel med = medicationList.get(position);

        holder.medNameText.setText(med.getMedicineName());
        String dosageFreq = med.getDosage() + " • " + med.getFrequency();
        holder.medDosageFreqText.setText(dosageFreq);

        // Times summary string
        List<String> times = med.getTimes();
        if (times != null && !times.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times.size(); i++) {
                sb.append(times.get(i));
                if (i < times.size() - 1) sb.append(", ");
            }
            holder.timesSummaryText.setText(sb.toString());
        } else {
            holder.timesSummaryText.setText("No times set");
        }

        // Tablet Count
        String tabletInfo = "Remaining: " + med.getRemainingTablets() + " / " + med.getTabletQuantity() + " tablets";
        holder.tabletCountText.setText(tabletInfo);

        // Next dose
        holder.nextDoseText.setText(med.getNextDoseDisplay());

        // Status Badge
        if (med.isCompletedByDate()) {
            holder.statusBadge.setText("Completed");
            holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_completed);
            holder.statusBadge.setTextColor(context.getResources().getColor(R.color.colorTextDarkMuted));
            holder.actionsRow.setVisibility(View.GONE);
        } else if (med.isPaused()) {
            holder.statusBadge.setText("Paused");
            holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_paused);
            holder.statusBadge.setTextColor(0xFFE65100);
            holder.actionsRow.setVisibility(View.GONE);
        } else {
            holder.statusBadge.setText("Active");
            holder.statusBadge.setBackgroundResource(R.drawable.bg_badge_active);
            holder.statusBadge.setTextColor(0xFF2E7D32);
            holder.actionsRow.setVisibility(View.VISIBLE);
        }

        // Low stock refill warning banner
        if (med.isLowStock() && !med.isCompletedByDate()) {
            holder.refillWarningLayout.setVisibility(View.VISIBLE);
            holder.refillWarningText.setText("⚠️ " + med.getMedicineName() + " running low: " + med.getRemainingTablets() + " left!");
            holder.markRefilledBtn.setOnClickListener(v -> {
                if (listener != null) listener.onRefill(med);
            });
        } else {
            holder.refillWarningLayout.setVisibility(View.GONE);
        }

        // Buttons [ TAKEN ] and [ SKIPPED ]
        holder.btnTaken.setOnClickListener(v -> {
            if (listener != null) listener.onTaken(med);
        });

        holder.btnSkipped.setOnClickListener(v -> {
            if (listener != null) listener.onSkipped(med);
        });

        // Popup Overflow Menu
        holder.moreMenuBtn.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.moreMenuBtn);
            popup.getMenu().add(0, 1, 0, "✏️ Edit Medicine");
            popup.getMenu().add(0, 2, 1, med.isPaused() ? "▶️ Resume Medicine" : "⏸️ Pause Medicine");
            popup.getMenu().add(0, 3, 2, "🔄 Mark as Refilled");
            popup.getMenu().add(0, 4, 3, "🗑️ Delete Medicine");

            popup.setOnMenuItemClickListener(item -> {
                if (listener == null) return false;
                switch (item.getItemId()) {
                    case 1:
                        listener.onEdit(med);
                        return true;
                    case 2:
                        listener.onTogglePause(med);
                        return true;
                    case 3:
                        listener.onRefill(med);
                        return true;
                    case 4:
                        listener.onDelete(med);
                        return true;
                    default:
                        return false;
                }
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return medicationList.size();
    }

    static class MedicationViewHolder extends RecyclerView.ViewHolder {
        TextView medNameText, medDosageFreqText, timesSummaryText, tabletCountText, nextDoseText, statusBadge, refillWarningText;
        ImageView moreMenuBtn;
        LinearLayout refillWarningLayout, actionsRow;
        MaterialButton btnTaken, btnSkipped, markRefilledBtn;

        public MedicationViewHolder(@NonNull View itemView) {
            super(itemView);
            medNameText = itemView.findViewById(R.id.medNameText);
            medDosageFreqText = itemView.findViewById(R.id.medDosageFreqText);
            timesSummaryText = itemView.findViewById(R.id.timesSummaryText);
            tabletCountText = itemView.findViewById(R.id.tabletCountText);
            nextDoseText = itemView.findViewById(R.id.nextDoseText);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            moreMenuBtn = itemView.findViewById(R.id.moreMenuBtn);
            refillWarningLayout = itemView.findViewById(R.id.refillWarningLayout);
            refillWarningText = itemView.findViewById(R.id.refillWarningText);
            markRefilledBtn = itemView.findViewById(R.id.markRefilledBtn);
            actionsRow = itemView.findViewById(R.id.actionsRow);
            btnTaken = itemView.findViewById(R.id.btnTaken);
            btnSkipped = itemView.findViewById(R.id.btnSkipped);
        }
    }
}
