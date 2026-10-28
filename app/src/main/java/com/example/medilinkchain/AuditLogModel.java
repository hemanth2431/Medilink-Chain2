package com.example.medilinkchain;

import java.io.Serializable;
import java.util.Date;

/**
 * AuditLogModel — Represents an immutable record in the audit_logs table.
 */
public class AuditLogModel implements Serializable {
    private String id;
    private String organizationId;
    private String actorId;
    private String actorEmail;
    private String actorRole;
    private String action;
    private String targetType;
    private String targetId;
    private String description;
    private Date createdAt;

    public AuditLogModel() {
        this.createdAt = new Date();
    }

    public AuditLogModel(String id, String organizationId, String actorId, String actorEmail,
                         String actorRole, String action, String targetType, String targetId,
                         String description, Date createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.description = description;
        this.createdAt = createdAt != null ? createdAt : new Date();
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

    public String getActorId() {
        return actorId != null ? actorId : "";
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorEmail() {
        return actorEmail != null ? actorEmail : "system@medilinkchain.org";
    }

    public void setActorEmail(String actorEmail) {
        this.actorEmail = actorEmail;
    }

    public String getActorRole() {
        return actorRole != null ? actorRole : "ADMIN";
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getAction() {
        return action != null ? action : "UNKNOWN_ACTION";
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType != null ? targetType : "";
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId != null ? targetId : "";
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
