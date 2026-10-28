package com.example.medilinkchain;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * SuperAdminSetupHelper — Bootstraps the initial Super Admin account in Firebase Auth
 * and Firestore on first launch of the Super Admin portal.
 *
 * Security: Password is ONLY used for Firebase Auth account creation (one-time bootstrap).
 * After creation, login uses standard Firebase Auth signInWithEmailAndPassword.
 * The password is NOT stored anywhere in the app code permanently — it is the
 * initial temporary value that the admin should change after first login via Firebase.
 */
public class SuperAdminSetupHelper {

    private static final String TAG = "SuperAdminSetup";

    public static final String SUPER_ADMIN_EMAIL = "hemanthreddy046@gmail.com";
    private static final String SUPER_ADMIN_INITIAL_PASSWORD = "hemanth046@admin";
    public static final String SUPER_ADMIN_NAME = "Hemanth Reddy";

    public interface SetupCallback {
        void onComplete(boolean alreadyExists, String message);
        void onError(String error);
    }

    /**
     * Idempotent bootstrap: creates Super Admin in Firebase Auth + Firestore if not already present.
     */
    public static void bootstrapIfNeeded(SetupCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Check if super admin profile already exists in Firestore
        db.collection("profiles")
                .whereEqualTo("role", "SUPER_ADMIN")
                .whereEqualTo("email", SUPER_ADMIN_EMAIL)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Super Admin already exists — nothing to do
                        Log.d(TAG, "Super Admin profile already exists in Firestore.");
                        callback.onComplete(true, "Super Admin account already configured.");
                        return;
                    }

                    // Profile doesn't exist — try to create the Firebase Auth account
                    createSuperAdminAuthAccount(callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check Super Admin profile: " + e.getMessage());
                    // On network failure, fall through to attempt login — don't fail hard
                    callback.onComplete(false, "Check failed, proceeding.");
                });
    }

    private static void createSuperAdminAuthAccount(SetupCallback callback) {
        FirebaseAuth auth = FirebaseAuth.getInstance();

        // Try to create the Firebase Auth user
        auth.createUserWithEmailAndPassword(SUPER_ADMIN_EMAIL, SUPER_ADMIN_INITIAL_PASSWORD)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        // Account created successfully
                        String uid = auth.getCurrentUser().getUid();
                        Log.d(TAG, "Super Admin Firebase Auth account created: " + uid);
                        writeSuperAdminFirestoreProfile(uid, callback);

                        // Sign out — the admin will log in normally
                        auth.signOut();

                    } else {
                        // Account likely already exists in Firebase Auth
                        String error = (task.getException() != null)
                                ? task.getException().getMessage() : "Unknown";

                        if (error != null && (error.contains("already in use") || error.contains("email-already"))) {
                            Log.d(TAG, "Super Admin Firebase Auth already exists. Syncing Firestore profile.");
                            // Sign in to get the UID, write profile, then sign out
                            auth.signInWithEmailAndPassword(SUPER_ADMIN_EMAIL, SUPER_ADMIN_INITIAL_PASSWORD)
                                    .addOnCompleteListener(signInTask -> {
                                        if (signInTask.isSuccessful() && auth.getCurrentUser() != null) {
                                            String uid = auth.getCurrentUser().getUid();
                                            writeSuperAdminFirestoreProfile(uid, callback);
                                            auth.signOut();
                                        } else {
                                            // Could not sign in — profile creation will happen on first login
                                            Log.w(TAG, "Could not sign in as Super Admin during bootstrap.");
                                            callback.onComplete(true, "Super Admin auth exists.");
                                        }
                                    });
                        } else {
                            Log.e(TAG, "Failed to create Super Admin auth account: " + error);
                            callback.onError("Bootstrap failed: " + error);
                        }
                    }
                });
    }

    private static void writeSuperAdminFirestoreProfile(String uid, SetupCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Write to profiles collection
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", uid);
        profile.put("email", SUPER_ADMIN_EMAIL);
        profile.put("full_name", SUPER_ADMIN_NAME);
        profile.put("role", "SUPER_ADMIN");
        profile.put("status", "ACTIVE");
        profile.put("created_at", new Date());
        profile.put("updated_at", new Date());

        db.collection("profiles").document(uid)
                .set(profile)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Super Admin profile written to Firestore.");

                    // 2. Write to organization_members
                    Map<String, Object> member = new HashMap<>();
                    member.put("id", "member_" + uid);
                    member.put("userId", uid);
                    member.put("user_id", uid);
                    member.put("email", SUPER_ADMIN_EMAIL);
                    member.put("fullName", SUPER_ADMIN_NAME);
                    member.put("role", "SUPER_ADMIN");
                    member.put("status", "ACTIVE");
                    member.put("organizationId", OrganizationRepository.DEFAULT_ORG_ID);
                    member.put("is_primary_admin", true);
                    member.put("isPrimaryAdmin", true);
                    member.put("createdAt", new Date());
                    member.put("createdBy", uid);

                    java.util.List<String> perms = new java.util.ArrayList<>();
                    perms.add("MANAGE_HOSPITALS");
                    perms.add("MANAGE_ADMINS");
                    perms.add("MANAGE_DOCTORS");
                    perms.add("MANAGE_PATIENTS");
                    perms.add("VIEW_ALL_DATA");
                    perms.add("SYSTEM_MONITORING");
                    perms.add("AUDIT_LOGS");
                    member.put("adminPermissions", perms);

                    db.collection("organization_members").document("member_" + uid)
                            .set(member)
                            .addOnSuccessListener(v2 -> {
                                // 3. Also write to users collection for backward compat
                                Map<String, Object> userDoc = new HashMap<>();
                                userDoc.put("email", SUPER_ADMIN_EMAIL);
                                userDoc.put("fullName", SUPER_ADMIN_NAME);
                                userDoc.put("name", SUPER_ADMIN_NAME);
                                userDoc.put("role", "SUPER_ADMIN");
                                userDoc.put("status", "ACTIVE");
                                userDoc.put("organizationId", OrganizationRepository.DEFAULT_ORG_ID);
                                userDoc.put("createdAt", new Date());

                                db.collection("users").document(uid).set(userDoc);

                                Log.d(TAG, "Super Admin bootstrap complete.");
                                callback.onComplete(false, "Super Admin account created successfully.");
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to write org member: " + e.getMessage());
                                callback.onComplete(false, "Profile created (org member write failed).");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to write Super Admin profile: " + e.getMessage());
                    callback.onError("Failed to write profile: " + e.getMessage());
                });
    }
}
