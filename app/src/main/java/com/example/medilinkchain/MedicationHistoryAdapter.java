package com.example.medilinkchain;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * MedicationHistoryAdapter — RecyclerView Adapter for displaying previous medicine events (TAKEN, SKIPPED, REFILLED).
 */
public class MedicationHistoryAdapter extends RecyclerView.Adapter<MedicationHistoryAdapter.HistoryViewHolder> {

    private final Context context;
    private List<MedicationHistoryModel> historyList = new ArrayList<>();

    public MedicationHistoryAdapter(Context context) {
        this.context = context;
    }

    public void setHistory(List<MedicationHistoryModel> list) {
        this.historyList = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_medication_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        MedicationHistoryModel item = historyList.get(position);

        String medTitle = (item.getMedicineName() != null ? item.getMedicineName() : "Medicine")
                + (item.getDosage() != null && !item.getDosage().isEmpty() ? " " + item.getDosage() : "");
        holder.historyMedName.setText(medTitle);

        String timeInfo = item.getFormattedDate() + " • " + (item.getScheduledTime() != null ? item.getScheduledTime() : "");
        holder.historyTimeText.setText(timeInfo);

        String action = item.getAction();
        if ("TAKEN".equalsIgnoreCase(action)) {
            holder.historyActionIcon.setImageResource(R.drawable.ic_check_circle);
            holder.historyActionBadge.setText("✓ Taken");
            holder.historyActionBadge.setBackgroundResource(R.drawable.bg_badge_active);
            holder.historyActionBadge.setTextColor(0xFF2E7D32);

        } else if ("SKIPPED".equalsIgnoreCase(action)) {
            holder.historyActionIcon.setImageResource(R.drawable.ic_cancel);
            holder.historyActionBadge.setText("✕ Skipped");
            holder.historyActionBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            holder.historyActionBadge.setTextColor(0xFFC62828);

        } else if ("REFILLED".equalsIgnoreCase(action)) {
            holder.historyActionIcon.setImageResource(R.drawable.ic_medicine);
            String refillText = "🔄 Refilled (+" + item.getQuantityUsed() + ")";
            holder.historyActionBadge.setText(refillText);
            holder.historyActionBadge.setBackgroundResource(R.drawable.bg_badge_paused);
            holder.historyActionBadge.setTextColor(0xFFE65100);
        }
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        ImageView historyActionIcon;
        TextView historyMedName, historyTimeText, historyActionBadge;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            historyActionIcon = itemView.findViewById(R.id.historyActionIcon);
            historyMedName = itemView.findViewById(R.id.historyMedName);
            historyTimeText = itemView.findViewById(R.id.historyTimeText);
            historyActionBadge = itemView.findViewById(R.id.historyActionBadge);
        }
    }
}
