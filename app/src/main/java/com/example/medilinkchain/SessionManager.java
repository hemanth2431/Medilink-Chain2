package com.example.medilinkchain;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SessionManager — Manages user session state, cached roles, hospital metadata, and organization status.
 */
public class SessionManager {
    private static final String PREF_NAME = "MediLinkSession";
    private static final String KEY_IS_LOGGED_IN = "IsLoggedIn";
    private static final String KEY_USER_ROLE = "UserRole";
    private static final String KEY_USER_EMAIL = "UserEmail";
    private static final String KEY_USER_NAME = "UserName";
    private static final String KEY_ORG_ID = "OrgId";
    private static final String KEY_HOSPITAL_NAME = "HospitalName";
    private static final String KEY_HOSPITAL_CODE = "HospitalCode";
    private static final String KEY_IS_PRIMARY_ADMIN = "IsPrimaryAdmin";
    private static final String KEY_ACCOUNT_STATUS = "AccountStatus";
    private static final String KEY_VERIFICATION_STATUS = "VerificationStatus";
    private static final String KEY_REGISTRATION_ID = "RegistrationId";
    private static final String KEY_SUPABASE_TOKEN = "SupabaseToken";
    private static final String KEY_SUPABASE_USER_ID = "SupabaseUserId";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;
    private final Context context;

    public SessionManager(Context context) {
        this.context = context;
        this.pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.editor = pref.edit();
    }

    public void createLoginSession(String email, String role) {
        createLoginSession(email, role, OrganizationRepository.DEFAULT_ORG_ID, "ACTIVE", "APPROVED");
    }

    public void createLoginSession(String email, String role, String orgId, String accountStatus, String verificationStatus) {
        createLoginSession(email, "", role, orgId, "", "", false, "", accountStatus, verificationStatus);
    }

    public void createLoginSession(
            String email,
            String fullName,
            String role,
            String orgId,
            String hospitalName,
            String hospitalCode,
            boolean isPrimaryAdmin,
            String registrationId,
            String accountStatus,
            String verificationStatus) {

        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_EMAIL, email != null ? email : "");
        editor.putString(KEY_USER_NAME, fullName != null ? fullName : "");
        editor.putString(KEY_USER_ROLE, role != null ? role.toUpperCase() : "PATIENT");
        editor.putString(KEY_ORG_ID, orgId != null ? orgId : OrganizationRepository.DEFAULT_ORG_ID);
        editor.putString(KEY_HOSPITAL_NAME, hospitalName != null ? hospitalName : "");
        editor.putString(KEY_HOSPITAL_CODE, hospitalCode != null ? hospitalCode : "");
        editor.putBoolean(KEY_IS_PRIMARY_ADMIN, isPrimaryAdmin);
        editor.putString(KEY_REGISTRATION_ID, registrationId != null ? registrationId : "");
        editor.putString(KEY_ACCOUNT_STATUS, accountStatus != null ? accountStatus.toUpperCase() : "ACTIVE");
        editor.putString(KEY_VERIFICATION_STATUS, verificationStatus != null ? verificationStatus.toUpperCase() : "APPROVED");
        editor.apply();
    }

    public void saveHospitalDetails(String hospitalName, String hospitalCode, boolean isPrimaryAdmin) {
        editor.putString(KEY_HOSPITAL_NAME, hospitalName != null ? hospitalName : "");
        editor.putString(KEY_HOSPITAL_CODE, hospitalCode != null ? hospitalCode : "");
        editor.putBoolean(KEY_IS_PRIMARY_ADMIN, isPrimaryAdmin);
        editor.apply();
    }

    public void setRegistrationId(String registrationId) {
        editor.putString(KEY_REGISTRATION_ID, registrationId != null ? registrationId : "");
        editor.apply();
    }

    public void setUserName(String userName) {
        editor.putString(KEY_USER_NAME, userName != null ? userName : "");
        editor.apply();
    }

    public void saveSupabaseSession(String token, String userId) {
        editor.putString(KEY_SUPABASE_TOKEN, token != null ? token : "");
        editor.putString(KEY_SUPABASE_USER_ID, userId != null ? userId : "");
        editor.apply();
    }

    public String getSupabaseToken() {
        return pref.getString(KEY_SUPABASE_TOKEN, "");
    }

    public String getSupabaseUserId() {
        return pref.getString(KEY_SUPABASE_USER_ID, "");
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserRole() {
        return pref.getString(KEY_USER_ROLE, "PATIENT").toUpperCase();
    }

    public String getUserEmail() {
        return pref.getString(KEY_USER_EMAIL, "");
    }

    public String getUserName() {
        return pref.getString(KEY_USER_NAME, "");
    }

    public String getOrgId() {
        return pref.getString(KEY_ORG_ID, OrganizationRepository.DEFAULT_ORG_ID);
    }

    public String getHospitalName() {
        return pref.getString(KEY_HOSPITAL_NAME, "");
    }

    public String getHospitalCode() {
        return pref.getString(KEY_HOSPITAL_CODE, "");
    }

    public boolean isPrimaryAdmin() {
        return pref.getBoolean(KEY_IS_PRIMARY_ADMIN, false);
    }

    public String getRegistrationId() {
        return pref.getString(KEY_REGISTRATION_ID, "");
    }

    public String getAccountStatus() {
        return pref.getString(KEY_ACCOUNT_STATUS, "ACTIVE").toUpperCase();
    }

    public String getVerificationStatus() {
        return pref.getString(KEY_VERIFICATION_STATUS, "APPROVED").toUpperCase();
    }

    public boolean isSuperAdmin() {
        return RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(getUserRole());
    }

    public boolean isHospitalAdmin() {
        return RolePermissionManager.ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(getUserRole())
                || (RolePermissionManager.ROLE_ADMIN.equalsIgnoreCase(getUserRole()) && !isSuperAdmin());
    }

    public boolean isAdmin() {
        String r = getUserRole();
        return RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(r)
                || RolePermissionManager.ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(r)
                || RolePermissionManager.ROLE_ADMIN.equalsIgnoreCase(r);
    }

    public boolean isDoctor() {
        return RolePermissionManager.ROLE_DOCTOR.equalsIgnoreCase(getUserRole());
    }

    public boolean isPatient() {
        return RolePermissionManager.ROLE_PATIENT.equalsIgnoreCase(getUserRole());
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(getAccountStatus());
    }

    public void logoutUser() {
        editor.clear();
        editor.apply();
    }
}
