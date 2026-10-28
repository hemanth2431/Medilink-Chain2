package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public class HealthRiskAssessmentActivity extends AppCompatActivity {

    private TextInputEditText ageEditText;
    private TextInputEditText heightEditText;
    private TextInputEditText weightEditText;
    private TextInputEditText systolicBpEditText;
    private TextInputEditText diastolicBpEditText;
    private TextInputEditText glucoseEditText;

    private AutoCompleteTextView genderDropdown;
    private AutoCompleteTextView smokingDropdown;
    private AutoCompleteTextView activityDropdown;
    private AutoCompleteTextView alcoholDropdown;
    private AutoCompleteTextView familyHistoryDropdown;

    private TextInputLayout ageInputLayout;
    private TextInputLayout heightInputLayout;
    private TextInputLayout weightInputLayout;
    private TextInputLayout systolicBpInputLayout;
    private TextInputLayout diastolicBpInputLayout;
    private TextInputLayout glucoseInputLayout;

    private TextView bmiPreviewText;
    private TextView bmiCategoryPreviewText;
    private MaterialButton calculateBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_health_risk_assessment);

        try {
            // Bind Views
            View backBtn = findViewById(R.id.backBtn);
            if (backBtn != null) backBtn.setOnClickListener(v -> finish());

            View historyBtn = findViewById(R.id.historyBtn);
            if (historyBtn != null) {
                historyBtn.setOnClickListener(v ->
                        startActivity(new Intent(HealthRiskAssessmentActivity.this, HealthRiskHistoryActivity.class))
                );
            }

            ageEditText = findViewById(R.id.ageEditText);
            heightEditText = findViewById(R.id.heightEditText);
            weightEditText = findViewById(R.id.weightEditText);
            systolicBpEditText = findViewById(R.id.systolicBpEditText);
            diastolicBpEditText = findViewById(R.id.diastolicBpEditText);
            glucoseEditText = findViewById(R.id.glucoseEditText);

            genderDropdown = findViewById(R.id.genderDropdown);
            smokingDropdown = findViewById(R.id.smokingDropdown);
            activityDropdown = findViewById(R.id.activityDropdown);
            alcoholDropdown = findViewById(R.id.alcoholDropdown);
            familyHistoryDropdown = findViewById(R.id.familyHistoryDropdown);

            ageInputLayout = findViewById(R.id.ageInputLayout);
            heightInputLayout = findViewById(R.id.heightInputLayout);
            weightInputLayout = findViewById(R.id.weightInputLayout);
            systolicBpInputLayout = findViewById(R.id.systolicBpInputLayout);
            diastolicBpInputLayout = findViewById(R.id.diastolicBpInputLayout);
            glucoseInputLayout = findViewById(R.id.glucoseInputLayout);

            bmiPreviewText = findViewById(R.id.bmiPreviewText);
            bmiCategoryPreviewText = findViewById(R.id.bmiCategoryPreviewText);
            calculateBtn = findViewById(R.id.calculateBtn);

            setupDropdowns();
            setupBmiWatcher();

            if (calculateBtn != null) {
                calculateBtn.setOnClickListener(v -> submitForm());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing form.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDropdowns() {
        String[] genders = new String[]{"Male", "Female", "Other"};
        genderDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, genders));
        genderDropdown.setText("Male", false);

        String[] smokingOptions = new String[]{"No", "Yes"};
        smokingDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, smokingOptions));
        smokingDropdown.setText("No", false);

        String[] activityOptions = new String[]{"Low", "Moderate", "High"};
        activityDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, activityOptions));
        activityDropdown.setText("Moderate", false);

        String[] alcoholOptions = new String[]{"None", "Moderate", "High"};
        alcoholDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, alcoholOptions));
        alcoholDropdown.setText("None", false);

        String[] familyOptions = new String[]{"No", "Yes"};
        familyHistoryDropdown.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, familyOptions));
        familyHistoryDropdown.setText("No", false);
    }

    private void setupBmiWatcher() {
        TextWatcher bmiWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateBmiPreview();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (heightEditText != null) heightEditText.addTextChangedListener(bmiWatcher);
        if (weightEditText != null) weightEditText.addTextChangedListener(bmiWatcher);
    }

    private void updateBmiPreview() {
        String hStr = heightEditText.getText() != null ? heightEditText.getText().toString().trim() : "";
        String wStr = weightEditText.getText() != null ? weightEditText.getText().toString().trim() : "";

        try {
            if (!hStr.isEmpty() && !wStr.isEmpty()) {
                double heightCm = Double.parseDouble(hStr);
                double weightKg = Double.parseDouble(wStr);
                if (heightCm > 0 && weightKg > 0) {
                    double bmi = HealthRiskCalculator.calculateBmi(weightKg, heightCm);
                    String category = HealthRiskCalculator.getBmiCategory(bmi);
                    bmiPreviewText.setText(String.format(Locale.US, "%.1f", bmi));
                    bmiCategoryPreviewText.setText(category);
                    return;
                }
            }
        } catch (NumberFormatException ignored) {}

        bmiPreviewText.setText("--.-");
        bmiCategoryPreviewText.setText("Enter Height & Weight");
    }

    private void submitForm() {
        // Clear previous errors
        ageInputLayout.setError(null);
        heightInputLayout.setError(null);
        weightInputLayout.setError(null);
        systolicBpInputLayout.setError(null);
        diastolicBpInputLayout.setError(null);
        glucoseInputLayout.setError(null);

        boolean isValid = true;

        // 1. Age Validation
        String ageStr = ageEditText.getText() != null ? ageEditText.getText().toString().trim() : "";
        int age = -1;
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
                ageInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        // 2. Height Validation
        String hStr = heightEditText.getText() != null ? heightEditText.getText().toString().trim() : "";
        double heightCm = -1;
        if (hStr.isEmpty()) {
            heightInputLayout.setError("Height is required");
            isValid = false;
        } else {
            try {
                heightCm = Double.parseDouble(hStr);
                if (heightCm < 50 || heightCm > 250) {
                    heightInputLayout.setError("Height must be between 50 and 250 cm");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                heightInputLayout.setError("Invalid height");
                isValid = false;
            }
        }

        // 3. Weight Validation
        String wStr = weightEditText.getText() != null ? weightEditText.getText().toString().trim() : "";
        double weightKg = -1;
        if (wStr.isEmpty()) {
            weightInputLayout.setError("Weight is required");
            isValid = false;
        } else {
            try {
                weightKg = Double.parseDouble(wStr);
                if (weightKg < 2 || weightKg > 300) {
                    weightInputLayout.setError("Weight must be between 2 and 300 kg");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                weightInputLayout.setError("Invalid weight");
                isValid = false;
            }
        }

        // 4. Systolic BP Validation (Optional)
        String sysStr = systolicBpEditText.getText() != null ? systolicBpEditText.getText().toString().trim() : "";
        int sysBp = 0;
        if (!sysStr.isEmpty()) {
            try {
                sysBp = Integer.parseInt(sysStr);
                if (sysBp <= 0 || sysBp > 300) {
                    systolicBpInputLayout.setError("Invalid pressure reading");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                systolicBpInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        // 5. Diastolic BP Validation (Optional)
        String diaStr = diastolicBpEditText.getText() != null ? diastolicBpEditText.getText().toString().trim() : "";
        int diaBp = 0;
        if (!diaStr.isEmpty()) {
            try {
                diaBp = Integer.parseInt(diaStr);
                if (diaBp <= 0 || diaBp > 200) {
                    diastolicBpInputLayout.setError("Invalid pressure reading");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                diastolicBpInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        // 6. Fasting Glucose Validation (Optional)
        String glucStr = glucoseEditText.getText() != null ? glucoseEditText.getText().toString().trim() : "";
        int glucose = 0;
        if (!glucStr.isEmpty()) {
            try {
                glucose = Integer.parseInt(glucStr);
                if (glucose <= 0 || glucose > 600) {
                    glucoseInputLayout.setError("Invalid sugar reading");
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                glucoseInputLayout.setError("Invalid number");
                isValid = false;
            }
        }

        if (!isValid) {
            Toast.makeText(this, "Please correct highlighted errors before proceeding.", Toast.LENGTH_SHORT).show();
            return;
        }

        String gender = genderDropdown.getText().toString();
        String smoking = smokingDropdown.getText().toString();
        String activity = activityDropdown.getText().toString();
        String alcohol = alcoholDropdown.getText().toString();
        String familyHistory = familyHistoryDropdown.getText().toString();

        HealthRiskAssessmentModel assessmentModel = HealthRiskCalculator.computeAssessment(
                age,
                gender,
                heightCm,
                weightKg,
                sysBp,
                diaBp,
                glucose,
                smoking,
                alcohol,
                activity,
                familyHistory
        );

        Intent intent = new Intent(HealthRiskAssessmentActivity.this, HealthRiskResultActivity.class);
        intent.putExtra("ASSESSMENT_MODEL", assessmentModel);
        startActivity(intent);
    }
}
