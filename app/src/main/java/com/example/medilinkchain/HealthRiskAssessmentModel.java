package com.example.medilinkchain;

import java.io.Serializable;

/**
 * Model representing a user's Health Risk Assessment entry.
 */
public class HealthRiskAssessmentModel implements Serializable {

    private String id;
    private String userId;
    private int age;
    private String gender;
    private double heightCm;
    private double weightKg;
    private double bmi;
    private String bmiCategory;
    private int systolicBp;
    private int diastolicBp;
    private String bpStatus;
    private int fastingGlucose;
    private String glucoseStatus;
    private String smoking;
    private String alcohol;
    private String physicalActivity;
    private String familyHistory;
    private int riskScore;
    private String riskCategory;
    private String createdAt;

    public HealthRiskAssessmentModel() {
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

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

    public int getSystolicBp() { return systolicBp; }
    public void setSystolicBp(int systolicBp) { this.systolicBp = systolicBp; }

    public int getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(int diastolicBp) { this.diastolicBp = diastolicBp; }

    public String getBpStatus() { return bpStatus; }
    public void setBpStatus(String bpStatus) { this.bpStatus = bpStatus; }

    public int getFastingGlucose() { return fastingGlucose; }
    public void setFastingGlucose(int fastingGlucose) { this.fastingGlucose = fastingGlucose; }

    public String getGlucoseStatus() { return glucoseStatus; }
    public void setGlucoseStatus(String glucoseStatus) { this.glucoseStatus = glucoseStatus; }

    public String getSmoking() { return smoking; }
    public void setSmoking(String smoking) { this.smoking = smoking; }

    public String getAlcohol() { return alcohol; }
    public void setAlcohol(String alcohol) { this.alcohol = alcohol; }

    public String getPhysicalActivity() { return physicalActivity; }
    public void setPhysicalActivity(String physicalActivity) { this.physicalActivity = physicalActivity; }

    public String getFamilyHistory() { return familyHistory; }
    public void setFamilyHistory(String familyHistory) { this.familyHistory = familyHistory; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public String getRiskCategory() { return riskCategory; }
    public void setRiskCategory(String riskCategory) { this.riskCategory = riskCategory; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
