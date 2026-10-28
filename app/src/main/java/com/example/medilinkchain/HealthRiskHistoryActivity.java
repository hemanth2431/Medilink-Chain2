package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HealthRiskHistoryActivity extends AppCompatActivity {

    private RecyclerView historyRecyclerView;
    private TextView emptyStateText;

    private TextView trendBmiText;
    private TextView trendBpText;
    private TextView trendGlucoseText;

    private HealthRiskHistoryAdapter adapter;
    private List<HealthRiskAssessmentModel> assessmentList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_risk_history);

        try {
            View backBtn = findViewById(R.id.backBtn);
            if (backBtn != null) backBtn.setOnClickListener(v -> finish());

            View newAssessmentBtn = findViewById(R.id.newAssessmentBtn);
            if (newAssessmentBtn != null) {
                newAssessmentBtn.setOnClickListener(v -> {
                    startActivity(new Intent(HealthRiskHistoryActivity.this, HealthRiskAssessmentActivity.class));
                });
            }

            historyRecyclerView = findViewById(R.id.historyRecyclerView);
            emptyStateText = findViewById(R.id.emptyStateText);

            trendBmiText = findViewById(R.id.trendBmiText);
            trendBpText = findViewById(R.id.trendBpText);
            trendGlucoseText = findViewById(R.id.trendGlucoseText);

            if (historyRecyclerView != null) {
                historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                adapter = new HealthRiskHistoryAdapter(this, assessmentList, item -> {
                    Intent intent = new Intent(HealthRiskHistoryActivity.this, HealthRiskResultActivity.class);
                    intent.putExtra("ASSESSMENT_MODEL", item);
                    startActivity(intent);
                });
                historyRecyclerView.setAdapter(adapter);
            }

            loadHistoryData();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing history.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistoryData();
    }

    private void loadHistoryData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to view your health assessment history.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String userId = currentUser.getUid();

        // 1. Fetch from Supabase DB
        SupabaseDbHelper.fetchHealthRiskAssessments(userId, new SupabaseDbHelper.FetchCallback() {
            @Override
            public void onSuccess(List<HealthRiskAssessmentModel> list) {
                if (list != null && !list.isEmpty()) {
                    updateUI(list);
                } else {
                    fetchFromFirestore(userId);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w("HealthRiskHistory", "Supabase fetch failed: " + errorMessage + ". Falling back to Firestore.");
                fetchFromFirestore(userId);
            }
        });
    }

    private void fetchFromFirestore(String userId) {
        try {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("health_risk_assessments")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        List<HealthRiskAssessmentModel> list = new ArrayList<>();
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            HealthRiskAssessmentModel m = doc.toObject(HealthRiskAssessmentModel.class);
                            if (m != null) list.add(m);
                        }
                        updateUI(list);
                    })
                    .addOnFailureListener(e -> updateUI(new ArrayList<>()));
        } catch (Exception e) {
            Log.e("HealthRiskHistory", "Firestore fetch exception", e);
            updateUI(new ArrayList<>());
        }
    }

    private void updateUI(List<HealthRiskAssessmentModel> list) {
        assessmentList.clear();
        if (list != null) {
            assessmentList.addAll(list);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (assessmentList.isEmpty()) {
            if (emptyStateText != null) emptyStateText.setVisibility(View.VISIBLE);
            if (historyRecyclerView != null) historyRecyclerView.setVisibility(View.GONE);
            updateTrends(null);
        } else {
            if (emptyStateText != null) emptyStateText.setVisibility(View.GONE);
            if (historyRecyclerView != null) historyRecyclerView.setVisibility(View.VISIBLE);
            updateTrends(assessmentList);
        }
    }

    private void updateTrends(List<HealthRiskAssessmentModel> list) {
        if (list == null || list.size() < 2) {
            if (trendBmiText != null) trendBmiText.setText("BMI Trend: Not enough data to show a trend.");
            if (trendBpText != null) trendBpText.setText("Blood Pressure Trend: Not enough data to show a trend.");
            if (trendGlucoseText != null) trendGlucoseText.setText("Blood Sugar Trend: Not enough data to show a trend.");
            return;
        }

        // List is ordered descending (latest first)
        HealthRiskAssessmentModel latest = list.get(0);
        HealthRiskAssessmentModel previous = list.get(1);

        // BMI Trend
        if (latest.getBmi() > 0 && previous.getBmi() > 0) {
            double diff = latest.getBmi() - previous.getBmi();
            if (Math.abs(diff) < 0.1) {
                trendBmiText.setText(String.format(Locale.US, "BMI Trend: Stable at %.1f", latest.getBmi()));
            } else if (diff < 0) {
                trendBmiText.setText(String.format(Locale.US, "BMI Trend: Reduced to %.1f (Previous: %.1f)", latest.getBmi(), previous.getBmi()));
            } else {
                trendBmiText.setText(String.format(Locale.US, "BMI Trend: Increased to %.1f (Previous: %.1f)", latest.getBmi(), previous.getBmi()));
            }
        } else {
            trendBmiText.setText("BMI Trend: Insufficient data");
        }

        // Blood Pressure Trend
        if (latest.getSystolicBp() > 0 && previous.getSystolicBp() > 0) {
            trendBpText.setText(String.format(Locale.US, "BP Trend: Latest %d/%d (Previous: %d/%d)",
                    latest.getSystolicBp(), latest.getDiastolicBp(),
                    previous.getSystolicBp(), previous.getDiastolicBp()));
        } else {
            trendBpText.setText("Blood Pressure Trend: Insufficient data");
        }

        // Blood Sugar Trend
        if (latest.getFastingGlucose() > 0 && previous.getFastingGlucose() > 0) {
            trendGlucoseText.setText(String.format(Locale.US, "Blood Sugar Trend: Latest %d mg/dL (Previous: %d mg/dL)",
                    latest.getFastingGlucose(), previous.getFastingGlucose()));
        } else {
            trendGlucoseText.setText("Blood Sugar Trend: Insufficient data");
        }
    }
}
