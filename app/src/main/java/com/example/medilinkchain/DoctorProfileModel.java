package com.example.medilinkchain;

import java.io.Serializable;
import java.util.Date;

/**
 * DoctorProfileModel — Represents a doctor's profile and verification state in MediLinkChain.
 * verificationStatus: PENDING, APPROVED, REJECTED
 * accountStatus / status: ACTIVE, DISABLED, PENDING, REJECTED
 */
public class DoctorProfileModel implements Serializable {
    private String userId;
    private String organizationId;
    private String fullName;
    private String email;
    private String phone;
    private String doctorRegistrationId;
    private String hospitalName;
    private String hospitalCode;
    private String specialization;
    private String medicalRegistrationNumber;
    private String verificationStatus; // PENDING, APPROVED, REJECTED
    private String status; // ACTIVE, DISABLED, PENDING, REJECTED
    private String accountStatus; // ACTIVE, DISABLED, PENDING, REJECTED
    private String rejectionReason;
    private Date submittedAt;
    private Date verifiedAt;
    private String verifiedBy;

    public DoctorProfileModel() {
        this.verificationStatus = "PENDING";
        this.status = "PENDING";
        this.accountStatus = "PENDING";
        this.submittedAt = new Date();
        this.specialization = "General Physician";
    }

    public DoctorProfileModel(String userId, String email, String doctorRegistrationId, String hospitalName) {
        this.userId = userId;
        this.email = email;
        this.doctorRegistrationId = doctorRegistrationId;
        this.hospitalName = hospitalName;
        this.verificationStatus = "PENDING";
        this.status = "PENDING";
        this.accountStatus = "PENDING";
        this.submittedAt = new Date();
        this.specialization = "General Physician";
    }

    public String getUserId() {
        return userId != null ? userId : "";
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOrganizationId() {
        return organizationId != null ? organizationId : "";
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getFullName() {
        if (fullName != null && !fullName.isEmpty()) return fullName;
        if (email != null && email.contains("@")) {
            String prefix = email.split("@")[0];
            return "Dr. " + prefix.substring(0, 1).toUpperCase() + prefix.substring(1);
        }
        return "Doctor";
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getName() {
        return getFullName();
    }

    public void setName(String name) {
        this.fullName = name;
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDoctorRegistrationId() {
        return doctorRegistrationId != null ? doctorRegistrationId : "";
    }

    public void setDoctorRegistrationId(String doctorRegistrationId) {
        this.doctorRegistrationId = doctorRegistrationId;
    }

    public String getHospitalName() {
        return hospitalName != null ? hospitalName : "General Hospital";
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getHospitalCode() {
        return hospitalCode != null ? hospitalCode : "";
    }

    public void setHospitalCode(String hospitalCode) {
        this.hospitalCode = hospitalCode;
    }

    public String getSpecialization() {
        return specialization != null && !specialization.isEmpty() ? specialization : "General Medicine";
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getMedicalRegistrationNumber() {
        return medicalRegistrationNumber != null ? medicalRegistrationNumber : "";
    }

    public void setMedicalRegistrationNumber(String medicalRegistrationNumber) {
        this.medicalRegistrationNumber = medicalRegistrationNumber;
    }

    public String getVerificationStatus() {
        return verificationStatus != null ? verificationStatus.toUpperCase() : "PENDING";
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus != null ? verificationStatus.toUpperCase() : "PENDING";
    }

    public String getStatus() {
        return status != null ? status.toUpperCase() : "PENDING";
    }

    public void setStatus(String status) {
        this.status = status != null ? status.toUpperCase() : "PENDING";
        this.accountStatus = this.status;
    }

    public String getAccountStatus() {
        return accountStatus != null ? accountStatus.toUpperCase() : getStatus();
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus != null ? accountStatus.toUpperCase() : "PENDING";
        this.status = this.accountStatus;
    }

    public String getRejectionReason() {
        return rejectionReason != null ? rejectionReason : "";
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Date getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Date getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Date verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getVerifiedBy() {
        return verifiedBy != null ? verifiedBy : "";
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(verificationStatus);
    }

    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(verificationStatus);
    }

    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(verificationStatus);
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(getStatus());
    }

    public boolean isDisabled() {
        return "DISABLED".equalsIgnoreCase(getStatus());
    }
}
