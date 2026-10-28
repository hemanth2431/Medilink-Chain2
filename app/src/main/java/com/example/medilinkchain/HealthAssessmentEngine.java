package com.example.medilinkchain;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * HealthAssessmentEngine — Deterministic, calculation-based, and rule-based clinical screening module.
 * Strictly free of AI, LLMs, or external APIs.
 */
public class HealthAssessmentEngine {

    public static HealthAssessmentModel performAssessment(
            String name,
            int age,
            String gender,
            double heightCm,
            double weightKg,
            String activityLevel,
            int systolicBp,
            int diastolicBp,
            int fastingBloodSugar,
            int postMealBloodSugar,
            String existingCondition,
            String foodPreference,
            String healthGoal) {

        HealthAssessmentModel model = new HealthAssessmentModel();
        model.setPatientName(name != null && !name.trim().isEmpty() ? name.trim() : "Patient");
        model.setAge(age);
        model.setGender(gender);
        model.setHeightCm(heightCm);
        model.setWeightKg(weightKg);
        model.setActivityLevel(activityLevel);
        model.setSystolicBp(systolicBp);
        model.setDiastolicBp(diastolicBp);
        model.setFastingBloodSugar(fastingBloodSugar);
        model.setPostMealBloodSugar(postMealBloodSugar);
        model.setExistingCondition(existingCondition != null ? existingCondition : "None");
        model.setFoodPreference(foodPreference != null ? foodPreference : "Vegetarian");
        model.setHealthGoal(healthGoal != null ? healthGoal : "Maintain Weight");

        SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy", Locale.US);
        model.setAssessmentDate(sdf.format(new Date()));

        // 1. BMI Calculation & Category
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        bmi = Math.round(bmi * 10.0) / 10.0;
        model.setBmi(bmi);

        String bmiCategory;
        if (bmi < 18.5) {
            bmiCategory = "Underweight";
        } else if (bmi <= 24.9) {
            bmiCategory = "Normal Weight";
        } else if (bmi <= 29.9) {
            bmiCategory = "Overweight";
        } else {
            bmiCategory = "Obesity";
        }
        model.setBmiCategory(bmiCategory);

        // 2. Healthy Weight Range (Estimated from standard BMI 18.5 - 24.9)
        double minHealthyWeight = Math.round(18.5 * heightM * heightM * 10.0) / 10.0;
        double maxHealthyWeight = Math.round(24.9 * heightM * heightM * 10.0) / 10.0;
        model.setHealthyWeightRange(String.format(Locale.US, "%.1f kg – %.1f kg (Estimated)", minHealthyWeight, maxHealthyWeight));

        // 3. Weight Guidance
        String weightGuidance;
        if (bmi > 24.9) {
            weightGuidance = "Your current BMI is above the general reference range. A gradual, healthy approach to weight management with balanced nutrition and physical activity may be appropriate.";
        } else if (bmi < 18.5) {
            weightGuidance = "Your current BMI is below the general reference range. Consider discussing healthy, nutrient-dense weight gain strategies with a healthcare professional.";
        } else {
            weightGuidance = "Your current BMI is within the general reference range. Focus on sustaining balanced daily nutrition and consistent healthy habits.";
        }
        model.setWeightGuidance(weightGuidance);

        // 4. Calorie Estimation (Mifflin-St Jeor Equation)
        double bmrCalc;
        if ("Female".equalsIgnoreCase(gender)) {
            bmrCalc = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) - 161.0;
        } else if ("Male".equalsIgnoreCase(gender)) {
            bmrCalc = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + 5.0;
        } else {
            bmrCalc = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) - 78.0;
        }
        int bmr = (int) Math.round(Math.max(bmrCalc, 1000.0));
        model.setBmr(bmr);

        double actMultiplier = 1.2; // Sedentary
        if ("Lightly Active".equalsIgnoreCase(activityLevel)) {
            actMultiplier = 1.375;
        } else if ("Moderately Active".equalsIgnoreCase(activityLevel)) {
            actMultiplier = 1.55;
        } else if ("Very Active".equalsIgnoreCase(activityLevel)) {
            actMultiplier = 1.725;
        }

        int maintenanceCalories = (int) Math.round(bmr * actMultiplier);
        model.setMaintenanceCalories(maintenanceCalories);

        int goalCalories = maintenanceCalories;
        if ("Lose Weight".equalsIgnoreCase(healthGoal)) {
            int deficit = 400;
            int floor = "Female".equalsIgnoreCase(gender) ? 1200 : 1500;
            goalCalories = Math.max(maintenanceCalories - deficit, floor);
        } else if ("Gain Weight".equalsIgnoreCase(healthGoal)) {
            goalCalories = maintenanceCalories + 350;
        }
        model.setGoalCalories(goalCalories);

        // 5. Activity Analysis
        String activityAnalysis;
        if ("Sedentary".equalsIgnoreCase(activityLevel)) {
            activityAnalysis = "Your activity level is currently sedentary. Gradually increasing daily steps (e.g. 20-30 min brisk walks) may support cardiovascular health.";
        } else if ("Lightly Active".equalsIgnoreCase(activityLevel)) {
            activityAnalysis = "You engage in light activity. Aiming for 150 minutes of moderate-intensity exercise per week supports sustained metabolic health.";
        } else if ("Moderately Active".equalsIgnoreCase(activityLevel)) {
            activityAnalysis = "You maintain a consistent moderate active routine. Continue a balance of cardiovascular and strength-building exercises.";
        } else {
            activityAnalysis = "You have a high activity level. Ensure proper recovery, hydration (3+ Liters), and adequate protein intake.";
        }
        model.setActivityAnalysis(activityAnalysis);

        // 6. Blood Pressure Assessment
        String bpCat;
        if (systolicBp >= 180 || diastolicBp >= 120) {
            bpCat = "Hypertensive Crisis (Seek prompt medical care)";
        } else if (systolicBp >= 140 || diastolicBp >= 90) {
            bpCat = "Stage 2 Elevated";
        } else if (systolicBp >= 130 || diastolicBp >= 80) {
            bpCat = "Stage 1 Elevated";
        } else if (systolicBp >= 120 && diastolicBp < 80) {
            bpCat = "Elevated";
        } else {
            bpCat = "Within Expected Range";
        }
        model.setBpCategory(bpCat);

        // 7. Blood Sugar Assessment
        String sugarCat;
        if (fastingBloodSugar >= 126) {
            sugarCat = "High Fasting Reading";
        } else if (fastingBloodSugar >= 100) {
            sugarCat = "Elevated (Impaired Fasting Glucose)";
        } else if (fastingBloodSugar < 70) {
            sugarCat = "Lower than Expected Range";
        } else {
            sugarCat = "Within Expected Range";
        }
        model.setBloodSugarCategory(sugarCat);

        // 8. Food Guidance & Foods to Limit
        List<String> recFoods = new ArrayList<>();
        List<String> limitFoods = new ArrayList<>();
        boolean isVegan = "Vegan".equalsIgnoreCase(foodPreference);
        boolean isVeg = "Vegetarian".equalsIgnoreCase(foodPreference) || isVegan;

        // Base recommended vegetables and grains
        recFoods.add("Leafy greens (spinach, methi, kale) and cruciferous vegetables");
        recFoods.add("Whole grains: Oats, brown rice, millets (ragi, jowar), whole wheat");
        recFoods.add("Hydrating fresh fruits: Apples, berries, guava, oranges, papaya");

        // Protein recommendations based on diet preference
        if (isVegan) {
            recFoods.add("Plant proteins: Tofu, tempeh, lentils (dal), chickpeas, kidney beans, edamame");
            recFoods.add("Healthy fats & seeds: Chia seeds, flaxseeds, walnuts, almonds");
        } else if (isVeg) {
            recFoods.add("Vegetarian proteins: Paneer (cottage cheese), Greek yogurt, lentils, beans, tofu");
            recFoods.add("Healthy nuts and seeds: Walnuts, almonds, pumpkin seeds");
        } else {
            recFoods.add("Lean proteins: Skinless chicken breast, grilled fish, eggs, lentils, paneer");
            recFoods.add("Heart-healthy fats: Olive oil, walnuts, almonds, flaxseeds");
        }

        // Foods to limit (safety and cardiovascular rules)
        limitFoods.add("Sugary carbonated beverages, sweetened fruit juices, energy drinks");
        limitFoods.add("Ultra-processed packaged snacks and deep-fried foods");
        limitFoods.add("Highly refined carbohydrates: White flour (maida), pastries, sugary cookies");
        if (systolicBp >= 130 || diastolicBp >= 80 || "High Blood Pressure".equalsIgnoreCase(existingCondition)) {
            limitFoods.add("Excess sodium: Pickles, salted nuts, instant noodles, processed canned soups");
        }
        if (fastingBloodSugar >= 100 || "Diabetes".equalsIgnoreCase(existingCondition)) {
            limitFoods.add("Refined table sugars, sweets, syrups, high-glycemic confectionery");
        }
        model.setRecommendedFoods(recFoods);
        model.setFoodsToLimit(limitFoods);

        // 9. Goal-based Guidance Points
        List<String> goalPoints = new ArrayList<>();
        if ("Lose Weight".equalsIgnoreCase(healthGoal)) {
            goalPoints.add("Maintain a sustainable, moderate calorie deficit (~400 kcal below maintenance).");
            goalPoints.add("Prioritize protein and dietary fiber at every meal to promote satiety.");
            goalPoints.add("Engage in regular aerobic exercise and resistance training 3-4 days weekly.");
        } else if ("Gain Weight".equalsIgnoreCase(healthGoal)) {
            goalPoints.add("Consume a gentle calorie surplus with nutrient-dense meals.");
            goalPoints.add("Include healthy calorie-dense foods: Nuts, peanut butter, avocados, whole milk/soymilk.");
            goalPoints.add("Focus on progressive resistance and strength training to build lean muscle mass.");
        } else if ("Manage Blood Sugar".equalsIgnoreCase(healthGoal) || fastingBloodSugar >= 100) {
            goalPoints.add("Distribute carbohydrate portions evenly throughout the day.");
            goalPoints.add("Pair carbohydrates with protein and fiber to moderate blood glucose absorption.");
            goalPoints.add("Take a 10-15 minute gentle walk after meals to support glycemic control.");
        } else if ("Manage Blood Pressure".equalsIgnoreCase(healthGoal) || systolicBp >= 130) {
            goalPoints.add("Adopt a DASH-style pattern: Rich in potassium (bananas, spinach) and low in sodium.");
            goalPoints.add("Limit sodium intake to under 2,000 mg daily by seasoning with herbs and lemon.");
            goalPoints.add("Practice stress-reduction techniques such as deep breathing or light yoga.");
        } else {
            goalPoints.add("Focus on well-balanced, portion-controlled meals containing all macronutrients.");
            goalPoints.add("Stay consistently active with a combination of cardio, flexibility, and strength work.");
            goalPoints.add("Prioritize 7-8 hours of quality sleep for optimal metabolic recovery.");
        }
        model.setGoalGuidance(goalPoints);

        // 10. Daily Health Plan
        if (isVegan) {
            model.setDailyPlanBreakfast("Oatmeal cooked with soy/almond milk, topped with chia seeds, sliced apple & walnuts.");
            model.setDailyPlanLunch("Quinoa or brown rice with mixed vegetable dal, stir-fried tofu, and cucumber salad.");
            model.setDailyPlanSnack("Handful of roasted chickpeas or a small bowl of fresh papaya/berries.");
            model.setDailyPlanDinner("Whole wheat / millet roti with steamed vegetable curry and grilled tempeh/edamame.");
        } else if (isVeg) {
            model.setDailyPlanBreakfast("Vegetable oats with a glass of milk / warm turmeric milk, or 2 moong dal chillas.");
            model.setDailyPlanLunch("Brown rice / 2 whole wheat rotis with dal, paneer bhurji, and green salad.");
            model.setDailyPlanSnack("Greek yogurt / curd with sliced almonds or a cup of green tea with walnuts.");
            model.setDailyPlanDinner("Millet khichdi with mixed vegetables or grilled paneer with sautéed veggies.");
        } else {
            model.setDailyPlanBreakfast("2 boiled eggs / egg bhurji with whole grain toast and a fresh orange.");
            model.setDailyPlanLunch("Brown rice or rotis with grilled chicken breast / fish curry, dal, and fresh salad.");
            model.setDailyPlanSnack("Handful of mixed roasted nuts (almonds, walnuts) and green tea.");
            model.setDailyPlanDinner("Grilled fish or lean chicken with steamed broccoli, carrots, and sweet potato.");
        }
        model.setDailyPlanHydration("Drink 2.5 to 3.5 Liters of water throughout the day. Keep a water bottle accessible.");
        model.setDailyPlanActivity("30-45 minutes of moderate physical activity (e.g. brisk walking, cycling, or light resistance training).");
        model.setDailyPlanSleep("7 to 9 hours of uninterrupted restful sleep. Turn off screens 30 minutes before bed.");

        // 11. Overall Health Indicator (Rule-based transparent scoring)
        int concernFlags = 0;
        List<String> reasons = new ArrayList<>();

        if (bmi >= 30.0 || bmi < 18.5) {
            concernFlags += 2;
            reasons.add("BMI is outside the general healthy reference range (" + bmiCategory + ")");
        } else if (bmi >= 25.0) {
            concernFlags += 1;
            reasons.add("BMI indicates overweight category");
        }

        if (systolicBp >= 140 || diastolicBp >= 90) {
            concernFlags += 2;
            reasons.add("Blood pressure is significantly elevated");
        } else if (systolicBp >= 130 || diastolicBp >= 80) {
            concernFlags += 1;
            reasons.add("Blood pressure is above the usual reference range");
        }

        if (fastingBloodSugar >= 126) {
            concernFlags += 2;
            reasons.add("Fasting blood sugar is in the high range");
        } else if (fastingBloodSugar >= 100) {
            concernFlags += 1;
            reasons.add("Fasting blood sugar is in the elevated range");
        }

        if ("Sedentary".equalsIgnoreCase(activityLevel)) {
            concernFlags += 1;
            reasons.add("Activity level is sedentary");
        }

        if (!"None".equalsIgnoreCase(existingCondition) && !existingCondition.trim().isEmpty()) {
            concernFlags += 1;
            reasons.add("Existing condition noted: " + existingCondition);
        }

        String overallIndicator;
        if (concernFlags >= 4) {
            overallIndicator = "HIGHER CONCERN";
        } else if (concernFlags >= 2) {
            overallIndicator = "MODERATE CONCERN";
        } else {
            overallIndicator = "LOWER CONCERN";
        }
        model.setOverallIndicator(overallIndicator);

        if (reasons.isEmpty()) {
            model.setIndicatorReason("All entered parameters are within standard general reference ranges. Maintain consistent healthy routines.");
        } else {
            StringBuilder sb = new StringBuilder("Factors considered: ");
            for (int i = 0; i < reasons.size(); i++) {
                sb.append(reasons.get(i));
                if (i < reasons.size() - 1) sb.append("; ");
            }
            sb.append(". Consider monitoring these parameters and discussing repeated out-of-range values with a healthcare professional.");
            model.setIndicatorReason(sb.toString());
        }

        return model;
    }
}
