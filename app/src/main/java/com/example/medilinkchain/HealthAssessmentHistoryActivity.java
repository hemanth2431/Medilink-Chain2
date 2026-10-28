package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HealthAssessmentHistoryActivity — Displays historical assessment records and calculates
 * real progress tracking trends (Weight Change, BMI Change, BP Trend, Sugar Trend).
 */
public class HealthAssessmentHistoryActivity extends AppCompatActivity implements HealthAssessmentAdapter.OnItemClickListener {

    private static final String TAG = "HealthAssessmentHistory";

    private ImageView backBtn;
    private ProgressBar progressBar;
    private LinearLayout emptyStateLayout;
    private RecyclerView recyclerView;
    private ExtendedFloatingActionButton fabNewAssessment;

    // Progress Tracking Views
    private LinearLayout trendsLayout;
    private TextView trendInsufficientText;
    private TextView trendWeightText, trendBmiText, trendBpText, trendSugarText;

    private HealthAssessmentAdapter adapter;
    private String currentUserId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_assessment_history);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }

        initViews();
        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }
        loadAssessments();
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        progressBar = findViewById(R.id.historyProgressBar);
        emptyStateLayout = findViewById(R.id.historyEmptyState);
        recyclerView = findViewById(R.id.historyRecyclerView);
        fabNewAssessment = findViewById(R.id.fabNewAssessment);

        trendsLayout = findViewById(R.id.trendsLayout);
        trendInsufficientText = findViewById(R.id.trendInsufficientText);
        trendWeightText = findViewById(R.id.trendWeightText);
        trendBmiText = findViewById(R.id.trendBmiText);
        trendBpText = findViewById(R.id.trendBpText);
        trendSugarText = findViewById(R.id.trendSugarText);

        backBtn.setOnClickListener(v -> finish());
        fabNewAssessment.setOnClickListener(v -> {
            Intent intent = new Intent(HealthAssessmentHistoryActivity.this, HealthAssessmentActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HealthAssessmentAdapter(this, this);
        recyclerView.setAdapter(adapter);
    }

    private void loadAssessments() {
        if (currentUserId.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);

        HealthAssessmentRepository.fetchAssessments(currentUserId, new HealthAssessmentRepository.Callback<List<HealthAssessmentModel>>() {
            @Override
            public void onSuccess(List<HealthAssessmentModel> list) {
                progressBar.setVisibility(View.GONE);
                if (list == null || list.isEmpty()) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    adapter.setList(new ArrayList<>());
                    trendsLayout.setVisibility(View.GONE);
                    trendInsufficientText.setVisibility(View.VISIBLE);
                } else {
                    emptyStateLayout.setVisibility(View.GONE);
                    adapter.setList(list);
                    computeProgressTrends(list);
                }
            }

            @Override
            public void onFailure(String error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to fetch assessments: " + error);
                Toast.makeText(HealthAssessmentHistoryActivity.this, "Unable to load history: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void computeProgressTrends(List<HealthAssessmentModel> list) {
        if (list == null || list.size() < 2) {
            trendsLayout.setVisibility(View.GONE);
            trendInsufficientText.setVisibility(View.VISIBLE);
            return;
        }

        // list is ordered newest first
        HealthAssessmentModel latest = list.get(0);
        HealthAssessmentModel oldest = list.get(list.size() - 1);

        double weightDiff = latest.getWeightKg() - oldest.getWeightKg();
        double bmiDiff = latest.getBmi() - oldest.getBmi();

        String weightSign = weightDiff > 0 ? "+" : "";
        trendWeightText.setText(String.format(Locale.US, "%s%.1f kg", weightSign, weightDiff));

        String bmiSign = bmiDiff > 0 ? "+" : "";
        trendBmiText.setText(String.format(Locale.US, "%s%.1f", bmiSign, bmiDiff));

        trendBpText.setText(String.format(Locale.US, "Blood Pressure Trend: %d/%d → %d/%d mmHg",
                oldest.getSystolicBp(), oldest.getDiastolicBp(), latest.getSystolicBp(), latest.getDiastolicBp()));

        trendSugarText.setText(String.format(Locale.US, "Blood Sugar Trend: %d → %d mg/dL",
                oldest.getFastingBloodSugar(), latest.getFastingBloodSugar()));

        trendsLayout.setVisibility(View.VISIBLE);
        trendInsufficientText.setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(HealthAssessmentModel item) {
        // Regenerate full rule-based calculations and recommendations if needed
        HealthAssessmentModel fullModel = HealthAssessmentEngine.performAssessment(
                item.getPatientName(),
                item.getAge(),
                item.getGender(),
                item.getHeightCm(),
                item.getWeightKg(),
                item.getActivityLevel(),
                item.getSystolicBp(),
                item.getDiastolicBp(),
                item.getFastingBloodSugar(),
                item.getPostMealBloodSugar(),
                item.getExistingCondition(),
                item.getFoodPreference(),
                item.getHealthGoal()
        );
        fullModel.setId(item.getId());
        fullModel.setUserId(item.getUserId());
        fullModel.setAssessmentDate(item.getAssessmentDate());

        Intent intent = new Intent(HealthAssessmentHistoryActivity.this, HealthAssessmentResultActivity.class);
        intent.putExtra("EXTRA_ASSESSMENT", fullModel);
        startActivity(intent);
    }
}
