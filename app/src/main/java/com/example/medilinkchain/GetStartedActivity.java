package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * GetStartedActivity — Welcome / Get Started Screen for MediLinkChain.
 *
 * Visual Features:
 *   - Blue healthcare hero section with medical patterns and logo
 *   - Smooth curved transition to white body
 *   - Two-column content layout with headline and 3D healthcare smartphone illustration
 *   - Single "Get Started" gradient button leading to authentication
 *   - Security badge footer
 */
public class GetStartedActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        auth = FirebaseAuth.getInstance();

        // Silently bootstrap Super Admin account on first launch (no UI shown)
        SuperAdminSetupHelper.bootstrapIfNeeded(new SuperAdminSetupHelper.SetupCallback() {
            @Override public void onComplete(boolean alreadyExists, String message) {}
            @Override public void onError(String error) {}
        });

        // Resume session if user is already logged in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && sessionManager.isLoggedIn()) {
            Intent intent;
            String role = sessionManager.getUserRole();
            String verificationStatus = sessionManager.getVerificationStatus();

            if (RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(role)
                    || RolePermissionManager.ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(role)
                    || RolePermissionManager.ROLE_ADMIN.equalsIgnoreCase(role)) {
                intent = new Intent(GetStartedActivity.this, AdminDashboardActivity.class);
            } else if (RolePermissionManager.ROLE_DOCTOR.equalsIgnoreCase(role)) {
                if ("APPROVED".equalsIgnoreCase(verificationStatus)) {
                    intent = new Intent(GetStartedActivity.this, DoctorDashboardActivity.class);
                } else {
                    intent = new Intent(GetStartedActivity.this, DoctorVerificationStatusActivity.class);
                }
            } else {
                // Direct patient login without email verification gating
                intent = new Intent(GetStartedActivity.this, DashboardActivity.class);
            }

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Get Started Button — Navigates to the common authentication screen
        View getStartedBtn = findViewById(R.id.getStartedBtn);
        if (getStartedBtn != null) {
            getStartedBtn.setOnClickListener(v -> {
                Intent intent = new Intent(GetStartedActivity.this, LoginActivity.class);
                startActivity(intent);
            });
        }
    }
}
