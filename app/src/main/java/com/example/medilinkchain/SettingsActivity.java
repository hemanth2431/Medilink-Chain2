package com.example.medilinkchain;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executor;

/**
 * SettingsActivity — Security & Preferences Screen.
 *
 * Exclusively contains the 7 designated options:
 *   1. Edit Profile
 *   2. Change Password
 *   3. Biometric Login (Real BiometricManager & BiometricPrompt integration)
 *   4. Privacy Policy
 *   5. Terms of Service
 *   6. App Version (Dynamic BuildConfig / PackageInfo)
 *   7. Logout (Confirmation modal with session cleanup & backstack removal)
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_SETTINGS = "MediLinkSettingsPrefs";
    private static final String KEY_BIOMETRIC_ENABLED = "BiometricLoginEnabled";

    private FirebaseAuth auth;
    private SessionManager sessionManager;
    private SharedPreferences sharedPreferences;

    private SwitchCompat switchBiometric;
    private TextView textAppVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        sharedPreferences = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);

        initHeader();
        setupAccountSection();
        setupPrivacyAndSecuritySection();
        setupAboutSection();
        setupLogoutSection();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Header
    // ──────────────────────────────────────────────────────────────────────────

    private void initHeader() {
        View backArrow = findViewById(R.id.backArrow);
        if (backArrow != null) {
            backArrow.setOnClickListener(v -> finish());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. ACCOUNT SECTION (Edit Profile & Change Password)
    // ──────────────────────────────────────────────────────────────────────────

    private void setupAccountSection() {
        // 1. Edit Profile
        View settingEditProfile = findViewById(R.id.settingEditProfile);
        if (settingEditProfile != null) {
            settingEditProfile.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, EditProfileActivity.class);
                startActivity(intent);
            });
        }

        // 2. Change Password
        View settingChangePassword = findViewById(R.id.settingChangePassword);
        if (settingChangePassword != null) {
            settingChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        }
    }

    private void showChangePasswordDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "No active authenticated session found.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText inputCurrentPassword = dialogView.findViewById(R.id.inputCurrentPassword);
        EditText inputNewPassword = dialogView.findViewById(R.id.inputNewPassword);
        EditText inputConfirmNewPassword = dialogView.findViewById(R.id.inputConfirmNewPassword);

        ImageView btnToggleCurrent = dialogView.findViewById(R.id.btnToggleCurrentPassword);
        ImageView btnToggleNew = dialogView.findViewById(R.id.btnToggleNewPassword);
        ImageView btnToggleConfirm = dialogView.findViewById(R.id.btnToggleConfirmNewPassword);

        ProgressBar progressBar = dialogView.findViewById(R.id.progressChangePassword);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelPasswordChange);
        MaterialButton btnSubmit = dialogView.findViewById(R.id.btnSubmitPasswordChange);

        // Password toggles
        setupEyeToggle(btnToggleCurrent, inputCurrentPassword);
        setupEyeToggle(btnToggleNew, inputNewPassword);
        setupEyeToggle(btnToggleConfirm, inputConfirmNewPassword);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String currentPass = inputCurrentPassword.getText().toString().trim();
            String newPass = inputNewPassword.getText().toString().trim();
            String confirmPass = inputConfirmNewPassword.getText().toString().trim();

            if (currentPass.isEmpty()) {
                inputCurrentPassword.setError("Current password is required");
                inputCurrentPassword.requestFocus();
                return;
            }
            if (newPass.isEmpty()) {
                inputNewPassword.setError("New password is required");
                inputNewPassword.requestFocus();
                return;
            }
            if (newPass.length() < 6) {
                inputNewPassword.setError("Password must be at least 6 characters");
                inputNewPassword.requestFocus();
                return;
            }
            if (!newPass.equals(confirmPass)) {
                inputConfirmNewPassword.setError("Passwords do not match");
                inputConfirmNewPassword.requestFocus();
                return;
            }

            // Re-authenticate user before changing password
            progressBar.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);
            btnCancel.setEnabled(false);

            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);
            user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
                if (reauthTask.isSuccessful()) {
                    user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                        progressBar.setVisibility(View.GONE);
                        if (updateTask.isSuccessful()) {
                            dialog.dismiss();
                            new MaterialAlertDialogBuilder(SettingsActivity.this)
                                    .setTitle("Password Updated")
                                    .setMessage("Your password has been changed successfully.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        } else {
                            btnSubmit.setEnabled(true);
                            btnCancel.setEnabled(true);
                            String err = updateTask.getException() != null ? updateTask.getException().getMessage() : "Failed to update password";
                            Toast.makeText(SettingsActivity.this, err, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    btnCancel.setEnabled(true);
                    inputCurrentPassword.setError("Incorrect current password");
                    inputCurrentPassword.requestFocus();
                }
            });
        });

        dialog.show();
    }

    private void setupEyeToggle(ImageView icon, EditText editText) {
        if (icon == null || editText == null) return;
        icon.setOnClickListener(v -> {
            boolean isVisible = editText.getInputType() == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            if (isVisible) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                icon.setImageResource(R.drawable.ic_eye_off);
                icon.setColorFilter(getColor(R.color.colorTextDarkMuted));
            } else {
                editText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                icon.setImageResource(R.drawable.ic_eye_custom);
                icon.setColorFilter(getColor(R.color.colorAccentBlue));
            }
            editText.setSelection(editText.getText().length());
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. PRIVACY & SECURITY SECTION (Biometric Login)
    // ──────────────────────────────────────────────────────────────────────────

    private void setupPrivacyAndSecuritySection() {
        switchBiometric = findViewById(R.id.switchBiometric);
        if (switchBiometric == null) return;

        boolean isEnabled = sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, false);
        switchBiometric.setChecked(isEnabled);

        switchBiometric.setOnClickListener(v -> {
            boolean willEnable = switchBiometric.isChecked();
            if (willEnable) {
                // Verify hardware & enroll status
                BiometricManager biometricManager = BiometricManager.from(this);
                int canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);

                switch (canAuth) {
                    case BiometricManager.BIOMETRIC_SUCCESS:
                        promptBiometricEnrollment();
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                        switchBiometric.setChecked(false);
                        Toast.makeText(this, "Biometric sensors not available on this device.", Toast.LENGTH_LONG).show();
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                        switchBiometric.setChecked(false);
                        Toast.makeText(this, "Biometric sensor is currently unavailable.", Toast.LENGTH_SHORT).show();
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                        switchBiometric.setChecked(false);
                        Toast.makeText(this, "No biometrics enrolled. Please set up fingerprint or face unlock in system settings.", Toast.LENGTH_LONG).show();
                        break;
                    default:
                        switchBiometric.setChecked(false);
                        Toast.makeText(this, "Biometric authentication cannot be used on this device.", Toast.LENGTH_SHORT).show();
                        break;
                }
            } else {
                sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply();
                Toast.makeText(this, "Biometric login disabled.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void promptBiometricEnrollment() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(SettingsActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, true).apply();
                switchBiometric.setChecked(true);
                Toast.makeText(SettingsActivity.this, "Biometric login enabled successfully.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                switchBiometric.setChecked(false);
                sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_ENABLED, false).apply();
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(SettingsActivity.this, "Biometric setup error: " + errString, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(SettingsActivity.this, "Biometric verification failed. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable Biometric Login")
                .setSubtitle("Verify your identity to enable biometric sign-in")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. ABOUT & SUPPORT SECTION (Privacy Policy, Terms of Service, App Version)
    // ──────────────────────────────────────────────────────────────────────────

    private void setupAboutSection() {
        // 4. Privacy Policy
        View settingPrivacyPolicy = findViewById(R.id.settingPrivacyPolicy);
        if (settingPrivacyPolicy != null) {
            settingPrivacyPolicy.setOnClickListener(v -> showInfoDialog(
                    "Privacy Policy",
                    "MediLink Chain Privacy & Data Protection Policy\n\n" +
                    "1. Data Privacy & Ownership:\n" +
                    "Your personal health records (EHR) and biometric identities remain strictly your property. No medical record is shared or exposed to third parties without verified patient consent.\n\n" +
                    "2. Encryption Standards:\n" +
                    "All medical data is end-to-end encrypted at rest and in transit using cryptographic hash chains and secure cloud vaults.\n\n" +
                    "3. Regulatory Compliance:\n" +
                    "MediLink Chain is designed in strict compliance with HIPAA (Health Insurance Portability and Accountability Act) and GDPR healthcare standards.\n\n" +
                    "4. Access Auditing:\n" +
                    "Every access attempt by healthcare providers and doctors is immutably logged with verifiable timestamps and cryptographic proofs."
            ));
        }

        // 5. Terms of Service
        View settingTerms = findViewById(R.id.settingTerms);
        if (settingTerms != null) {
            settingTerms.setOnClickListener(v -> showInfoDialog(
                    "Terms of Service",
                    "MediLink Chain Platform Terms of Service\n\n" +
                    "1. Account Responsibilities:\n" +
                    "You are responsible for maintaining the confidentiality of your credentials and monitoring permissions granted to healthcare providers.\n\n" +
                    "2. Medical Disclaimer:\n" +
                    "MediLink Chain is a secure healthcare record infrastructure and communication network. It does not replace professional emergency clinical diagnoses.\n\n" +
                    "3. Service Availability:\n" +
                    "We maintain continuous security monitoring and high availability for uninterrupted patient-doctor access.\n\n" +
                    "4. Termination & Export:\n" +
                    "You may request a cryptographic export or deletion of your profile data at any time through authorized hospital administrators."
            ));
        }

        // 6. App Version (Dynamic Version)
        textAppVersion = findViewById(R.id.textAppVersion);
        if (textAppVersion != null) {
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                textAppVersion.setText("v" + pInfo.versionName + " (Build " + pInfo.versionCode + ")");
            } catch (PackageManager.NameNotFoundException e) {
                textAppVersion.setText("v" + BuildConfig.VERSION_NAME);
            }
        }
    }

    private void showInfoDialog(String title, String content) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_info_sheet, null);
        TextView dialogTitle = dialogView.findViewById(R.id.dialogInfoTitle);
        TextView dialogBody = dialogView.findViewById(R.id.dialogInfoBody);
        MaterialButton closeBtn = dialogView.findViewById(R.id.dialogInfoCloseBtn);

        dialogTitle.setText(title);
        dialogBody.setText(content);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. DANGER ZONE: LOGOUT
    // ──────────────────────────────────────────────────────────────────────────

    private void setupLogoutSection() {
        View settingLogout = findViewById(R.id.settingLogout);
        if (settingLogout != null) {
            settingLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
        }
    }

    private void showLogoutConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .show();
    }

    private void performLogout() {
        try {
            auth.signOut();
            sessionManager.logoutUser();

            Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Logout error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
