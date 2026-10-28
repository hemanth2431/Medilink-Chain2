package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * SignupActivity — Patient Account Registration Screen for MediLinkChain.
 *
 * Implements direct registration and authentication:
 * - Direct login without mandatory email verification
 * - Supabase + Firebase user identity creation
 * - Session verification before dashboard routing
 * - Friendly error handling for duplicate emails and network issues
 */
public class SignupActivity extends AppCompatActivity {

    private EditText signupEmail, signupPassword, signupConfirmPassword;
    private RelativeLayout signupBtn;
    private TextView textSignupBtn, loginBtn;
    private ImageView btnTogglePassword, btnToggleConfirmPassword;
    private RelativeLayout loadingOverlay;

    private FirebaseAuth auth;
    private SessionManager sessionManager;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        initViews();
        disableAutofillAndClearFields();
        setupListeners();
    }

    private void initViews() {
        signupEmail              = findViewById(R.id.signupEmail);
        signupPassword           = findViewById(R.id.signupPassword);
        signupConfirmPassword    = findViewById(R.id.signupConfirmPassword);
        signupBtn                = findViewById(R.id.signupBtn);
        textSignupBtn            = findViewById(R.id.textSignupBtn);
        loginBtn                 = findViewById(R.id.loginBtn);
        btnTogglePassword        = findViewById(R.id.btn_toggle_password);
        btnToggleConfirmPassword = findViewById(R.id.btn_toggle_confirm_password);
        loadingOverlay           = findViewById(R.id.loadingOverlay);
    }

    private void disableAutofillAndClearFields() {
        EditText[] fields = new EditText[] { signupEmail, signupPassword, signupConfirmPassword };
        for (EditText field : fields) {
            if (field != null) {
                field.setText("");
                field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
                field.clearFocus();
            }
        }
    }

    private void setupListeners() {
        // Toggle Password Visibility
        if (btnTogglePassword != null) {
            btnTogglePassword.setOnClickListener(v -> {
                isPasswordVisible = !isPasswordVisible;
                if (isPasswordVisible) {
                    signupPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnTogglePassword.setImageResource(R.drawable.ic_eye_custom);
                    btnTogglePassword.setColorFilter(getColor(R.color.colorAccentBlue));
                } else {
                    signupPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnTogglePassword.setImageResource(R.drawable.ic_eye_off);
                    btnTogglePassword.setColorFilter(getColor(R.color.colorTextDarkMuted));
                }
                signupPassword.setSelection(signupPassword.getText().length());
            });
        }

        // Toggle Confirm Password Visibility
        if (btnToggleConfirmPassword != null) {
            btnToggleConfirmPassword.setOnClickListener(v -> {
                isConfirmPasswordVisible = !isConfirmPasswordVisible;
                if (isConfirmPasswordVisible) {
                    signupConfirmPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnToggleConfirmPassword.setImageResource(R.drawable.ic_eye_custom);
                    btnToggleConfirmPassword.setColorFilter(getColor(R.color.colorAccentBlue));
                } else {
                    signupConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnToggleConfirmPassword.setImageResource(R.drawable.ic_eye_off);
                    btnToggleConfirmPassword.setColorFilter(getColor(R.color.colorTextDarkMuted));
                }
                signupConfirmPassword.setSelection(signupConfirmPassword.getText().length());
            });
        }

        // Sign In link redirection
        if (loginBtn != null) {
            loginBtn.setOnClickListener(v -> {
                startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                finish();
            });
        }

        // Create Account Action
        if (signupBtn != null) {
            signupBtn.setOnClickListener(v -> handleSignup());
        }
    }

    private void handleSignup() {
        String email = signupEmail.getText().toString().trim();
        String password = signupPassword.getText().toString().trim();
        String confirmPassword = signupConfirmPassword.getText().toString().trim();

        // 1. Email Validation
        if (email.isEmpty()) {
            signupEmail.setError("Email address is required");
            signupEmail.requestFocus();
            return;
        }

        if (!email.contains("@") || !email.contains(".")) {
            signupEmail.setError("Please enter a valid email address");
            signupEmail.requestFocus();
            return;
        }

        // 2. Password Validation
        if (password.isEmpty()) {
            signupPassword.setError("Password is required");
            signupPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            signupPassword.setError("Password must be at least 6 characters long");
            signupPassword.requestFocus();
            return;
        }

        // 3. Confirm Password Validation
        if (confirmPassword.isEmpty()) {
            signupConfirmPassword.setError("Please confirm your password");
            signupConfirmPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            signupConfirmPassword.setError("Passwords do not match");
            signupConfirmPassword.requestFocus();
            return;
        }

        // Prevent multiple simultaneous submissions
        showLoading(true);

        // 4. Supabase & Firebase Registration
        SupabaseAuthHelper.signUp(email, password, "PATIENT", new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(@NonNull SupabaseAuthHelper.AuthResult result) {
                completeFirebaseAndFirestoreSignup(email, password, result);
            }

            @Override
            public void onFailure(@NonNull String errorMessage, boolean isDuplicateEmail) {
                if (isDuplicateEmail) {
                    showLoading(false);
                    Toast.makeText(SignupActivity.this, "An account with this email address already exists. Please sign in instead.", Toast.LENGTH_LONG).show();
                } else {
                    // If Supabase encountered network issue, proceed with Firebase creation
                    completeFirebaseAndFirestoreSignup(email, password, null);
                }
            }
        });
    }

    private void completeFirebaseAndFirestoreSignup(String email, String password, SupabaseAuthHelper.AuthResult supabaseResult) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        FirebaseUser user = auth.getCurrentUser();
                        String uid = user.getUid();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        String patientId = "MLC" + uid.substring(0, Math.min(uid.length(), 8)).toUpperCase();

                        // 5. Build Active Patient Profile (Email verified automatically)
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("patientId", patientId);
                        userData.put("email", email);
                        userData.put("role", "patient");
                        userData.put("status", "ACTIVE");
                        userData.put("emailVerified", true);
                        userData.put("profileComplete", false);
                        userData.put("createdAt", new Date());
                        if (supabaseResult != null && !supabaseResult.userId.isEmpty()) {
                            userData.put("supabaseUid", supabaseResult.userId);
                        }

                        db.collection("users").document(uid).set(userData)
                                .addOnCompleteListener(dbTask -> {
                                    // 6. Organization member entry
                                    String memberId = "member_" + uid;
                                    Map<String, Object> memberData = new HashMap<>();
                                    memberData.put("id", memberId);
                                    memberData.put("userId", uid);
                                    memberData.put("email", email);
                                    memberData.put("organizationId", OrganizationRepository.DEFAULT_ORG_ID);
                                    memberData.put("role", "PATIENT");
                                    memberData.put("status", "ACTIVE");
                                    memberData.put("createdAt", new Date());
                                    memberData.put("createdBy", uid);

                                    db.collection("organization_members").document(memberId).set(memberData);

                                    // Save Supabase credentials if available
                                    if (supabaseResult != null && supabaseResult.sessionAvailable) {
                                        sessionManager.saveSupabaseSession(supabaseResult.accessToken, supabaseResult.userId);
                                    }

                                    // 7. Check session availability
                                    boolean hasValidSession = (supabaseResult != null && supabaseResult.sessionAvailable)
                                            || auth.getCurrentUser() != null;

                                    if (supabaseResult != null && !supabaseResult.sessionAvailable && auth.getCurrentUser() == null) {
                                        showLoading(false);
                                        new MaterialAlertDialogBuilder(SignupActivity.this)
                                                .setTitle("Account Created")
                                                .setMessage("Your account has been created successfully, but your login session is not yet active. Please log in using your email and password.")
                                                .setPositiveButton("Go to Login", (dialog, which) -> {
                                                    Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                                                    intent.putExtra("email", email);
                                                    startActivity(intent);
                                                    finish();
                                                })
                                                .setCancelable(false)
                                                .show();
                                        return;
                                    }

                                    // Establish authenticated patient session
                                    sessionManager.createLoginSession(
                                            email,
                                            RolePermissionManager.ROLE_PATIENT,
                                            OrganizationRepository.DEFAULT_ORG_ID,
                                            "ACTIVE",
                                            "APPROVED"
                                    );

                                    AuditLogManager.log(
                                            OrganizationRepository.DEFAULT_ORG_ID,
                                            uid, email, "PATIENT",
                                            "SIGNUP_SUCCESS", "AUTH", uid,
                                            "New patient account created directly: " + email
                                    );

                                    showLoading(false);
                                    Toast.makeText(SignupActivity.this, "Account created successfully! Welcome to MediLinkChain.", Toast.LENGTH_SHORT).show();

                                    // Direct navigation to profile completion / patient dashboard
                                    Intent intent = new Intent(SignupActivity.this, CompleteProfileActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                });
                    } else {
                        showLoading(false);
                        Exception exp = task.getException();
                        if (exp instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(SignupActivity.this, "An account with this email address already exists. Please sign in instead.", Toast.LENGTH_LONG).show();
                        } else {
                            String error = exp != null ? exp.getMessage() : "Registration failed";
                            Toast.makeText(SignupActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (signupBtn != null) {
            signupBtn.setEnabled(!show);
        }
    }
}
