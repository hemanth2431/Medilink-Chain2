package com.example.medilinkchain;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

/**
 * ResetPasswordActivity — Provides complete end-to-end password recovery for MediLinkChain.
 * Handles both requesting reset emails and intercepting deep-link recovery codes (oobCode).
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPasswordActivity";

    private ImageView btnBackReset;
    private TextView tvHeaderTitle;

    // State 1: Request Reset
    private LinearLayout layoutRequestReset;
    private TextInputLayout tilResetEmail;
    private TextInputEditText etResetEmail;
    private MaterialButton btnSendResetLink;
    private MaterialButton btnBackToLogin;

    // State 2: Confirm Reset (from Deep Link)
    private LinearLayout layoutConfirmReset;
    private TextView tvResetEmailTarget;
    private TextInputLayout tilNewPassword;
    private TextInputEditText etNewPassword;
    private TextInputLayout tilConfirmNewPassword;
    private TextInputEditText etConfirmNewPassword;
    private MaterialButton btnSubmitNewPassword;

    private ProgressBar resetProgressBar;

    private FirebaseAuth auth;
    private String oobCode = null;
    private String recoveryEmail = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        auth = FirebaseAuth.getInstance();

        btnBackReset = findViewById(R.id.btnBackReset);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);

        layoutRequestReset = findViewById(R.id.layoutRequestReset);
        tilResetEmail = findViewById(R.id.tilResetEmail);
        etResetEmail = findViewById(R.id.etResetEmail);
        btnSendResetLink = findViewById(R.id.btnSendResetLink);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        layoutConfirmReset = findViewById(R.id.layoutConfirmReset);
        tvResetEmailTarget = findViewById(R.id.tvResetEmailTarget);
        tilNewPassword = findViewById(R.id.tilNewPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        tilConfirmNewPassword = findViewById(R.id.tilConfirmNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        btnSubmitNewPassword = findViewById(R.id.btnSubmitNewPassword);

        resetProgressBar = findViewById(R.id.resetProgressBar);

        btnBackReset.setOnClickListener(v -> finish());
        btnBackToLogin.setOnClickListener(v -> finish());
        btnSendResetLink.setOnClickListener(v -> handleSendResetEmail());
        btnSubmitNewPassword.setOnClickListener(v -> handleConfirmNewPassword());

        // Inspect Intent for incoming recovery deep links
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            showRequestState();
            return;
        }

        Uri data = intent.getData();
        if (data != null) {
            String mode = data.getQueryParameter("mode");
            String code = data.getQueryParameter("oobCode");

            if (code == null) {
                // Check if code was passed as path or alternate parameter
                code = data.getQueryParameter("code");
            }

            if (code != null && !code.trim().isEmpty()) {
                oobCode = code.trim();
                verifyResetCode(oobCode);
                return;
            }
        }

        // Check if pre-filled email was passed from LoginActivity
        String passedEmail = intent.getStringExtra("email");
        if (passedEmail != null && !passedEmail.isEmpty() && etResetEmail != null) {
            etResetEmail.setText(passedEmail);
        }

        showRequestState();
    }

    private void showRequestState() {
        if (layoutRequestReset != null) layoutRequestReset.setVisibility(View.VISIBLE);
        if (layoutConfirmReset != null) layoutConfirmReset.setVisibility(View.GONE);
        if (tvHeaderTitle != null) tvHeaderTitle.setText("Password Recovery");
    }

    private void showConfirmState(String email) {
        recoveryEmail = email;
        if (layoutRequestReset != null) layoutRequestReset.setVisibility(View.GONE);
        if (layoutConfirmReset != null) layoutConfirmReset.setVisibility(View.VISIBLE);
        if (tvHeaderTitle != null) tvHeaderTitle.setText("Set New Password");
        if (tvResetEmailTarget != null && email != null) {
            tvResetEmailTarget.setText("Reset password for: " + email);
        }
    }

    private void handleSendResetEmail() {
        String email = etResetEmail.getText() != null ? etResetEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            tilResetEmail.setError("Email address is required");
            etResetEmail.requestFocus();
            return;
        }

        if (!email.contains("@") || !email.contains(".")) {
            tilResetEmail.setError("Please enter a valid email address");
            etResetEmail.requestFocus();
            return;
        }

        tilResetEmail.setError(null);
        setLoading(true);

        auth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
            setLoading(false);
            // Present accurate confirmation message without revealing database account existence
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Check Your Inbox")
                    .setMessage("If an account exists for " + email + ", a password recovery link has been sent. Please follow the instructions in the email to securely reset your password.")
                    .setPositiveButton("Back to Sign In", (dialog, which) -> finish())
                    .setNegativeButton("Done", null)
                    .show();
        });
    }

    private void verifyResetCode(String code) {
        setLoading(true);
        auth.verifyPasswordResetCode(code).addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful() && task.getResult() != null) {
                String email = task.getResult();
                showConfirmState(email);
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Invalid or Expired Link")
                        .setMessage("This password reset link is invalid, expired, or has already been used. Please request a new password recovery link.")
                        .setPositiveButton("Request New Link", (dialog, which) -> showRequestState())
                        .setNegativeButton("Cancel", (dialog, which) -> finish())
                        .show();
            }
        });
    }

    private void handleConfirmNewPassword() {
        String newPassword = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmNewPassword.getText() != null ? etConfirmNewPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(newPassword)) {
            tilNewPassword.setError("Password is required");
            etNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            tilNewPassword.setError("Password must be at least 6 characters");
            etNewPassword.requestFocus();
            return;
        }
        tilNewPassword.setError(null);

        if (TextUtils.isEmpty(confirmPassword)) {
            tilConfirmNewPassword.setError("Please confirm your password");
            etConfirmNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            tilConfirmNewPassword.setError("Passwords do not match");
            etConfirmNewPassword.requestFocus();
            return;
        }
        tilConfirmNewPassword.setError(null);

        if (oobCode == null || oobCode.isEmpty()) {
            Toast.makeText(this, "Recovery session missing. Please request a new reset link.", Toast.LENGTH_LONG).show();
            showRequestState();
            return;
        }

        setLoading(true);

        auth.confirmPasswordReset(oobCode, newPassword).addOnCompleteListener(task -> {
            setLoading(false);
            if (task.isSuccessful()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Password Reset Successful")
                        .setMessage("Your MediLinkChain account password has been updated. You can now sign in with your new password.")
                        .setCancelable(false)
                        .setPositiveButton("Sign In Now", (dialog, which) -> {
                            Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .show();
            } else {
                String error = task.getException() != null ? task.getException().getMessage() : "Unable to reset password.";
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Password Reset Failed")
                        .setMessage(error)
                        .setPositiveButton("Try Again", null)
                        .show();
            }
        });
    }

    private void setLoading(boolean loading) {
        if (resetProgressBar != null) {
            resetProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnSendResetLink != null) {
            btnSendResetLink.setEnabled(!loading);
        }
        if (btnSubmitNewPassword != null) {
            btnSubmitNewPassword.setEnabled(!loading);
        }
    }
}
