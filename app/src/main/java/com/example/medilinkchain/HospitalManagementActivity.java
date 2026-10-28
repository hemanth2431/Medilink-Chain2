package com.example.medilinkchain;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * HospitalManagementActivity — Super Admin central module for managing hospitals
 * and provisioning/managing Hospital Admins with custom Admin IDs (e.g. AD26H001).
 */
public class HospitalManagementActivity extends AppCompatActivity {

    private RecyclerView recyclerViewHospitals;
    private View layoutEmptyHospitals;
    private ProgressBar progressBarHospitals;
    private EditText inputSearchHospital;
    private TextView textHospitalCountSummary;
    private MaterialButton btnAddHospital;
    private MaterialButton btnAddAdmin;
    private MaterialButton btnTabHospitals;
    private MaterialButton btnTabAdmins;

    private final List<OrganizationModel> allHospitalsList = new ArrayList<>();
    private final List<OrganizationModel> filteredHospitalsList = new ArrayList<>();
    private HospitalAdapter hospitalAdapter;

    private final List<OrganizationMemberModel> allAdminsList = new ArrayList<>();
    private final List<OrganizationMemberModel> filteredAdminsList = new ArrayList<>();
    private AdminMemberAdapter adminAdapter;

    private boolean isShowingAdmins = false;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final SimpleDateFormat detailDateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hospital_management);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        initViews();
        setupListeners();
        loadHospitals();
    }

    private void initViews() {
        recyclerViewHospitals = findViewById(R.id.recyclerViewHospitals);
        layoutEmptyHospitals = findViewById(R.id.layoutEmptyHospitals);
        progressBarHospitals = findViewById(R.id.progressBarHospitals);
        inputSearchHospital = findViewById(R.id.inputSearchHospital);
        textHospitalCountSummary = findViewById(R.id.textHospitalCountSummary);
        btnAddHospital = findViewById(R.id.btnAddHospital);
        btnAddAdmin = findViewById(R.id.btnAddAdmin);
        btnTabHospitals = findViewById(R.id.btnTabHospitals);
        btnTabAdmins = findViewById(R.id.btnTabAdmins);

        recyclerViewHospitals.setLayoutManager(new LinearLayoutManager(this));
        hospitalAdapter = new HospitalAdapter(filteredHospitalsList);
        adminAdapter = new AdminMemberAdapter(filteredAdminsList);
        recyclerViewHospitals.setAdapter(hospitalAdapter);

        updateTabStyles();
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnAddHospital.setOnClickListener(v -> showAddHospitalDialog());
        btnAddAdmin.setOnClickListener(v -> showCreateAdminDialog(null));

        btnTabHospitals.setOnClickListener(v -> switchTab(false));
        btnTabAdmins.setOnClickListener(v -> switchTab(true));

        inputSearchHospital.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (isShowingAdmins) {
                    filterAdmins(query);
                } else {
                    filterHospitals(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void switchTab(boolean showAdmins) {
        if (isShowingAdmins == showAdmins) return;
        isShowingAdmins = showAdmins;
        updateTabStyles();

        inputSearchHospital.setText("");
        if (isShowingAdmins) {
            inputSearchHospital.setHint("Search admins by name, ID, email or hospital...");
            recyclerViewHospitals.setAdapter(adminAdapter);
            if (allAdminsList.isEmpty()) {
                loadAdmins();
            } else {
                filterAdmins("");
            }
        } else {
            inputSearchHospital.setHint("Search hospitals by name, code or location...");
            recyclerViewHospitals.setAdapter(hospitalAdapter);
            filterHospitals("");
        }
    }

    private void updateTabStyles() {
        if (btnTabHospitals == null || btnTabAdmins == null) return;
        if (!isShowingAdmins) {
            btnTabHospitals.setBackgroundColor(0xFF4F46E5);
            btnTabHospitals.setTextColor(0xFFFFFFFF);
            btnTabHospitals.setStrokeColor(ColorStateList.valueOf(0xFF4F46E5));

            btnTabAdmins.setBackgroundColor(0x00000000);
            btnTabAdmins.setTextColor(0xFF4F46E5);
            btnTabAdmins.setStrokeColor(ColorStateList.valueOf(0xFFCBD5E1));
        } else {
            btnTabAdmins.setBackgroundColor(0xFF0284C7);
            btnTabAdmins.setTextColor(0xFFFFFFFF);
            btnTabAdmins.setStrokeColor(ColorStateList.valueOf(0xFF0284C7));

            btnTabHospitals.setBackgroundColor(0x00000000);
            btnTabHospitals.setTextColor(0xFF4F46E5);
            btnTabHospitals.setStrokeColor(ColorStateList.valueOf(0xFFCBD5E1));
        }
    }

    private void loadHospitals() {
        progressBarHospitals.setVisibility(View.VISIBLE);
        layoutEmptyHospitals.setVisibility(View.GONE);

        OrganizationRepository.listOrganizations(new OrganizationRepository.Callback<List<OrganizationModel>>() {
            @Override
            public void onSuccess(List<OrganizationModel> list) {
                progressBarHospitals.setVisibility(View.GONE);
                allHospitalsList.clear();
                allHospitalsList.addAll(list);
                if (!isShowingAdmins) {
                    filterHospitals(inputSearchHospital.getText().toString().trim());
                } else {
                    syncHospitalNamesInAdmins();
                }
            }

            @Override
            public void onFailure(String error) {
                progressBarHospitals.setVisibility(View.GONE);
                Toast.makeText(HospitalManagementActivity.this, "Failed to load hospitals: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadAdmins() {
        progressBarHospitals.setVisibility(View.VISIBLE);
        layoutEmptyHospitals.setVisibility(View.GONE);

        OrganizationRepository.listAdmins(null, new OrganizationRepository.Callback<List<OrganizationMemberModel>>() {
            @Override
            public void onSuccess(List<OrganizationMemberModel> list) {
                progressBarHospitals.setVisibility(View.GONE);
                allAdminsList.clear();
                allAdminsList.addAll(list);
                syncHospitalNamesInAdmins();
                filterAdmins(inputSearchHospital.getText().toString().trim());
            }

            @Override
            public void onFailure(String error) {
                progressBarHospitals.setVisibility(View.GONE);
                Toast.makeText(HospitalManagementActivity.this, "Failed to load administrators: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void syncHospitalNamesInAdmins() {
        for (OrganizationMemberModel member : allAdminsList) {
            if ((member.getHospitalName() == null || member.getHospitalName().isEmpty()) && member.getOrganizationId() != null) {
                for (OrganizationModel h : allHospitalsList) {
                    if (member.getOrganizationId().equals(h.getId())) {
                        member.setHospitalName(h.getName());
                        member.setHospitalCode(h.getCode());
                        break;
                    }
                }
            }
        }
    }

    private void filterHospitals(String query) {
        filteredHospitalsList.clear();
        String lower = query.toLowerCase(Locale.ROOT);

        for (OrganizationModel h : allHospitalsList) {
            boolean matches = lower.isEmpty()
                    || (h.getName() != null && h.getName().toLowerCase(Locale.ROOT).contains(lower))
                    || (h.getCode() != null && h.getCode().toLowerCase(Locale.ROOT).contains(lower))
                    || (h.getAddress() != null && h.getAddress().toLowerCase(Locale.ROOT).contains(lower))
                    || (h.getPrimaryAdminEmail() != null && h.getPrimaryAdminEmail().toLowerCase(Locale.ROOT).contains(lower))
                    || (h.getPrimaryAdminName() != null && h.getPrimaryAdminName().toLowerCase(Locale.ROOT).contains(lower));

            if (matches) {
                filteredHospitalsList.add(h);
            }
        }

        hospitalAdapter.notifyDataSetChanged();
        layoutEmptyHospitals.setVisibility(filteredHospitalsList.isEmpty() ? View.VISIBLE : View.GONE);
        textHospitalCountSummary.setText(filteredHospitalsList.size() + " Registered Healthcare Organizations");
    }

    private void filterAdmins(String query) {
        filteredAdminsList.clear();
        String lower = query.toLowerCase(Locale.ROOT);

        for (OrganizationMemberModel m : allAdminsList) {
            boolean matches = lower.isEmpty()
                    || (m.getFullName() != null && m.getFullName().toLowerCase(Locale.ROOT).contains(lower))
                    || (m.getAdminId() != null && m.getAdminId().toLowerCase(Locale.ROOT).contains(lower))
                    || (m.getEmail() != null && m.getEmail().toLowerCase(Locale.ROOT).contains(lower))
                    || (m.getHospitalName() != null && m.getHospitalName().toLowerCase(Locale.ROOT).contains(lower))
                    || (m.getHospitalCode() != null && m.getHospitalCode().toLowerCase(Locale.ROOT).contains(lower))
                    || (m.getRole() != null && m.getRole().toLowerCase(Locale.ROOT).contains(lower));

            if (matches) {
                filteredAdminsList.add(m);
            }
        }

        adminAdapter.notifyDataSetChanged();
        layoutEmptyHospitals.setVisibility(filteredAdminsList.isEmpty() ? View.VISIBLE : View.GONE);
        textHospitalCountSummary.setText(filteredAdminsList.size() + " Hospital Administrators");
    }

    // =========================================================================
    // CREATE HOSPITAL ADMIN MODAL (WITH UNIQUE ADMIN ID GENERATION)
    // =========================================================================

    private void showCreateAdminDialog(OrganizationModel preselectedHospital) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_hospital_admin, null);

        Spinner spinnerHospital = dialogView.findViewById(R.id.spinnerCreateAdminHospital);
        TextInputEditText inputAdminId = dialogView.findViewById(R.id.inputCreateAdminId);
        MaterialButton btnRegenerate = dialogView.findViewById(R.id.btnRegenerateAdminId);
        TextInputEditText inputName = dialogView.findViewById(R.id.inputCreateAdminName);
        TextInputEditText inputEmail = dialogView.findViewById(R.id.inputCreateAdminEmail);
        TextInputEditText inputPhone = dialogView.findViewById(R.id.inputCreateAdminPhone);
        TextInputEditText inputPassword = dialogView.findViewById(R.id.inputCreateAdminPassword);
        TextInputEditText inputConfirmPassword = dialogView.findViewById(R.id.inputCreateAdminConfirmPassword);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBarCreateAdmin);

        // Security rule: all password fields must be clean and unpopulated by default
        inputPassword.setText("");
        inputConfirmPassword.setText("");

        // Populate hospital spinner
        List<String> hospitalNames = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < allHospitalsList.size(); i++) {
            OrganizationModel h = allHospitalsList.get(i);
            hospitalNames.add(h.getName() + " [" + h.getCode() + "]");
            if (preselectedHospital != null && h.getId().equals(preselectedHospital.getId())) {
                selectedIndex = i;
            }
        }

        if (hospitalNames.isEmpty()) {
            hospitalNames.add("No hospitals registered yet");
        }

        ArrayAdapter<String> hospitalSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hospitalNames);
        hospitalSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHospital.setAdapter(hospitalSpinnerAdapter);
        if (!allHospitalsList.isEmpty()) {
            spinnerHospital.setSelection(selectedIndex);
        }

        // Auto-generate project-standard Admin ID (e.g. AD26H001)
        inputAdminId.setHint("Generating ID...");
        OrganizationRepository.generateNextAdminId(new OrganizationRepository.Callback<String>() {
            @Override
            public void onSuccess(String generatedId) {
                inputAdminId.setText(generatedId);
            }

            @Override
            public void onFailure(String error) {
                inputAdminId.setText("AD26H001");
            }
        });

        btnRegenerate.setOnClickListener(v -> {
            inputAdminId.setHint("Generating ID...");
            OrganizationRepository.generateNextAdminId(new OrganizationRepository.Callback<String>() {
                @Override
                public void onSuccess(String generatedId) {
                    inputAdminId.setText(generatedId);
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(HospitalManagementActivity.this, "Failed to refresh ID", Toast.LENGTH_SHORT).show();
                }
            });
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("CREATE ADMIN", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveBtn.setOnClickListener(v -> {
                if (allHospitalsList.isEmpty()) {
                    Toast.makeText(this, "Please create at least one hospital organization first.", Toast.LENGTH_SHORT).show();
                    return;
                }

                int hospitalPos = spinnerHospital.getSelectedItemPosition();
                if (hospitalPos < 0 || hospitalPos >= allHospitalsList.size()) {
                    Toast.makeText(this, "Please select an assigned hospital.", Toast.LENGTH_SHORT).show();
                    return;
                }

                OrganizationModel selectedHospital = allHospitalsList.get(hospitalPos);
                String adminId = inputAdminId.getText() != null ? inputAdminId.getText().toString().trim() : "";
                String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
                String email = inputEmail.getText() != null ? inputEmail.getText().toString().trim() : "";
                String phone = inputPhone.getText() != null ? inputPhone.getText().toString().trim() : "";
                String password = inputPassword.getText() != null ? inputPassword.getText().toString().trim() : "";
                String confirmPassword = inputConfirmPassword.getText() != null ? inputConfirmPassword.getText().toString().trim() : "";

                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter the Administrator Full Name.", Toast.LENGTH_SHORT).show();
                    inputName.requestFocus();
                    return;
                }

                if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid administrator email address.", Toast.LENGTH_SHORT).show();
                    inputEmail.requestFocus();
                    return;
                }

                if (adminId.isEmpty() || adminId.length() < 4) {
                    Toast.makeText(this, "Please provide a valid Admin ID (e.g. AD26H001).", Toast.LENGTH_SHORT).show();
                    inputAdminId.requestFocus();
                    return;
                }

                if (password.isEmpty()) {
                    Toast.makeText(this, "Please enter an initial password.", Toast.LENGTH_SHORT).show();
                    inputPassword.requestFocus();
                    return;
                }

                if (password.length() < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters long.", Toast.LENGTH_SHORT).show();
                    inputPassword.requestFocus();
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    Toast.makeText(this, "Password and confirmation password do not match.", Toast.LENGTH_SHORT).show();
                    inputConfirmPassword.requestFocus();
                    return;
                }

                // Super Admin identity verification
                String actorId = (currentUser != null) ? currentUser.getUid() : "super_admin";
                String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "superadmin@medilinkchain.org";

                progressBar.setVisibility(View.VISIBLE);
                positiveBtn.setEnabled(false);

                OrganizationRepository.createHospitalAdmin(
                        HospitalManagementActivity.this,
                        selectedHospital.getId(),
                        selectedHospital.getName(),
                        selectedHospital.getCode(),
                        adminId,
                        name,
                        email,
                        phone,
                        password,
                        actorId,
                        actorEmail,
                        new OrganizationRepository.ActionCallback() {
                            @Override
                            public void onSuccess() {
                                progressBar.setVisibility(View.GONE);
                                dialog.dismiss();

                                new MaterialAlertDialogBuilder(HospitalManagementActivity.this)
                                        .setTitle("Hospital Admin Created Successfully")
                                        .setMessage("Administrator " + name + " has been created and assigned to " + selectedHospital.getName() + " [" + selectedHospital.getCode() + "].\n\n"
                                                + "• Admin ID: " + adminId + "\n"
                                                + "• Email: " + email + "\n"
                                                + "• Assigned Hospital: " + selectedHospital.getName() + "\n\n"
                                                + "A verification link has been sent to their email. The administrator can sign in from the common login portal using either their Email or Admin ID.")
                                        .setPositiveButton("OK", null)
                                        .show();

                                loadHospitals();
                                loadAdmins();
                            }

                            @Override
                            public void onFailure(String error) {
                                progressBar.setVisibility(View.GONE);
                                positiveBtn.setEnabled(true);
                                Toast.makeText(HospitalManagementActivity.this, "Creation failed: " + error, Toast.LENGTH_LONG).show();
                            }
                        }
                );
            });
        });

        dialog.show();
    }

    private void showAddHospitalDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_hospital, null);
        EditText inputName = dialogView.findViewById(R.id.inputHospitalName);
        EditText inputCode = dialogView.findViewById(R.id.inputHospitalCode);
        EditText inputAddress = dialogView.findViewById(R.id.inputHospitalAddress);
        EditText inputContact = dialogView.findViewById(R.id.inputHospitalContact);
        EditText inputEmail = dialogView.findViewById(R.id.inputHospitalEmail);

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("CREATE HOSPITAL", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    String code = inputCode.getText().toString().trim().toUpperCase(Locale.ROOT);
                    String address = inputAddress.getText().toString().trim();
                    String contact = inputContact.getText().toString().trim();
                    String email = inputEmail.getText().toString().trim();

                    if (name.isEmpty() || code.isEmpty()) {
                        Toast.makeText(this, "Please enter Hospital Name and Hospital Code", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createHospital(name, code, address, contact, email);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createHospital(String name, String code, String address, String contact, String email) {
        progressBarHospitals.setVisibility(View.VISIBLE);
        String actorId = (currentUser != null) ? currentUser.getUid() : "super_admin";
        String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "superadmin@medilinkchain.org";

        OrganizationRepository.createOrganization(
                name,
                code,
                address,
                contact,
                email,
                actorId,
                actorEmail,
                new OrganizationRepository.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        progressBarHospitals.setVisibility(View.GONE);
                        Toast.makeText(HospitalManagementActivity.this, "Hospital created successfully! Status: ACTIVE", Toast.LENGTH_SHORT).show();
                        loadHospitals();
                    }

                    @Override
                    public void onFailure(String error) {
                        progressBarHospitals.setVisibility(View.GONE);
                        Toast.makeText(HospitalManagementActivity.this, "Creation failed: " + error, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    // =========================================================================
    // ADMIN ACTIONS: DETAILS, REASSIGN, PASSWORD RESET, STATUS, REMOVE
    // =========================================================================

    private void showAdminDetailsDialog(OrganizationMemberModel member) {
        String hospitalDisplay = (member.getHospitalName() != null && !member.getHospitalName().isEmpty())
                ? member.getHospitalName() + (member.getHospitalCode() != null ? " [" + member.getHospitalCode() + "]" : "")
                : "Not assigned to a specific hospital";

        String createdDateStr = member.getCreatedAt() != null ? detailDateFormat.format(member.getCreatedAt()) : "N/A";
        String phoneStr = (member.getPhone() != null && !member.getPhone().isEmpty()) ? member.getPhone() : "Not provided";
        String adminIdStr = member.getAdminId() != null ? member.getAdminId() : "N/A";

        String detailsMessage = "• Full Name: " + member.getFullName() + "\n"
                + "• Admin ID: " + adminIdStr + "\n"
                + "• Email Address: " + member.getEmail() + "\n"
                + "• Contact Number: " + phoneStr + "\n"
                + "• Assigned Hospital: " + hospitalDisplay + "\n"
                + "• Role: " + member.getRole() + "\n"
                + "• Primary Admin: " + (member.isPrimaryAdmin() ? "Yes" : "No") + "\n"
                + "• Account Status: " + member.getStatus() + "\n"
                + "• Created Date: " + createdDateStr;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Administrator Details")
                .setMessage(detailsMessage)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showReassignHospitalDialog(OrganizationMemberModel member) {
        if (allHospitalsList.isEmpty()) {
            Toast.makeText(this, "No hospitals available for reassignment.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> hospitalNames = new ArrayList<>();
        int currentSelection = 0;
        for (int i = 0; i < allHospitalsList.size(); i++) {
            OrganizationModel h = allHospitalsList.get(i);
            hospitalNames.add(h.getName() + " [" + h.getCode() + "]");
            if (member.getOrganizationId() != null && member.getOrganizationId().equals(h.getId())) {
                currentSelection = i;
            }
        }

        final int[] selectedIndex = {currentSelection};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reassign Hospital — " + member.getFullName())
                .setSingleChoiceItems(hospitalNames.toArray(new String[0]), currentSelection, (dialog, which) -> {
                    selectedIndex[0] = which;
                })
                .setPositiveButton("REASSIGN", (dialog, which) -> {
                    if (selectedIndex[0] < 0 || selectedIndex[0] >= allHospitalsList.size()) return;
                    OrganizationModel newHosp = allHospitalsList.get(selectedIndex[0]);

                    progressBarHospitals.setVisibility(View.VISIBLE);
                    String actorId = (currentUser != null) ? currentUser.getUid() : "super_admin";
                    String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "superadmin@medilinkchain.org";

                    OrganizationRepository.reassignAdminHospital(
                            member.getId(),
                            member.getUserId(),
                            newHosp.getId(),
                            newHosp.getName(),
                            newHosp.getCode(),
                            actorId,
                            actorEmail,
                            new OrganizationRepository.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    progressBarHospitals.setVisibility(View.GONE);
                                    Toast.makeText(HospitalManagementActivity.this, "Reassigned to " + newHosp.getName(), Toast.LENGTH_SHORT).show();
                                    loadAdmins();
                                    loadHospitals();
                                }

                                @Override
                                public void onFailure(String error) {
                                    progressBarHospitals.setVisibility(View.GONE);
                                    Toast.makeText(HospitalManagementActivity.this, "Reassignment failed: " + error, Toast.LENGTH_LONG).show();
                                }
                            }
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendAdminPasswordReset(OrganizationMemberModel member) {
        if (member.getEmail() == null || !member.getEmail().contains("@")) {
            Toast.makeText(this, "Administrator has no valid registered email address.", Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Send Password Reset Email")
                .setMessage("Send a secure password reset link to " + member.getFullName() + " at " + member.getEmail() + "?")
                .setPositiveButton("Send Reset Link", (dialog, which) -> {
                    progressBarHospitals.setVisibility(View.VISIBLE);
                    OrganizationRepository.sendAdminPasswordReset(member.getEmail(), new OrganizationRepository.ActionCallback() {
                        @Override
                        public void onSuccess() {
                            progressBarHospitals.setVisibility(View.GONE);
                            Toast.makeText(HospitalManagementActivity.this, "Password reset email dispatched to " + member.getEmail(), Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onFailure(String error) {
                            progressBarHospitals.setVisibility(View.GONE);
                            Toast.makeText(HospitalManagementActivity.this, "Failed to send reset email: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmToggleStatus(OrganizationMemberModel member) {
        boolean isCurrentlyActive = member.isActive();
        String targetStatus = isCurrentlyActive ? "DISABLED" : "ACTIVE";
        String actionVerb = isCurrentlyActive ? "Disable" : "Enable";

        new MaterialAlertDialogBuilder(this)
                .setTitle(actionVerb + " Administrator Account")
                .setMessage("Are you sure you want to " + actionVerb.toLowerCase() + " " + member.getFullName() + " (" + (member.getAdminId() != null ? member.getAdminId() : member.getEmail()) + ")?\n\n"
                        + (isCurrentlyActive ? "This administrator will immediately lose access to the Hospital Admin portal." : "This administrator will regain access to the Hospital Admin portal."))
                .setPositiveButton(actionVerb, (dialog, which) -> {
                    String actorId = (currentUser != null) ? currentUser.getUid() : "super_admin";
                    String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "superadmin@medilinkchain.org";

                    OrganizationRepository.updateAdminStatus(
                            member.getOrganizationId(),
                            member.getId(),
                            member.getRole(),
                            targetStatus,
                            actorId,
                            actorEmail,
                            new OrganizationRepository.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(HospitalManagementActivity.this, "Status updated to " + targetStatus, Toast.LENGTH_SHORT).show();
                                    loadAdmins();
                                }

                                @Override
                                public void onFailure(String error) {
                                    new MaterialAlertDialogBuilder(HospitalManagementActivity.this)
                                            .setTitle("Action Blocked")
                                            .setMessage(error)
                                            .setPositiveButton("OK", null)
                                            .show();
                                }
                            }
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemoveAdmin(OrganizationMemberModel member) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove Administrator")
                .setMessage("Are you sure you want to remove " + member.getFullName() + " (" + (member.getAdminId() != null ? member.getAdminId() : member.getEmail()) + ")? This action cannot be undone.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    String actorId = (currentUser != null) ? currentUser.getUid() : "super_admin";
                    String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "superadmin@medilinkchain.org";

                    OrganizationRepository.removeAdmin(
                            member.getOrganizationId(),
                            member.getId(),
                            member.getRole(),
                            actorId,
                            actorEmail,
                            new OrganizationRepository.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    Toast.makeText(HospitalManagementActivity.this, "Administrator removed.", Toast.LENGTH_SHORT).show();
                                    loadAdmins();
                                    loadHospitals();
                                }

                                @Override
                                public void onFailure(String error) {
                                    new MaterialAlertDialogBuilder(HospitalManagementActivity.this)
                                            .setTitle("Action Blocked")
                                            .setMessage(error)
                                            .setPositiveButton("OK", null)
                                            .show();
                                }
                            }
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // RECYCLERVIEW ADAPTER — HOSPITALS
    // =========================================================================

    private class HospitalAdapter extends RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder> {

        private final List<OrganizationModel> list;

        public HospitalAdapter(List<OrganizationModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public HospitalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hospital_admin, parent, false);
            return new HospitalViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull HospitalViewHolder holder, int position) {
            OrganizationModel hospital = list.get(position);

            holder.textHospitalName.setText(hospital.getName());
            holder.textHospitalCodeBadge.setText(hospital.getCode());
            holder.textHospitalAddress.setText(hospital.getAddress() != null && !hospital.getAddress().isEmpty()
                    ? hospital.getAddress()
                    : "No address specified");

            if (hospital.isActive()) {
                holder.textHospitalStatus.setText("ACTIVE");
                holder.textHospitalStatus.setTextColor(0xFF166534);
                holder.hospitalStatusBadge.setCardBackgroundColor(0xFFDCFCE7);
            } else {
                holder.textHospitalStatus.setText("DISABLED");
                holder.textHospitalStatus.setTextColor(0xFF991B1B);
                holder.hospitalStatusBadge.setCardBackgroundColor(0xFFFEE2E2);
            }

            String adminEmail = hospital.getPrimaryAdminEmail();
            String adminName = hospital.getPrimaryAdminName();
            if (adminEmail != null && !adminEmail.isEmpty()) {
                String label = (adminName != null && !adminName.isEmpty()) ? adminName + " (" + adminEmail + ")" : adminEmail;
                holder.textPrimaryAdminEmail.setText(label);
                holder.textPrimaryAdminStatus.setVisibility(View.VISIBLE);
                holder.textPrimaryAdminStatus.setText("Status: ACTIVE");
            } else {
                holder.textPrimaryAdminEmail.setText("No admin assigned");
                holder.textPrimaryAdminStatus.setVisibility(View.GONE);
            }

            holder.btnAssignAdmin.setOnClickListener(v -> showCreateAdminDialog(hospital));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class HospitalViewHolder extends RecyclerView.ViewHolder {
            TextView textHospitalName, textHospitalCodeBadge, textHospitalAddress, textHospitalStatus;
            MaterialCardView hospitalStatusBadge;
            TextView textPrimaryAdminEmail, textPrimaryAdminStatus;
            MaterialButton btnAssignAdmin;

            public HospitalViewHolder(@NonNull View itemView) {
                super(itemView);
                textHospitalName = itemView.findViewById(R.id.textHospitalName);
                textHospitalCodeBadge = itemView.findViewById(R.id.textHospitalCodeBadge);
                textHospitalAddress = itemView.findViewById(R.id.textHospitalAddress);
                textHospitalStatus = itemView.findViewById(R.id.textHospitalStatus);
                hospitalStatusBadge = itemView.findViewById(R.id.hospitalStatusBadge);
                textPrimaryAdminEmail = itemView.findViewById(R.id.textPrimaryAdminEmail);
                textPrimaryAdminStatus = itemView.findViewById(R.id.textPrimaryAdminStatus);
                btnAssignAdmin = itemView.findViewById(R.id.btnAssignAdmin);
            }
        }
    }

    // =========================================================================
    // RECYCLERVIEW ADAPTER — ADMIN MEMBERS
    // =========================================================================

    private class AdminMemberAdapter extends RecyclerView.Adapter<AdminMemberAdapter.AdminMemberViewHolder> {

        private final List<OrganizationMemberModel> list;

        public AdminMemberAdapter(List<OrganizationMemberModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public AdminMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_member, parent, false);
            return new AdminMemberViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull AdminMemberViewHolder holder, int position) {
            OrganizationMemberModel member = list.get(position);

            holder.textAdminName.setText(member.getFullName());
            holder.textAdminEmail.setText(member.getEmail());

            if (member.getAdminId() != null && !member.getAdminId().isEmpty()) {
                holder.cardAdminIdBadge.setVisibility(View.VISIBLE);
                holder.textAdminId.setText(member.getAdminId());
            } else {
                holder.cardAdminIdBadge.setVisibility(View.GONE);
            }

            String hospitalDisplay = (member.getHospitalName() != null && !member.getHospitalName().isEmpty())
                    ? member.getHospitalName() + (member.getHospitalCode() != null ? " [" + member.getHospitalCode() + "]" : "")
                    : "All Hospitals (Global)";
            holder.textAdminAssignedHospital.setText(hospitalDisplay);

            holder.textRoleTag.setText(member.getRole());
            if (member.isSuperAdmin()) {
                holder.cardRoleTag.setCardBackgroundColor(0xFFEEF2FF);
                holder.textRoleTag.setTextColor(0xFF4338CA);
            } else {
                holder.cardRoleTag.setCardBackgroundColor(0xFFEFF6FF);
                holder.textRoleTag.setTextColor(0xFF1D4ED8);
            }

            if (member.isActive()) {
                holder.adminStatusBadge.setCardBackgroundColor(0xFFDCFCE7);
                holder.textAdminStatus.setText("ACTIVE");
                holder.textAdminStatus.setTextColor(0xFF166534);
                holder.btnToggleStatus.setText("Disable");
                holder.btnToggleStatus.setTextColor(0xFFDC2626);
                holder.btnToggleStatus.setStrokeColor(ColorStateList.valueOf(0xFFDC2626));
            } else {
                holder.adminStatusBadge.setCardBackgroundColor(0xFFFEE2E2);
                holder.textAdminStatus.setText("DISABLED");
                holder.textAdminStatus.setTextColor(0xFF991B1B);
                holder.btnToggleStatus.setText("Enable");
                holder.btnToggleStatus.setTextColor(0xFF059669);
                holder.btnToggleStatus.setStrokeColor(ColorStateList.valueOf(0xFF059669));
            }

            if (member.getCreatedAt() != null) {
                holder.textAdminCreatedAt.setText("Created: " + dateFormat.format(member.getCreatedAt()));
            } else {
                holder.textAdminCreatedAt.setText("Created: Recently");
            }

            holder.btnViewAdminDetails.setOnClickListener(v -> showAdminDetailsDialog(member));
            holder.btnReassignHospital.setOnClickListener(v -> showReassignHospitalDialog(member));
            holder.btnResetAdminPassword.setOnClickListener(v -> sendAdminPasswordReset(member));
            holder.btnToggleStatus.setOnClickListener(v -> confirmToggleStatus(member));
            holder.btnRemoveAdmin.setOnClickListener(v -> confirmRemoveAdmin(member));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class AdminMemberViewHolder extends RecyclerView.ViewHolder {
            TextView textAdminName, textAdminId, textAdminEmail, textAdminStatus, textAdminAssignedHospital, textRoleTag, textAdminCreatedAt;
            MaterialCardView cardAdminIdBadge, adminStatusBadge, cardRoleTag;
            MaterialButton btnViewAdminDetails, btnReassignHospital, btnResetAdminPassword, btnToggleStatus, btnRemoveAdmin;

            public AdminMemberViewHolder(@NonNull View itemView) {
                super(itemView);
                textAdminName = itemView.findViewById(R.id.textAdminName);
                textAdminId = itemView.findViewById(R.id.textAdminId);
                cardAdminIdBadge = itemView.findViewById(R.id.cardAdminIdBadge);
                textAdminEmail = itemView.findViewById(R.id.textAdminEmail);
                textAdminStatus = itemView.findViewById(R.id.textAdminStatus);
                textAdminAssignedHospital = itemView.findViewById(R.id.textAdminAssignedHospital);
                textRoleTag = itemView.findViewById(R.id.textRoleTag);
                textAdminCreatedAt = itemView.findViewById(R.id.textAdminCreatedAt);
                adminStatusBadge = itemView.findViewById(R.id.adminStatusBadge);
                cardRoleTag = itemView.findViewById(R.id.cardRoleTag);
                btnViewAdminDetails = itemView.findViewById(R.id.btnViewAdminDetails);
                btnReassignHospital = itemView.findViewById(R.id.btnReassignHospital);
                btnResetAdminPassword = itemView.findViewById(R.id.btnResetAdminPassword);
                btnToggleStatus = itemView.findViewById(R.id.btnToggleStatus);
                btnRemoveAdmin = itemView.findViewById(R.id.btnRemoveAdmin);
            }
        }
    }
}
