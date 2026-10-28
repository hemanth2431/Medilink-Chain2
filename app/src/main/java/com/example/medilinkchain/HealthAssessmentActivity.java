package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

/**
 * HealthAssessmentActivity — Form to input personal, anthropometric, lifestyle, and health data
 * for transparent, rule-based clinical screening and personalized health guidance.
 */
public class HealthAssessmentActivity extends AppCompatActivity {

    private ImageView backBtn, historyBtn;
    private TextInputLayout nameInputLayout, ageInputLayout, genderInputLayout, heightInputLayout, weightInputLayout;
    private TextInputLayout activityInputLayout, systolicBpInputLayout, diastolicBpInputLayout, fastingGlucoseInputLayout, postMealGlucoseInputLayout;
    private TextInputLayout existingConditionInputLayout, foodPreferenceInputLayout, healthGoalInputLayout;

    private TextInputEditText nameEditText, ageEditText, heightEditText, weightEditText;
    private TextInputEditText systolicBpEditText, diastolicBpEditText, fastingGlucoseEditText, postMealGlucoseEditText;

    private AutoCompleteTextView genderDropdown, activityDropdown, existingConditionDropdown, foodPreferenceDropdown, healthGoalDropdown;
    private TextView bmiPreviewText, bmiCategoryPreviewText;
    private MaterialButton btnAssessHealth;

    private String currentUserId = "";

