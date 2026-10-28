package com.example.medilinkchain;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class HealthRiskHistoryAdapter extends RecyclerView.Adapter<HealthRiskHistoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(HealthRiskAssessmentModel item);
    }

    private final Context context;
    private final List<HealthRiskAssessmentModel> list;
    private final OnItemClickListener listener;

    public HealthRiskHistoryAdapter(Context context, List<HealthRiskAssessmentModel> list, OnItemClickListener listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_health_risk_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HealthRiskAssessmentModel model = list.get(position);

        String dateStr = model.getCreatedAt();
        if (dateStr != null && dateStr.length() >= 10) {
            dateStr = dateStr.substring(0, 10);
        } else {
            dateStr = "Recent";
        }
        holder.dateText.setText(dateStr);

        if (model.getBmi() > 0) {
            holder.bmiText.setText(String.format(Locale.US, "BMI: %.1f", model.getBmi()));
        } else {
            holder.bmiText.setText("BMI: N/A");
        }

        if (model.getSystolicBp() > 0 && model.getDiastolicBp() > 0) {
            holder.bpText.setText(String.format(Locale.US, "BP: %d/%d", model.getSystolicBp(), model.getDiastolicBp()));
        } else {
            holder.bpText.setText("BP: N/A");
        }

        if (model.getFastingGlucose() > 0) {
            holder.sugarText.setText(String.format(Locale.US, "Sugar: %d", model.getFastingGlucose()));
        } else {
            holder.sugarText.setText("Sugar: N/A");
        }

        String cat = model.getRiskCategory() != null ? model.getRiskCategory() : HealthRiskCalculator.CAT_LOWER_RISK;
        holder.riskBadgeText.setText(cat);

        if (HealthRiskCalculator.CAT_HIGHER_RISK.equalsIgnoreCase(cat)) {
            holder.riskBadgeText.setBackgroundColor(Color.parseColor("#C62828"));
        } else if (HealthRiskCalculator.CAT_MODERATE_RISK.equalsIgnoreCase(cat)) {
            holder.riskBadgeText.setBackgroundColor(Color.parseColor("#EF6C00"));
        } else {
            holder.riskBadgeText.setBackgroundColor(Color.parseColor("#2E7D32"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(model);
        });
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        TextView riskBadgeText;
        TextView bmiText;
        TextView bpText;
        TextView sugarText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            riskBadgeText = itemView.findViewById(R.id.riskBadgeText);
            bmiText = itemView.findViewById(R.id.bmiText);
            bpText = itemView.findViewById(R.id.bpText);
            sugarText = itemView.findViewById(R.id.sugarText);
        }
    }
}
