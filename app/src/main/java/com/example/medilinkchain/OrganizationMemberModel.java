package com.example.medilinkchain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * OrganizationMemberModel — Represents membership of a user in an organization.
 * Roles: SUPER_ADMIN, HOSPITAL_ADMIN, ADMIN, DOCTOR, PATIENT
 * Statuses: ACTIVE, DISABLED, PENDING
 */
public class OrganizationMemberModel implements Serializable {
    private String id;
    private String organizationId;
    private String userId;
    private String role; // SUPER_ADMIN, HOSPITAL_ADMIN, ADMIN, DOCTOR, PATIENT
    private String status; // ACTIVE, DISABLED, PENDING
    private boolean isPrimaryAdmin;
    private String hospitalCode;
    private String hospitalName;
    private Date createdAt;
    private Date updatedAt;
    private String createdBy;
    private String email;
    private String fullName;
    private String phone;
    private String adminId;
    private List<String> permissions;

    public OrganizationMemberModel() {
        this.permissions = new ArrayList<>();
        this.status = "ACTIVE";
        this.createdAt = new Date();
    }

    public OrganizationMemberModel(String id, String organizationId, String userId, String role, String status, Date createdAt, String createdBy) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.role = role != null ? role.toUpperCase() : "PATIENT";
        this.status = status != null ? status.toUpperCase() : "ACTIVE";
        this.createdAt = createdAt != null ? createdAt : new Date();
        this.createdBy = createdBy;
        this.permissions = new ArrayList<>();
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrganizationId() {
        return organizationId != null ? organizationId : "";
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getUserId() {
        return userId != null ? userId : "";
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role != null ? role.toUpperCase() : "PATIENT";
    }

    public void setRole(String role) {
        this.role = role != null ? role.toUpperCase() : "PATIENT";
    }

    public String getStatus() {
        return status != null ? status.toUpperCase() : "ACTIVE";
    }

    public void setStatus(String status) {
        this.status = status != null ? status.toUpperCase() : "ACTIVE";
    }

    public boolean isPrimaryAdmin() {
        return isPrimaryAdmin;
    }

    public void setPrimaryAdmin(boolean primaryAdmin) {
        isPrimaryAdmin = primaryAdmin;
    }

    public String getHospitalCode() {
        return hospitalCode != null ? hospitalCode : "";
    }

    public void setHospitalCode(String hospitalCode) {
        this.hospitalCode = hospitalCode;
    }

    public String getHospitalName() {
        return hospitalName != null ? hospitalName : "";
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy != null ? createdBy : "";
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName != null ? fullName : "";
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAdminId() {
        return adminId != null ? adminId : "";
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public List<String> getPermissions() {
        if (permissions == null) permissions = new ArrayList<>();
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public boolean isSuperAdmin() {
        return RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(role);
    }

    public boolean isHospitalAdmin() {
        return RolePermissionManager.ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(role);
    }

    public boolean isAdmin() {
        return isSuperAdmin() || isHospitalAdmin() || RolePermissionManager.ROLE_ADMIN.equalsIgnoreCase(role);
    }

    public boolean isDoctor() {
        return RolePermissionManager.ROLE_DOCTOR.equalsIgnoreCase(role);
    }

    public boolean isPatient() {
        return RolePermissionManager.ROLE_PATIENT.equalsIgnoreCase(role);
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean hasPermission(String permission) {
        if (isSuperAdmin()) return true;
        return permissions != null && permissions.contains(permission);
    }
}
