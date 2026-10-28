package com.example.medilinkchain;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HealthAssessmentAdapter — RecyclerView Adapter for displaying previous assessment summaries.
 */
public class HealthAssessmentAdapter extends RecyclerView.Adapter<HealthAssessmentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(HealthAssessmentModel item);
    }

    private final Context context;
    private List<HealthAssessmentModel> list = new ArrayList<>();
    private final OnItemClickListener listener;

    public HealthAssessmentAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setList(List<HealthAssessmentModel> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_health_assessment_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HealthAssessmentModel item = list.get(position);

        holder.historyDateText.setText(item.getAssessmentDate() != null ? item.getAssessmentDate() : "Assessment");

        String indicator = item.getOverallIndicator() != null ? item.getOverallIndicator() : "LOWER CONCERN";
        holder.historyIndicatorBadge.setText(indicator);

        if ("HIGHER CONCERN".equalsIgnoreCase(indicator)) {
            holder.historyIndicatorBadge.setTextColor(Color.parseColor("#DC2626"));
            holder.historyIndicatorBadge.setBackgroundResource(R.drawable.bg_badge_warning);
        } else if ("MODERATE CONCERN".equalsIgnoreCase(indicator)) {
            holder.historyIndicatorBadge.setTextColor(Color.parseColor("#D97706"));
            holder.historyIndicatorBadge.setBackgroundResource(R.drawable.bg_badge_paused);
        } else {
            holder.historyIndicatorBadge.setTextColor(Color.parseColor("#16A34A"));
            holder.historyIndicatorBadge.setBackgroundResource(R.drawable.bg_badge_active);
        }

        holder.historyWeightBmiText.setText(String.format(Locale.US, "Weight: %.1f kg • BMI: %.1f", item.getWeightKg(), item.getBmi()));
        holder.historyBpText.setText(String.format(Locale.US, "BP: %d/%d mmHg", item.getSystolicBp(), item.getDiastolicBp()));
        holder.historySugarText.setText(String.format(Locale.US, "Fasting Sugar: %d mg/dL • Goal: %s", item.getFastingBloodSugar(), item.getHealthGoal()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView historyDateText, historyIndicatorBadge, historyWeightBmiText, historyBpText, historySugarText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            historyDateText = itemView.findViewById(R.id.historyDateText);
            historyIndicatorBadge = itemView.findViewById(R.id.historyIndicatorBadge);
            historyWeightBmiText = itemView.findViewById(R.id.historyWeightBmiText);
            historyBpText = itemView.findViewById(R.id.historyBpText);
            historySugarText = itemView.findViewById(R.id.historySugarText);
        }
    }
}
