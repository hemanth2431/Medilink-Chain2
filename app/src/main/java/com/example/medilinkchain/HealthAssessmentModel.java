package com.example.medilinkchain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * HealthAssessmentModel — Represents a complete, transparent, rule-based health assessment
 * including calculated metrics, dietary recommendations, and safety evaluations.
 */
public class HealthAssessmentModel implements Serializable {

    private String id;
    private String userId;
    private String patientName;
    private String assessmentDate;
    private int age;
    private String gender;
    private double heightCm;
    private double weightKg;
    private double bmi;
    private String bmiCategory;
    private String healthyWeightRange;
    private String activityLevel;
    private int systolicBp;
    private int diastolicBp;
    private String bpCategory;
    private int fastingBloodSugar;
    private int postMealBloodSugar;
    private String bloodSugarCategory;
    private String existingCondition;
    private String foodPreference;
    private String healthGoal;
    private int bmr;
    private int maintenanceCalories;
    private int goalCalories;
    private String overallIndicator; // "LOWER CONCERN", "MODERATE CONCERN", "HIGHER CONCERN"
    private String indicatorReason;
    private String weightGuidance;
    private String activityAnalysis;
    private List<String> recommendedFoods = new ArrayList<>();
    private List<String> foodsToLimit = new ArrayList<>();
    private List<String> goalGuidance = new ArrayList<>();
    private String dailyPlanBreakfast;
    private String dailyPlanLunch;
    private String dailyPlanSnack;
    private String dailyPlanDinner;
    private String dailyPlanHydration;
    private String dailyPlanActivity;
    private String dailyPlanSleep;
    private String createdAt;

