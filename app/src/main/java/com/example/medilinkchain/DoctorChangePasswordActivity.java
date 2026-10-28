package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * DoctorChangePasswordActivity — Forced password change screen for Doctors on first login.
 *
 * Flow:
 *   1. Doctor logs in for the first time (first_login == true in Firestore)
 *   2. LoginActivity routes here instead of DoctorDashboardActivity
 *   3. Doctor enters current temp password + new password
 *   4. Re-authenticates with Firebase (to allow password update)
 *   5. Updates password in Firebase Auth
 *   6. Sets first_login = false in Firestore
 *   7. Routes to DoctorDashboardActivity
 */
public class DoctorChangePasswordActivity extends AppCompatActivity {

    private static final String TAG = "DoctorChangePwd";

    private EditText editCurrentPassword, editNewPassword, editConfirmNewPassword;
    private ImageView btnToggleCurrentPwd, btnToggleNewPwd, btnToggleConfirmPwd;
    private MaterialButton btnChangePassword;
    private TextView textChangePasswordError;
    private ProgressBar progressChangePassword;

    private boolean showCurrentPwd = false;
    private boolean showNewPwd = false;
    private boolean showConfirmPwd = false;

    private FirebaseAuth auth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_change_password);

        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        // Security check: must be logged in as a Doctor
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        editCurrentPassword = findViewById(R.id.editCurrentPassword);
        editNewPassword = findViewById(R.id.editNewPassword);
        editConfirmNewPassword = findViewById(R.id.editConfirmNewPassword);
        btnToggleCurrentPwd = findViewById(R.id.btnToggleCurrentPwd);
        btnToggleNewPwd = findViewById(R.id.btnToggleNewPwd);
        btnToggleConfirmPwd = findViewById(R.id.btnToggleConfirmPwd);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        textChangePasswordError = findViewById(R.id.textChangePasswordError);
        progressChangePassword = findViewById(R.id.progressChangePassword);
    }

    private void setupListeners() {
        if (btnToggleCurrentPwd != null) {
            btnToggleCurrentPwd.setOnClickListener(v -> {
                showCurrentPwd = !showCurrentPwd;
                editCurrentPassword.setInputType(showCurrentPwd
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                editCurrentPassword.setSelection(editCurrentPassword.getText().length());
            });
        }

        if (btnToggleNewPwd != null) {
            btnToggleNewPwd.setOnClickListener(v -> {
                showNewPwd = !showNewPwd;
                editNewPassword.setInputType(showNewPwd
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                editNewPassword.setSelection(editNewPassword.getText().length());
            });
        }

        if (btnToggleConfirmPwd != null) {
            btnToggleConfirmPwd.setOnClickListener(v -> {
                showConfirmPwd = !showConfirmPwd;
                editConfirmNewPassword.setInputType(showConfirmPwd
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                editConfirmNewPassword.setSelection(editConfirmNewPassword.getText().length());
            });
        }

        if (btnChangePassword != null) {
            btnChangePassword.setOnClickListener(v -> performPasswordChange());
        }

        // Prevent going back — the Doctor MUST change their password
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(DoctorChangePasswordActivity.this, "You must set a new password before continuing.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performPasswordChange() {
        String currentPassword = editCurrentPassword.getText().toString().trim();
        String newPassword = editNewPassword.getText().toString().trim();
        String confirmPassword = editConfirmNewPassword.getText().toString().trim();

        // Validate inputs
        if (currentPassword.isEmpty()) {
            showError("Please enter your current temporary password.");
            return;
        }
        if (newPassword.isEmpty()) {
            showError("Please enter a new password.");
            return;
        }
        if (newPassword.length() < 8) {
            showError("New password must be at least 8 characters long.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            showError("New passwords do not match.");
            return;
        }
        if (newPassword.equals(currentPassword)) {
            showError("New password must be different from your temporary password.");
            return;
        }

        showLoading(true);
        clearError();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            showLoading(false);
            showError("Session expired. Please log in again.");
            navigateToLogin();
            return;
        }

        // Re-authenticate with current (temporary) password first
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnCompleteListener(reAuthTask -> {
                    if (reAuthTask.isSuccessful()) {
                        // Now update to new password
                        user.updatePassword(newPassword)
                                .addOnCompleteListener(updateTask -> {
                                    if (updateTask.isSuccessful()) {
                                        // Mark first_login = false in Firestore
                                        markFirstLoginComplete(user.getUid());
                                    } else {
                                        showLoading(false);
                                        String msg = (updateTask.getException() != null)
                                                ? updateTask.getException().getMessage()
                                                : "Update failed";
                                        showError("Failed to update password: " + msg);
                                    }
                                });
                    } else {
                        showLoading(false);
                        String msg = (reAuthTask.getException() != null)
                                ? reAuthTask.getException().getMessage()
                                : "Re-authentication failed";
                        showError("Incorrect current password: " + msg);
                    }
                });
    }

    private void markFirstLoginComplete(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> updates = new HashMap<>();
        updates.put("first_login", false);
        updates.put("firstLogin", false);
        updates.put("updated_at", new Date());

        // Update both users and doctor_profiles collections
        db.collection("users").document(uid)
                .update(updates)
                .addOnCompleteListener(task -> {
                    // Also update doctor_profiles
                    db.collection("doctor_profiles")
                            .whereEqualTo("user_id", uid)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(qs -> {
                                if (!qs.isEmpty()) {
                                    qs.getDocuments().get(0).getReference().update(updates);
                                }
                            });

                    showLoading(false);
                    Toast.makeText(DoctorChangePasswordActivity.this,
                            "Password updated successfully! Welcome to MediLinkChain.",
                            Toast.LENGTH_SHORT).show();

                    // Navigate to Doctor Dashboard
                    Intent intent = new Intent(DoctorChangePasswordActivity.this, DoctorDashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void showLoading(boolean show) {
        if (progressChangePassword != null) {
            progressChangePassword.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnChangePassword != null) {
            btnChangePassword.setEnabled(!show);
            btnChangePassword.setText(show ? "Updating Password..." : "Set New Password & Continue");
        }
    }

    private void showError(String message) {
        if (textChangePasswordError != null) {
            textChangePasswordError.setText(message);
            textChangePasswordError.setVisibility(View.VISIBLE);
        }
    }

    private void clearError() {
        if (textChangePasswordError != null) {
            textChangePasswordError.setText("");
            textChangePasswordError.setVisibility(View.GONE);
        }
    }

    private void navigateToLogin() {
        auth.signOut();
        sessionManager.logoutUser();
        Intent intent = new Intent(DoctorChangePasswordActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
