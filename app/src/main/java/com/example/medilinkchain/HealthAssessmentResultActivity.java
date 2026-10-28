package com.example.medilinkchain;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

/**
 * HealthAssessmentResultActivity — Displays comprehensive calculated metrics, personalized
 * food guidance, daily health plans, and rule-based screening evaluations.
 */
public class HealthAssessmentResultActivity extends AppCompatActivity {

    private HealthAssessmentModel model;

    private ImageView backBtn;
    private MaterialCardView indicatorCard;
    private TextView indicatorValueText, indicatorReasonText;

    private TextView bmiValueText, bmiCategoryBadge, healthyWeightRangeText, weightGuidanceText;
    private TextView bmrText, maintenanceCaloriesText, goalCaloriesText;
    private TextView bpResultText, bpCategoryText, sugarResultText, sugarCategoryText;

    private LinearLayout recommendedFoodsContainer, foodsToLimitContainer;
    private TextView planBreakfastText, planLunchText, planSnackText, planDinnerText, planHydrationText, planActivityText, planSleepText;

    private MaterialButton btnSaveAssessment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_assessment_result);

        model = (HealthAssessmentModel) getIntent().getSerializableExtra("EXTRA_ASSESSMENT");
        if (model == null) {
            Toast.makeText(this, "No assessment data found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        displayData();
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        indicatorCard = findViewById(R.id.indicatorCard);
        indicatorValueText = findViewById(R.id.indicatorValueText);
        indicatorReasonText = findViewById(R.id.indicatorReasonText);

        bmiValueText = findViewById(R.id.bmiValueText);
        bmiCategoryBadge = findViewById(R.id.bmiCategoryBadge);
        healthyWeightRangeText = findViewById(R.id.healthyWeightRangeText);
        weightGuidanceText = findViewById(R.id.weightGuidanceText);

        bmrText = findViewById(R.id.bmrText);
        maintenanceCaloriesText = findViewById(R.id.maintenanceCaloriesText);
        goalCaloriesText = findViewById(R.id.goalCaloriesText);

        bpResultText = findViewById(R.id.bpResultText);
        bpCategoryText = findViewById(R.id.bpCategoryText);
        sugarResultText = findViewById(R.id.sugarResultText);
        sugarCategoryText = findViewById(R.id.sugarCategoryText);

        recommendedFoodsContainer = findViewById(R.id.recommendedFoodsContainer);
        foodsToLimitContainer = findViewById(R.id.foodsToLimitContainer);

        planBreakfastText = findViewById(R.id.planBreakfastText);
        planLunchText = findViewById(R.id.planLunchText);
        planSnackText = findViewById(R.id.planSnackText);
        planDinnerText = findViewById(R.id.planDinnerText);
        planHydrationText = findViewById(R.id.planHydrationText);
        planActivityText = findViewById(R.id.planActivityText);
        planSleepText = findViewById(R.id.planSleepText);

        btnSaveAssessment = findViewById(R.id.btnSaveAssessment);

        backBtn.setOnClickListener(v -> finish());
        btnSaveAssessment.setOnClickListener(v -> saveAssessment());
    }

    private void displayData() {
        // 1. Overall Health Indicator
        String indicator = model.getOverallIndicator() != null ? model.getOverallIndicator() : "LOWER CONCERN";
        indicatorValueText.setText(indicator);
        indicatorReasonText.setText(model.getIndicatorReason());

        if ("HIGHER CONCERN".equalsIgnoreCase(indicator)) {
            indicatorCard.setCardBackgroundColor(Color.parseColor("#C62828"));
        } else if ("MODERATE CONCERN".equalsIgnoreCase(indicator)) {
            indicatorCard.setCardBackgroundColor(Color.parseColor("#EF6C00"));
        } else {
            indicatorCard.setCardBackgroundColor(Color.parseColor("#2E7D32"));
        }

        // 2. BMI & Weight
        bmiValueText.setText(String.format(Locale.US, "%.1f", model.getBmi()));
        bmiCategoryBadge.setText(model.getBmiCategory());
        healthyWeightRangeText.setText("Healthy Range: " + model.getHealthyWeightRange());
        weightGuidanceText.setText(model.getWeightGuidance());

        // 3. Calorie Requirements
        bmrText.setText(String.format(Locale.US, "%,d kcal", model.getBmr()));
        maintenanceCaloriesText.setText(String.format(Locale.US, "%,d kcal", model.getMaintenanceCalories()));
        goalCaloriesText.setText(String.format(Locale.US, "Approximately %,d kcal/day for %s", model.getGoalCalories(), model.getHealthGoal()));

        // 4. Clinical Metrics
        bpResultText.setText(String.format(Locale.US, "%d/%d mmHg", model.getSystolicBp(), model.getDiastolicBp()));
        bpCategoryText.setText("Category: " + model.getBpCategory());

        sugarResultText.setText(String.format(Locale.US, "%d mg/dL", model.getFastingBloodSugar()));
        sugarCategoryText.setText("Status: " + model.getBloodSugarCategory());

        // 5. Food Recommendations
        recommendedFoodsContainer.removeAllViews();
        if (model.getRecommendedFoods() != null) {
            for (String food : model.getRecommendedFoods()) {
                TextView tv = new TextView(this);
                tv.setText("• " + food);
                tv.setTextColor(Color.parseColor("#334155"));
                tv.setTextSize(13);
                tv.setPadding(0, 4, 0, 6);
                recommendedFoodsContainer.addView(tv);
            }
        }

        foodsToLimitContainer.removeAllViews();
        if (model.getFoodsToLimit() != null) {
            for (String food : model.getFoodsToLimit()) {
                TextView tv = new TextView(this);
                tv.setText("• " + food);
                tv.setTextColor(Color.parseColor("#64748B"));
                tv.setTextSize(13);
                tv.setPadding(0, 4, 0, 6);
                foodsToLimitContainer.addView(tv);
            }
        }

        // 6. Daily Health Plan
        planBreakfastText.setText("🍳 Breakfast: " + (model.getDailyPlanBreakfast() != null ? model.getDailyPlanBreakfast() : ""));
        planLunchText.setText("🥗 Lunch: " + (model.getDailyPlanLunch() != null ? model.getDailyPlanLunch() : ""));
        planSnackText.setText("🍎 Evening Snack: " + (model.getDailyPlanSnack() != null ? model.getDailyPlanSnack() : ""));
        planDinnerText.setText("🍲 Dinner: " + (model.getDailyPlanDinner() != null ? model.getDailyPlanDinner() : ""));
        planHydrationText.setText("💧 Hydration: " + (model.getDailyPlanHydration() != null ? model.getDailyPlanHydration() : ""));
        planActivityText.setText("🏃 Physical Activity: " + (model.getDailyPlanActivity() != null ? model.getDailyPlanActivity() : ""));
        planSleepText.setText("🌙 Sleep: " + (model.getDailyPlanSleep() != null ? model.getDailyPlanSleep() : ""));
    }

    private void saveAssessment() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? user.getUid() : "patient_local";
        model.setUserId(uid);

        btnSaveAssessment.setEnabled(false);
        btnSaveAssessment.setText("SAVING ASSESSMENT...");

        HealthAssessmentRepository.saveAssessment(model, new HealthAssessmentRepository.Callback<HealthAssessmentModel>() {
            @Override
            public void onSuccess(HealthAssessmentModel result) {
                Toast.makeText(HealthAssessmentResultActivity.this, "Assessment saved successfully!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(HealthAssessmentResultActivity.this, HealthAssessmentHistoryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFailure(String error) {
                btnSaveAssessment.setEnabled(true);
                btnSaveAssessment.setText("SAVE ASSESSMENT");
                Toast.makeText(HealthAssessmentResultActivity.this, "Save error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}
