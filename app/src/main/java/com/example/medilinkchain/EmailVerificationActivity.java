package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * EmailVerificationActivity — Mandatory email verification flow has been removed.
 * This activity automatically marks the patient account ACTIVE/verified and
 * seamlessly directs them to their Dashboard or Profile setup.
 */
public class EmailVerificationActivity extends AppCompatActivity {

    private static final String TAG = "EmailVerification";

    private ImageView btnBackVerification;
    private TextView tvRegisteredEmail;
    private MaterialButton btnContinueToDashboard;
    private TextView btnSwitchAccount;
    private ProgressBar verificationProgressBar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        btnBackVerification = findViewById(R.id.btnBackVerification);
        tvRegisteredEmail = findViewById(R.id.tvRegisteredEmail);
        btnContinueToDashboard = findViewById(R.id.btnContinueToDashboard);
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount);
        verificationProgressBar = findViewById(R.id.verificationProgressBar);

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            redirectToLogin();
            return;
        }

        if (tvRegisteredEmail != null) {
            tvRegisteredEmail.setText(user.getEmail() != null ? user.getEmail() : "your account");
        }

        if (btnBackVerification != null) {
            btnBackVerification.setOnClickListener(v -> proceedToDashboard(user));
        }

        if (btnContinueToDashboard != null) {
            btnContinueToDashboard.setOnClickListener(v -> proceedToDashboard(user));
        }

        if (btnSwitchAccount != null) {
            btnSwitchAccount.setOnClickListener(v -> handleSignOutAndSwitch());
        }

        // Direct auto-progression
        proceedToDashboard(user);
    }

    private void proceedToDashboard(FirebaseUser user) {
        if (user == null) {
            redirectToLogin();
            return;
        }

        setLoading(true);
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";

        // Ensure user is marked ACTIVE and verified
        Map<String, Object> updates = new HashMap<>();
        updates.put("emailVerified", true);
        updates.put("status", "ACTIVE");

        db.collection("users").document(uid).update(updates)
                .addOnCompleteListener(t -> {
                    sessionManager.createLoginSession(
                            email,
                            RolePermissionManager.ROLE_PATIENT,
                            OrganizationRepository.DEFAULT_ORG_ID,
                            "ACTIVE",
                            "APPROVED"
                    );

                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                setLoading(false);
                                boolean profileComplete = false;
                                if (doc != null && doc.exists()) {
                                    Boolean comp = doc.getBoolean("profileComplete");
                                    if (comp != null) profileComplete = comp;
                                }

                                Intent nextIntent = profileComplete
                                        ? new Intent(EmailVerificationActivity.this, DashboardActivity.class)
                                        : new Intent(EmailVerificationActivity.this, CompleteProfileActivity.class);

                                nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(nextIntent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                Intent nextIntent = new Intent(EmailVerificationActivity.this, DashboardActivity.class);
                                nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(nextIntent);
                                finish();
                            });
                });
    }

    private void handleSignOutAndSwitch() {
        auth.signOut();
        sessionManager.logoutUser();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(EmailVerificationActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        if (verificationProgressBar != null) {
            verificationProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnContinueToDashboard != null) {
            btnContinueToDashboard.setEnabled(!loading);
        }
    }
}
