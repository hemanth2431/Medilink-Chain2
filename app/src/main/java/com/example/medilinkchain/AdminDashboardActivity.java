package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * AdminDashboardActivity — Organization Administration Dashboard for MediLinkChain.
 * Supports SUPER_ADMIN and HOSPITAL_ADMIN roles with role-based feature gating and real-time telemetry.
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboard";

    // Header & Banner Views
    private TextView textRoleBadge;
    private TextView textOrgNameSubtitle;
    private MaterialCardView badgeRoleCard;
    private View btnRefreshStats;
    private View btnLogout;
    private MaterialCardView bannerTakeControl;
    private TextView textTakeControlTitle;
    private View btnExitTakeControl;
    private View layoutSuperAdminStatsRow;

    // Stat Values
    private TextView statTotalHospitals;
    private TextView statAdminActions;
    private TextView statTotalPatients;
    private TextView statTodayPatients;
    private TextView statTotalDoctors;
    private TextView statApprovedDoctors;
    private TextView statPendingApprovals;
    private TextView statActiveAdmins;

    // Module Navigation Cards
    private View navCardHospitalManagement;
    private View navCardUserManagement;
    private View navCardDoctorManagement;
    private TextView textPendingDocsBadge;
    private View navCardPatientManagement;
    private View navCardSystemMonitoring;
    private View navCardAuditLogs;
    private View navCardAdminManagement;
    private View navCardOrgSettings;

    // Loading overlay
    private RelativeLayout loadingOverlay;
    private TextView loadingText;

    private FirebaseAuth auth;
    private SessionManager sessionManager;
    private OrganizationMemberModel currentMember;
    private boolean isSuperAdmin = false;
    private boolean isTakeControlMode = false;
    private String takeControlOrgId = null;
    private String takeControlHospitalName = null;
    private String takeControlHospitalCode = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        // Read Take Control Intent Parameters
        if (getIntent() != null) {
            isTakeControlMode = getIntent().getBooleanExtra("takeControlMode", false);
            takeControlOrgId = getIntent().getStringExtra("targetOrgId");
            takeControlHospitalName = getIntent().getStringExtra("targetHospitalName");
            takeControlHospitalCode = getIntent().getStringExtra("targetHospitalCode");
        }

        initViews();
        setupListeners();
        loadMemberRoleAndStats();
    }

    private void initViews() {
        textRoleBadge = findViewById(R.id.textRoleBadge);
        textOrgNameSubtitle = findViewById(R.id.textOrgNameSubtitle);
        badgeRoleCard = findViewById(R.id.badgeRoleCard);
        btnRefreshStats = findViewById(R.id.btnRefreshStats);
        btnLogout = findViewById(R.id.btnLogout);

        bannerTakeControl = findViewById(R.id.bannerTakeControl);
        textTakeControlTitle = findViewById(R.id.textTakeControlTitle);
        btnExitTakeControl = findViewById(R.id.btnExitTakeControl);
        layoutSuperAdminStatsRow = findViewById(R.id.layoutSuperAdminStatsRow);

        statTotalHospitals = findViewById(R.id.statTotalHospitals);
        statAdminActions = findViewById(R.id.statAdminActions);
        statTotalPatients = findViewById(R.id.statTotalPatients);
        statTodayPatients = findViewById(R.id.statTodayPatients);
        statTotalDoctors = findViewById(R.id.statTotalDoctors);
        statApprovedDoctors = findViewById(R.id.statApprovedDoctors);
        statPendingApprovals = findViewById(R.id.statPendingApprovals);
        statActiveAdmins = findViewById(R.id.statActiveAdmins);

        navCardHospitalManagement = findViewById(R.id.navCardHospitalManagement);
        navCardUserManagement = findViewById(R.id.navCardUserManagement);
        navCardDoctorManagement = findViewById(R.id.navCardDoctorManagement);
        textPendingDocsBadge = findViewById(R.id.textPendingDocsBadge);
        navCardPatientManagement = findViewById(R.id.navCardPatientManagement);
        navCardSystemMonitoring = findViewById(R.id.navCardSystemMonitoring);
        navCardAuditLogs = findViewById(R.id.navCardAuditLogs);
        navCardAdminManagement = findViewById(R.id.navCardAdminManagement);
        navCardOrgSettings = findViewById(R.id.navCardOrgSettings);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);

        // Safe Default Values immediately (Zero blocking)
        if (statTotalHospitals != null) statTotalHospitals.setText("1");
        if (statAdminActions != null) statAdminActions.setText("0");
        if (statTotalPatients != null) statTotalPatients.setText("0");
        if (statTodayPatients != null) statTodayPatients.setText("+0 new today");
        if (statTotalDoctors != null) statTotalDoctors.setText("0");
        if (statApprovedDoctors != null) statApprovedDoctors.setText("0 Active • 0 Rejected");
        if (statPendingApprovals != null) statPendingApprovals.setText("0");
        if (textPendingDocsBadge != null) textPendingDocsBadge.setText("0 PENDING");
        if (statActiveAdmins != null) statActiveAdmins.setText("1");

        // Synchronous initial role gating from session cache (instant UI rendering)
        isSuperAdmin = sessionManager.isSuperAdmin();
        if (isSuperAdmin) {
            textRoleBadge.setText("SUPER ADMIN");
            badgeRoleCard.setCardBackgroundColor(0xFF4F46E5);
            if (navCardHospitalManagement != null) navCardHospitalManagement.setVisibility(View.VISIBLE);
            if (navCardUserManagement != null) navCardUserManagement.setVisibility(View.VISIBLE);
            if (layoutSuperAdminStatsRow != null) layoutSuperAdminStatsRow.setVisibility(View.VISIBLE);
            navCardAdminManagement.setVisibility(View.VISIBLE);
            navCardOrgSettings.setVisibility(View.VISIBLE);
        } else {
            textRoleBadge.setText("HOSPITAL ADMIN");
            badgeRoleCard.setCardBackgroundColor(0xFF1E88E5);
            if (navCardHospitalManagement != null) navCardHospitalManagement.setVisibility(View.GONE);
            if (navCardUserManagement != null) navCardUserManagement.setVisibility(View.GONE);
            if (layoutSuperAdminStatsRow != null) layoutSuperAdminStatsRow.setVisibility(View.GONE);
            navCardAdminManagement.setVisibility(View.GONE);
            navCardOrgSettings.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        btnRefreshStats.setOnClickListener(v -> {
            Toast.makeText(this, "Refreshing statistics...", Toast.LENGTH_SHORT).show();
            loadStatistics();
        });

        btnLogout.setOnClickListener(v -> performLogout());

        if (btnExitTakeControl != null) {
            btnExitTakeControl.setOnClickListener(v -> exitTakeControl());
        }

        // 0. Hospital Management (Super Admin only)
        if (navCardHospitalManagement != null) {
            navCardHospitalManagement.setOnClickListener(v -> {
                if (isSuperAdmin) {
                    startActivity(new Intent(AdminDashboardActivity.this, HospitalManagementActivity.class));
                } else {
                    Toast.makeText(this, "Access Denied: Only Super Admin can manage hospital organizations.", Toast.LENGTH_LONG).show();
                }
            });
        }

        // 0B. Global User Management (Super Admin only)
        if (navCardUserManagement != null) {
            navCardUserManagement.setOnClickListener(v -> {
                if (isSuperAdmin) {
                    startActivity(new Intent(AdminDashboardActivity.this, SuperAdminUserManagementActivity.class));
                } else {
                    Toast.makeText(this, "Access Denied: Super Admin authorization required.", Toast.LENGTH_LONG).show();
                }
            });
        }

        // 1. Doctor Management
        navCardDoctorManagement.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, DoctorManagementActivity.class);
            String orgToUse = isTakeControlMode && takeControlOrgId != null ? takeControlOrgId : (currentMember != null ? currentMember.getOrganizationId() : OrganizationRepository.DEFAULT_ORG_ID);
            intent.putExtra("orgId", orgToUse);
            intent.putExtra("isSuperAdmin", isSuperAdmin);
            startActivity(intent);
        });

        // 2. Patient Management
        navCardPatientManagement.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, PatientManagementActivity.class);
            String orgToUse = isTakeControlMode && takeControlOrgId != null ? takeControlOrgId : (currentMember != null ? currentMember.getOrganizationId() : OrganizationRepository.DEFAULT_ORG_ID);
            intent.putExtra("orgId", orgToUse);
            intent.putExtra("isSuperAdmin", isSuperAdmin);
            startActivity(intent);
        });

        // 3. System Monitoring
        navCardSystemMonitoring.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, SystemMonitoringActivity.class);
            String orgToUse = isTakeControlMode && takeControlOrgId != null ? takeControlOrgId : (currentMember != null ? currentMember.getOrganizationId() : OrganizationRepository.DEFAULT_ORG_ID);
            intent.putExtra("orgId", orgToUse);
            intent.putExtra("isSuperAdmin", isSuperAdmin);
            startActivity(intent);
        });

        // 4. Audit Logs
        navCardAuditLogs.setOnClickListener(v -> {
            Intent intent = new Intent(AdminDashboardActivity.this, AuditLogActivity.class);
            String orgToUse = isTakeControlMode && takeControlOrgId != null ? takeControlOrgId : (currentMember != null ? currentMember.getOrganizationId() : OrganizationRepository.DEFAULT_ORG_ID);
            intent.putExtra("orgId", orgToUse);
            intent.putExtra("isSuperAdmin", isSuperAdmin);
            startActivity(intent);
        });

        // 5. Admin Management (Super Admin only)
        navCardAdminManagement.setOnClickListener(v -> {
            if (isSuperAdmin) {
                startActivity(new Intent(AdminDashboardActivity.this, AdminManagementActivity.class));
            } else {
                Toast.makeText(this, "Access Denied: Only Super Admin can manage administrators.", Toast.LENGTH_LONG).show();
            }
        });

        // 6. Organization Settings (Super Admin only)
        navCardOrgSettings.setOnClickListener(v -> {
            if (isSuperAdmin) {
                startActivity(new Intent(AdminDashboardActivity.this, OrganizationSettingsActivity.class));
            } else {
                Toast.makeText(this, "Access Denied: Only Super Admin can modify organization settings.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void exitTakeControl() {
        if (!isTakeControlMode) return;

        isTakeControlMode = false;
        takeControlOrgId = null;
        takeControlHospitalName = null;
        takeControlHospitalCode = null;

        if (bannerTakeControl != null) bannerTakeControl.setVisibility(View.GONE);
        Toast.makeText(this, "Exited Take Control mode. Restored Super Admin Console.", Toast.LENGTH_SHORT).show();

        loadMemberRoleAndStats();
    }

    private void loadMemberRoleAndStats() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            performLogout();
            return;
        }

        final boolean isSessionSuperAdmin = sessionManager.isSuperAdmin()
                || (user.getEmail() != null && (
                        SuperAdminSetupHelper.SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())
                        || "superadmin@medilinkchain.org".equalsIgnoreCase(user.getEmail())
                        || "admin@medilinkchain.org".equalsIgnoreCase(user.getEmail())
                        || "admin@medilink.com".equalsIgnoreCase(user.getEmail())
                ));

        // Non-blocking verification in background
        OrganizationRepository.getUserMembership(user.getUid(), new OrganizationRepository.Callback<OrganizationMemberModel>() {
            @Override
            public void onSuccess(OrganizationMemberModel member) {
                if (isSessionSuperAdmin) {
                    member.setRole(RolePermissionManager.ROLE_SUPER_ADMIN);
                    member.setStatus("ACTIVE");
                }

                currentMember = member;
                isSuperAdmin = member.isSuperAdmin() || isSessionSuperAdmin;

                // Check disabled status
                if (!member.isActive() && !isSessionSuperAdmin) {
                    Toast.makeText(AdminDashboardActivity.this, "Your administrator account has been disabled. Please contact Super Admin.", Toast.LENGTH_LONG).show();
                    performLogout();
                    return;
                }

                // Security check: Must be ADMIN, HOSPITAL_ADMIN, or SUPER_ADMIN
                if (!member.isAdmin() && !isSessionSuperAdmin) {
                    Toast.makeText(AdminDashboardActivity.this, "Access Denied: Administrative authorization required.", Toast.LENGTH_LONG).show();
                    performLogout();
                    return;
                }

                // Update UI based on Role
                if (isSuperAdmin) {
                    textRoleBadge.setText("SUPER ADMIN");
                    badgeRoleCard.setCardBackgroundColor(0xFF4F46E5);
                    if (navCardHospitalManagement != null) navCardHospitalManagement.setVisibility(View.VISIBLE);
                    if (navCardUserManagement != null) navCardUserManagement.setVisibility(View.VISIBLE);
                    if (layoutSuperAdminStatsRow != null) layoutSuperAdminStatsRow.setVisibility(View.VISIBLE);
                    navCardAdminManagement.setVisibility(View.VISIBLE);
                    navCardOrgSettings.setVisibility(View.VISIBLE);
                } else {
                    textRoleBadge.setText("HOSPITAL ADMIN");
                    badgeRoleCard.setCardBackgroundColor(0xFF1E88E5);
                    if (navCardHospitalManagement != null) navCardHospitalManagement.setVisibility(View.GONE);
                    if (navCardUserManagement != null) navCardUserManagement.setVisibility(View.GONE);
                    if (layoutSuperAdminStatsRow != null) layoutSuperAdminStatsRow.setVisibility(View.GONE);
                    navCardAdminManagement.setVisibility(View.GONE);
                    navCardOrgSettings.setVisibility(View.GONE);
                }

                // Take Control Banner Display
                if (isTakeControlMode && takeControlHospitalName != null) {
                    if (bannerTakeControl != null) bannerTakeControl.setVisibility(View.VISIBLE);
                    if (textTakeControlTitle != null) {
                        textTakeControlTitle.setText("Super Admin Mode — Managing " + takeControlHospitalName);
                    }
                    if (textOrgNameSubtitle != null) {
                        textOrgNameSubtitle.setText(takeControlHospitalName + (takeControlHospitalCode != null ? " (" + takeControlHospitalCode + ")" : ""));
                    }
                } else {
                    if (bannerTakeControl != null) bannerTakeControl.setVisibility(View.GONE);
                    loadOrganizationName();
                }

                loadStatistics();
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to verify membership: " + error);
                loadStatistics();
            }
        });
    }

    private void loadOrganizationName() {
        if (currentMember != null && !isSuperAdmin && currentMember.getHospitalName() != null && !currentMember.getHospitalName().isEmpty()) {
            String code = currentMember.getHospitalCode() != null && !currentMember.getHospitalCode().isEmpty()
                    ? " (" + currentMember.getHospitalCode() + ")"
                    : "";
            textOrgNameSubtitle.setText(currentMember.getHospitalName() + code);
            return;
        }

        String orgId = (currentMember != null && currentMember.getOrganizationId() != null)
                ? currentMember.getOrganizationId()
                : OrganizationRepository.DEFAULT_ORG_ID;

        OrganizationRepository.getOrganization(orgId, new OrganizationRepository.Callback<OrganizationModel>() {
            @Override
            public void onSuccess(OrganizationModel org) {
                if (textOrgNameSubtitle != null) {
                    if (isSuperAdmin) {
                        textOrgNameSubtitle.setText("Global Network Central Administration");
                    } else {
                        textOrgNameSubtitle.setText(org.getName() + " (" + org.getCode() + ")");
                    }
                }
            }

            @Override
            public void onFailure(String error) {
                // Keep default subtitle
            }
        });
    }

    private void loadStatistics() {
        // NON-BLOCKING data fetch — dashboard remains fully interactive
        String orgId = (currentMember != null) ? currentMember.getOrganizationId() : OrganizationRepository.DEFAULT_ORG_ID;

        OrganizationRepository.getSystemStats(orgId, isSuperAdmin, new OrganizationRepository.Callback<OrganizationRepository.SystemStats>() {
            @Override
            public void onSuccess(OrganizationRepository.SystemStats stats) {
                if (statTotalHospitals != null) {
                    statTotalHospitals.setText(String.valueOf(stats.totalHospitals));
                }
                if (statAdminActions != null) {
                    statAdminActions.setText(String.valueOf(stats.adminActionsCount));
                }

                if (statTotalPatients != null) {
                    statTotalPatients.setText(String.valueOf(stats.totalPatients));
                }
                if (statTodayPatients != null) {
                    statTodayPatients.setText("+" + stats.todayPatients + " new today");
                }

                if (statTotalDoctors != null) {
                    statTotalDoctors.setText(String.valueOf(stats.totalDoctors));
                }
                if (statApprovedDoctors != null) {
                    statApprovedDoctors.setText(stats.approvedDoctors + " Active • " + stats.rejectedDoctors + " Rejected");
                }

                if (statPendingApprovals != null) {
                    statPendingApprovals.setText(String.valueOf(stats.pendingDoctors));
                }
                if (textPendingDocsBadge != null) {
                    textPendingDocsBadge.setText(stats.pendingDoctors + " PENDING");
                }

                if (statActiveAdmins != null) {
                    statActiveAdmins.setText(String.valueOf(stats.activeAdministrators));
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Stats load failure: " + error);
                // Graceful fallback — keep default numbers visible without freezing
            }
        });
    }

    private void showLoading(boolean show, String message) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (loadingText != null && message != null && !message.isEmpty()) {
            loadingText.setText(message);
        }
    }

    private void performLogout() {
        auth.signOut();
        sessionManager.logoutUser();
        Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!sessionManager.isLoggedIn() || auth.getCurrentUser() == null) {
            performLogout();
            return;
        }

        String userRole = sessionManager.getUserRole();
        if (!RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(userRole)
                && !RolePermissionManager.ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(userRole)
                && !RolePermissionManager.ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            Toast.makeText(this, "Access Denied: Super Admin authorization required.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        if (currentMember != null) {
            loadStatistics();
        }
    }
}