    private static final String[] GENDERS = {"Male", "Female", "Other"};
    private static final String[] ACTIVITY_LEVELS = {"Sedentary", "Lightly Active", "Moderately Active", "Very Active"};
    private static final String[] CONDITIONS = {"None", "Diabetes", "High Blood Pressure", "Heart-related condition", "Other"};
    private static final String[] FOOD_PREFS = {"Vegetarian", "Non-Vegetarian", "Vegan"};
    private static final String[] HEALTH_GOALS = {
            "Maintain Weight",
            "Lose Weight",
            "Gain Weight",
            "Manage Blood Sugar",
            "Manage Blood Pressure",
            "Improve Overall Fitness"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_assessment);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }

        initViews();
        setupDropdowns();
        setupLiveBmiPreview();
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        historyBtn = findViewById(R.id.historyBtn);

        nameInputLayout = findViewById(R.id.nameInputLayout);
        ageInputLayout = findViewById(R.id.ageInputLayout);
        genderInputLayout = findViewById(R.id.genderInputLayout);
        heightInputLayout = findViewById(R.id.heightInputLayout);
        weightInputLayout = findViewById(R.id.weightInputLayout);

        activityInputLayout = findViewById(R.id.activityInputLayout);
        systolicBpInputLayout = findViewById(R.id.systolicBpInputLayout);
        diastolicBpInputLayout = findViewById(R.id.diastolicBpInputLayout);
        fastingGlucoseInputLayout = findViewById(R.id.fastingGlucoseInputLayout);
        postMealGlucoseInputLayout = findViewById(R.id.postMealGlucoseInputLayout);

        existingConditionInputLayout = findViewById(R.id.existingConditionInputLayout);
        foodPreferenceInputLayout = findViewById(R.id.foodPreferenceInputLayout);
        healthGoalInputLayout = findViewById(R.id.healthGoalInputLayout);

        nameEditText = findViewById(R.id.nameEditText);
        ageEditText = findViewById(R.id.ageEditText);
        heightEditText = findViewById(R.id.heightEditText);
        weightEditText = findViewById(R.id.weightEditText);

        systolicBpEditText = findViewById(R.id.systolicBpEditText);
        diastolicBpEditText = findViewById(R.id.diastolicBpEditText);
        fastingGlucoseEditText = findViewById(R.id.fastingGlucoseEditText);
        postMealGlucoseEditText = findViewById(R.id.postMealGlucoseEditText);

        genderDropdown = findViewById(R.id.genderDropdown);
        activityDropdown = findViewById(R.id.activityDropdown);
        existingConditionDropdown = findViewById(R.id.existingConditionDropdown);
        foodPreferenceDropdown = findViewById(R.id.foodPreferenceDropdown);
        healthGoalDropdown = findViewById(R.id.healthGoalDropdown);

        bmiPreviewText = findViewById(R.id.bmiPreviewText);
        bmiCategoryPreviewText = findViewById(R.id.bmiCategoryPreviewText);
        btnAssessHealth = findViewById(R.id.btnAssessHealth);

        backBtn.setOnClickListener(v -> finish());
        historyBtn.setOnClickListener(v -> {
            Intent intent = new Intent(HealthAssessmentActivity.this, HealthAssessmentHistoryActivity.class);
            startActivity(intent);
        });

        btnAssessHealth.setOnClickListener(v -> validateAndAssess());
    }

    private void setupDropdowns() {
        genderDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, GENDERS));
        activityDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, ACTIVITY_LEVELS));
        existingConditionDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CONDITIONS));
        foodPreferenceDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, FOOD_PREFS));
        healthGoalDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, HEALTH_GOALS));
    }

    private void setupLiveBmiPreview() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateBmiPreview();
            }
        };

        heightEditText.addTextChangedListener(watcher);
        weightEditText.addTextChangedListener(watcher);
    }

    private void updateBmiPreview() {
        String hStr = heightEditText.getText() != null ? heightEditText.getText().toString().trim() : "";
        String wStr = weightEditText.getText() != null ? weightEditText.getText().toString().trim() : "";

        try {
            if (!hStr.isEmpty() && !wStr.isEmpty()) {
                double h = Double.parseDouble(hStr);
                double w = Double.parseDouble(wStr);

                if (h >= 50 && h <= 250 && w >= 10 && w <= 300) {
                    double hm = h / 100.0;
                    double bmi = Math.round((w / (hm * hm)) * 10.0) / 10.0;
                    bmiPreviewText.setText(String.format(Locale.US, "%.1f", bmi));

                    String category;
                    if (bmi < 18.5) category = "Underweight";
                    else if (bmi <= 24.9) category = "Normal Weight";
                    else if (bmi <= 29.9) category = "Overweight";
                    else category = "Obesity";

                    bmiCategoryPreviewText.setText("(" + category + ")");
                    return;
                }
            }
        } catch (NumberFormatException ignored) {}

        bmiPreviewText.setText("--.-");
        bmiCategoryPreviewText.setText("(Enter height and weight)");
    }

    private void validateAndAssess() {
        if (nameInputLayout != null) nameInputLayout.setError(null);
        if (ageInputLayout != null) ageInputLayout.setError(null);
        if (genderInputLayout != null) genderInputLayout.setError(null);
        if (heightInputLayout != null) heightInputLayout.setError(null);
        if (weightInputLayout != null) weightInputLayout.setError(null);
        if (activityInputLayout != null) activityInputLayout.setError(null);
        if (systolicBpInputLayout != null) systolicBpInputLayout.setError(null);
        if (diastolicBpInputLayout != null) diastolicBpInputLayout.setError(null);
        if (fastingGlucoseInputLayout != null) fastingGlucoseInputLayout.setError(null);
        if (postMealGlucoseInputLayout != null) postMealGlucoseInputLayout.setError(null);

        boolean isValid = true;

        String name = nameEditText.getText() != null ? nameEditText.getText().toString().trim() : "";
        if (name.isEmpty()) {
            name = "Patient";
        }

        // Age Validation
        String ageStr = ageEditText.getText() != null ? ageEditText.getText().toString().trim() : "";
        int age = 0;
        if (ageStr.isEmpty()) {
            ageInputLayout.setError("Age is required");
            isValid = false;
        } else {
            try {
                age = Integer.parseInt(ageStr);
                if (age < 1 || age > 120) {
                    ageInputLayout.setError("Age must be between 1 and 120");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                ageInputLayout.setError("Enter a valid age");
                isValid = false;
            }
        }

        // Height Validation
        String hStr = heightEditText.getText() != null ? heightEditText.getText().toString().trim() : "";
        double height = 0;
        if (hStr.isEmpty()) {
            heightInputLayout.setError("Height is required");
            isValid = false;
        } else {
            try {
                height = Double.parseDouble(hStr);
                if (height < 50 || height > 250) {
                    heightInputLayout.setError("Height must be between 50 and 250 cm");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                heightInputLayout.setError("Enter a valid height");
                isValid = false;
            }
        }

        // Weight Validation
        String wStr = weightEditText.getText() != null ? weightEditText.getText().toString().trim() : "";
        double weight = 0;
        if (wStr.isEmpty()) {
            weightInputLayout.setError("Weight is required");
            isValid = false;
        } else {
            try {
                weight = Double.parseDouble(wStr);
                if (weight < 10 || weight > 300) {
                    weightInputLayout.setError("Weight must be between 10 and 300 kg");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                weightInputLayout.setError("Enter a valid weight");
                isValid = false;
            }
        }

        // Blood Pressure Validation
        String sysStr = systolicBpEditText.getText() != null ? systolicBpEditText.getText().toString().trim() : "";
        int sysBp = 0;
        if (sysStr.isEmpty()) {
            systolicBpInputLayout.setError("Systolic BP is required");
            isValid = false;
        } else {
            try {
                sysBp = Integer.parseInt(sysStr);
                if (sysBp < 50 || sysBp > 260) {
                    systolicBpInputLayout.setError("Systolic BP must be between 50 and 260");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                systolicBpInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        String diaStr = diastolicBpEditText.getText() != null ? diastolicBpEditText.getText().toString().trim() : "";
        int diaBp = 0;
        if (diaStr.isEmpty()) {
            diastolicBpInputLayout.setError("Diastolic BP is required");
            isValid = false;
        } else {
            try {
                diaBp = Integer.parseInt(diaStr);
                if (diaBp < 30 || diaBp > 160) {
                    diastolicBpInputLayout.setError("Diastolic BP must be between 30 and 160");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                diastolicBpInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        // Fasting Blood Sugar Validation
        String fastStr = fastingGlucoseEditText.getText() != null ? fastingGlucoseEditText.getText().toString().trim() : "";
        int fastingSugar = 0;
        if (fastStr.isEmpty()) {
            fastingGlucoseInputLayout.setError("Fasting sugar is required");
            isValid = false;
        } else {
            try {
                fastingSugar = Integer.parseInt(fastStr);
                if (fastingSugar < 30 || fastingSugar > 500) {
                    fastingGlucoseInputLayout.setError("Value must be between 30 and 500 mg/dL");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                fastingGlucoseInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        // Optional Post-meal Blood Sugar Validation
        String postStr = postMealGlucoseEditText.getText() != null ? postMealGlucoseEditText.getText().toString().trim() : "";
        int postMealSugar = 0;
        if (!postStr.isEmpty()) {
            try {
                postMealSugar = Integer.parseInt(postStr);
                if (postMealSugar < 30 || postMealSugar > 500) {
                    postMealGlucoseInputLayout.setError("Value must be between 30 and 500 mg/dL");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                postMealGlucoseInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        if (!isValid) {
            Toast.makeText(this, "Please fill in all required health measurements.", Toast.LENGTH_SHORT).show();
            return;
        }

        String gender = genderDropdown.getText() != null ? genderDropdown.getText().toString().trim() : "";
        if (gender.isEmpty()) gender = "Other";

        String activityLevel = activityDropdown.getText() != null ? activityDropdown.getText().toString().trim() : "";
        if (activityLevel.isEmpty()) activityLevel = "Moderately Active";

        String existingCondition = existingConditionDropdown.getText() != null ? existingConditionDropdown.getText().toString().trim() : "";
        if (existingCondition.isEmpty()) existingCondition = "None";

        String foodPreference = foodPreferenceDropdown.getText() != null ? foodPreferenceDropdown.getText().toString().trim() : "";
        if (foodPreference.isEmpty()) foodPreference = "Vegetarian";

        String healthGoal = healthGoalDropdown.getText() != null ? healthGoalDropdown.getText().toString().trim() : "";
        if (healthGoal.isEmpty()) healthGoal = "Improve Overall Fitness";

        // Perform transparent rule-based clinical assessment
        HealthAssessmentModel assessment = HealthAssessmentEngine.performAssessment(
                name,
                age,
                gender,
                height,
                weight,
                activityLevel,
                sysBp,
                diaBp,
                fastingSugar,
                postMealSugar,
                existingCondition,
                foodPreference,
                healthGoal
        );
        assessment.setUserId(currentUserId);

        Intent intent = new Intent(HealthAssessmentActivity.this, HealthAssessmentResultActivity.class);
        intent.putExtra("EXTRA_ASSESSMENT", assessment);
        startActivity(intent);
    }
}
