package com.example.medilinkchain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OrganizationRepository — Central repository for hierarchical organization,
 * Super Admin, Hospital Admin, Doctor invitations, and patient management.
 */
public class OrganizationRepository {

    private static final String TAG = "OrganizationRepo";
    public static final String DEFAULT_ORG_ID = "org_medilink_main";
    public static final String DEFAULT_ORG_NAME = "MediLinkChain Central Network";
    public static final String DEFAULT_ORG_CODE = "MAIN";

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final FirebaseFirestore DB = FirebaseFirestore.getInstance();

    // Callbacks
    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    public interface ActionCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static class SystemStats {
        public int totalHospitals;
        public int totalPatients;
        public int totalDoctors;
        public int pendingDoctors;
        public int approvedDoctors;
        public int rejectedDoctors;
        public int totalMedicalRecords;
        public int activeAdministrators;
        public int todayPatients;
        public int todayDoctors;
        public int todayRecords;
        public int adminActionsCount;
    }

    // =========================================================================
    // 1. ORGANIZATION MEMBERSHIP & ROLE LOOKUP
    // =========================================================================

    public static void getUserMembership(String userId, Callback<OrganizationMemberModel> callback) {
        if (userId == null || userId.isEmpty()) {
            postFailure(callback, "Invalid user ID");
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            String email = currentUser.getEmail();
            if (email != null && (
                    SuperAdminSetupHelper.SUPER_ADMIN_EMAIL.equalsIgnoreCase(email)
                    || "superadmin@medilinkchain.org".equalsIgnoreCase(email)
                    || "admin@medilinkchain.org".equalsIgnoreCase(email)
                    || "admin@medilink.com".equalsIgnoreCase(email)
            )) {
                OrganizationMemberModel saMember = new OrganizationMemberModel();
                saMember.setUserId(userId);
                saMember.setEmail(email);
                saMember.setFullName(SuperAdminSetupHelper.SUPER_ADMIN_NAME != null ? SuperAdminSetupHelper.SUPER_ADMIN_NAME : "Super Admin");
                saMember.setRole(RolePermissionManager.ROLE_SUPER_ADMIN);
                saMember.setPermissions(RolePermissionManager.ALL_PERMISSIONS);
                saMember.setStatus("ACTIVE");
                saMember.setOrganizationId(DEFAULT_ORG_ID);
                saMember.setHospitalName("MediLinkChain Global Network");
                saMember.setHospitalCode("SUPER");
                postSuccess(callback, saMember);
                return;
            }
        }

        EXECUTOR.execute(() -> {
            // First check organization_members collection in Firestore
            DB.collection("organization_members")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                            DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                            OrganizationMemberModel member = parseMemberDoc(doc);

                            // Load hospital details if organizationId is present
                            if (member.getOrganizationId() != null && !member.getOrganizationId().isEmpty()) {
                                DB.collection("organizations").document(member.getOrganizationId()).get()
                                        .addOnSuccessListener(orgDoc -> {
                                            if (orgDoc.exists()) {
                                                member.setHospitalName(orgDoc.getString("name") != null ? orgDoc.getString("name") : orgDoc.getString("organizationName"));
                                                member.setHospitalCode(orgDoc.getString("code"));
                                            }
                                            postSuccess(callback, member);
                                        })
                                        .addOnFailureListener(e -> postSuccess(callback, member));
                            } else {
                                postSuccess(callback, member);
                            }
                        } else {
                            // Fallback to users collection
                            DB.collection("users").document(userId).get()
                                    .addOnSuccessListener(userDoc -> {
                                        OrganizationMemberModel member = new OrganizationMemberModel();
                                        member.setUserId(userId);
                                        member.setOrganizationId(DEFAULT_ORG_ID);

                                        if (userDoc.exists()) {
                                            String role = userDoc.getString("role");
                                            String status = userDoc.getString("status");
                                            String email = userDoc.getString("email");
                                            String name = userDoc.getString("fullName");
                                            String orgId = userDoc.getString("organizationId");
                                            String hospName = userDoc.getString("hospitalName");
                                            String hospCode = userDoc.getString("hospitalCode");

                                            if (orgId != null && !orgId.isEmpty()) member.setOrganizationId(orgId);
                                            if (hospName != null) member.setHospitalName(hospName);
                                            if (hospCode != null) member.setHospitalCode(hospCode);

                                            member.setEmail(email != null ? email : "");
                                            member.setFullName(name != null ? name : "");

                                            if ("super_admin".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
                                                member.setRole(RolePermissionManager.ROLE_SUPER_ADMIN);
                                                member.setPermissions(RolePermissionManager.ALL_PERMISSIONS);
                                            } else if ("hospital_admin".equalsIgnoreCase(role) || "HOSPITAL_ADMIN".equalsIgnoreCase(role)) {
                                                member.setRole(RolePermissionManager.ROLE_HOSPITAL_ADMIN);
                                                member.setPrimaryAdmin(true);
                                                member.setPermissions(RolePermissionManager.DEFAULT_HOSPITAL_ADMIN_PERMISSIONS);
                                            } else if ("admin".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role)) {
                                                member.setRole(RolePermissionManager.ROLE_HOSPITAL_ADMIN);
                                                member.setPermissions(RolePermissionManager.DEFAULT_HOSPITAL_ADMIN_PERMISSIONS);
                                            } else if ("doctor".equalsIgnoreCase(role) || "DOCTOR".equalsIgnoreCase(role)) {
                                                member.setRole(RolePermissionManager.ROLE_DOCTOR);
                                            } else {
                                                member.setRole(RolePermissionManager.ROLE_PATIENT);
                                            }

                                            member.setStatus(status != null ? status : "ACTIVE");
                                        } else {
                                            member.setRole(RolePermissionManager.ROLE_PATIENT);
                                            member.setStatus("ACTIVE");
                                        }

                                        postSuccess(callback, member);
                                    })
                                    .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                        }
                    });
        });
    }

    // =========================================================================
    // 2. HOSPITAL / ORGANIZATION MANAGEMENT (SUPER ADMIN)
    // =========================================================================

    public static void listOrganizations(Callback<List<OrganizationModel>> callback) {
        DB.collection("organizations")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<OrganizationModel> list = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            OrganizationModel model = parseOrgDoc(doc);
                            list.add(model);
                        }
                    }
                    if (list.isEmpty()) {
                        // Ensure at least default organization exists
                        OrganizationModel defaultOrg = new OrganizationModel(DEFAULT_ORG_ID, DEFAULT_ORG_CODE, DEFAULT_ORG_NAME, "Central Healthcare Hub, Metro City", "+91-11-2658-8500", "admin@medilinkchain.org", "ACTIVE");
                        list.add(defaultOrg);
                    }
                    postSuccess(callback, list);
                })
                .addOnFailureListener(e -> {
                    List<OrganizationModel> list = new ArrayList<>();
                    OrganizationModel defaultOrg = new OrganizationModel(DEFAULT_ORG_ID, DEFAULT_ORG_CODE, DEFAULT_ORG_NAME, "Central Healthcare Hub, Metro City", "+91-11-2658-8500", "admin@medilinkchain.org", "ACTIVE");
                    list.add(defaultOrg);
                    postSuccess(callback, list);
                });
    }

    public static void getOrganization(String orgId, Callback<OrganizationModel> callback) {
        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        DB.collection("organizations").document(targetOrgId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        postSuccess(callback, parseOrgDoc(doc));
                    } else {
                        OrganizationModel model = new OrganizationModel(targetOrgId, DEFAULT_ORG_CODE, DEFAULT_ORG_NAME, "Healthcare Hub", "+91-11-2658-8500", "admin@medilinkchain.org", "ACTIVE");
                        postSuccess(callback, model);
                    }
                })
                .addOnFailureListener(e -> {
                    OrganizationModel model = new OrganizationModel(targetOrgId, DEFAULT_ORG_CODE, DEFAULT_ORG_NAME, "Healthcare Hub", "+91-11-2658-8500", "admin@medilinkchain.org", "ACTIVE");
                    postSuccess(callback, model);
                });
    }

    public static void updateOrganizationName(
            String orgId,
            String newName,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("organizationName", newName);
        updates.put("updatedAt", new Date());

        DB.collection("organizations").document(targetOrgId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    AuditLogManager.log(
                            targetOrgId,
                            actorId,
                            actorEmail,
                            "SUPER_ADMIN",
                            "ORGANIZATION_UPDATED",
                            "ORGANIZATION",
                            targetOrgId,
                            "Organization name updated to: " + newName
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void createOrganization(
            String name,
            String code,
            String address,
            String contact,
            String email,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        final String cleanCode = (code != null ? code.trim().toUpperCase() : "HOSP").replaceAll("[^A-Z0-9]", "");
        final String orgId = "org_" + cleanCode.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 6);

        // Check if code already exists
        DB.collection("organizations")
                .whereEqualTo("code", cleanCode)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        postFailure(callback, "A hospital with code '" + cleanCode + "' already exists. Please choose a unique hospital code.");
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("id", orgId);
                    data.put("code", cleanCode);
                    data.put("name", name);
                    data.put("organizationName", name);
                    data.put("address", address);
                    data.put("contact", contact);
                    data.put("email", email);
                    data.put("status", "ACTIVE");
                    data.put("createdAt", new Date());
                    data.put("updatedAt", new Date());
                    data.put("createdBy", actorId);

                    DB.collection("organizations").document(orgId).set(data)
                            .addOnSuccessListener(aVoid -> {
                                AuditLogManager.log(
                                        orgId,
                                        actorId,
                                        actorEmail,
                                        "SUPER_ADMIN",
                                        "HOSPITAL_CREATED",
                                        "ORGANIZATION",
                                        orgId,
                                        "New hospital created: " + name + " (Code: " + cleanCode + ")"
                                );
                                postSuccess(callback);
                            })
                            .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void updateOrganizationStatus(
            String orgId,
            String newStatus,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("updatedAt", new Date());

        DB.collection("organizations").document(orgId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    String action = "ACTIVE".equalsIgnoreCase(newStatus) ? "HOSPITAL_ACTIVATED" : "HOSPITAL_DISABLED";
                    AuditLogManager.log(
                            orgId,
                            actorId,
                            actorEmail,
                            "SUPER_ADMIN",
                            action,
                            "ORGANIZATION",
                            orgId,
                            "Hospital status updated to: " + newStatus
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    // =========================================================================
    // 3. ASSIGN PRIMARY HOSPITAL ADMIN (ONE PRIMARY ADMIN PER HOSPITAL)
    // =========================================================================

    // 3. ASSIGN PRIMARY HOSPITAL ADMIN (ONE PRIMARY ADMIN PER HOSPITAL)
    // =========================================================================

    // =========================================================================
    // 3. HOSPITAL ADMIN ASSIGNMENT & CREDENTIAL MANAGEMENT
    // =========================================================================

    public static void assignHospitalAdmin(
            Context context,
            String orgId,
            String adminId,
            String adminName,
            String password,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        if (orgId == null || orgId.isEmpty()) {
            postFailure(callback, "Invalid hospital ID");
            return;
        }

        if (adminId == null || adminId.trim().isEmpty()) {
            postFailure(callback, "Please enter a valid Admin ID");
            return;
        }

        if (password == null || password.length() < 6) {
            postFailure(callback, "Password must be at least 6 characters long");
            return;
        }

        final String cleanAdminId = adminId.trim().toUpperCase();
        final String cleanAdminName = (adminName != null && !adminName.trim().isEmpty()) ? adminName.trim() : "Hospital Admin";
        final String cleanPassword = password.trim();

        // Check Admin ID uniqueness across other hospitals in Firestore first
        DB.collection("organizations")
                .whereEqualTo("primaryAdminEmail", cleanAdminId)
                .get()
                .addOnSuccessListener(existingOrgSnap -> {
                    if (existingOrgSnap != null && !existingOrgSnap.isEmpty()) {
                        for (DocumentSnapshot doc : existingOrgSnap.getDocuments()) {
                            if (!orgId.equals(doc.getId())) {
                                postFailure(callback, "Admin ID already exists.");
                                return;
                            }
                        }
                    }

                    // Also check organization_members collection for active admin with this ID
                    DB.collection("organization_members")
                            .whereEqualTo("adminId", cleanAdminId)
                            .whereEqualTo("status", "ACTIVE")
                            .get()
                            .addOnSuccessListener(existingMemberSnap -> {
                                if (existingMemberSnap != null && !existingMemberSnap.isEmpty()) {
                                    for (DocumentSnapshot doc : existingMemberSnap.getDocuments()) {
                                        String mOrgId = doc.getString("organizationId");
                                        if (mOrgId != null && !mOrgId.equals(orgId)) {
                                            postFailure(callback, "Admin ID already exists.");
                                            return;
                                        }
                                    }
                                }

                                proceedWithCreatingAdminAuth(context, orgId, cleanAdminId, cleanAdminName, cleanPassword, actorId, actorEmail, callback);
                            })
                            .addOnFailureListener(e -> proceedWithCreatingAdminAuth(context, orgId, cleanAdminId, cleanAdminName, cleanPassword, actorId, actorEmail, callback));
                })
                .addOnFailureListener(e -> proceedWithCreatingAdminAuth(context, orgId, cleanAdminId, cleanAdminName, cleanPassword, actorId, actorEmail, callback));
    }

    private static void proceedWithCreatingAdminAuth(
            Context context,
            String orgId,
            String cleanAdminId,
            String cleanAdminName,
            String cleanPassword,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        final String authEmail = cleanAdminId.contains("@")
                ? cleanAdminId.toLowerCase()
                : (cleanAdminId.toLowerCase() + "@admin.medilinkchain.org");

        // 1. Fetch hospital details first to get name and code
        DB.collection("organizations").document(orgId).get()
                .addOnSuccessListener(orgDoc -> {
                    final String hospName = orgDoc.exists() && orgDoc.getString("name") != null
                            ? orgDoc.getString("name")
                            : (orgDoc.exists() && orgDoc.getString("organizationName") != null ? orgDoc.getString("organizationName") : "Hospital");
                    final String hospCode = orgDoc.exists() && orgDoc.getString("code") != null
                            ? orgDoc.getString("code")
                            : "HOSP";

                    // 2. Provision credentials in Firebase Auth using a secondary FirebaseApp instance
                    FirebaseApp secondaryApp;
                    try {
                        secondaryApp = FirebaseApp.getInstance("HospitalAdminAuthApp");
                    } catch (IllegalStateException e) {
                        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                        secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, "HospitalAdminAuthApp");
                    }

                    final FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

                    secondaryAuth.createUserWithEmailAndPassword(authEmail, cleanPassword)
                            .addOnCompleteListener(authTask -> {
                                if (authTask.isSuccessful() && secondaryAuth.getCurrentUser() != null) {
                                    String newUid = secondaryAuth.getCurrentUser().getUid();
                                    secondaryAuth.signOut();
                                    proceedWithHospitalAdminAssignment(orgId, hospName, hospCode, newUid, cleanAdminId, cleanAdminName, authEmail, actorId, actorEmail, callback);
                                } else {
                                    // If user already exists in Firebase Auth, attempt signing in or finding UID to re-link to this hospital
                                    secondaryAuth.signInWithEmailAndPassword(authEmail, cleanPassword)
                                            .addOnCompleteListener(signInTask -> {
                                                if (signInTask.isSuccessful() && secondaryAuth.getCurrentUser() != null) {
                                                    String existingUid = secondaryAuth.getCurrentUser().getUid();
                                                    secondaryAuth.signOut();
                                                    proceedWithHospitalAdminAssignment(orgId, hospName, hospCode, existingUid, cleanAdminId, cleanAdminName, authEmail, actorId, actorEmail, callback);
                                                } else {
                                                    // Check Firestore users collection to find existing user UID by adminId or email
                                                    DB.collection("users").whereEqualTo("adminId", cleanAdminId).limit(1).get()
                                                            .addOnSuccessListener(existingUserSnap -> {
                                                                if (existingUserSnap != null && !existingUserSnap.isEmpty()) {
                                                                    String existingUid = existingUserSnap.getDocuments().get(0).getId();
                                                                    proceedWithHospitalAdminAssignment(orgId, hospName, hospCode, existingUid, cleanAdminId, cleanAdminName, authEmail, actorId, actorEmail, callback);
                                                                } else {
                                                                    String err = authTask.getException() != null ? authTask.getException().getMessage() : "Failed to create Admin account.";
                                                                    postFailure(callback, err);
                                                                }
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                String err = authTask.getException() != null ? authTask.getException().getMessage() : "Failed to create Admin account.";
                                                                postFailure(callback, err);
                                                            });
                                                }
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    private static void proceedWithHospitalAdminAssignment(
            String orgId,
            String hospName,
            String hospCode,
            String adminUid,
            String cleanAdminId,
            String cleanAdminName,
            String authEmail,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        // 1. Demote any previously assigned active primary admin for this hospital
        DB.collection("organization_members")
                .whereEqualTo("organizationId", orgId)
                .whereEqualTo("isPrimaryAdmin", true)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .addOnSuccessListener(oldAdminsSnap -> {
                    if (oldAdminsSnap != null && !oldAdminsSnap.isEmpty()) {
                        for (DocumentSnapshot oldDoc : oldAdminsSnap.getDocuments()) {
                            if (!adminUid.equals(oldDoc.getString("userId"))) {
                                Map<String, Object> demoteData = new HashMap<>();
                                demoteData.put("isPrimaryAdmin", false);
                                demoteData.put("status", "DISABLED");
                                demoteData.put("updatedAt", new Date());
                                DB.collection("organization_members").document(oldDoc.getId()).update(demoteData);

                                String oldUserId = oldDoc.getString("userId");
                                if (oldUserId != null && !oldUserId.isEmpty()) {
                                    Map<String, Object> userDemote = new HashMap<>();
                                    userDemote.put("status", "DISABLED");
                                    userDemote.put("role", "patient");
                                    userDemote.put("updatedAt", new Date());
                                    DB.collection("users").document(oldUserId).update(userDemote);
                                }

                                AuditLogManager.log(
                                        orgId,
                                        actorId,
                                        actorEmail,
                                        "SUPER_ADMIN",
                                        "HOSPITAL_ADMIN_REMOVED",
                                        "ADMIN",
                                        oldDoc.getString("userId"),
                                        "Previous primary Hospital Admin (" + oldDoc.getString("adminId") + ") unlinked."
                                );
                            }
                        }
                    }

                    // 2. Create/Assign new Hospital Admin member document
                    final String memberDocId = "member_" + adminUid;

                    Map<String, Object> memberData = new HashMap<>();
                    memberData.put("id", memberDocId);
                    memberData.put("organizationId", orgId);
                    memberData.put("userId", adminUid);
                    memberData.put("adminId", cleanAdminId);
                    memberData.put("role", RolePermissionManager.ROLE_HOSPITAL_ADMIN);
                    memberData.put("status", "ACTIVE");
                    memberData.put("isPrimaryAdmin", true);
                    memberData.put("fullName", cleanAdminName);
                    memberData.put("email", cleanAdminId);
                    memberData.put("authEmail", authEmail);
                    memberData.put("hospitalName", hospName);
                    memberData.put("hospitalCode", hospCode);
                    memberData.put("createdBy", actorId);
                    memberData.put("createdAt", new Date());
                    memberData.put("updatedAt", new Date());
                    memberData.put("permissions", RolePermissionManager.DEFAULT_HOSPITAL_ADMIN_PERMISSIONS);

                    DB.collection("organization_members").document(memberDocId).set(memberData, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                // 3. Sync to users collection so login & role resolution work seamlessly
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("adminId", cleanAdminId);
                                userData.put("email", cleanAdminId);
                                userData.put("authEmail", authEmail);
                                userData.put("fullName", cleanAdminName);
                                userData.put("role", "HOSPITAL_ADMIN");
                                userData.put("organizationId", orgId);
                                userData.put("hospitalName", hospName);
                                userData.put("hospitalCode", hospCode);
                                userData.put("status", "ACTIVE");
                                userData.put("profileComplete", true);
                                userData.put("createdAt", new Date());
                                userData.put("updatedAt", new Date());

                                DB.collection("users").document(adminUid).set(userData, SetOptions.merge());

                                // 4. Update hospital document with primary admin details
                                Map<String, Object> orgUpdate = new HashMap<>();
                                orgUpdate.put("primaryAdminId", adminUid);
                                orgUpdate.put("primaryAdminAdminId", cleanAdminId);
                                orgUpdate.put("primaryAdminName", cleanAdminName);
                                orgUpdate.put("primaryAdminEmail", cleanAdminId);
                                orgUpdate.put("updatedAt", new Date());
                                DB.collection("organizations").document(orgId).set(orgUpdate, SetOptions.merge());

                                AuditLogManager.log(
                                        orgId,
                                        actorId,
                                        actorEmail,
                                        "SUPER_ADMIN",
                                        "HOSPITAL_ADMIN_ASSIGNED",
                                        "ADMIN",
                                        adminUid,
                                        "Hospital Admin assigned: " + cleanAdminName + " (" + cleanAdminId + ") for " + hospName
                                );

                                postSuccess(callback);
                            })
                            .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    /**
     * Generates a unique, standardized Hospital Admin ID conforming to project conventions:
     * AD{YY}H{SEQ} (e.g. AD26H001, AD26H002...)
     */
    public static void generateNextAdminId(Callback<String> callback) {
        final String yearSuffix = new SimpleDateFormat("yy", Locale.US).format(new Date());
        final String prefix = "AD" + yearSuffix + "H"; // e.g. AD26H

        DB.collection("organization_members").get()
                .addOnSuccessListener(querySnapshot -> {
                    int maxSeq = 0;
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String id = doc.getString("adminId");
                            if (id != null && id.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                                try {
                                    String seqStr = id.substring(prefix.length()).trim();
                                    int val = Integer.parseInt(seqStr);
                                    if (val > maxSeq) maxSeq = val;
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    // Check users collection as well to ensure total global uniqueness
                    final int startSeq = maxSeq + 1;
                    DB.collection("users").get()
                            .addOnSuccessListener(userSnap -> {
                                int currentMax = startSeq - 1;
                                if (userSnap != null) {
                                    for (DocumentSnapshot uDoc : userSnap.getDocuments()) {
                                        String uidAdmin = uDoc.getString("adminId");
                                        if (uidAdmin != null && uidAdmin.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                                            try {
                                                String seqStr = uidAdmin.substring(prefix.length()).trim();
                                                int val = Integer.parseInt(seqStr);
                                                if (val > currentMax) currentMax = val;
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                }

                                String generatedId = String.format(Locale.US, "AD%sH%03d", yearSuffix, currentMax + 1);
                                postSuccess(callback, generatedId);
                            })
                            .addOnFailureListener(e -> {
                                String generatedId = String.format(Locale.US, "AD%sH%03d", yearSuffix, startSeq);
                                postSuccess(callback, generatedId);
                            });
                })
                .addOnFailureListener(e -> {
                    String fallbackId = String.format(Locale.US, "AD%sH001", yearSuffix);
                    postSuccess(callback, fallbackId);
                });
    }

    /**
     * Complete end-to-end Hospital Admin creation:
     * 1. Validates fields, Admin ID & email uniqueness
     * 2. Creates genuine Firebase Auth user with Super Admin session protection
     * 3. Dispatches email verification
     * 4. Persists organization_members document with role HOSPITAL_ADMIN
     * 5. Persists users document
     * 6. Updates hospital document if primary admin
     * 7. Logs audit event
     */
    public static void createHospitalAdmin(
            Context context,
            String hospitalId,
            String hospitalName,
            String hospitalCode,
            String adminId,
            String fullName,
            String email,
            String phone,
            String password,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        if (hospitalId == null || hospitalId.trim().isEmpty()) {
            postFailure(callback, "Please select an assigned hospital.");
            return;
        }

        if (fullName == null || fullName.trim().isEmpty()) {
            postFailure(callback, "Please provide the Administrator Full Name.");
            return;
        }

        if (email == null || !email.contains("@") || !email.contains(".")) {
            postFailure(callback, "Please enter a valid email address.");
            return;
        }

        if (adminId == null || adminId.trim().length() < 4) {
            postFailure(callback, "Please enter a valid Admin ID (e.g. AD26H001).");
            return;
        }

        if (password == null || password.length() < 6) {
            postFailure(callback, "Password must be at least 6 characters long.");
            return;
        }

        final String cleanAdminId = adminId.trim().toUpperCase(Locale.ROOT);
        final String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        final String cleanFullName = fullName.trim();
        final String cleanPhone = phone != null ? phone.trim() : "";
        final String cleanPassword = password.trim();

        // 1. Verify uniqueness of Admin ID
        DB.collection("organization_members").whereEqualTo("adminId", cleanAdminId).limit(1).get()
                .addOnSuccessListener(snapAdmin -> {
                    if (snapAdmin != null && !snapAdmin.isEmpty()) {
                        postFailure(callback, "Admin ID '" + cleanAdminId + "' is already assigned to an existing administrator.");
                        return;
                    }

                    // 2. Verify uniqueness of Email
                    DB.collection("users").whereEqualTo("email", cleanEmail).limit(1).get()
                            .addOnSuccessListener(snapEmail -> {
                                if (snapEmail != null && !snapEmail.isEmpty()) {
                                    postFailure(callback, "An account with email '" + cleanEmail + "' already exists.");
                                    return;
                                }

                                // 3. Provision authentication account via isolated secondary FirebaseApp
                                FirebaseApp secondaryApp;
                                try {
                                    secondaryApp = FirebaseApp.getInstance("HospitalAdminAuthApp");
                                } catch (IllegalStateException e) {
                                    FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                                    secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, "HospitalAdminAuthApp");
                                }

                                final FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

                                secondaryAuth.createUserWithEmailAndPassword(cleanEmail, cleanPassword)
                                        .addOnCompleteListener(authTask -> {
                                            if (!authTask.isSuccessful() || secondaryAuth.getCurrentUser() == null) {
                                                String err = authTask.getException() != null ? authTask.getException().getMessage() : "Failed to create authentication account.";
                                                postFailure(callback, err);
                                                return;
                                            }

                                            FirebaseUser newAdminUser = secondaryAuth.getCurrentUser();
                                            String newUid = newAdminUser.getUid();

                                            // Send genuine email verification
                                            newAdminUser.sendEmailVerification();
                                            secondaryAuth.signOut();

                                            // 4. Check if hospital already has a primary admin
                                            DB.collection("organizations").document(hospitalId).get()
                                                    .addOnSuccessListener(orgDoc -> {
                                                        final String hospActualName = orgDoc.exists() && orgDoc.getString("name") != null
                                                                ? orgDoc.getString("name")
                                                                : hospitalName;
                                                        final String hospActualCode = orgDoc.exists() && orgDoc.getString("code") != null
                                                                ? orgDoc.getString("code")
                                                                : hospitalCode;

                                                        boolean isPrimary = true;
                                                        if (orgDoc.exists() && orgDoc.getString("primaryAdminId") != null && !orgDoc.getString("primaryAdminId").isEmpty()) {
                                                            isPrimary = false;
                                                        }

                                                        // 5. Persist organization_members document
                                                        String memberDocId = "member_" + newUid;
                                                        Map<String, Object> memberData = new HashMap<>();
                                                        memberData.put("id", memberDocId);
                                                        memberData.put("organizationId", hospitalId);
                                                        memberData.put("userId", newUid);
                                                        memberData.put("adminId", cleanAdminId);
                                                        memberData.put("fullName", cleanFullName);
                                                        memberData.put("email", cleanEmail);
                                                        memberData.put("authEmail", cleanEmail);
                                                        memberData.put("phone", cleanPhone);
                                                        memberData.put("contactNumber", cleanPhone);
                                                        memberData.put("hospitalName", hospActualName);
                                                        memberData.put("hospitalCode", hospActualCode);
                                                        memberData.put("role", RolePermissionManager.ROLE_HOSPITAL_ADMIN);
                                                        memberData.put("status", "ACTIVE");
                                                        memberData.put("isPrimaryAdmin", isPrimary);
                                                        memberData.put("permissions", RolePermissionManager.DEFAULT_HOSPITAL_ADMIN_PERMISSIONS);
                                                        memberData.put("createdAt", new Date());
                                                        memberData.put("updatedAt", new Date());
                                                        memberData.put("createdBy", actorId);

                                                        final boolean finalIsPrimary = isPrimary;
                                                        DB.collection("organization_members").document(memberDocId).set(memberData, SetOptions.merge())
                                                                .addOnSuccessListener(mVoid -> {
                                                                    // 6. Persist user document for login & role resolution
                                                                    Map<String, Object> userData = new HashMap<>();
                                                                    userData.put("adminId", cleanAdminId);
                                                                    userData.put("fullName", cleanFullName);
                                                                    userData.put("email", cleanEmail);
                                                                    userData.put("authEmail", cleanEmail);
                                                                    userData.put("contactNumber", cleanPhone);
                                                                    userData.put("role", "HOSPITAL_ADMIN");
                                                                    userData.put("organizationId", hospitalId);
                                                                    userData.put("hospitalName", hospActualName);
                                                                    userData.put("hospitalCode", hospActualCode);
                                                                    userData.put("status", "ACTIVE");
                                                                    userData.put("profileComplete", true);
                                                                    userData.put("emailVerified", true);
                                                                    userData.put("createdAt", new Date());
                                                                    userData.put("updatedAt", new Date());

                                                                    DB.collection("users").document(newUid).set(userData, SetOptions.merge())
                                                                            .addOnSuccessListener(uVoid -> {
                                                                                // 7. Update organization primary admin metadata if primary
                                                                                if (finalIsPrimary) {
                                                                                    Map<String, Object> orgUpdate = new HashMap<>();
                                                                                    orgUpdate.put("primaryAdminId", newUid);
                                                                                    orgUpdate.put("primaryAdminAdminId", cleanAdminId);
                                                                                    orgUpdate.put("primaryAdminName", cleanFullName);
                                                                                    orgUpdate.put("primaryAdminEmail", cleanEmail);
                                                                                    orgUpdate.put("updatedAt", new Date());
                                                                                    DB.collection("organizations").document(hospitalId).set(orgUpdate, SetOptions.merge());
                                                                                }

                                                                                // 8. Audit log
                                                                                AuditLogManager.log(
                                                                                        hospitalId,
                                                                                        actorId,
                                                                                        actorEmail,
                                                                                        "SUPER_ADMIN",
                                                                                        "HOSPITAL_ADMIN_CREATED",
                                                                                        "ADMIN",
                                                                                        newUid,
                                                                                        "Created Hospital Admin " + cleanFullName + " (" + cleanAdminId + ", " + cleanEmail + ") assigned to " + hospActualName
                                                                                );

                                                                                postSuccess(callback);
                                                                            })
                                                                            .addOnFailureListener(e -> postFailure(callback, "Failed to persist user profile: " + e.getMessage()));
                                                                })
                                                                .addOnFailureListener(e -> postFailure(callback, "Failed to create organization membership: " + e.getMessage()));
                                                    })
                                                    .addOnFailureListener(e -> postFailure(callback, "Failed reading hospital metadata: " + e.getMessage()));
                                        });
                            })
                            .addOnFailureListener(e -> postFailure(callback, "Failed checking email uniqueness: " + e.getMessage()));
                })
                .addOnFailureListener(e -> postFailure(callback, "Failed checking Admin ID uniqueness: " + e.getMessage()));
    }

    /**
     * Reassigns an existing Hospital Admin to another hospital organization.
     */
    public static void reassignAdminHospital(
            String memberDocId,
            String adminUserId,
            String newHospitalId,
            String newHospitalName,
            String newHospitalCode,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        if (memberDocId == null || memberDocId.isEmpty() || newHospitalId == null || newHospitalId.isEmpty()) {
            postFailure(callback, "Invalid hospital reassignment request.");
            return;
        }

        Map<String, Object> updateMember = new HashMap<>();
        updateMember.put("organizationId", newHospitalId);
        updateMember.put("hospitalName", newHospitalName);
        updateMember.put("hospitalCode", newHospitalCode);
        updateMember.put("updatedAt", new Date());

        DB.collection("organization_members").document(memberDocId).update(updateMember)
                .addOnSuccessListener(aVoid -> {
                    if (adminUserId != null && !adminUserId.isEmpty()) {
                        Map<String, Object> updateUser = new HashMap<>();
                        updateUser.put("organizationId", newHospitalId);
                        updateUser.put("hospitalName", newHospitalName);
                        updateUser.put("hospitalCode", newHospitalCode);
                        updateUser.put("updatedAt", new Date());
                        DB.collection("users").document(adminUserId).update(updateUser);
                    }

                    AuditLogManager.log(
                            newHospitalId,
                            actorId,
                            actorEmail,
                            "SUPER_ADMIN",
                            "ADMIN_REASSIGNED",
                            "ADMIN",
                            adminUserId,
                            "Hospital Admin reassigned to " + newHospitalName + " [" + newHospitalCode + "]"
                    );

                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    /**
     * Triggers a genuine password reset email to the admin's email address.
     */
    public static void sendAdminPasswordReset(String email, ActionCallback callback) {
        if (email == null || !email.contains("@")) {
            postFailure(callback, "Valid email required for password reset.");
            return;
        }

        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> postSuccess(callback))
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }


    // =========================================================================
    // 4. DOCTOR REGISTRATION ID GENERATION & INVITATION WORKFLOW
    // =========================================================================

    /**
     * Generates a safe, globally unique Doctor Registration ID: DOC-{HOSPITAL_CODE}-{YEAR}-{SEQUENCE}
     * (e.g. DOC-ABC-2026-0001) and saves an active invitation.
     */
    public static void generateDoctorRegistrationId(
            String orgId,
            String doctorName,
            String specialization,
            String email,
            String phone,
            String medicalRegistrationNumber,
            String actorId,
            String actorEmail,
            Callback<String> callback) {

        if (orgId == null || orgId.isEmpty()) {
            postFailure(callback, "Invalid hospital ID for invitation generation.");
            return;
        }

        EXECUTOR.execute(() -> {
            DB.collection("organizations").document(orgId).get()
                    .addOnSuccessListener(orgDoc -> {
                        if (!orgDoc.exists() || !"ACTIVE".equalsIgnoreCase(orgDoc.getString("status"))) {
                            postFailure(callback, "Hospital is not active or does not exist.");
                            return;
                        }

                        String rawCode = orgDoc.getString("code");
                        final String hospCode = (rawCode != null && !rawCode.isEmpty()) ? rawCode.toUpperCase() : "HOSP";
                        final String hospName = orgDoc.getString("name") != null ? orgDoc.getString("name") : orgDoc.getString("organizationName");
                        final String currentYear = new SimpleDateFormat("yyyy", Locale.US).format(new Date());

                        // Query existing invitations for sequence determination
                        DB.collection("doctor_invitations")
                                .whereEqualTo("organizationId", orgId)
                                .get()
                                .addOnSuccessListener(invitationsSnap -> {
                                    int seq = 1;
                                    if (invitationsSnap != null) {
                                        seq = invitationsSnap.size() + 1;
                                    }

                                    String regId = String.format(Locale.US, "DOC-%s-%s-%04d", hospCode, currentYear, seq);

                                    // Verify uniqueness against existing
                                    if (invitationsSnap != null) {
                                        boolean duplicate = true;
                                        while (duplicate) {
                                            duplicate = false;
                                            for (DocumentSnapshot doc : invitationsSnap.getDocuments()) {
                                                if (regId.equalsIgnoreCase(doc.getString("registrationId"))) {
                                                    seq++;
                                                    regId = String.format(Locale.US, "DOC-%s-%s-%04d", hospCode, currentYear, seq);
                                                    duplicate = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                    final String finalRegId = regId;
                                    final String invitationId = "inv_" + UUID.randomUUID().toString().substring(0, 8);

                                    Calendar cal = Calendar.getInstance();
                                    cal.add(Calendar.DAY_OF_YEAR, 30);
                                    Date expiresAt = cal.getTime();

                                    Map<String, Object> data = new HashMap<>();
                                    data.put("id", invitationId);
                                    data.put("organizationId", orgId);
                                    data.put("hospitalName", hospName);
                                    data.put("hospitalCode", hospCode);
                                    data.put("registrationId", finalRegId);
                                    data.put("doctorName", doctorName != null ? doctorName.trim() : "");
                                    data.put("specialization", specialization != null ? specialization.trim() : "General Medicine");
                                    data.put("email", email != null ? email.trim() : "");
                                    data.put("phone", phone != null ? phone.trim() : "");
                                    data.put("medicalRegistrationNumber", medicalRegistrationNumber != null ? medicalRegistrationNumber.trim() : "");
                                    data.put("status", "ACTIVE");
                                    data.put("createdBy", actorId);
                                    data.put("createdAt", new Date());
                                    data.put("expiresAt", expiresAt);

                                    DB.collection("doctor_invitations").document(invitationId).set(data)
                                            .addOnSuccessListener(aVoid -> {
                                                AuditLogManager.log(
                                                        orgId,
                                                        actorId,
                                                        actorEmail,
                                                        "HOSPITAL_ADMIN",
                                                        "DOCTOR_INVITATION_CREATED",
                                                        "DOCTOR_INVITATION",
                                                        finalRegId,
                                                        "Generated Doctor Registration ID: " + finalRegId + " for " + (doctorName != null && !doctorName.isEmpty() ? doctorName : "Doctor")
                                                );
                                                postSuccess(callback, finalRegId);
                                            })
                                            .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                                })
                                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                    })
                    .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
        });
    }

    /**
     * Validates a Doctor Registration ID during doctor registration.
     * Returns the associated hospital, specialization, and doctor details.
     */
    public static void validateDoctorInvitation(String registrationId, Callback<DoctorInvitationModel> callback) {
        if (registrationId == null || registrationId.trim().isEmpty()) {
            postFailure(callback, "Please enter a valid Doctor Registration ID.");
            return;
        }

        final String cleanId = registrationId.trim().toUpperCase();

        EXECUTOR.execute(() -> {
            DB.collection("doctor_invitations")
                    .whereEqualTo("registrationId", cleanId)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot == null || querySnapshot.isEmpty()) {
                            postFailure(callback, "Invalid or unrecognized Doctor Registration ID.");
                            return;
                        }

                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        DoctorInvitationModel model = parseInvitationDoc(doc);

                        if ("USED".equalsIgnoreCase(model.getStatus())) {
                            postFailure(callback, "This Doctor Registration ID has already been used.");
                            return;
                        }

                        if ("CANCELLED".equalsIgnoreCase(model.getStatus())) {
                            postFailure(callback, "This Doctor Registration ID has been cancelled by administration.");
                            return;
                        }

                        if (model.getExpiresAt() != null && model.getExpiresAt().before(new Date())) {
                            postFailure(callback, "This Doctor Registration ID has expired. Please contact your Hospital Administrator.");
                            return;
                        }

                        // Load and verify hospital is active
                        DB.collection("organizations").document(model.getOrganizationId()).get()
                                .addOnSuccessListener(orgDoc -> {
                                    if (!orgDoc.exists() || !"ACTIVE".equalsIgnoreCase(orgDoc.getString("status"))) {
                                        postFailure(callback, "The hospital associated with this ID is currently inactive or disabled.");
                                        return;
                                    }

                                    model.setHospitalName(orgDoc.getString("name") != null ? orgDoc.getString("name") : orgDoc.getString("organizationName"));
                                    model.setHospitalCode(orgDoc.getString("code"));
                                    postSuccess(callback, model);
                                })
                                .addOnFailureListener(e -> postSuccess(callback, model));
                    })
                    .addOnFailureListener(e -> postFailure(callback, "Verification error: " + e.getMessage()));
        });
    }

    /**
     * Marks an invitation as USED upon successful doctor account creation.
     */
    public static void markInvitationUsed(String registrationId, String doctorUserId, ActionCallback callback) {
        if (registrationId == null || registrationId.isEmpty()) {
            if (callback != null) callback.onSuccess();
            return;
        }

        DB.collection("doctor_invitations")
                .whereEqualTo("registrationId", registrationId.trim().toUpperCase())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", "USED");
                        updates.put("usedBy", doctorUserId);
                        updates.put("usedAt", new Date());
                        DB.collection("doctor_invitations").document(doc.getId()).update(updates);
                    }
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onSuccess(); // Non-blocking
                });
    }

    public static void listDoctorInvitations(String orgId, Callback<List<DoctorInvitationModel>> callback) {
        Query q = DB.collection("doctor_invitations");
        if (orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
            q = q.whereEqualTo("organizationId", orgId);
        }

        q.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DoctorInvitationModel> list = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            list.add(parseInvitationDoc(doc));
                        }
                    }
                    postSuccess(callback, list);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    // =========================================================================
    // 5. SCOPED DOCTOR MANAGEMENT (SUPER ADMIN OVERRIDE & HOSPITAL ADMIN SCOPED)
    // =========================================================================

    public static void assignDoctor(
            Context context,
            String orgId,
            String doctorId,
            String doctorName,
            String specialization,
            String password,
            String actorId,
            String actorEmail,
            String actorRole,
            ActionCallback callback) {

        if (orgId == null || orgId.isEmpty()) {
            postFailure(callback, "Invalid hospital ID");
            return;
        }

        if (doctorId == null || doctorId.trim().isEmpty()) {
            postFailure(callback, "Please enter a valid Doctor ID");
            return;
        }

        if (password == null || password.length() < 6) {
            postFailure(callback, "Password must be at least 6 characters long");
            return;
        }

        final String cleanDoctorId = doctorId.trim().toUpperCase();
        final String cleanDoctorName = (doctorName != null && !doctorName.trim().isEmpty()) ? doctorName.trim() : "Doctor";
        final String cleanSpec = (specialization != null && !specialization.trim().isEmpty()) ? specialization.trim() : "General Medicine";
        final String cleanPassword = password.trim();

        // Check Doctor ID uniqueness across users collection
        DB.collection("users")
                .whereEqualTo("doctorId", cleanDoctorId)
                .get()
                .addOnSuccessListener(snap1 -> {
                    if (snap1 != null && !snap1.isEmpty()) {
                        postFailure(callback, "Doctor ID already exists.");
                        return;
                    }

                    DB.collection("users")
                            .whereEqualTo("doctorRegistrationId", cleanDoctorId)
                            .get()
                            .addOnSuccessListener(snap2 -> {
                                if (snap2 != null && !snap2.isEmpty()) {
                                    postFailure(callback, "Doctor ID already exists.");
                                    return;
                                }

                                DB.collection("users")
                                        .whereEqualTo("email", cleanDoctorId)
                                        .get()
                                        .addOnSuccessListener(snap3 -> {
                                            if (snap3 != null && !snap3.isEmpty()) {
                                                postFailure(callback, "Doctor ID already exists.");
                                                return;
                                            }

                                            proceedWithCreatingDoctorAuth(context, orgId, cleanDoctorId, cleanDoctorName, cleanSpec, cleanPassword, actorId, actorEmail, actorRole, callback);
                                        })
                                        .addOnFailureListener(e -> proceedWithCreatingDoctorAuth(context, orgId, cleanDoctorId, cleanDoctorName, cleanSpec, cleanPassword, actorId, actorEmail, actorRole, callback));
                            })
                            .addOnFailureListener(e -> proceedWithCreatingDoctorAuth(context, orgId, cleanDoctorId, cleanDoctorName, cleanSpec, cleanPassword, actorId, actorEmail, actorRole, callback));
                })
                .addOnFailureListener(e -> proceedWithCreatingDoctorAuth(context, orgId, cleanDoctorId, cleanDoctorName, cleanSpec, cleanPassword, actorId, actorEmail, actorRole, callback));
    }

    private static void proceedWithCreatingDoctorAuth(
            Context context,
            String orgId,
            String cleanDoctorId,
            String cleanDoctorName,
            String cleanSpec,
            String cleanPassword,
            String actorId,
            String actorEmail,
            String actorRole,
            ActionCallback callback) {

        final String authEmail = cleanDoctorId.contains("@")
                ? cleanDoctorId.toLowerCase()
                : (cleanDoctorId.toLowerCase() + "@doctor.medilinkchain.org");

        DB.collection("organizations").document(orgId).get()
                .addOnSuccessListener(orgDoc -> {
                    final String hospName = orgDoc.exists() && orgDoc.getString("name") != null
                            ? orgDoc.getString("name")
                            : (orgDoc.exists() && orgDoc.getString("organizationName") != null ? orgDoc.getString("organizationName") : "Hospital");
                    final String hospCode = orgDoc.exists() && orgDoc.getString("code") != null
                            ? orgDoc.getString("code")
                            : "HOSP";

                    FirebaseApp secondaryApp;
                    try {
                        secondaryApp = FirebaseApp.getInstance("DoctorAuthApp");
                    } catch (IllegalStateException e) {
                        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                        secondaryApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, "DoctorAuthApp");
                    }

                    final FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

                    secondaryAuth.createUserWithEmailAndPassword(authEmail, cleanPassword)
                            .addOnCompleteListener(authTask -> {
                                if (authTask.isSuccessful() && secondaryAuth.getCurrentUser() != null) {
                                    String newUid = secondaryAuth.getCurrentUser().getUid();
                                    secondaryAuth.signOut();
                                    proceedWithDoctorAssignment(orgId, hospName, hospCode, newUid, cleanDoctorId, cleanDoctorName, cleanSpec, authEmail, actorId, actorEmail, actorRole, callback);
                                } else {
                                    secondaryAuth.signInWithEmailAndPassword(authEmail, cleanPassword)
                                            .addOnCompleteListener(signInTask -> {
                                                if (signInTask.isSuccessful() && secondaryAuth.getCurrentUser() != null) {
                                                    String existingUid = secondaryAuth.getCurrentUser().getUid();
                                                    secondaryAuth.signOut();
                                                    proceedWithDoctorAssignment(orgId, hospName, hospCode, existingUid, cleanDoctorId, cleanDoctorName, cleanSpec, authEmail, actorId, actorEmail, actorRole, callback);
                                                } else {
                                                    DB.collection("users").whereEqualTo("doctorId", cleanDoctorId).limit(1).get()
                                                            .addOnSuccessListener(existingUserSnap -> {
                                                                if (existingUserSnap != null && !existingUserSnap.isEmpty()) {
                                                                    String existingUid = existingUserSnap.getDocuments().get(0).getId();
                                                                    proceedWithDoctorAssignment(orgId, hospName, hospCode, existingUid, cleanDoctorId, cleanDoctorName, cleanSpec, authEmail, actorId, actorEmail, actorRole, callback);
                                                                } else {
                                                                    String err = authTask.getException() != null ? authTask.getException().getMessage() : "Failed to create Doctor account.";
                                                                    postFailure(callback, err);
                                                                }
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                String err = authTask.getException() != null ? authTask.getException().getMessage() : "Failed to create Doctor account.";
                                                                postFailure(callback, err);
                                                            });
                                                }
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    private static void proceedWithDoctorAssignment(
            String orgId,
            String hospName,
            String hospCode,
            String doctorUid,
            String cleanDoctorId,
            String cleanDoctorName,
            String cleanSpec,
            String authEmail,
            String actorId,
            String actorEmail,
            String actorRole,
            ActionCallback callback) {

        final Date now = new Date();
        final String memberDocId = "member_" + doctorUid;

        // 1. organization_members collection
        Map<String, Object> memberData = new HashMap<>();
        memberData.put("id", memberDocId);
        memberData.put("organizationId", orgId);
        memberData.put("userId", doctorUid);
        memberData.put("doctorId", cleanDoctorId);
        memberData.put("role", RolePermissionManager.ROLE_DOCTOR);
        memberData.put("status", "PENDING");
        memberData.put("fullName", cleanDoctorName);
        memberData.put("email", cleanDoctorId);
        memberData.put("authEmail", authEmail);
        memberData.put("hospitalName", hospName);
        memberData.put("hospitalCode", hospCode);
        memberData.put("specialization", cleanSpec);
        memberData.put("createdBy", actorId);
        memberData.put("createdAt", now);
        memberData.put("updatedAt", now);

        DB.collection("organization_members").document(memberDocId).set(memberData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // 2. users collection
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("doctorId", cleanDoctorId);
                    userData.put("doctorRegistrationId", cleanDoctorId);
                    userData.put("email", cleanDoctorId);
                    userData.put("authEmail", authEmail);
                    userData.put("fullName", cleanDoctorName);
                    userData.put("name", cleanDoctorName);
                    userData.put("role", "DOCTOR");
                    userData.put("organizationId", orgId);
                    userData.put("hospitalName", hospName);
                    userData.put("hospitalCode", hospCode);
                    userData.put("specialization", cleanSpec);
                    userData.put("status", "PENDING");
                    userData.put("verificationStatus", "PENDING");
                    userData.put("accountStatus", "PENDING");
                    userData.put("profileComplete", true);
                    userData.put("createdAt", now);
                    userData.put("updatedAt", now);

                    DB.collection("users").document(doctorUid).set(userData, SetOptions.merge());

                    // 3. doctor_profiles collection
                    Map<String, Object> doctorProfile = new HashMap<>();
                    doctorProfile.put("id", doctorUid);
                    doctorProfile.put("userId", doctorUid);
                    doctorProfile.put("organizationId", orgId);
                    doctorProfile.put("registration_id", cleanDoctorId);
                    doctorProfile.put("doctorRegistrationId", cleanDoctorId);
                    doctorProfile.put("fullName", cleanDoctorName);
                    doctorProfile.put("email", cleanDoctorId);
                    doctorProfile.put("hospitalName", hospName);
                    doctorProfile.put("hospitalCode", hospCode);
                    doctorProfile.put("specialization", cleanSpec);
                    doctorProfile.put("verificationStatus", "PENDING");
                    doctorProfile.put("accountStatus", "PENDING");
                    doctorProfile.put("status", "PENDING");
                    doctorProfile.put("createdAt", now);
                    doctorProfile.put("submittedAt", now);

                    DB.collection("doctor_profiles").document(doctorUid).set(doctorProfile, SetOptions.merge());

                    AuditLogManager.log(
                            orgId,
                            actorId,
                            actorEmail,
                            actorRole != null ? actorRole : "ADMIN",
                            "DOCTOR_INVITED",
                            "DOCTOR",
                            doctorUid,
                            "Doctor assigned: " + cleanDoctorName + " (" + cleanDoctorId + ") for " + hospName
                    );

                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void listDoctors(String orgId, Callback<List<DoctorProfileModel>> callback) {
        Query q = DB.collection("users").whereIn("role", java.util.Arrays.asList("doctor", "DOCTOR", "Doctor"));

        // Filter by organization if provided (Hospital Admin scope)
        if (orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
            q = q.whereEqualTo("organizationId", orgId);
        }

        q.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<DoctorProfileModel> list = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            list.add(parseDoctorDoc(doc));
                        }
                    }
                    postSuccess(callback, list);
                })
                .addOnFailureListener(e -> {
                    // Fallback query if composite index is pending
                    DB.collection("users").get()
                            .addOnSuccessListener(allSnap -> {
                                List<DoctorProfileModel> list = new ArrayList<>();
                                if (allSnap != null) {
                                    for (DocumentSnapshot doc : allSnap.getDocuments()) {
                                        String r = doc.getString("role");
                                        if ("doctor".equalsIgnoreCase(r)) {
                                            String o = doc.getString("organizationId");
                                            if (orgId == null || orgId.isEmpty() || DEFAULT_ORG_ID.equals(orgId) || (o != null && o.equals(orgId))) {
                                                list.add(parseDoctorDoc(doc));
                                            }
                                        }
                                    }
                                }
                                postSuccess(callback, list);
                            })
                            .addOnFailureListener(e2 -> postFailure(callback, e.getMessage()));
                });
    }

    public static void approveDoctor(
            String doctorUserId,
            String doctorRegistrationId,
            String orgId,
            String actorId,
            String actorEmail,
            String actorRole,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        Map<String, Object> updates = new HashMap<>();
        updates.put("verificationStatus", "APPROVED");
        updates.put("status", "ACTIVE");
        updates.put("accountStatus", "ACTIVE");
        updates.put("verifiedAt", new Date());
        updates.put("verifiedBy", actorId);

        DB.collection("users").document(doctorUserId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Also update organization_members status
                    DB.collection("organization_members")
                            .whereEqualTo("userId", doctorUserId)
                            .get()
                            .addOnSuccessListener(snap -> {
                                if (snap != null && !snap.isEmpty()) {
                                    for (DocumentSnapshot doc : snap.getDocuments()) {
                                        Map<String, Object> memberUpdate = new HashMap<>();
                                        memberUpdate.put("status", "ACTIVE");
                                        memberUpdate.put("updatedAt", new Date());
                                        DB.collection("organization_members").document(doc.getId()).update(memberUpdate);
                                    }
                                }
                            });

                    AuditLogManager.log(
                            targetOrgId,
                            actorId,
                            actorEmail,
                            actorRole != null ? actorRole : "HOSPITAL_ADMIN",
                            "DOCTOR_APPROVED",
                            "DOCTOR",
                            doctorRegistrationId != null ? doctorRegistrationId : doctorUserId,
                            "Doctor registration approved. Doctor account is now ACTIVE."
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void rejectDoctor(
            String doctorUserId,
            String doctorRegistrationId,
            String orgId,
            String rejectionReason,
            String actorId,
            String actorEmail,
            String actorRole,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        Map<String, Object> updates = new HashMap<>();
        updates.put("verificationStatus", "REJECTED");
        updates.put("status", "REJECTED");
        updates.put("accountStatus", "REJECTED");
        updates.put("rejectionReason", rejectionReason);
        updates.put("verifiedAt", new Date());
        updates.put("verifiedBy", actorId);

        DB.collection("users").document(doctorUserId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    AuditLogManager.log(
                            targetOrgId,
                            actorId,
                            actorEmail,
                            actorRole != null ? actorRole : "HOSPITAL_ADMIN",
                            "DOCTOR_REJECTED",
                            "DOCTOR",
                            doctorRegistrationId != null ? doctorRegistrationId : doctorUserId,
                            "Doctor application rejected. Reason: " + rejectionReason
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void setDoctorAccountStatus(
            String doctorUserId,
            String doctorRegistrationId,
            String orgId,
            String newStatus,
            String actorId,
            String actorEmail,
            String actorRole,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("accountStatus", newStatus);
        updates.put("updatedAt", new Date());

        DB.collection("users").document(doctorUserId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    String action = "ACTIVE".equalsIgnoreCase(newStatus) ? "DOCTOR_REACTIVATED" : "DOCTOR_DISABLED";
                    AuditLogManager.log(
                            targetOrgId,
                            actorId,
                            actorEmail,
                            actorRole != null ? actorRole : "HOSPITAL_ADMIN",
                            action,
                            "DOCTOR",
                            doctorRegistrationId != null ? doctorRegistrationId : doctorUserId,
                            "Doctor account status changed to: " + newStatus
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    // =========================================================================
    // 6. SCOPED SYSTEM MONITORING & TELEMETRY
    // =========================================================================

    public static void getSystemStats(String orgId, Callback<SystemStats> callback) {
        getSystemStats(orgId, false, callback);
    }

    public static void getSystemStats(String orgId, boolean isSuperAdmin, Callback<SystemStats> callback) {
        final SystemStats stats = new SystemStats();

        EXECUTOR.execute(() -> {
            // 1. Hospitals count
            DB.collection("organizations").get().addOnSuccessListener(hospSnap -> {
                stats.totalHospitals = (hospSnap != null) ? hospSnap.size() : 1;

                // 2. Patients count
                Query patientQuery = DB.collection("users").whereEqualTo("role", "patient");
                if (!isSuperAdmin && orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
                    patientQuery = patientQuery.whereEqualTo("organizationId", orgId);
                }

                patientQuery.get().addOnSuccessListener(patientsSnap -> {
                    stats.totalPatients = (patientsSnap != null) ? patientsSnap.size() : 0;
                    if (patientsSnap != null) {
                        for (DocumentSnapshot doc : patientsSnap.getDocuments()) {
                            if (isToday(doc.getDate("createdAt"))) {
                                stats.todayPatients++;
                            }
                        }
                    }

                    // 3. Doctors count
                    Query doctorQuery = DB.collection("users").whereEqualTo("role", "doctor");
                    if (!isSuperAdmin && orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
                        doctorQuery = doctorQuery.whereEqualTo("organizationId", orgId);
                    }

                    doctorQuery.get().addOnSuccessListener(doctorsSnap -> {
                        stats.totalDoctors = (doctorsSnap != null) ? doctorsSnap.size() : 0;
                        if (doctorsSnap != null) {
                            for (DocumentSnapshot doc : doctorsSnap.getDocuments()) {
                                String vStatus = doc.getString("verificationStatus");
                                String status = doc.getString("status");
                                if ("APPROVED".equalsIgnoreCase(vStatus) && "ACTIVE".equalsIgnoreCase(status)) {
                                    stats.approvedDoctors++;
                                } else if ("REJECTED".equalsIgnoreCase(vStatus)) {
                                    stats.rejectedDoctors++;
                                } else {
                                    stats.pendingDoctors++;
                                }

                                if (isToday(doc.getDate("createdAt")) || isToday(doc.getDate("submittedAt"))) {
                                    stats.todayDoctors++;
                                }
                            }
                        }

                        // 4. Admins count
                        Query adminQuery = DB.collection("organization_members").whereEqualTo("status", "ACTIVE");
                        if (!isSuperAdmin && orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
                            adminQuery = adminQuery.whereEqualTo("organizationId", orgId);
                        }

                        adminQuery.get().addOnSuccessListener(adminsSnap -> {
                            stats.activeAdministrators = 0;
                            if (adminsSnap != null) {
                                for (DocumentSnapshot doc : adminsSnap.getDocuments()) {
                                    String role = doc.getString("role");
                                    if (RolePermissionManager.isAdministrativeRole(role)) {
                                        stats.activeAdministrators++;
                                    }
                                }
                            }
                            if (stats.activeAdministrators == 0) stats.activeAdministrators = 1;

                            // 5. Audit logs count
                            Query auditQuery = DB.collection("audit_logs");
                            if (!isSuperAdmin && orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
                                auditQuery = auditQuery.whereEqualTo("organizationId", orgId);
                            }

                            auditQuery.get().addOnSuccessListener(auditSnap -> {
                                stats.adminActionsCount = (auditSnap != null) ? auditSnap.size() : 0;
                                postSuccess(callback, stats);
                            }).addOnFailureListener(e -> postSuccess(callback, stats));
                        }).addOnFailureListener(e -> postSuccess(callback, stats));
                    }).addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                }).addOnFailureListener(e -> postFailure(callback, e.getMessage()));
            }).addOnFailureListener(e -> postFailure(callback, e.getMessage()));
        });
    }

    // =========================================================================
    // 7. SCOPED AUDIT LOGS
    // =========================================================================

    public static void listAuditLogs(String orgId, Callback<List<AuditLogModel>> callback) {
        listAuditLogs(orgId, false, callback);
    }

    public static void listAuditLogs(String orgId, boolean isSuperAdmin, Callback<List<AuditLogModel>> callback) {
        Query q = DB.collection("audit_logs");
        if (!isSuperAdmin && orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
            q = q.whereEqualTo("organizationId", orgId);
        }

        q.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<AuditLogModel> list = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            list.add(parseAuditDoc(doc));
                        }
                    }
                    postSuccess(callback, list);
                })
                .addOnFailureListener(e -> {
                    // Fallback query without ordering index
                    Query fallbackQ = DB.collection("audit_logs");
                    if (!isSuperAdmin && orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
                        fallbackQ = fallbackQ.whereEqualTo("organizationId", orgId);
                    }
                    fallbackQ.limit(50).get()
                            .addOnSuccessListener(fallbackSnap -> {
                                List<AuditLogModel> list = new ArrayList<>();
                                if (fallbackSnap != null) {
                                    for (DocumentSnapshot doc : fallbackSnap.getDocuments()) {
                                        list.add(parseAuditDoc(doc));
                                    }
                                }
                                Collections.sort(list, (a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                                postSuccess(callback, list);
                            })
                            .addOnFailureListener(e2 -> postFailure(callback, e2.getMessage()));
                });
    }

    // =========================================================================
    // 8. PATIENT MANAGEMENT
    // =========================================================================

    public static void listPatients(Callback<List<PatientProfileModel>> callback) {
        listPatients(null, true, callback);
    }

    public static void listPatients(String orgId, Callback<List<PatientProfileModel>> callback) {
        listPatients(orgId, false, callback);
    }

    public static void listPatients(String orgId, boolean isSuperAdmin, Callback<List<PatientProfileModel>> callback) {
        Query q = DB.collection("users").whereEqualTo("role", "patient");
        if (!isSuperAdmin && orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
            q = q.whereEqualTo("organizationId", orgId);
        }

        q.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<PatientProfileModel> list = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            list.add(parsePatientDoc(doc));
                        }
                    }
                    postSuccess(callback, list);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void updatePatientStatus(
            String patientUserId,
            String patientIdCode,
            String orgId,
            String newStatus,
            String actorId,
            String actorEmail,
            String actorRole,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("updatedAt", new Date());

        DB.collection("users").document(patientUserId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    String action = "ACTIVE".equalsIgnoreCase(newStatus) ? "PATIENT_ENABLED" : "PATIENT_DISABLED";
                    AuditLogManager.log(
                            targetOrgId,
                            actorId,
                            actorEmail,
                            actorRole != null ? actorRole : "HOSPITAL_ADMIN",
                            action,
                            "PATIENT",
                            patientIdCode != null ? patientIdCode : patientUserId,
                            "Patient account status updated to: " + newStatus
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    // =========================================================================
    // 9. SUPER ADMIN & ADMIN MANAGEMENT
    // =========================================================================

    public static void listAdmins(String orgId, Callback<List<OrganizationMemberModel>> callback) {
        Query q = DB.collection("organization_members");
        if (orgId != null && !orgId.isEmpty() && !DEFAULT_ORG_ID.equals(orgId)) {
            q = q.whereEqualTo("organizationId", orgId);
        }

        q.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<OrganizationMemberModel> list = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String role = doc.getString("role");
                            if (RolePermissionManager.isAdministrativeRole(role)) {
                                list.add(parseMemberDoc(doc));
                            }
                        }
                    }
                    postSuccess(callback, list);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void addAdmin(
            String orgId,
            String fullName,
            String email,
            String phone,
            List<String> permissions,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;
        final String memberId = UUID.randomUUID().toString();
        final String adminUserId = "admin_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> data = new HashMap<>();
        data.put("id", memberId);
        data.put("organizationId", targetOrgId);
        data.put("userId", adminUserId);
        data.put("role", RolePermissionManager.ROLE_HOSPITAL_ADMIN);
        data.put("status", "ACTIVE");
        data.put("isPrimaryAdmin", false);
        data.put("fullName", fullName);
        data.put("email", email);
        data.put("phone", phone);
        data.put("createdBy", actorId);
        data.put("createdAt", new Date());
        data.put("permissions", permissions != null ? permissions : RolePermissionManager.DEFAULT_HOSPITAL_ADMIN_PERMISSIONS);

        DB.collection("organization_members").document(memberId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("email", email);
                    userData.put("fullName", fullName);
                    userData.put("role", "hospital_admin");
                    userData.put("organizationId", targetOrgId);
                    userData.put("status", "ACTIVE");
                    userData.put("profileComplete", true);
                    userData.put("createdAt", new Date());
                    DB.collection("users").document(adminUserId).set(userData);

                    AuditLogManager.log(
                            targetOrgId,
                            actorId,
                            actorEmail,
                            "SUPER_ADMIN",
                            "ADMIN_CREATED",
                            "ADMIN",
                            adminUserId,
                            "New administrator created: " + fullName + " (" + email + ")"
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void updateAdminStatus(
            String orgId,
            String memberId,
            String targetRole,
            String newStatus,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        // If disabling Super Admin, verify count
        if (RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(targetRole) && !"ACTIVE".equalsIgnoreCase(newStatus)) {
            DB.collection("organization_members")
                    .whereEqualTo("role", RolePermissionManager.ROLE_SUPER_ADMIN)
                    .whereEqualTo("status", "ACTIVE")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int activeCount = (querySnapshot != null) ? querySnapshot.size() : 0;
                        if (activeCount <= 1) {
                            postFailure(callback, "At least one active Super Admin must remain in the organization.");
                            return;
                        }
                        performAdminStatusUpdate(targetOrgId, memberId, newStatus, actorId, actorEmail, callback);
                    })
                    .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
        } else {
            performAdminStatusUpdate(targetOrgId, memberId, newStatus, actorId, actorEmail, callback);
        }
    }

    private static void performAdminStatusUpdate(
            String orgId,
            String memberId,
            String newStatus,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("updatedAt", new Date());

        DB.collection("organization_members").document(memberId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    String action = "ACTIVE".equalsIgnoreCase(newStatus) ? "ADMIN_ENABLED" : "ADMIN_DISABLED";
                    AuditLogManager.log(
                            orgId,
                            actorId,
                            actorEmail,
                            "SUPER_ADMIN",
                            action,
                            "ADMIN",
                            memberId,
                            "Administrator account status changed to: " + newStatus
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    public static void removeAdmin(
            String orgId,
            String memberId,
            String targetRole,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;

        if (RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(targetRole)) {
            DB.collection("organization_members")
                    .whereEqualTo("role", RolePermissionManager.ROLE_SUPER_ADMIN)
                    .whereEqualTo("status", "ACTIVE")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int activeCount = (querySnapshot != null) ? querySnapshot.size() : 0;
                        if (activeCount <= 1) {
                            postFailure(callback, "At least one active Super Admin must remain in the organization.");
                            return;
                        }
                        performAdminRemoval(targetOrgId, memberId, actorId, actorEmail, callback);
                    })
                    .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
        } else {
            performAdminRemoval(targetOrgId, memberId, actorId, actorEmail, callback);
        }
    }

    private static void performAdminRemoval(
            String orgId,
            String memberId,
            String actorId,
            String actorEmail,
            ActionCallback callback) {

        DB.collection("organization_members").document(memberId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    AuditLogManager.log(
                            orgId,
                            actorId,
                            actorEmail,
                            "SUPER_ADMIN",
                            "ADMIN_REMOVED",
                            "ADMIN",
                            memberId,
                            "Administrator removed from organization."
                    );
                    postSuccess(callback);
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    // =========================================================================
    // 10. INITIAL SUPER ADMIN BOOTSTRAP
    // =========================================================================

    public static void bootstrapInitialSuperAdmin(
            String orgId,
            String orgName,
            String orgCode,
            String superAdminUid,
            String superAdminEmail,
            String superAdminName,
            ActionCallback callback) {

        final String targetOrgId = (orgId != null && !orgId.isEmpty()) ? orgId : DEFAULT_ORG_ID;
        final String targetOrgName = (orgName != null && !orgName.isEmpty()) ? orgName : DEFAULT_ORG_NAME;
        final String targetOrgCode = (orgCode != null && !orgCode.isEmpty()) ? orgCode.toUpperCase() : DEFAULT_ORG_CODE;

        Map<String, Object> orgData = new HashMap<>();
        orgData.put("id", targetOrgId);
        orgData.put("code", targetOrgCode);
        orgData.put("name", targetOrgName);
        orgData.put("organizationName", targetOrgName);
        orgData.put("status", "ACTIVE");
        orgData.put("createdAt", new Date());
        orgData.put("updatedAt", new Date());

        DB.collection("organizations").document(targetOrgId).set(orgData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    String memberDocId = "member_" + superAdminUid;
                    Map<String, Object> memberData = new HashMap<>();
                    memberData.put("id", memberDocId);
                    memberData.put("organizationId", targetOrgId);
                    memberData.put("userId", superAdminUid);
                    memberData.put("role", RolePermissionManager.ROLE_SUPER_ADMIN);
                    memberData.put("status", "ACTIVE");
                    memberData.put("isPrimaryAdmin", true);
                    memberData.put("fullName", superAdminName);
                    memberData.put("email", superAdminEmail);
                    memberData.put("permissions", RolePermissionManager.ALL_PERMISSIONS);
                    memberData.put("createdAt", new Date());
                    memberData.put("createdBy", superAdminUid);

                    DB.collection("organization_members").document(memberDocId).set(memberData, SetOptions.merge())
                            .addOnSuccessListener(aVoid2 -> {
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("role", "SUPER_ADMIN");
                                userData.put("status", "ACTIVE");
                                userData.put("fullName", superAdminName);
                                userData.put("email", superAdminEmail);
                                userData.put("organizationId", targetOrgId);
                                userData.put("profileComplete", true);

                                DB.collection("users").document(superAdminUid)
                                        .set(userData, SetOptions.merge())
                                        .addOnSuccessListener(aVoid3 -> {
                                            AuditLogManager.log(
                                                    targetOrgId,
                                                    superAdminUid,
                                                    superAdminEmail,
                                                    "SUPER_ADMIN",
                                                    "ADMIN_CREATED",
                                                    "ADMIN",
                                                    superAdminUid,
                                                    "Initial Super Admin organization setup completed."
                                            );
                                            postSuccess(callback);
                                        })
                                        .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                            })
                            .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
                })
                .addOnFailureListener(e -> postFailure(callback, e.getMessage()));
    }

    // =========================================================================
    // HELPER PARSERS
    // =========================================================================

    private static OrganizationModel parseOrgDoc(DocumentSnapshot doc) {
        OrganizationModel model = new OrganizationModel();
        model.setId(doc.getId());
        model.setCode(doc.getString("code"));
        model.setName(doc.getString("name") != null ? doc.getString("name") : doc.getString("organizationName"));
        model.setOrganizationName(model.getName());
        model.setAddress(doc.getString("address"));
        model.setContact(doc.getString("contact"));
        model.setEmail(doc.getString("email"));
        model.setStatus(doc.getString("status"));
        model.setPrimaryAdminId(doc.getString("primaryAdminId"));
        model.setPrimaryAdminName(doc.getString("primaryAdminName"));
        model.setPrimaryAdminEmail(doc.getString("primaryAdminEmail"));
        model.setPrimaryAdminPhone(doc.getString("primaryAdminPhone"));

        Date createdAt = doc.getDate("createdAt");
        if (createdAt != null) model.setCreatedAt(createdAt);

        Date updatedAt = doc.getDate("updatedAt");
        if (updatedAt != null) model.setUpdatedAt(updatedAt);

        return model;
    }

    private static OrganizationMemberModel parseMemberDoc(DocumentSnapshot doc) {
        OrganizationMemberModel model = new OrganizationMemberModel();
        model.setId(doc.getId());
        model.setOrganizationId(doc.getString("organizationId"));
        model.setUserId(doc.getString("userId"));
        model.setRole(doc.getString("role"));
        model.setStatus(doc.getString("status"));
        model.setFullName(doc.getString("fullName"));
        String em = doc.getString("email");
        if ((em == null || em.isEmpty() || !em.contains("@")) && doc.getString("authEmail") != null) {
            em = doc.getString("authEmail");
        }
        model.setEmail(em);
        model.setPhone(doc.getString("phone") != null ? doc.getString("phone") : doc.getString("contactNumber"));
        model.setAdminId(doc.getString("adminId"));
        model.setHospitalName(doc.getString("hospitalName"));
        model.setHospitalCode(doc.getString("hospitalCode"));
        model.setCreatedBy(doc.getString("createdBy"));

        Boolean primary = doc.getBoolean("isPrimaryAdmin");
        if (primary != null) model.setPrimaryAdmin(primary);

        Date d = doc.getDate("createdAt");
        if (d != null) model.setCreatedAt(d);

        List<String> perms = (List<String>) doc.get("permissions");
        if (perms != null) model.setPermissions(perms);

        return model;
    }

    private static DoctorInvitationModel parseInvitationDoc(DocumentSnapshot doc) {
        DoctorInvitationModel model = new DoctorInvitationModel();
        model.setId(doc.getId());
        model.setOrganizationId(doc.getString("organizationId"));
        model.setHospitalName(doc.getString("hospitalName"));
        model.setHospitalCode(doc.getString("hospitalCode"));
        model.setRegistrationId(doc.getString("registrationId"));
        model.setDoctorName(doc.getString("doctorName"));
        model.setSpecialization(doc.getString("specialization"));
        model.setEmail(doc.getString("email"));
        model.setPhone(doc.getString("phone"));
        model.setMedicalRegistrationNumber(doc.getString("medicalRegistrationNumber"));
        model.setStatus(doc.getString("status"));
        model.setCreatedBy(doc.getString("createdBy"));

        Date created = doc.getDate("createdAt");
        if (created != null) model.setCreatedAt(created);

        Date expires = doc.getDate("expiresAt");
        if (expires != null) model.setExpiresAt(expires);

        return model;
    }

    private static DoctorProfileModel parseDoctorDoc(DocumentSnapshot doc) {
        DoctorProfileModel model = new DoctorProfileModel();
        model.setUserId(doc.getId());
        model.setOrganizationId(doc.getString("organizationId"));
        String name = doc.getString("fullName");
        if (name == null || name.isEmpty()) name = doc.getString("name");
        model.setFullName(name != null ? name : "Doctor");
        model.setEmail(doc.getString("email"));
        model.setPhone(doc.getString("phone"));

        String dId = doc.getString("doctorId");
        if (dId == null || dId.isEmpty()) dId = doc.getString("doctorRegistrationId");
        if (dId == null || dId.isEmpty()) dId = doc.getString("registration_id");
        model.setDoctorRegistrationId(dId != null ? dId : "");

        model.setHospitalName(doc.getString("hospitalName"));
        model.setHospitalCode(doc.getString("hospitalCode"));
        model.setSpecialization(doc.getString("specialization"));
        model.setMedicalRegistrationNumber(doc.getString("medicalRegistrationNumber"));

        String vStatus = doc.getString("verificationStatus");
        model.setVerificationStatus(vStatus != null ? vStatus : "PENDING");

        String status = doc.getString("status");
        model.setStatus(status != null ? status : "PENDING");

        String accStatus = doc.getString("accountStatus");
        if (accStatus != null) model.setAccountStatus(accStatus);

        model.setRejectionReason(doc.getString("rejectionReason"));

        Date submitted = doc.getDate("submittedAt");
        if (submitted != null) model.setSubmittedAt(submitted);

        Date verified = doc.getDate("verifiedAt");
        if (verified != null) model.setVerifiedAt(verified);

        model.setVerifiedBy(doc.getString("verifiedBy"));
        return model;
    }

    private static PatientProfileModel parsePatientDoc(DocumentSnapshot doc) {
        PatientProfileModel model = new PatientProfileModel();
        model.setUserId(doc.getId());
        model.setFullName(doc.getString("fullName"));
        model.setEmail(doc.getString("email"));
        model.setContactNumber(doc.getString("contactNumber"));
        model.setBloodGroup(doc.getString("bloodGroup"));
        model.setPatientId(doc.getString("patientId"));

        Long ageLong = doc.getLong("age");
        if (ageLong != null) model.setAge(ageLong.intValue());

        model.setGender(doc.getString("gender"));
        String status = doc.getString("status");
        model.setStatus(status != null ? status : "ACTIVE");

        Boolean complete = doc.getBoolean("profileComplete");
        model.setProfileComplete(complete != null && complete);

        Date created = doc.getDate("createdAt");
        if (created != null) model.setCreatedAt(created);

        return model;
    }

    private static AuditLogModel parseAuditDoc(DocumentSnapshot doc) {
        AuditLogModel model = new AuditLogModel();
        model.setId(doc.getId());
        model.setOrganizationId(doc.getString("organizationId"));
        model.setActorId(doc.getString("actorId"));
        model.setActorEmail(doc.getString("actorEmail"));
        model.setActorRole(doc.getString("actorRole"));
        model.setAction(doc.getString("action"));
        model.setTargetType(doc.getString("targetType"));
        model.setTargetId(doc.getString("targetId"));
        model.setDescription(doc.getString("description"));

        Date created = doc.getDate("createdAt");
        if (created != null) model.setCreatedAt(created);

        return model;
    }

    private static boolean isToday(Date date) {
        if (date == null) return false;
        Calendar target = Calendar.getInstance();
        target.setTime(date);

        Calendar today = Calendar.getInstance();
        return target.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private static <T> void postSuccess(Callback<T> callback, T result) {
        if (callback != null) {
            MAIN_HANDLER.post(() -> callback.onSuccess(result));
        }
    }

    private static void postSuccess(ActionCallback callback) {
        if (callback != null) {
            MAIN_HANDLER.post(callback::onSuccess);
        }
    }

    private static <T> void postFailure(Callback<T> callback, String error) {
        if (callback != null) {
            MAIN_HANDLER.post(() -> callback.onFailure(error != null ? error : "An unexpected error occurred"));
        }
    }

    private static void postFailure(ActionCallback callback, String error) {
        if (callback != null) {
            MAIN_HANDLER.post(() -> callback.onFailure(error != null ? error : "An unexpected error occurred"));
        }
    }
}
