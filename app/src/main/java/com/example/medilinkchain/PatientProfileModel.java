package com.example.medilinkchain;

import java.io.Serializable;
import java.util.Date;

/**
 * PatientProfileModel — Represents a patient account and status in MediLinkChain.
 */
public class PatientProfileModel implements Serializable {
    private String userId;
    private String patientId;
    private String fullName;
    private String email;
    private String contactNumber;
    private String bloodGroup;
    private int age;
    private String gender;
    private String status; // ACTIVE, DISABLED
    private boolean profileComplete;
    private Date createdAt;
    private Date lastLogin;

    public PatientProfileModel() {
        this.status = "ACTIVE";
        this.createdAt = new Date();
    }

    public PatientProfileModel(String userId, String email, String fullName, String patientId) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.patientId = patientId;
        this.status = "ACTIVE";
        this.createdAt = new Date();
    }

    public String getUserId() {
        return userId != null ? userId : "";
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPatientId() {
        if (patientId != null && !patientId.isEmpty()) return patientId;
        if (userId != null && userId.length() >= 6) {
            return "MLC-" + userId.substring(0, Math.min(userId.length(), 6)).toUpperCase();
        }
        return "MLC-PATIENT";
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getFullName() {
        if (fullName != null && !fullName.isEmpty()) return fullName;
        if (email != null && email.contains("@")) {
            String prefix = email.split("@")[0];
            return prefix.substring(0, 1).toUpperCase() + prefix.substring(1);
        }
        return "Patient";
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContactNumber() {
        return contactNumber != null && !contactNumber.isEmpty() ? contactNumber : "--";
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getBloodGroup() {
        return bloodGroup != null ? bloodGroup : "--";
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getGender() {
        return gender != null ? gender : "--";
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getStatus() {
        return status != null ? status.toUpperCase() : "ACTIVE";
    }

    public void setStatus(String status) {
        this.status = status != null ? status.toUpperCase() : "ACTIVE";
    }

    public boolean isProfileComplete() {
        return profileComplete;
    }

    public void setProfileComplete(boolean profileComplete) {
        this.profileComplete = profileComplete;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(Date lastLogin) {
        this.lastLogin = lastLogin;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
