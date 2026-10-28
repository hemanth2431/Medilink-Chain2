package com.example.medilinkchain;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * DoctorManagementActivity — Admin module for verifying, approving, rejecting,
 * disabling, and generating unique Doctor Registration IDs (DOC-{CODE}-{YEAR}-{SEQUENCE}).
 */
public class DoctorManagementActivity extends AppCompatActivity {

    private RecyclerView recyclerViewDoctors;
    private View layoutEmptyDoctors;
    private TextView textEmptyDoctorTitle;
    private ProgressBar progressBarDoctors;
    private EditText inputSearchDoctors;
    private ChipGroup chipGroupDoctorStatus;
    private Chip chipFilterPending;
    private MaterialButton btnGenerateDocId;

    private final List<DoctorProfileModel> allDoctorsList = new ArrayList<>();
    private final List<DoctorProfileModel> filteredDoctorsList = new ArrayList<>();
    private DoctorAdapter adapter;

    private String currentFilterStatus = "PENDING";
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SessionManager sessionManager;
    private String currentOrgId;
    private boolean isSuperAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_management);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        sessionManager = new SessionManager(this);

        currentOrgId = getIntent().getStringExtra("orgId");
        if (currentOrgId == null || currentOrgId.isEmpty()) {
            currentOrgId = sessionManager.getOrgId();
        }
        isSuperAdmin = getIntent().getBooleanExtra("isSuperAdmin", sessionManager.isSuperAdmin());

        initViews();
        setupListeners();
        loadDoctors();
    }

    private void initViews() {
        recyclerViewDoctors = findViewById(R.id.recyclerViewDoctors);
        layoutEmptyDoctors = findViewById(R.id.layoutEmptyDoctors);
        textEmptyDoctorTitle = findViewById(R.id.textEmptyDoctorTitle);
        progressBarDoctors = findViewById(R.id.progressBarDoctors);
        inputSearchDoctors = findViewById(R.id.inputSearchDoctors);
        chipGroupDoctorStatus = findViewById(R.id.chipGroupDoctorStatus);
        chipFilterPending = findViewById(R.id.chipFilterPending);
        btnGenerateDocId = findViewById(R.id.btnGenerateDocId);

        recyclerViewDoctors.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DoctorAdapter(filteredDoctorsList);
        recyclerViewDoctors.setAdapter(adapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        if (btnGenerateDocId != null) {
            btnGenerateDocId.setOnClickListener(v -> showCreateDoctorAccountDialog());
        }

        inputSearchDoctors.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroupDoctorStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipFilterPending) {
                currentFilterStatus = "PENDING";
            } else if (checkedId == R.id.chipFilterApproved) {
                currentFilterStatus = "APPROVED";
            } else if (checkedId == R.id.chipFilterRejected) {
                currentFilterStatus = "REJECTED";
            } else {
                currentFilterStatus = "ALL";
            }
            applyFilters();
        });
    }

    private void loadDoctors() {
        progressBarDoctors.setVisibility(View.VISIBLE);
        layoutEmptyDoctors.setVisibility(View.GONE);

        // Hospital Admins are scoped to their hospital, Super Admins can see all
        String queryOrgId = isSuperAdmin ? null : currentOrgId;

        OrganizationRepository.listDoctors(queryOrgId, new OrganizationRepository.Callback<List<DoctorProfileModel>>() {
            @Override
            public void onSuccess(List<DoctorProfileModel> list) {
                progressBarDoctors.setVisibility(View.GONE);
                allDoctorsList.clear();
                allDoctorsList.addAll(list);

                // Update pending chip count
                int pendingCount = 0;
                for (DoctorProfileModel d : list) {
                    if (d.isPending()) pendingCount++;
                }
                chipFilterPending.setText("Pending (" + pendingCount + ")");

                applyFilters();
            }

            @Override
            public void onFailure(String error) {
                progressBarDoctors.setVisibility(View.GONE);
                Toast.makeText(DoctorManagementActivity.this, "Failed to load doctors: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilters() {
        filteredDoctorsList.clear();
        String query = inputSearchDoctors.getText().toString().trim().toLowerCase(Locale.ROOT);

        for (DoctorProfileModel d : allDoctorsList) {
            // Status match
            boolean statusMatches;
            if ("ALL".equalsIgnoreCase(currentFilterStatus)) {
                statusMatches = true;
            } else {
                statusMatches = currentFilterStatus.equalsIgnoreCase(d.getVerificationStatus());
            }

            if (!statusMatches) continue;

            // Search query match
            boolean searchMatches = query.isEmpty()
                    || d.getFullName().toLowerCase(Locale.ROOT).contains(query)
                    || d.getDoctorRegistrationId().toLowerCase(Locale.ROOT).contains(query)
                    || d.getHospitalName().toLowerCase(Locale.ROOT).contains(query)
                    || (d.getSpecialization() != null && d.getSpecialization().toLowerCase(Locale.ROOT).contains(query))
                    || d.getEmail().toLowerCase(Locale.ROOT).contains(query);

            if (searchMatches) {
                filteredDoctorsList.add(d);
            }
        }

        adapter.notifyDataSetChanged();
        layoutEmptyDoctors.setVisibility(filteredDoctorsList.isEmpty() ? View.VISIBLE : View.GONE);
        textEmptyDoctorTitle.setText("No " + currentFilterStatus.toLowerCase() + " doctors found");
    }

    /**
     * showCreateDoctorAccountDialog — Administrator creates/invites a Doctor account directly.
     * Supports hospital selection for Super Admin and locked hospital for Hospital Admin.
     */
    private void showCreateDoctorAccountDialog() {
        SessionManager sm = new SessionManager(this);
        String defaultHospitalName = sm.getHospitalName();
        if (defaultHospitalName == null || defaultHospitalName.isEmpty()) {
            defaultHospitalName = "MediLinkChain Central Network";
        }
        String defaultOrgId = (currentOrgId != null && !currentOrgId.isEmpty()) ? currentOrgId : sm.getOrgId();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_doctor_account, null);

        EditText inputDocId = dialogView.findViewById(R.id.inputDoctorRegId);
        EditText inputFullName = dialogView.findViewById(R.id.inputDoctorFullName);
        EditText inputSpec = dialogView.findViewById(R.id.inputSpecialization);
        EditText inputPassword = dialogView.findViewById(R.id.inputDoctorPassword);
        EditText inputConfirmPassword = dialogView.findViewById(R.id.inputDoctorConfirmPassword);

        View layoutHospitalReadOnly = dialogView.findViewById(R.id.layoutHospitalReadOnly);
        TextView textHospital = dialogView.findViewById(R.id.textAssignedHospital);
        View layoutHospitalSpinnerContainer = dialogView.findViewById(R.id.layoutHospitalSpinnerContainer);
        android.widget.Spinner spinnerHospital = dialogView.findViewById(R.id.spinnerHospitalSelect);

        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmCreateDoctor);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelCreateDoctor);

        // Always open with completely clean empty fields and disable autofill
        if (inputDocId != null) {
            inputDocId.setText("");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                inputDocId.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }
        if (inputFullName != null) {
            inputFullName.setText("");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                inputFullName.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }
        if (inputSpec != null) {
            inputSpec.setText("");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                inputSpec.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }
        if (inputPassword != null) {
            inputPassword.setText("");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                inputPassword.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }
        if (inputConfirmPassword != null) {
            inputConfirmPassword.setText("");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                inputConfirmPassword.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }

        final List<OrganizationModel> loadedHospitals = new ArrayList<>();
        final String[] targetOrgId = new String[]{defaultOrgId};

        if (isSuperAdmin) {
            if (layoutHospitalReadOnly != null) layoutHospitalReadOnly.setVisibility(View.GONE);
            if (layoutHospitalSpinnerContainer != null) layoutHospitalSpinnerContainer.setVisibility(View.VISIBLE);

            OrganizationRepository.listOrganizations(new OrganizationRepository.Callback<List<OrganizationModel>>() {
                @Override
                public void onSuccess(List<OrganizationModel> hospitals) {
                    loadedHospitals.clear();
                    List<String> hospNames = new ArrayList<>();
                    if (hospitals != null) {
                        for (OrganizationModel h : hospitals) {
                            if ("ACTIVE".equalsIgnoreCase(h.getStatus())) {
                                loadedHospitals.add(h);
                                hospNames.add(h.getName() + " (" + h.getCode() + ")");
                            }
                        }
                    }
                    if (hospNames.isEmpty()) {
                        hospNames.add("MediLinkChain Central Network (MAIN)");
                    }
                    if (spinnerHospital != null) {
                        android.widget.ArrayAdapter<String> hospAdapter = new android.widget.ArrayAdapter<>(
                                DoctorManagementActivity.this,
                                android.R.layout.simple_spinner_dropdown_item,
                                hospNames
                        );
                        spinnerHospital.setAdapter(hospAdapter);
                    }
                }

                @Override
                public void onFailure(String error) {
                    Log.w("DoctorManagement", "Could not load hospitals for selector: " + error);
                }
            });
        } else {
            if (layoutHospitalReadOnly != null) layoutHospitalReadOnly.setVisibility(View.VISIBLE);
            if (layoutHospitalSpinnerContainer != null) layoutHospitalSpinnerContainer.setVisibility(View.GONE);
            if (textHospital != null) {
                textHospital.setText(defaultHospitalName);
            }
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String docId = inputDocId != null ? inputDocId.getText().toString().trim() : "";
                String fullName = inputFullName != null ? inputFullName.getText().toString().trim() : "";
                String spec = inputSpec != null ? inputSpec.getText().toString().trim() : "";
                String password = inputPassword != null ? inputPassword.getText().toString().trim() : "";
                String confirmPassword = inputConfirmPassword != null ? inputConfirmPassword.getText().toString().trim() : "";

                if (docId.isEmpty()) {
                    Toast.makeText(this, "Please enter a valid Doctor ID", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (fullName.isEmpty()) {
                    Toast.makeText(this, "Doctor Name is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (spec.isEmpty()) {
                    spec = "General Medicine";
                }
                if (password.isEmpty()) {
                    Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (password.length() < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (confirmPassword.isEmpty()) {
                    Toast.makeText(this, "Please confirm the password", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!password.equals(confirmPassword)) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }

                String chosenOrgId = defaultOrgId;
                if (isSuperAdmin && !loadedHospitals.isEmpty() && spinnerHospital != null) {
                    int selectedPos = spinnerHospital.getSelectedItemPosition();
                    if (selectedPos >= 0 && selectedPos < loadedHospitals.size()) {
                        chosenOrgId = loadedHospitals.get(selectedPos).getId();
                    }
                }

                dialog.dismiss();
                performAssignDoctor(chosenOrgId, docId, fullName, spec, password);
            });
        }

        dialog.show();
    }

    private void performAssignDoctor(String orgId, String docId, String fullName, String spec, String password) {
        progressBarDoctors.setVisibility(View.VISIBLE);
        String actorId = (currentUser != null) ? currentUser.getUid() : "admin";
        String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "admin@medilinkchain.org";
        String actorRole = isSuperAdmin ? "SUPER_ADMIN" : "HOSPITAL_ADMIN";

        OrganizationRepository.assignDoctor(
                this,
                orgId,
                docId,
                fullName,
                spec,
                password,
                actorId,
                actorEmail,
                actorRole,
                new OrganizationRepository.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        progressBarDoctors.setVisibility(View.GONE);
                        Toast.makeText(DoctorManagementActivity.this, "Doctor invited successfully! Status: PENDING", Toast.LENGTH_SHORT).show();
                        loadDoctors();
                    }

                    @Override
                    public void onFailure(String error) {
                        progressBarDoctors.setVisibility(View.GONE);
                        Toast.makeText(DoctorManagementActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void writeDoctorFirestoreData(FirebaseFirestore db, String uid, String fullName,
                                           String email, String medReg, String spec, String phone,
                                           String regId, String orgId, String hospitalName,
                                           String tempPassword, String doctorRegId) {
        Date now = new Date();

        // users collection (legacy compatibility)
        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("email", email);
        userDoc.put("fullName", fullName);
        userDoc.put("name", fullName);
        userDoc.put("role", "DOCTOR");
        userDoc.put("status", "PENDING");
        userDoc.put("verificationStatus", "PENDING");
        userDoc.put("accountStatus", "PENDING");
        userDoc.put("doctorRegistrationId", doctorRegId);
        userDoc.put("organizationId", orgId);
        userDoc.put("hospitalName", hospitalName != null ? hospitalName : "");
        userDoc.put("specialization", spec);
        userDoc.put("medicalRegistrationNumber", medReg);
        userDoc.put("phone", phone);
        userDoc.put("first_login", true);
        userDoc.put("firstLogin", true);
        userDoc.put("submittedAt", now);
        userDoc.put("createdAt", now);
        db.collection("users").document(uid).set(userDoc);

        // organization_members collection
        Map<String, Object> memberDoc = new HashMap<>();
        memberDoc.put("id", "member_" + uid);
        memberDoc.put("userId", uid);
        memberDoc.put("user_id", uid);
        memberDoc.put("email", email);
        memberDoc.put("fullName", fullName);
        memberDoc.put("organizationId", orgId);
        memberDoc.put("hospitalName", hospitalName != null ? hospitalName : "");
        memberDoc.put("role", "DOCTOR");
        memberDoc.put("status", "PENDING");
        memberDoc.put("createdAt", now);
        memberDoc.put("createdBy", (currentUser != null) ? currentUser.getUid() : "admin");
        db.collection("organization_members").document("member_" + uid).set(memberDoc);

        // doctor_profiles collection
        Map<String, Object> doctorProfile = new HashMap<>();
        doctorProfile.put("id", uid);
        doctorProfile.put("user_id", uid);
        doctorProfile.put("userId", uid);
        doctorProfile.put("organization_id", orgId);
        doctorProfile.put("organizationId", orgId);
        doctorProfile.put("registration_id", doctorRegId);
        doctorProfile.put("doctor_registration_id", doctorRegId);
        doctorProfile.put("doctorRegistrationId", doctorRegId);
        doctorProfile.put("full_name", fullName);
        doctorProfile.put("fullName", fullName);
        doctorProfile.put("email", email);
        doctorProfile.put("phone", phone);
        doctorProfile.put("hospital_name", hospitalName != null ? hospitalName : "");
        doctorProfile.put("hospitalName", hospitalName != null ? hospitalName : "");
        doctorProfile.put("specialization", spec);
        doctorProfile.put("medical_registration_number", medReg);
        doctorProfile.put("medicalRegistrationNumber", medReg);
        doctorProfile.put("verification_status", "PENDING");
        doctorProfile.put("verificationStatus", "PENDING");
        doctorProfile.put("account_status", "PENDING");
        doctorProfile.put("accountStatus", "PENDING");
        doctorProfile.put("status", "PENDING");
        doctorProfile.put("first_login", true);
        doctorProfile.put("firstLogin", true);
        doctorProfile.put("submitted_at", now);
        doctorProfile.put("created_at", now);
        db.collection("doctor_profiles").document(uid).set(doctorProfile);

        // Audit log
        AuditLogManager.log(
                orgId,
                (currentUser != null) ? currentUser.getUid() : "admin",
                (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "",
                "HOSPITAL_ADMIN",
                "DOCTOR_ACCOUNT_CREATED",
                "DOCTOR",
                uid,
                "Doctor account created for: " + fullName + " (" + email + ") with Reg ID: " + doctorRegId
        );
    }

    private void showGenerateDocIdDialog() {

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_generate_doc_reg_id, null);
        EditText inputDocName = dialogView.findViewById(R.id.inputDocName);
        EditText inputDocSpec = dialogView.findViewById(R.id.inputDocSpecialization);
        EditText inputDocEmail = dialogView.findViewById(R.id.inputDocEmail);
        EditText inputDocPhone = dialogView.findViewById(R.id.inputDocPhone);
        EditText inputDocMedReg = dialogView.findViewById(R.id.inputDocMedRegNum);

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("GENERATE ID", (dialog, which) -> {
                    String name = inputDocName.getText().toString().trim();
                    String spec = inputDocSpec.getText().toString().trim();
                    String email = inputDocEmail.getText().toString().trim();
                    String phone = inputDocPhone.getText().toString().trim();
                    String medReg = inputDocMedReg.getText().toString().trim();

                    generateDoctorId(name, spec, email, phone, medReg);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void generateDoctorId(String name, String spec, String email, String phone, String medReg) {
        progressBarDoctors.setVisibility(View.VISIBLE);
        String actorId = (currentUser != null) ? currentUser.getUid() : "admin";
        String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "admin@medilinkchain.org";
        String orgIdToUse = (currentOrgId != null && !currentOrgId.isEmpty()) ? currentOrgId : OrganizationRepository.DEFAULT_ORG_ID;

        OrganizationRepository.generateDoctorRegistrationId(
                orgIdToUse,
                name,
                spec,
                email,
                phone,
                medReg,
                actorId,
                actorEmail,
                new OrganizationRepository.Callback<String>() {
                    @Override
                    public void onSuccess(String registrationId) {
                        progressBarDoctors.setVisibility(View.GONE);
                        showGeneratedIdDialog(registrationId, name, spec);
                    }

                    @Override
                    public void onFailure(String error) {
                        progressBarDoctors.setVisibility(View.GONE);
                        Toast.makeText(DoctorManagementActivity.this, "Failed to generate ID: " + error, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void showGeneratedIdDialog(String registrationId, String doctorName, String spec) {
        String msg = "Doctor Registration ID successfully generated:\n\n"
                + "📋 " + registrationId + "\n\n"
                + "Doctor: " + (doctorName.isEmpty() ? "New Doctor" : doctorName) + "\n"
                + "Specialization: " + (spec.isEmpty() ? "General Medicine" : spec) + "\n\n"
                + "Share this Registration ID with the doctor. They will enter this ID during Doctor Registration.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Doctor Registration ID Ready")
                .setMessage(msg)
                .setPositiveButton("COPY ID", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Doctor Registration ID", registrationId);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "Registration ID copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void handleApproveDoctor(DoctorProfileModel doctor) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Approve Doctor Registration")
                .setMessage("Approve registration for " + doctor.getFullName() + " (" + doctor.getDoctorRegistrationId() + ")?\n\nThis will grant the doctor full portal access.")
                .setPositiveButton("APPROVE", (dialog, which) -> {
                    progressBarDoctors.setVisibility(View.VISIBLE);
                    String actorId = (currentUser != null) ? currentUser.getUid() : "admin";
                    String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "admin@medilinkchain.org";
                    String actorRole = isSuperAdmin ? "SUPER_ADMIN" : "HOSPITAL_ADMIN";

                    OrganizationRepository.approveDoctor(
                            doctor.getUserId(),
                            doctor.getDoctorRegistrationId(),
                            doctor.getOrganizationId(),
                            actorId,
                            actorEmail,
                            actorRole,
                            new OrganizationRepository.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    progressBarDoctors.setVisibility(View.GONE);
                                    Toast.makeText(DoctorManagementActivity.this, "Doctor registration approved. Account is now ACTIVE.", Toast.LENGTH_SHORT).show();
                                    loadDoctors();
                                }

                                @Override
                                public void onFailure(String error) {
                                    progressBarDoctors.setVisibility(View.GONE);
                                    Toast.makeText(DoctorManagementActivity.this, "Approval failed: " + error, Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleRejectDoctor(DoctorProfileModel doctor) {
        final EditText inputReason = new EditText(this);
        inputReason.setHint("e.g. Doctor registration credentials could not be verified.");
        inputReason.setSingleLine(false);
        inputReason.setLines(3);
        inputReason.setPadding(32, 24, 32, 24);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Reject Doctor Registration")
                .setMessage("Please specify the reason for rejecting " + doctor.getFullName() + ":")
                .setView(inputReason)
                .setPositiveButton("REJECT", (dialog, which) -> {
                    String reason = inputReason.getText().toString().trim();
                    if (reason.isEmpty()) {
                        reason = "Doctor registration credentials could not be verified.";
                    }

                    progressBarDoctors.setVisibility(View.VISIBLE);
                    String actorId = (currentUser != null) ? currentUser.getUid() : "admin";
                    String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "admin@medilinkchain.org";
                    String actorRole = isSuperAdmin ? "SUPER_ADMIN" : "HOSPITAL_ADMIN";

                    OrganizationRepository.rejectDoctor(
                            doctor.getUserId(),
                            doctor.getDoctorRegistrationId(),
                            doctor.getOrganizationId(),
                            reason,
                            actorId,
                            actorEmail,
                            actorRole,
                            new OrganizationRepository.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    progressBarDoctors.setVisibility(View.GONE);
                                    Toast.makeText(DoctorManagementActivity.this, "Doctor application rejected.", Toast.LENGTH_SHORT).show();
                                    loadDoctors();
                                }

                                @Override
                                public void onFailure(String error) {
                                    progressBarDoctors.setVisibility(View.GONE);
                                    Toast.makeText(DoctorManagementActivity.this, "Rejection failed: " + error, Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleToggleDoctorStatus(DoctorProfileModel doctor) {
        boolean isCurrentlyActive = doctor.isActive();
        String targetStatus = isCurrentlyActive ? "DISABLED" : "ACTIVE";
        String actionLabel = isCurrentlyActive ? "Disable" : "Reactivate";

        new MaterialAlertDialogBuilder(this)
                .setTitle(actionLabel + " Doctor Account")
                .setMessage("Are you sure you want to " + actionLabel.toLowerCase() + " access for " + doctor.getFullName() + "?")
                .setPositiveButton(actionLabel.toUpperCase(), (dialog, which) -> {
                    progressBarDoctors.setVisibility(View.VISIBLE);
                    String actorId = (currentUser != null) ? currentUser.getUid() : "admin";
                    String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "admin@medilinkchain.org";
                    String actorRole = isSuperAdmin ? "SUPER_ADMIN" : "HOSPITAL_ADMIN";

                    OrganizationRepository.setDoctorAccountStatus(
                            doctor.getUserId(),
                            doctor.getDoctorRegistrationId(),
                            doctor.getOrganizationId(),
                            targetStatus,
                            actorId,
                            actorEmail,
                            actorRole,
                            new OrganizationRepository.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    progressBarDoctors.setVisibility(View.GONE);
                                    Toast.makeText(DoctorManagementActivity.this, "Doctor status updated to " + targetStatus, Toast.LENGTH_SHORT).show();
                                    loadDoctors();
                                }

                                @Override
                                public void onFailure(String error) {
                                    progressBarDoctors.setVisibility(View.GONE);
                                    Toast.makeText(DoctorManagementActivity.this, "Status update failed: " + error, Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // RECYCLERVIEW ADAPTER
    // =========================================================================

    private class DoctorAdapter extends RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder> {

        private final List<DoctorProfileModel> list;

        public DoctorAdapter(List<DoctorProfileModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public DoctorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_doctor_admin, parent, false);
            return new DoctorViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull DoctorViewHolder holder, int position) {
            DoctorProfileModel doctor = list.get(position);

            holder.textDoctorName.setText(doctor.getFullName());
            holder.textDoctorSpecialization.setText(doctor.getSpecialization());
            holder.textDoctorRegId.setText(doctor.getDoctorRegistrationId());
            if (holder.textDoctorHospital != null) {
                holder.textDoctorHospital.setText(doctor.getHospitalName() != null && !doctor.getHospitalName().isEmpty()
                        ? doctor.getHospitalName()
                        : "MediLinkChain Organization");
            }
            if (holder.textDoctorEmail != null) {
                holder.textDoctorEmail.setText(doctor.getEmail());
            }

            if (doctor.getMedicalRegistrationNumber() != null && !doctor.getMedicalRegistrationNumber().isEmpty()) {
                if (holder.layoutDoctorMedRegNum != null) holder.layoutDoctorMedRegNum.setVisibility(View.VISIBLE);
                if (holder.textDoctorMedRegNum != null) holder.textDoctorMedRegNum.setText(doctor.getMedicalRegistrationNumber());
            } else {
                if (holder.layoutDoctorMedRegNum != null) holder.layoutDoctorMedRegNum.setVisibility(View.GONE);
            }

            String vStatus = doctor.getVerificationStatus();
            String accStatus = doctor.getStatus();

            if ("APPROVED".equalsIgnoreCase(vStatus)) {
                if ("DISABLED".equalsIgnoreCase(accStatus)) {
                    holder.textDoctorStatusBadge.setText("DISABLED");
                    holder.textDoctorStatusBadge.setTextColor(0xFF991B1B);
                    holder.docStatusBadgeCard.setCardBackgroundColor(0xFFFEE2E2);
                    holder.btnToggleDoctorStatus.setText("REACTIVATE");
                    holder.btnToggleDoctorStatus.setTextColor(0xFF16A34A);
                    holder.btnToggleDoctorStatus.setStrokeColor(ColorStateList.valueOf(0xFF16A34A));
                } else {
                    holder.textDoctorStatusBadge.setText("ACTIVE");
                    holder.textDoctorStatusBadge.setTextColor(0xFF166534);
                    holder.docStatusBadgeCard.setCardBackgroundColor(0xFFDCFCE7);
                    holder.btnToggleDoctorStatus.setText("DISABLE");
                    holder.btnToggleDoctorStatus.setTextColor(0xFFDC2626);
                    holder.btnToggleDoctorStatus.setStrokeColor(ColorStateList.valueOf(0xFFDC2626));
                }
                holder.layoutDoctorActions.setVisibility(View.VISIBLE);
                holder.btnApproveDoctor.setVisibility(View.GONE);
                holder.btnRejectDoctor.setVisibility(View.GONE);
                holder.btnToggleDoctorStatus.setVisibility(View.VISIBLE);
                holder.layoutRejectionReason.setVisibility(View.GONE);

            } else if ("REJECTED".equalsIgnoreCase(vStatus)) {
                holder.textDoctorStatusBadge.setText("REJECTED");
                holder.textDoctorStatusBadge.setTextColor(0xFFDC2626);
                holder.docStatusBadgeCard.setCardBackgroundColor(0xFFFEE2E2);
                holder.layoutDoctorActions.setVisibility(View.GONE);
                holder.layoutRejectionReason.setVisibility(View.VISIBLE);
                holder.textRejectionReason.setText(doctor.getRejectionReason() != null && !doctor.getRejectionReason().isEmpty()
                        ? doctor.getRejectionReason()
                        : "Doctor credentials could not be verified.");

            } else {
                // PENDING
                holder.textDoctorStatusBadge.setText("PENDING");
                holder.textDoctorStatusBadge.setTextColor(0xFFB45309);
                holder.docStatusBadgeCard.setCardBackgroundColor(0xFFFEF3C7);
                holder.layoutDoctorActions.setVisibility(View.VISIBLE);
                holder.btnApproveDoctor.setVisibility(View.VISIBLE);
                holder.btnRejectDoctor.setVisibility(View.VISIBLE);
                holder.btnToggleDoctorStatus.setVisibility(View.GONE);
                holder.layoutRejectionReason.setVisibility(View.GONE);
            }

            holder.btnApproveDoctor.setOnClickListener(v -> handleApproveDoctor(doctor));
            holder.btnRejectDoctor.setOnClickListener(v -> handleRejectDoctor(doctor));
            holder.btnToggleDoctorStatus.setOnClickListener(v -> handleToggleDoctorStatus(doctor));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class DoctorViewHolder extends RecyclerView.ViewHolder {
            TextView textDoctorName, textDoctorSpecialization, textDoctorRegId, textDoctorHospital, textDoctorEmail, textDoctorMedRegNum;
            TextView textDoctorStatusBadge, textRejectionReason;
            View layoutRejectionReason, layoutDoctorActions, layoutDoctorMedRegNum;
            MaterialCardView docStatusBadgeCard;
            MaterialButton btnApproveDoctor, btnRejectDoctor, btnToggleDoctorStatus;

            public DoctorViewHolder(@NonNull View itemView) {
                super(itemView);
                textDoctorName = itemView.findViewById(R.id.textDoctorName);
                textDoctorSpecialization = itemView.findViewById(R.id.textDoctorSpecialization);
                textDoctorRegId = itemView.findViewById(R.id.textDoctorRegId);
                textDoctorHospital = itemView.findViewById(R.id.textDoctorHospital);
                textDoctorEmail = itemView.findViewById(R.id.textDoctorEmail);
                textDoctorMedRegNum = itemView.findViewById(R.id.textDoctorMedRegNum);
                layoutDoctorMedRegNum = itemView.findViewById(R.id.layoutDoctorMedRegNum);
                textDoctorStatusBadge = itemView.findViewById(R.id.textDoctorStatusBadge);
                textRejectionReason = itemView.findViewById(R.id.textRejectionReason);
                layoutRejectionReason = itemView.findViewById(R.id.layoutRejectionReason);
                layoutDoctorActions = itemView.findViewById(R.id.layoutDoctorActions);
                docStatusBadgeCard = itemView.findViewById(R.id.docStatusBadgeCard);
                btnApproveDoctor = itemView.findViewById(R.id.btnApproveDoctor);
                btnRejectDoctor = itemView.findViewById(R.id.btnRejectDoctor);
                btnToggleDoctorStatus = itemView.findViewById(R.id.btnToggleDoctorStatus);
            }
        }
    }
}
