package com.example.medilinkchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Transparent rule-based scoring engine for Health Risk Assessment.
 * Educational screening tool — not a clinical medical diagnosis.
 */
public class HealthRiskCalculator {

    public static final String CAT_LOWER_RISK = "LOWER RISK INDICATORS";
    public static final String CAT_MODERATE_RISK = "MODERATE RISK INDICATORS";
    public static final String CAT_HIGHER_RISK = "HIGHER RISK INDICATORS";

    public static double calculateBmi(double weightKg, double heightCm) {
        if (heightCm <= 0 || weightKg <= 0) return 0.0;
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        return Math.round(bmi * 10.0) / 10.0;
    }

    public static String getBmiCategory(double bmi) {
        if (bmi <= 0) return "Not provided";
        if (bmi < 18.5) return "Underweight";
        if (bmi <= 24.9) return "Normal Weight";
        if (bmi <= 29.9) return "Overweight";
        return "Higher BMI Range";
    }

    public static String getBmiGuidance(double bmi) {
        if (bmi <= 0) return "Height and weight were not provided for BMI calculation.";
        if (bmi < 18.5) {
            return "Your BMI is below the general healthy range. Consider discussing healthy weight gain with a healthcare professional or nutritionist.";
        } else if (bmi <= 24.9) {
            return "Your BMI is within the standard healthy weight range. Continue maintaining balanced nutrition and regular physical activity.";
        } else if (bmi <= 29.9) {
            return "Your BMI is above the general healthy range. Consider discussing healthy weight-management strategies with a healthcare professional.";
        } else {
            return "Your BMI falls in the higher range. Consider consulting a healthcare professional for guidance on long-term wellness and weight management.";
        }
    }

    public static String getBpStatus(int systolic, int diastolic) {
        if (systolic <= 0 || diastolic <= 0) return "Not provided";
        if (systolic < 120 && diastolic < 80) {
            return "Within expected range";
        } else {
            return "Elevated reading";
        }
    }

    public static String getGlucoseStatus(int glucose) {
        if (glucose <= 0) return "Not provided";
        if (glucose <= 99) {
            return "Within expected range";
        } else {
            return "Elevated reading";
        }
    }

    public static HealthRiskAssessmentModel computeAssessment(
            int age,
            String gender,
            double heightCm,
            double weightKg,
            int systolicBp,
            int diastolicBp,
            int fastingGlucose,
            String smoking,
            String alcohol,
            String physicalActivity,
            String familyHistory) {

        HealthRiskAssessmentModel model = new HealthRiskAssessmentModel();
        model.setAge(age);
        model.setGender(gender != null ? gender : "Unspecified");
        model.setHeightCm(heightCm);
        model.setWeightKg(weightKg);
        model.setSystolicBp(systolicBp);
        model.setDiastolicBp(diastolicBp);
        model.setFastingGlucose(fastingGlucose);
        model.setSmoking(smoking != null ? smoking : "No");
        model.setAlcohol(alcohol != null ? alcohol : "None");
        model.setPhysicalActivity(physicalActivity != null ? physicalActivity : "Moderate");
        model.setFamilyHistory(familyHistory != null ? familyHistory : "No");

        // 1. BMI Calculation
        double bmi = calculateBmi(weightKg, heightCm);
        model.setBmi(bmi);
        model.setBmiCategory(getBmiCategory(bmi));

        // 2. BP & Glucose Status
        model.setBpStatus(getBpStatus(systolicBp, diastolicBp));
        model.setGlucoseStatus(getGlucoseStatus(fastingGlucose));

        // 3. Transparent Rule-Based Scoring
        int score = 0;

        // BMI Factor
        if (bmi > 0 && (bmi < 18.5 || bmi >= 25.0)) {
            score += 1;
        }

        // BP Factor
        if (systolicBp > 0 && diastolicBp > 0) {
            if (systolicBp >= 120 || diastolicBp >= 80) {
                score += 1;
            }
        }

        // Fasting Glucose Factor
        if (fastingGlucose > 0 && fastingGlucose >= 100) {
            score += 1;
        }

        // Smoking Factor
        if ("Yes".equalsIgnoreCase(model.getSmoking())) {
            score += 1;
        }

        // Physical Activity Factor
        if ("Low".equalsIgnoreCase(model.getPhysicalActivity())) {
            score += 1;
        }

        // Age Factor
        if (age > 60) {
            score += 1;
        }

        // Family History Factor
        if ("Yes".equalsIgnoreCase(model.getFamilyHistory())) {
            score += 1;
        }

        model.setRiskScore(score);

        // 4. Map Score to Category
        if (score <= 1) {
            model.setRiskCategory(CAT_LOWER_RISK);
        } else if (score <= 3) {
            model.setRiskCategory(CAT_MODERATE_RISK);
        } else {
            model.setRiskCategory(CAT_HIGHER_RISK);
        }

        return model;
    }

    public static List<String> generatePersonalizedSuggestions(HealthRiskAssessmentModel model) {
        List<String> suggestions = new ArrayList<>();

        // BMI Suggestions
        if (model.getBmi() > 0) {
            if (model.getBmi() < 18.5) {
                suggestions.add("Maintain regular balanced meals with nutrient-dense foods.");
                suggestions.add("Consider discussing healthy weight gain with a healthcare professional or nutritionist.");
            } else if (model.getBmi() >= 25.0) {
                suggestions.add("Maintain regular physical activity (e.g., 150 minutes per week).");
                suggestions.add("Focus on balanced, portion-conscious meals.");
                suggestions.add("Discuss sustainable weight management strategies with a professional.");
            } else {
                suggestions.add("Maintain your current healthy lifestyle and balanced diet.");
            }
        }

        // Blood Sugar Suggestions
        if (model.getFastingGlucose() > 0) {
            if (model.getFastingGlucose() >= 100) {
                suggestions.add("Limit excessive sugary foods, refined carbohydrates, and sweetened drinks.");
                suggestions.add("Prefer balanced meals rich in fiber and whole grains.");
                suggestions.add("Monitor repeated blood sugar readings and discuss persistent abnormal values with a healthcare professional.");
            } else {
                suggestions.add("Continue a balanced diet low in added sugars to preserve healthy blood glucose levels.");
            }
        }

        // Blood Pressure Suggestions
        if (model.getSystolicBp() > 0 && model.getDiastolicBp() > 0) {
            if (model.getSystolicBp() >= 120 || model.getDiastolicBp() >= 80) {
                suggestions.add("Reduce excessive sodium and processed food intake.");
                suggestions.add("Maintain regular aerobic exercise and stress management practices.");
                suggestions.add("Monitor your blood pressure regularly and discuss persistent elevated readings with a doctor.");
            } else {
                suggestions.add("Keep up your heart-healthy habits to maintain optimal blood pressure.");
            }
        }

        // Lifestyle Suggestions
        if ("Yes".equalsIgnoreCase(model.getSmoking())) {
            suggestions.add("Consider exploring smoking cessation support programs or consulting a health advisor.");
        }

        if ("Low".equalsIgnoreCase(model.getPhysicalActivity())) {
            suggestions.add("Gradually increase safe physical activity (such as brisk walking) according to your personal ability.");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Maintain regular health check-ups and continue your current healthy routines.");
        }

        return suggestions;
    }
}