    public HealthAssessmentModel() {}

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(String assessmentDate) { this.assessmentDate = assessmentDate; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public double getHeightCm() { return heightCm; }
    public void setHeightCm(double heightCm) { this.heightCm = heightCm; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public double getBmi() { return bmi; }
    public void setBmi(double bmi) { this.bmi = bmi; }

    public String getBmiCategory() { return bmiCategory; }
    public void setBmiCategory(String bmiCategory) { this.bmiCategory = bmiCategory; }

    public String getHealthyWeightRange() { return healthyWeightRange; }
    public void setHealthyWeightRange(String healthyWeightRange) { this.healthyWeightRange = healthyWeightRange; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }

    public int getSystolicBp() { return systolicBp; }
    public void setSystolicBp(int systolicBp) { this.systolicBp = systolicBp; }

    public int getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(int diastolicBp) { this.diastolicBp = diastolicBp; }

    public String getBpCategory() { return bpCategory; }
    public void setBpCategory(String bpCategory) { this.bpCategory = bpCategory; }

    public int getFastingBloodSugar() { return fastingBloodSugar; }
    public void setFastingBloodSugar(int fastingBloodSugar) { this.fastingBloodSugar = fastingBloodSugar; }

    public int getPostMealBloodSugar() { return postMealBloodSugar; }
    public void setPostMealBloodSugar(int postMealBloodSugar) { this.postMealBloodSugar = postMealBloodSugar; }

    public String getBloodSugarCategory() { return bloodSugarCategory; }
    public void setBloodSugarCategory(String bloodSugarCategory) { this.bloodSugarCategory = bloodSugarCategory; }

    public String getExistingCondition() { return existingCondition; }
    public void setExistingCondition(String existingCondition) { this.existingCondition = existingCondition; }

    public String getFoodPreference() { return foodPreference; }
    public void setFoodPreference(String foodPreference) { this.foodPreference = foodPreference; }

    public String getHealthGoal() { return healthGoal; }
    public void setHealthGoal(String healthGoal) { this.healthGoal = healthGoal; }

    public int getBmr() { return bmr; }
    public void setBmr(int bmr) { this.bmr = bmr; }

    public int getMaintenanceCalories() { return maintenanceCalories; }
    public void setMaintenanceCalories(int maintenanceCalories) { this.maintenanceCalories = maintenanceCalories; }

    public int getGoalCalories() { return goalCalories; }
    public void setGoalCalories(int goalCalories) { this.goalCalories = goalCalories; }

    public String getOverallIndicator() { return overallIndicator; }
    public void setOverallIndicator(String overallIndicator) { this.overallIndicator = overallIndicator; }

    public String getIndicatorReason() { return indicatorReason; }
    public void setIndicatorReason(String indicatorReason) { this.indicatorReason = indicatorReason; }

    public String getWeightGuidance() { return weightGuidance; }
    public void setWeightGuidance(String weightGuidance) { this.weightGuidance = weightGuidance; }

    public String getActivityAnalysis() { return activityAnalysis; }
    public void setActivityAnalysis(String activityAnalysis) { this.activityAnalysis = activityAnalysis; }

    public List<String> getRecommendedFoods() { return recommendedFoods; }
    public void setRecommendedFoods(List<String> recommendedFoods) { this.recommendedFoods = recommendedFoods; }

    public List<String> getFoodsToLimit() { return foodsToLimit; }
    public void setFoodsToLimit(List<String> foodsToLimit) { this.foodsToLimit = foodsToLimit; }

    public List<String> getGoalGuidance() { return goalGuidance; }
    public void setGoalGuidance(List<String> goalGuidance) { this.goalGuidance = goalGuidance; }

    public String getDailyPlanBreakfast() { return dailyPlanBreakfast; }
    public void setDailyPlanBreakfast(String dailyPlanBreakfast) { this.dailyPlanBreakfast = dailyPlanBreakfast; }

    public String getDailyPlanLunch() { return dailyPlanLunch; }
    public void setDailyPlanLunch(String dailyPlanLunch) { this.dailyPlanLunch = dailyPlanLunch; }

    public String getDailyPlanSnack() { return dailyPlanSnack; }
    public void setDailyPlanSnack(String dailyPlanSnack) { this.dailyPlanSnack = dailyPlanSnack; }

    public String getDailyPlanDinner() { return dailyPlanDinner; }
    public void setDailyPlanDinner(String dailyPlanDinner) { this.dailyPlanDinner = dailyPlanDinner; }

    public String getDailyPlanHydration() { return dailyPlanHydration; }
    public void setDailyPlanHydration(String dailyPlanHydration) { this.dailyPlanHydration = dailyPlanHydration; }

    public String getDailyPlanActivity() { return dailyPlanActivity; }
    public void setDailyPlanActivity(String dailyPlanActivity) { this.dailyPlanActivity = dailyPlanActivity; }

    public String getDailyPlanSleep() { return dailyPlanSleep; }
    public void setDailyPlanSleep(String dailyPlanSleep) { this.dailyPlanSleep = dailyPlanSleep; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            if (id != null) json.put("id", id);
            json.put("user_id", userId);
            json.put("assessment_date", assessmentDate != null ? assessmentDate : "");
            json.put("age", age);
            json.put("gender", gender != null ? gender : "");
            json.put("height_cm", heightCm);
            json.put("weight_kg", weightKg);
            json.put("bmi", bmi);
            json.put("bmi_category", bmiCategory != null ? bmiCategory : "");
            json.put("activity_level", activityLevel != null ? activityLevel : "");
            json.put("systolic_bp", systolicBp);
            json.put("diastolic_bp", diastolicBp);
            json.put("fasting_blood_sugar", fastingBloodSugar);
            json.put("post_meal_blood_sugar", postMealBloodSugar);
            json.put("existing_condition", existingCondition != null ? existingCondition : "None");
            json.put("food_preference", foodPreference != null ? foodPreference : "Vegetarian");
            json.put("health_goal", healthGoal != null ? healthGoal : "Maintain Weight");
            json.put("maintenance_calories", maintenanceCalories);
            json.put("goal_calories", goalCalories);
            json.put("overall_indicator", overallIndicator != null ? overallIndicator : "LOWER CONCERN");
            if (createdAt != null) json.put("created_at", createdAt);
        } catch (Exception ignored) {}
        return json;
    }

    public static HealthAssessmentModel fromJsonObject(JSONObject json) {
        HealthAssessmentModel m = new HealthAssessmentModel();
        m.setId(json.optString("id"));
        m.setUserId(json.optString("user_id"));
        m.setAssessmentDate(json.optString("assessment_date"));
        m.setAge(json.optInt("age", 25));
        m.setGender(json.optString("gender", "Male"));
        m.setHeightCm(json.optDouble("height_cm", 170.0));
        m.setWeightKg(json.optDouble("weight_kg", 70.0));
        m.setBmi(json.optDouble("bmi", 24.2));
        m.setBmiCategory(json.optString("bmi_category", "Normal Weight"));
        m.setActivityLevel(json.optString("activity_level", "Moderately Active"));
        m.setSystolicBp(json.optInt("systolic_bp", 120));
        m.setDiastolicBp(json.optInt("diastolic_bp", 80));
        m.setFastingBloodSugar(json.optInt("fasting_blood_sugar", 90));
        m.setPostMealBloodSugar(json.optInt("post_meal_blood_sugar", 0));
        m.setExistingCondition(json.optString("existing_condition", "None"));
        m.setFoodPreference(json.optString("food_preference", "Vegetarian"));
        m.setHealthGoal(json.optString("health_goal", "Maintain Weight"));
        m.setMaintenanceCalories(json.optInt("maintenance_calories", 2000));
        m.setGoalCalories(json.optInt("goal_calories", 2000));
        m.setOverallIndicator(json.optString("overall_indicator", "LOWER CONCERN"));
        m.setCreatedAt(json.optString("created_at"));
        return m;
    }
}
