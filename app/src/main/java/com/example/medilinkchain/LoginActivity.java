package com.example.medilinkchain;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/**
 * LoginActivity — Unified Authentication Screen for MediLinkChain.
 *
 * In-place Tab Switcher:
 *   - "SIGN IN" and "REGISTER" tabs switch forms inside the SAME white card.
 *   - No new activities, screens, or routes are launched when switching tabs.
 *
 * Direct Access & Zero Role Selection:
 *   - No role choices shown on UI.
 *   - Direct login and registration without mandatory email verification links.
 *   - Authenticates against Supabase Auth and Firebase Auth.
 *   - Automatic role resolution and portal routing.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // Tab switcher views
    private LinearLayout tabSignIn, tabRegister;
    private TextView tabSignInText, tabRegisterText;
    private View tabSignInIndicator, tabRegisterIndicator;
    private ViewGroup layoutCardContent;

    // Form containers
    private LinearLayout layoutSignInForm;
    private LinearLayout layoutRegisterForm;

    // Sign In views
    private EditText emailEditText, passwordEditText;
    private Button loginBtn;
    private ImageView btnTogglePassword;
    private TextView textForgotPassword;

    // Register views
    private EditText registerEmailEditText, registerPasswordEditText, registerConfirmPasswordEditText;
    private RelativeLayout btnCreateAccount;
    private ImageView btnToggleRegPassword, btnToggleRegConfirmPassword;
    private TextView textSwitchToSignIn;

    // Loading overlay
    private RelativeLayout loadingOverlay;
    private TextView textLoadingTitle, textLoadingSubtitle;

    // State & Auth
    private FirebaseAuth auth;
    private SessionManager sessionManager;
    private boolean isLoginPasswordVisible = false;
    private boolean isRegPasswordVisible = false;
    private boolean isRegConfirmPasswordVisible = false;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME   = "MediLinkPrefs";
    private static final String KEY_EMAIL    = "RememberEmail";
    private static final String KEY_PASSWORD = "RememberPassword";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initViews();
        disableAutofillAndClearFields();
        setupTabListeners();
        setupSignInFormListeners();
        setupRegisterFormListeners();

        // Check if prefilled email was passed
        if (getIntent() != null && getIntent().getStringExtra("email") != null) {
            String passedEmail = getIntent().getStringExtra("email");
            emailEditText.setText(passedEmail);
        }

        // Default to Sign In tab
        showSignInTab(false);
    }

    private void initViews() {
        // Tab switcher
        tabSignIn            = findViewById(R.id.tabSignIn);
        tabRegister          = findViewById(R.id.tabRegister);
        tabSignInText        = findViewById(R.id.tabSignInText);
        tabRegisterText      = findViewById(R.id.tabRegisterText);
        tabSignInIndicator   = findViewById(R.id.tabSignInIndicator);
        tabRegisterIndicator = findViewById(R.id.tabRegisterIndicator);
        layoutCardContent    = findViewById(R.id.layoutCardContent);

        // Form containers
        layoutSignInForm     = findViewById(R.id.layoutSignInForm);
        layoutRegisterForm   = findViewById(R.id.layoutRegisterForm);

        // Sign In form elements
        emailEditText        = findViewById(R.id.emailEditText);
        passwordEditText     = findViewById(R.id.passwordEditText);
        loginBtn             = findViewById(R.id.loginBtn);
        btnTogglePassword    = findViewById(R.id.btn_toggle_password);
        textForgotPassword   = findViewById(R.id.text_forgot_password);

        // Register form elements
        registerEmailEditText           = findViewById(R.id.registerEmailEditText);
        registerPasswordEditText        = findViewById(R.id.registerPasswordEditText);
        registerConfirmPasswordEditText = findViewById(R.id.registerConfirmPasswordEditText);
        btnCreateAccount                = findViewById(R.id.btnCreateAccount);
        btnToggleRegPassword            = findViewById(R.id.btn_toggle_reg_password);
        btnToggleRegConfirmPassword     = findViewById(R.id.btn_toggle_reg_confirm_password);
        textSwitchToSignIn              = findViewById(R.id.textSwitchToSignIn);

        // Loading overlay
        loadingOverlay     = findViewById(R.id.loadingOverlay);
        textLoadingTitle   = findViewById(R.id.textLoadingTitle);
        textLoadingSubtitle= findViewById(R.id.textLoadingSubtitle);
    }

    private void disableAutofillAndClearFields() {
        EditText[] fields = new EditText[] {
                emailEditText, passwordEditText,
                registerEmailEditText, registerPasswordEditText, registerConfirmPasswordEditText
        };
        for (EditText field : fields) {
            if (field != null) {
                field.setText("");
                field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
                field.clearFocus();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IN-PLACE TAB SWITCHING (NO NAVIGATION, NO NEW PAGES)
    // ──────────────────────────────────────────────────────────────────────────

    private void setupTabListeners() {
        if (tabSignIn != null) {
            tabSignIn.setOnClickListener(v -> showSignInTab(true));
        }

        if (tabRegister != null) {
            tabRegister.setOnClickListener(v -> showRegisterTab(true));
        }

        if (textSwitchToSignIn != null) {
            textSwitchToSignIn.setOnClickListener(v -> showSignInTab(true));
        }
    }

    private void showSignInTab(boolean animate) {
        if (animate && layoutCardContent != null) {
            TransitionManager.beginDelayedTransition(layoutCardContent, new AutoTransition().setDuration(200));
        }

        // 1. Update Tab Header Styles
        if (tabSignInText != null) {
            tabSignInText.setTextColor(getColor(R.color.colorAccentBlue));
        }
        if (tabSignInIndicator != null) {
            tabSignInIndicator.setBackgroundResource(R.drawable.tab_indicator_blue);
        }
        if (tabRegisterText != null) {
            tabRegisterText.setTextColor(getColor(R.color.colorTextDarkMuted));
        }
        if (tabRegisterIndicator != null) {
            tabRegisterIndicator.setBackgroundResource(android.R.color.transparent);
        }

        // 2. Switch In-Place Form Visibility
        if (layoutSignInForm != null) {
            layoutSignInForm.setVisibility(View.VISIBLE);
        }
        if (layoutRegisterForm != null) {
            layoutRegisterForm.setVisibility(View.GONE);
        }
    }

    private void showRegisterTab(boolean animate) {
        if (animate && layoutCardContent != null) {
            TransitionManager.beginDelayedTransition(layoutCardContent, new AutoTransition().setDuration(200));
        }

        // 1. Update Tab Header Styles
        if (tabRegisterText != null) {
            tabRegisterText.setTextColor(getColor(R.color.colorAccentBlue));
        }
        if (tabRegisterIndicator != null) {
            tabRegisterIndicator.setBackgroundResource(R.drawable.tab_indicator_blue);
        }
        if (tabSignInText != null) {
            tabSignInText.setTextColor(getColor(R.color.colorTextDarkMuted));
        }
        if (tabSignInIndicator != null) {
            tabSignInIndicator.setBackgroundResource(android.R.color.transparent);
        }

        // 2. Switch In-Place Form Visibility
        if (layoutSignInForm != null) {
            layoutSignInForm.setVisibility(View.GONE);
        }
        if (layoutRegisterForm != null) {
            layoutRegisterForm.setVisibility(View.VISIBLE);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SIGN IN FORM LOGIC
    // ──────────────────────────────────────────────────────────────────────────

    private void setupSignInFormListeners() {
        // Toggle Password Visibility
        if (btnTogglePassword != null) {
            btnTogglePassword.setOnClickListener(v -> {
                isLoginPasswordVisible = !isLoginPasswordVisible;
                if (isLoginPasswordVisible) {
                    passwordEditText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnTogglePassword.setImageResource(R.drawable.ic_eye_custom);
                    btnTogglePassword.setColorFilter(getColor(R.color.colorAccentBlue));
                } else {
                    passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnTogglePassword.setImageResource(R.drawable.ic_eye_off);
                    btnTogglePassword.setColorFilter(getColor(R.color.colorTextDarkMuted));
                }
                passwordEditText.setSelection(passwordEditText.getText().length());
            });
        }

        // Forgot password
        if (textForgotPassword != null) {
            textForgotPassword.setOnClickListener(v -> handleForgotPassword());
        }

        // Sign In Button Action
        if (loginBtn != null) {
            loginBtn.setOnClickListener(v -> performLogin());
        }
    }

    private void handleForgotPassword() {
        String input = emailEditText.getText().toString().trim();
        Intent intent = new Intent(LoginActivity.this, ResetPasswordActivity.class);
        if (!input.isEmpty() && input.contains("@")) {
            intent.putExtra("email", input);
        }
        startActivity(intent);
    }

    private void performLogin() {
        String input    = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (input.isEmpty()) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        showLoading(true, "Authenticating...", "Securing session & resolving portal");

        final String rawInput = input;

        if (!input.contains("@")) {
            // Check if input matches an adminId in users collection
            FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("adminId", rawInput.toUpperCase(Locale.ROOT))
                    .limit(1)
                    .get()
                    .addOnSuccessListener(adminSnap -> {
                        String targetEmail = null;
                        if (adminSnap != null && !adminSnap.isEmpty()) {
                            DocumentSnapshot d = adminSnap.getDocuments().get(0);
                            targetEmail = d.getString("email");
                            if (targetEmail == null || !targetEmail.contains("@")) {
                                targetEmail = d.getString("authEmail");
                            }
                        }
                        if (targetEmail == null || !targetEmail.contains("@")) {
                            if (rawInput.toUpperCase(Locale.ROOT).startsWith("DR") || rawInput.toUpperCase(Locale.ROOT).startsWith("DOC")) {
                                targetEmail = rawInput.toLowerCase(Locale.ROOT) + "@doctor.medilinkchain.org";
                            } else {
                                targetEmail = rawInput.toLowerCase(Locale.ROOT) + "@admin.medilinkchain.org";
                            }
                        }
                        executeSignIn(targetEmail, password, rawInput);
                    })
                    .addOnFailureListener(e -> {
                        String targetEmail = rawInput.toUpperCase(Locale.ROOT).startsWith("DR") || rawInput.toUpperCase(Locale.ROOT).startsWith("DOC")
                                ? rawInput.toLowerCase(Locale.ROOT) + "@doctor.medilinkchain.org"
                                : rawInput.toLowerCase(Locale.ROOT) + "@admin.medilinkchain.org";
                        executeSignIn(targetEmail, password, rawInput);
                    });
            return;
        }

        executeSignIn(input, password, rawInput);
    }

    private void executeSignIn(String effectiveAuthEmail, String password, String rawInput) {
        // Authenticate concurrently with Supabase to acquire session/token
        SupabaseAuthHelper.signIn(effectiveAuthEmail, password, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(@NonNull SupabaseAuthHelper.AuthResult result) {
                if (result.sessionAvailable) {
                    sessionManager.saveSupabaseSession(result.accessToken, result.userId);
                }
            }

            @Override
            public void onFailure(@NonNull String errorMessage, boolean isDuplicateEmail) {
                Log.d(TAG, "Supabase signIn fallback notice: " + errorMessage);
            }
        });

        auth.signInWithEmailAndPassword(effectiveAuthEmail, password).addOnCompleteListener(task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                saveCredentials(rawInput, password);
                String uid = auth.getCurrentUser().getUid();
                // Identify role from backend
                resolveRoleAndNavigate(uid, rawInput);
            } else if (!rawInput.contains("@")) {
                // Try alternative domain in case prefix differed
                String altEmail = effectiveAuthEmail.contains("@doctor")
                        ? (rawInput.toLowerCase(Locale.ROOT) + "@admin.medilinkchain.org")
                        : (rawInput.toLowerCase(Locale.ROOT) + "@doctor.medilinkchain.org");

                auth.signInWithEmailAndPassword(altEmail, password).addOnCompleteListener(altTask -> {
                    if (altTask.isSuccessful() && auth.getCurrentUser() != null) {
                        saveCredentials(rawInput, password);
                        String uid = auth.getCurrentUser().getUid();
                        resolveRoleAndNavigate(uid, rawInput);
                    } else {
                        showLoading(false, null, null);
                        Toast.makeText(this, "Invalid email or password. Please try again.", Toast.LENGTH_LONG).show();
                        AuditLogManager.log(
                                OrganizationRepository.DEFAULT_ORG_ID,
                                "unauthenticated", rawInput, "UNKNOWN",
                                "LOGIN_FAILED", "AUTH", rawInput,
                                "Failed login attempt for: " + rawInput
                        );
                    }
                });
            } else {
                showLoading(false, null, null);
                Toast.makeText(this, "Invalid email or password. Please try again.", Toast.LENGTH_LONG).show();
                AuditLogManager.log(
                        OrganizationRepository.DEFAULT_ORG_ID,
                        "unauthenticated", rawInput, "UNKNOWN",
                        "LOGIN_FAILED", "AUTH", rawInput,
                        "Failed login attempt for: " + rawInput
                );
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // REGISTER FORM LOGIC
    // ──────────────────────────────────────────────────────────────────────────

    private void setupRegisterFormListeners() {
        // Toggle Register Password Visibility
        if (btnToggleRegPassword != null) {
            btnToggleRegPassword.setOnClickListener(v -> {
                isRegPasswordVisible = !isRegPasswordVisible;
                if (isRegPasswordVisible) {
                    registerPasswordEditText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnToggleRegPassword.setImageResource(R.drawable.ic_eye_custom);
                    btnToggleRegPassword.setColorFilter(getColor(R.color.colorAccentBlue));
                } else {
                    registerPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnToggleRegPassword.setImageResource(R.drawable.ic_eye_off);
                    btnToggleRegPassword.setColorFilter(getColor(R.color.colorTextDarkMuted));
                }
                registerPasswordEditText.setSelection(registerPasswordEditText.getText().length());
            });
        }

        // Toggle Register Confirm Password Visibility
        if (btnToggleRegConfirmPassword != null) {
            btnToggleRegConfirmPassword.setOnClickListener(v -> {
                isRegConfirmPasswordVisible = !isRegConfirmPasswordVisible;
                if (isRegConfirmPasswordVisible) {
                    registerConfirmPasswordEditText.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnToggleRegConfirmPassword.setImageResource(R.drawable.ic_eye_custom);
                    btnToggleRegConfirmPassword.setColorFilter(getColor(R.color.colorAccentBlue));
                } else {
                    registerConfirmPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnToggleRegConfirmPassword.setImageResource(R.drawable.ic_eye_off);
                    btnToggleRegConfirmPassword.setColorFilter(getColor(R.color.colorTextDarkMuted));
                }
                registerConfirmPasswordEditText.setSelection(registerConfirmPasswordEditText.getText().length());
            });
        }

        // Create Account Action
        if (btnCreateAccount != null) {
            btnCreateAccount.setOnClickListener(v -> performRegister());
        }
    }

    private void performRegister() {
        String email = registerEmailEditText.getText().toString().trim();
        String password = registerPasswordEditText.getText().toString().trim();
        String confirmPassword = registerConfirmPasswordEditText.getText().toString().trim();

        // 1. Email Validation
        if (email.isEmpty()) {
            registerEmailEditText.setError("Email address is required");
            registerEmailEditText.requestFocus();
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            registerEmailEditText.setError("Please enter a valid email address");
            registerEmailEditText.requestFocus();
            return;
        }

        // 2. Password Validation
        if (password.isEmpty()) {
            registerPasswordEditText.setError("Password is required");
            registerPasswordEditText.requestFocus();
            return;
        }
        if (password.length() < 6) {
            registerPasswordEditText.setError("Password must be at least 6 characters long");
            registerPasswordEditText.requestFocus();
            return;
        }

        // 3. Confirm Password Validation
        if (confirmPassword.isEmpty()) {
            registerConfirmPasswordEditText.setError("Please confirm your password");
            registerConfirmPasswordEditText.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            registerConfirmPasswordEditText.setError("Passwords do not match");
            registerConfirmPasswordEditText.requestFocus();
            return;
        }

        // Prevent repeated clicks / submissions
        showLoading(true, "Creating Account...", "Securing identity credentials");

        // 4. Supabase & Firebase Registration
        SupabaseAuthHelper.signUp(email, password, "PATIENT", new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(@NonNull SupabaseAuthHelper.AuthResult result) {
                completeFirebaseRegister(email, password, result);
            }

            @Override
            public void onFailure(@NonNull String errorMessage, boolean isDuplicateEmail) {
                if (isDuplicateEmail) {
                    showLoading(false, null, null);
                    Toast.makeText(LoginActivity.this, "An account with this email address already exists. Please sign in instead.", Toast.LENGTH_LONG).show();
                } else {
                    completeFirebaseRegister(email, password, null);
                }
            }
        });
    }

    private void completeFirebaseRegister(String email, String password, SupabaseAuthHelper.AuthResult supabaseResult) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        String uid = auth.getCurrentUser().getUid();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        String patientId = "MLC" + uid.substring(0, Math.min(uid.length(), 8)).toUpperCase();

                        // 5. Build Active Patient Profile
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

                                    if (supabaseResult != null && supabaseResult.sessionAvailable) {
                                        sessionManager.saveSupabaseSession(supabaseResult.accessToken, supabaseResult.userId);
                                    }

                                    // Check session availability
                                    if (supabaseResult != null && !supabaseResult.sessionAvailable && auth.getCurrentUser() == null) {
                                        showLoading(false, null, null);
                                        new MaterialAlertDialogBuilder(LoginActivity.this)
                                                .setTitle("Account Created")
                                                .setMessage("Your account has been created successfully, but your login session is not yet active. Please log in using your registered email and password.")
                                                .setPositiveButton("OK", (dialog, which) -> {
                                                    showSignInTab(true);
                                                    emailEditText.setText(email);
                                                    passwordEditText.setText("");
                                                })
                                                .setCancelable(false)
                                                .show();
                                        return;
                                    }

                                    // 7. Establish active session
                                    sessionManager.createLoginSession(email, RolePermissionManager.ROLE_PATIENT, OrganizationRepository.DEFAULT_ORG_ID, "ACTIVE", "APPROVED");

                                    AuditLogManager.log(
                                            OrganizationRepository.DEFAULT_ORG_ID,
                                            uid, email, "PATIENT",
                                            "SIGNUP_SUCCESS", "AUTH", uid,
                                            "New patient account registered directly from login screen: " + email
                                    );

                                    showLoading(false, null, null);
                                    Toast.makeText(LoginActivity.this, "Account created successfully! Welcome to MediLinkChain.", Toast.LENGTH_SHORT).show();

                                    // Navigate directly to CompleteProfileActivity
                                    Intent intent = new Intent(LoginActivity.this, CompleteProfileActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                });
                    } else {
                        showLoading(false, null, null);
                        Exception exp = task.getException();
                        if (exp instanceof FirebaseAuthUserCollisionException) {
                            Toast.makeText(LoginActivity.this, "An account with this email address already exists. Please sign in instead.", Toast.LENGTH_LONG).show();
                        } else {
                            String error = exp != null ? exp.getMessage() : "Registration failed";
                            Toast.makeText(LoginActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BACKEND ROLE RESOLUTION & ROUTING (AUTOMATIC, NO UI ROLE SELECTOR)
    // ──────────────────────────────────────────────────────────────────────────

    private void resolveRoleAndNavigate(String uid, String email) {
        // Special Super Admin account detection (hidden internally)
        if (SuperAdminSetupHelper.SUPER_ADMIN_EMAIL.equalsIgnoreCase(email)
                || "superadmin@medilinkchain.org".equalsIgnoreCase(email)
                || "admin@medilinkchain.org".equalsIgnoreCase(email)
                || "admin@medilink.com".equalsIgnoreCase(email)) {
            showLoading(false, null, null);
            navigateToPortal(uid, email, RolePermissionManager.ROLE_SUPER_ADMIN, "ACTIVE", OrganizationRepository.DEFAULT_ORG_ID, "MediLinkChain Global Network", "SUPER", true, null);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("organization_members")
                .whereEqualTo("userId", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(memberSnap -> {
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(userDoc -> {
                                showLoading(false, null, null);

                                String role         = "PATIENT";
                                String status       = "ACTIVE";
                                String orgId        = OrganizationRepository.DEFAULT_ORG_ID;
                                String hospitalName = "";
                                String hospitalCode = "";
                                boolean isPrimaryAdmin = false;

                                // Extract from organization_members
                                if (!memberSnap.isEmpty()) {
                                    DocumentSnapshot mem = memberSnap.getDocuments().get(0);
                                    String memRole = mem.getString("role");
                                    if (memRole != null && !memRole.isEmpty()) role = memRole;
                                    String memStatus = mem.getString("status");
                                    if (memStatus != null && !memStatus.isEmpty()) status = memStatus;
                                    String memOrg = mem.getString("organizationId");
                                    if (memOrg != null && !memOrg.isEmpty()) orgId = memOrg;
                                    String hn = mem.getString("hospitalName");
                                    if (hn != null && !hn.isEmpty()) hospitalName = hn;
                                    String hc = mem.getString("hospitalCode");
                                    if (hc != null && !hc.isEmpty()) hospitalCode = hc;
                                    Boolean ipa = mem.getBoolean("isPrimaryAdmin");
                                    if (ipa != null) isPrimaryAdmin = ipa;
                                }

                                // Supplement / override from users doc
                                if (userDoc != null && userDoc.exists()) {
                                    String uRole = userDoc.getString("role");
                                    if (uRole != null && !uRole.isEmpty()) role = normaliseRole(uRole);
                                    String uStatus = userDoc.getString("status");
                                    if (uStatus != null && !uStatus.isEmpty()) status = uStatus;
                                    String uOrg = userDoc.getString("organizationId");
                                    if ((orgId.equals(OrganizationRepository.DEFAULT_ORG_ID) || orgId.isEmpty()) && uOrg != null && !uOrg.isEmpty()) orgId = uOrg;
                                    String uHN = userDoc.getString("hospitalName");
                                    if (hospitalName.isEmpty() && uHN != null) hospitalName = uHN;
                                    String uHC = userDoc.getString("hospitalCode");
                                    if (hospitalCode.isEmpty() && uHC != null) hospitalCode = uHC;
                                }

                                // Block disabled accounts before routing
                                if ("DISABLED".equalsIgnoreCase(status)) {
                                    auth.signOut();
                                    sessionManager.logoutUser();
                                    new MaterialAlertDialogBuilder(LoginActivity.this)
                                            .setTitle("Account Unavailable")
                                            .setMessage("Your account has been suspended. Please contact support for assistance.")
                                            .setPositiveButton("OK", null)
                                            .show();
                                    return;
                                }

                                navigateToPortal(uid, email, role, status, orgId, hospitalName, hospitalCode, isPrimaryAdmin, userDoc);
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false, null, null);
                                Log.e(TAG, "Failed to read user doc: " + e.getMessage());
                                Toast.makeText(this, "Account configuration not found. Please contact support.", Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    // Fallback: try users collection directly
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(userDoc -> {
                                showLoading(false, null, null);
                                if (userDoc == null || !userDoc.exists()) {
                                    auth.signOut();
                                    Toast.makeText(this, "Account configuration not found. Please contact support.", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                String role = normaliseRole(userDoc.getString("role"));
                                String status = userDoc.getString("status");
                                if (status == null) status = "ACTIVE";
                                String orgId = userDoc.getString("organizationId");
                                if (orgId == null) orgId = OrganizationRepository.DEFAULT_ORG_ID;
                                navigateToPortal(uid, email, role, status, orgId, "", "", false, userDoc);
                            })
                            .addOnFailureListener(e2 -> {
                                showLoading(false, null, null);
                                Toast.makeText(this, "Account configuration not found. Please contact support.", Toast.LENGTH_LONG).show();
                            });
                });
    }

    private void navigateToPortal(
            String uid, String email, String role, String status,
            String orgId, String hospitalName, String hospitalCode,
            boolean isPrimaryAdmin, DocumentSnapshot userDoc
    ) {
        AuditLogManager.log(orgId, uid, email, role, "LOGIN_SUCCESS", "AUTH", uid,
                "Successful login — routed to portal for role: " + role);

        Intent intent;

        if (RolePermissionManager.ROLE_SUPER_ADMIN.equalsIgnoreCase(role)) {
            // ── Super Admin Portal ─────────────────────────────────────────
            sessionManager.createLoginSession(email, RolePermissionManager.ROLE_SUPER_ADMIN, orgId, "ACTIVE", "APPROVED");
            sessionManager.saveHospitalDetails("MediLinkChain Global Network", "SUPER", true);
            intent = new Intent(this, AdminDashboardActivity.class);

        } else if (RolePermissionManager.ROLE_HOSPITAL_ADMIN.equalsIgnoreCase(role)
                || RolePermissionManager.ROLE_ADMIN.equalsIgnoreCase(role)) {
            // ── Hospital Admin Portal ──────────────────────────────────────
            sessionManager.createLoginSession(email, RolePermissionManager.ROLE_HOSPITAL_ADMIN, orgId, "ACTIVE", "APPROVED");
            sessionManager.saveHospitalDetails(
                    hospitalName.isEmpty() ? "Hospital" : hospitalName,
                    hospitalCode.isEmpty() ? "HOSP" : hospitalCode,
                    isPrimaryAdmin
            );
            intent = new Intent(this, AdminDashboardActivity.class);

        } else if (RolePermissionManager.ROLE_DOCTOR.equalsIgnoreCase(role)) {
            // ── Doctor Portal ──────────────────────────────────────────────
            String verificationStatus = "PENDING";
            String doctorRegId        = "";
            boolean isFirstLogin      = false;

            if (userDoc != null && userDoc.exists()) {
                String v = userDoc.getString("verificationStatus");
                if (v != null) verificationStatus = v.toUpperCase();
                String dId = userDoc.getString("doctorRegistrationId");
                if (dId != null) doctorRegId = dId;
                Boolean fl = userDoc.getBoolean("first_login");
                if (fl == null) fl = userDoc.getBoolean("firstLogin");
                if (fl != null && fl) isFirstLogin = true;
            }

            sessionManager.createLoginSession(email, RolePermissionManager.ROLE_DOCTOR, orgId, status, verificationStatus);
            sessionManager.saveHospitalDetails(
                    hospitalName.isEmpty() ? "Hospital" : hospitalName,
                    hospitalCode.isEmpty() ? "HOSP" : hospitalCode,
                    false
            );
            sessionManager.setRegistrationId(doctorRegId);

            if ("APPROVED".equalsIgnoreCase(verificationStatus) && !"DISABLED".equalsIgnoreCase(status)) {
                intent = new Intent(this, DoctorDashboardActivity.class);
            } else {
                auth.signOut();
                sessionManager.logoutUser();
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Doctor Account Verification")
                        .setMessage("Your doctor account has not been approved yet.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

        } else {
            // ── Patient Portal (Direct Login without email verification requirement) ────
            boolean profileComplete = false;
            if (userDoc != null && userDoc.exists()) {
                Boolean comp = userDoc.getBoolean("profileComplete");
                if (comp != null) profileComplete = comp;
            }
            sessionManager.createLoginSession(email, RolePermissionManager.ROLE_PATIENT, orgId, "ACTIVE", "APPROVED");
            intent = profileComplete
                    ? new Intent(this, DashboardActivity.class)
                    : new Intent(this, CompleteProfileActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private String normaliseRole(String raw) {
        if (raw == null || raw.isEmpty()) return "PATIENT";
        String r = raw.trim().toUpperCase();
        if (r.equals("SUPER_ADMIN") || r.equals("SUPERADMIN")) return "SUPER_ADMIN";
        if (r.equals("HOSPITAL_ADMIN") || r.equals("HOSPITALADMIN") || r.equals("ADMIN")) return "HOSPITAL_ADMIN";
        if (r.equals("DOCTOR")) return "DOCTOR";
        return "PATIENT";
    }

    private void showLoading(boolean show, String title, String subtitle) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (title != null && textLoadingTitle != null) {
            textLoadingTitle.setText(title);
        }
        if (subtitle != null && textLoadingSubtitle != null) {
            textLoadingSubtitle.setText(subtitle);
        }
        if (loginBtn != null) loginBtn.setEnabled(!show);
        if (btnCreateAccount != null) btnCreateAccount.setEnabled(!show);
    }

    private void loadRememberedCredentials() {
        emailEditText.setText(sharedPreferences.getString(KEY_EMAIL, ""));
        passwordEditText.setText(sharedPreferences.getString(KEY_PASSWORD, ""));
    }

    private void saveCredentials(String email, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }
}
