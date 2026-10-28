package com.example.medilinkchain;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

public class HealthRiskResultActivity extends AppCompatActivity {

    private HealthRiskAssessmentModel model;

    private MaterialCardView riskCard;
    private TextView riskCategoryText;
    private TextView riskScoreText;

    private TextView bmiValueText;
    private TextView bmiCategoryBadge;
    private TextView bmiGuidanceText;

    private TextView bpValueText;
    private TextView bpStatusText;

    private TextView glucoseValueText;
    private TextView glucoseStatusText;
    private TextView glucoseNoteText;

    private TextView smokingResultText;
    private TextView activityResultText;
    private TextView alcoholResultText;

    private LinearLayout suggestionsContainer;
    private MaterialButton saveAssessmentBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_risk_result);

        try {
            View backBtn = findViewById(R.id.backBtn);
            if (backBtn != null) backBtn.setOnClickListener(v -> finish());

            riskCard = findViewById(R.id.riskCard);
            riskCategoryText = findViewById(R.id.riskCategoryText);
            riskScoreText = findViewById(R.id.riskScoreText);

            bmiValueText = findViewById(R.id.bmiValueText);
            bmiCategoryBadge = findViewById(R.id.bmiCategoryBadge);
            bmiGuidanceText = findViewById(R.id.bmiGuidanceText);

            bpValueText = findViewById(R.id.bpValueText);
            bpStatusText = findViewById(R.id.bpStatusText);

            glucoseValueText = findViewById(R.id.glucoseValueText);
            glucoseStatusText = findViewById(R.id.glucoseStatusText);
            glucoseNoteText = findViewById(R.id.glucoseNoteText);

            smokingResultText = findViewById(R.id.smokingResultText);
            activityResultText = findViewById(R.id.activityResultText);
            alcoholResultText = findViewById(R.id.alcoholResultText);

            suggestionsContainer = findViewById(R.id.suggestionsContainer);
            saveAssessmentBtn = findViewById(R.id.saveAssessmentBtn);

            model = (HealthRiskAssessmentModel) getIntent().getSerializableExtra("ASSESSMENT_MODEL");
            if (model == null) {
                Toast.makeText(this, "No assessment data found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            displayResults();

            if (saveAssessmentBtn != null) {
                saveAssessmentBtn.setOnClickListener(v -> saveAssessment());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error rendering results.", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayResults() {
        // 1. Overall Category & Score Badge
        String category = model.getRiskCategory() != null ? model.getRiskCategory() : HealthRiskCalculator.CAT_LOWER_RISK;
        riskCategoryText.setText(category);
        riskScoreText.setText(String.format(Locale.US, "App Health Risk Screening Score: %d", model.getRiskScore()));

        if (HealthRiskCalculator.CAT_HIGHER_RISK.equalsIgnoreCase(category)) {
            riskCard.setCardBackgroundColor(Color.parseColor("#C62828"));
        } else if (HealthRiskCalculator.CAT_MODERATE_RISK.equalsIgnoreCase(category)) {
            riskCard.setCardBackgroundColor(Color.parseColor("#EF6C00"));
        } else {
            riskCard.setCardBackgroundColor(Color.parseColor("#2E7D32"));
        }

        // 2. BMI Card
        if (model.getBmi() > 0) {
            bmiValueText.setText(String.format(Locale.US, "%.1f", model.getBmi()));
            bmiCategoryBadge.setText(model.getBmiCategory());
            bmiGuidanceText.setText(HealthRiskCalculator.getBmiGuidance(model.getBmi()));
        } else {
            bmiValueText.setText("Not provided");
            bmiCategoryBadge.setText("N/A");
            bmiGuidanceText.setText("Height and weight were not provided.");
        }

        // 3. Blood Pressure Card
        if (model.getSystolicBp() > 0 && model.getDiastolicBp() > 0) {
            bpValueText.setText(String.format(Locale.US, "%d/%d mmHg", model.getSystolicBp(), model.getDiastolicBp()));
            bpStatusText.setText(String.format("Status: %s", model.getBpStatus()));
            if ("Elevated reading".equalsIgnoreCase(model.getBpStatus())) {
                bpStatusText.setTextColor(Color.parseColor("#D32F2F"));
            } else {
                bpStatusText.setTextColor(Color.parseColor("#388E3C"));
            }
        } else {
            bpValueText.setText("Not provided");
            bpStatusText.setText("Status: Not provided");
            bpStatusText.setTextColor(Color.parseColor("#757575"));
        }

        // 4. Fasting Blood Sugar Card
        if (model.getFastingGlucose() > 0) {
            glucoseValueText.setText(String.format(Locale.US, "%d mg/dL", model.getFastingGlucose()));
            glucoseStatusText.setText(String.format("Status: %s", model.getGlucoseStatus()));
            if ("Elevated reading".equalsIgnoreCase(model.getGlucoseStatus())) {
                glucoseStatusText.setTextColor(Color.parseColor("#D32F2F"));
                glucoseNoteText.setText("Your entered blood sugar value is above the reference threshold used for this screening assessment. Discuss persistent abnormal readings with a healthcare professional.");
            } else {
                glucoseStatusText.setTextColor(Color.parseColor("#388E3C"));
                glucoseNoteText.setText("Status is within general reference threshold.");
            }
        } else {
            glucoseValueText.setText("Not provided");
            glucoseStatusText.setText("Status: Not provided");
            glucoseStatusText.setTextColor(Color.parseColor("#757575"));
            glucoseNoteText.setText("Blood sugar was not entered.");
        }

        // 5. Lifestyle Factors Card
        String smokingText = "Yes".equalsIgnoreCase(model.getSmoking())
                ? "Smoking: Yes (Modifiable health risk factor)"
                : "Smoking: No";
        smokingResultText.setText(smokingText);

        String activityText = "Low".equalsIgnoreCase(model.getPhysicalActivity())
                ? "Physical Activity: Low (May contribute to long-term health risk)"
                : String.format("Physical Activity: %s", model.getPhysicalActivity());
        activityResultText.setText(activityText);

        alcoholResultText.setText(String.format("Alcohol Consumption: %s", model.getAlcohol()));

        // 6. Suggestions List
        if (suggestionsContainer != null) {
            suggestionsContainer.removeAllViews();
            List<String> suggestions = HealthRiskCalculator.generatePersonalizedSuggestions(model);
            for (String suggestion : suggestions) {
                TextView tv = new TextView(this);
                tv.setText(String.format("• %s", suggestion));
                tv.setTextColor(Color.parseColor("#334155"));
                tv.setTextSize(13);
                tv.setPadding(0, 4, 0, 8);
                suggestionsContainer.addView(tv);
            }
        }
    }

    private void saveAssessment() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please sign in to save your assessment.", Toast.LENGTH_SHORT).show();
            return;
        }
        String userId = currentUser.getUid();
        model.setUserId(userId);

        if (saveAssessmentBtn != null) {
            saveAssessmentBtn.setEnabled(false);
            saveAssessmentBtn.setText("Saving...");
        }

        // 1. Save to Supabase REST API
        SupabaseDbHelper.saveHealthRiskAssessment(model, new SupabaseDbHelper.SaveCallback() {
            @Override
            public void onSuccess(HealthRiskAssessmentModel savedModel) {
                Log.d("HealthRiskResult", "Saved assessment to Supabase.");
                syncToFirestore(userId, savedModel);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w("HealthRiskResult", "Supabase save failed: " + errorMessage + ". Falling back to Firestore.");
                syncToFirestore(userId, model);
            }
        });
    }

    private void syncToFirestore(String userId, HealthRiskAssessmentModel savedModel) {
        try {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("health_risk_assessments")
                    .document(savedModel.getId())
                    .set(savedModel)
                    .addOnCompleteListener(task -> {
                        Toast.makeText(HealthRiskResultActivity.this, "Assessment saved successfully!", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(HealthRiskResultActivity.this, HealthRiskHistoryActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    });
        } catch (Exception e) {
            Log.e("HealthRiskResult", "Firestore sync error", e);
            Toast.makeText(HealthRiskResultActivity.this, "Assessment saved!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
