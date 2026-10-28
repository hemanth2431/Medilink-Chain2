package com.example.medilinkchain;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RolePermissionManager — Central authorization rules, roles, and granular permission constants.
 */
public final class RolePermissionManager {

    private RolePermissionManager() {}

    // Roles
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_HOSPITAL_ADMIN = "HOSPITAL_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_DOCTOR = "DOCTOR";
    public static final String ROLE_PATIENT = "PATIENT";

    // Granular Permissions
    public static final String PERM_HOSPITAL_CREATE = "HOSPITAL_CREATE";
    public static final String PERM_HOSPITAL_MANAGE = "HOSPITAL_MANAGE";
    public static final String PERM_HOSPITAL_ADMIN_ASSIGN = "HOSPITAL_ADMIN_ASSIGN";

    public static final String PERM_ADMIN_CREATE = "ADMIN_CREATE";
    public static final String PERM_ADMIN_DISABLE = "ADMIN_DISABLE";
    public static final String PERM_ADMIN_ENABLE = "ADMIN_ENABLE";
    public static final String PERM_ADMIN_REMOVE = "ADMIN_REMOVE";

    public static final String PERM_DOCTOR_INVITE = "DOCTOR_INVITE";
    public static final String PERM_DOCTOR_VIEW = "DOCTOR_VIEW";
    public static final String PERM_DOCTOR_APPROVE = "DOCTOR_APPROVE";
    public static final String PERM_DOCTOR_REJECT = "DOCTOR_REJECT";
    public static final String PERM_DOCTOR_DISABLE = "DOCTOR_DISABLE";
    public static final String PERM_DOCTOR_ENABLE = "DOCTOR_ENABLE";

    public static final String PERM_PATIENT_VIEW = "PATIENT_VIEW";
    public static final String PERM_PATIENT_ENABLE = "PATIENT_ENABLE";
    public static final String PERM_PATIENT_DISABLE = "PATIENT_DISABLE";

    public static final String PERM_SYSTEM_VIEW = "SYSTEM_VIEW";
    public static final String PERM_AUDIT_VIEW = "AUDIT_VIEW";
    public static final String PERM_ORG_SETTINGS_MANAGE = "ORG_SETTINGS_MANAGE";

    // All available permissions for Super Admin
    public static final List<String> ALL_PERMISSIONS = Collections.unmodifiableList(Arrays.asList(
            PERM_HOSPITAL_CREATE,
            PERM_HOSPITAL_MANAGE,
            PERM_HOSPITAL_ADMIN_ASSIGN,
            PERM_ADMIN_CREATE,
            PERM_ADMIN_DISABLE,
            PERM_ADMIN_ENABLE,
            PERM_ADMIN_REMOVE,
            PERM_DOCTOR_INVITE,
            PERM_DOCTOR_VIEW,
            PERM_DOCTOR_APPROVE,
            PERM_DOCTOR_REJECT,
            PERM_DOCTOR_DISABLE,
            PERM_DOCTOR_ENABLE,
            PERM_PATIENT_VIEW,
            PERM_PATIENT_ENABLE,
            PERM_PATIENT_DISABLE,
            PERM_SYSTEM_VIEW,
            PERM_AUDIT_VIEW,
            PERM_ORG_SETTINGS_MANAGE
    ));

    // Default permissions granted to Hospital Admin
    public static final List<String> DEFAULT_HOSPITAL_ADMIN_PERMISSIONS = Collections.unmodifiableList(Arrays.asList(
            PERM_DOCTOR_INVITE,
            PERM_DOCTOR_VIEW,
            PERM_DOCTOR_APPROVE,
            PERM_DOCTOR_REJECT,
            PERM_DOCTOR_DISABLE,
            PERM_DOCTOR_ENABLE,
            PERM_PATIENT_VIEW,
            PERM_PATIENT_ENABLE,
            PERM_PATIENT_DISABLE,
            PERM_SYSTEM_VIEW,
            PERM_AUDIT_VIEW
    ));

    // Default permissions granted to standard legacy Admin
    public static final List<String> DEFAULT_ADMIN_PERMISSIONS = DEFAULT_HOSPITAL_ADMIN_PERMISSIONS;

    /**
     * Checks if a user has a specific permission.
     */
    public static boolean hasPermission(String role, List<String> grantedPermissions, String targetPermission) {
        if (ROLE_SUPER_ADMIN.equalsIgnoreCase(role)) {
            return true;
        }
        if (ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(role) || ROLE_ADMIN.equalsIgnoreCase(role)) {
            if (grantedPermissions != null && grantedPermissions.contains(targetPermission)) {
                return true;
            }
            return DEFAULT_HOSPITAL_ADMIN_PERMISSIONS.contains(targetPermission);
        }
        return false;
    }

    /**
     * Validates if a role is permitted to perform Hospital Management operations.
     */
    public static boolean canManageHospitals(String role) {
        return ROLE_SUPER_ADMIN.equalsIgnoreCase(role);
    }

    /**
     * Validates if a role is permitted to perform Admin Management operations.
     */
    public static boolean canManageAdmins(String role) {
        return ROLE_SUPER_ADMIN.equalsIgnoreCase(role);
    }

    /**
     * Validates if a role is permitted to modify organization settings.
     */
    public static boolean canManageOrganization(String role) {
        return ROLE_SUPER_ADMIN.equalsIgnoreCase(role);
    }

    /**
     * Helper to check if role is an administrative role.
     */
    public static boolean isAdministrativeRole(String role) {
        return ROLE_SUPER_ADMIN.equalsIgnoreCase(role)
                || ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(role)
                || ROLE_ADMIN.equalsIgnoreCase(role);
    }
}
