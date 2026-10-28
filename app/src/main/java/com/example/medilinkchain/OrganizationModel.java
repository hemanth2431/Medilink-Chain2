package com.example.medilinkchain;

import java.io.Serializable;
import java.util.Date;

/**
 * OrganizationModel — Represents a Hospital / Healthcare Organization in MediLinkChain.
 */
public class OrganizationModel implements Serializable {
    private String id;
    private String code; // e.g. ABC, XYZ, HYD01
    private String organizationName;
    private String name;
    private String address;
    private String contact;
    private String email;
    private String status; // ACTIVE, DISABLED
    private String primaryAdminId;
    private String primaryAdminName;
    private String primaryAdminEmail;
    private String primaryAdminPhone;
    private Date createdAt;
    private Date updatedAt;

    public OrganizationModel() {
        this.status = "ACTIVE";
        this.createdAt = new Date();
    }

    public OrganizationModel(String id, String organizationName, String status, Date createdAt) {
        this.id = id;
        this.organizationName = organizationName;
        this.name = organizationName;
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = createdAt != null ? createdAt : new Date();
    }

    public OrganizationModel(String id, String code, String name, String address, String contact, String email, String status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.organizationName = name;
        this.address = address;
        this.contact = contact;
        this.email = email;
        this.status = status != null ? status : "ACTIVE";
        this.createdAt = new Date();
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code != null ? code.toUpperCase() : "";
    }

    public void setCode(String code) {
        this.code = code != null ? code.toUpperCase() : "";
    }

    public String getOrganizationName() {
        if (organizationName != null && !organizationName.isEmpty()) return organizationName;
        if (name != null && !name.isEmpty()) return name;
        return "MediLinkChain Organization";
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
        if (this.name == null) this.name = organizationName;
    }

    public String getName() {
        return getOrganizationName();
    }

    public void setName(String name) {
        this.name = name;
        this.organizationName = name;
    }

    public String getAddress() {
        return address != null ? address : "";
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContact() {
        return contact != null ? contact : "";
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status != null ? status : "ACTIVE";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPrimaryAdminId() {
        return primaryAdminId != null ? primaryAdminId : "";
    }

    public void setPrimaryAdminId(String primaryAdminId) {
        this.primaryAdminId = primaryAdminId;
    }

    public String getPrimaryAdminName() {
        return primaryAdminName != null ? primaryAdminName : "Not Assigned";
    }

    public void setPrimaryAdminName(String primaryAdminName) {
        this.primaryAdminName = primaryAdminName;
    }

    public String getPrimaryAdminEmail() {
        return primaryAdminEmail != null ? primaryAdminEmail : "";
    }

    public void setPrimaryAdminEmail(String primaryAdminEmail) {
        this.primaryAdminEmail = primaryAdminEmail;
    }

    public String getPrimaryAdminPhone() {
        return primaryAdminPhone != null ? primaryAdminPhone : "";
    }

    public void setPrimaryAdminPhone(String primaryAdminPhone) {
        this.primaryAdminPhone = primaryAdminPhone;
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

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
