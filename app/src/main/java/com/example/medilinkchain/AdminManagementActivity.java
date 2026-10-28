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
 * AdminManagementActivity — Super Admin exclusive module for creating, inviting,
 * managing, reassigning, enabling, and disabling organization administrators.
 */
public class AdminManagementActivity extends AppCompatActivity {

    private RecyclerView recyclerViewAdmins;
    private View layoutEmptyAdmins;
    private ProgressBar progressBarAdmins;
    private EditText inputSearchAdmin;
    private TextView textAdminCountSummary;
    private MaterialButton btnAddAdmin;

    private final List<OrganizationMemberModel> allAdminsList = new ArrayList<>();
    private final List<OrganizationMemberModel> filteredAdminsList = new ArrayList<>();
    private final List<OrganizationModel> allHospitalsList = new ArrayList<>();
    private AdminAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);
    private final SimpleDateFormat detailDateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_management);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        initViews();
        setupListeners();
        loadHospitals();
        loadAdmins();
    }

    private void initViews() {
        recyclerViewAdmins = findViewById(R.id.recyclerViewAdmins);
        layoutEmptyAdmins = findViewById(R.id.layoutEmptyAdmins);
        progressBarAdmins = findViewById(R.id.progressBarAdmins);
        inputSearchAdmin = findViewById(R.id.inputSearchAdmin);
        textAdminCountSummary = findViewById(R.id.textAdminCountSummary);
        btnAddAdmin = findViewById(R.id.btnAddAdmin);

        recyclerViewAdmins.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminAdapter(filteredAdminsList);
        recyclerViewAdmins.setAdapter(adapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnAddAdmin.setOnClickListener(v -> showAddAdminDialog());

        inputSearchAdmin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAdmins(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadHospitals() {
        OrganizationRepository.listOrganizations(new OrganizationRepository.Callback<List<OrganizationModel>>() {
            @Override
            public void onSuccess(List<OrganizationModel> list) {
                allHospitalsList.clear();
                allHospitalsList.addAll(list);
                syncHospitalNamesInAdmins();
            }

            @Override
            public void onFailure(String error) {
                // Silently handle hospital load failure; will retry on dialog opening
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
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadAdmins() {
        progressBarAdmins.setVisibility(View.VISIBLE);
        layoutEmptyAdmins.setVisibility(View.GONE);

        OrganizationRepository.listAdmins(null, new OrganizationRepository.Callback<List<OrganizationMemberModel>>() {
            @Override
            public void onSuccess(List<OrganizationMemberModel> list) {
                progressBarAdmins.setVisibility(View.GONE);
                allAdminsList.clear();
                allAdminsList.addAll(list);
                syncHospitalNamesInAdmins();
                filterAdmins(inputSearchAdmin.getText().toString().trim());
            }

            @Override
            public void onFailure(String error) {
                progressBarAdmins.setVisibility(View.GONE);
                Toast.makeText(AdminManagementActivity.this, "Failed to load admins: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterAdmins(String query) {
        filteredAdminsList.clear();
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        for (OrganizationMemberModel m : allAdminsList) {
            boolean matches = lowerQuery.isEmpty()
                    || (m.getFullName() != null && m.getFullName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    || (m.getAdminId() != null && m.getAdminId().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    || (m.getEmail() != null && m.getEmail().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    || (m.getHospitalName() != null && m.getHospitalName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    || (m.getHospitalCode() != null && m.getHospitalCode().toLowerCase(Locale.ROOT).contains(lowerQuery))
                    || (m.getRole() != null && m.getRole().toLowerCase(Locale.ROOT).contains(lowerQuery));

            if (matches) {
                filteredAdminsList.add(m);
            }
        }

        adapter.notifyDataSetChanged();
        layoutEmptyAdmins.setVisibility(filteredAdminsList.isEmpty() ? View.VISIBLE : View.GONE);
        textAdminCountSummary.setText(filteredAdminsList.size() + " Organization Administrators");
    }

    // =========================================================================
    // ADD / CREATE HOSPITAL ADMIN DIALOG
    // =========================================================================

    private void showAddAdminDialog() {
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

        inputPassword.setText("");
        inputConfirmPassword.setText("");

        List<String> hospitalNames = new ArrayList<>();
        for (OrganizationModel h : allHospitalsList) {
            hospitalNames.add(h.getName() + " [" + h.getCode() + "]");
        }
        if (hospitalNames.isEmpty()) {
            hospitalNames.add("No hospitals registered yet");
        }

        ArrayAdapter<String> hospitalSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hospitalNames);
        hospitalSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHospital.setAdapter(hospitalSpinnerAdapter);

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
                    Toast.makeText(AdminManagementActivity.this, "Failed to refresh ID", Toast.LENGTH_SHORT).show();
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

                String actorId = (currentUser != null) ? currentUser.getUid() : "super_admin";
                String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "superadmin@medilinkchain.org";

                progressBar.setVisibility(View.VISIBLE);
                positiveBtn.setEnabled(false);

                OrganizationRepository.createHospitalAdmin(
                        AdminManagementActivity.this,
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

                                new MaterialAlertDialogBuilder(AdminManagementActivity.this)
                                        .setTitle("Hospital Admin Created Successfully")
                                        .setMessage("Administrator " + name + " has been created and assigned to " + selectedHospital.getName() + " [" + selectedHospital.getCode() + "].\n\n"
                                                + "• Admin ID: " + adminId + "\n"
                                                + "• Email: " + email + "\n"
                                                + "• Assigned Hospital: " + selectedHospital.getName() + "\n\n"
                                                + "A verification link has been sent to their email. The administrator can sign in from the common login portal using either their Email or Admin ID.")
                                        .setPositiveButton("OK", null)
                                        .show();

                                loadAdmins();
                            }

                            @Override
                            public void onFailure(String error) {
                                progressBar.setVisibility(View.GONE);
                                positiveBtn.setEnabled(true);
                                Toast.makeText(AdminManagementActivity.this, "Creation failed: " + error, Toast.LENGTH_LONG).show();
                            }
                        }
                );
            });
        });

        dialog.show();
    }

    // =========================================================================
    // ADMIN ACTIONS
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

                    progressBarAdmins.setVisibility(View.VISIBLE);
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
                                    progressBarAdmins.setVisibility(View.GONE);
                                    Toast.makeText(AdminManagementActivity.this, "Reassigned to " + newHosp.getName(), Toast.LENGTH_SHORT).show();
                                    loadAdmins();
                                }

                                @Override
                                public void onFailure(String error) {
                                    progressBarAdmins.setVisibility(View.GONE);
                                    Toast.makeText(AdminManagementActivity.this, "Reassignment failed: " + error, Toast.LENGTH_LONG).show();
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
                    progressBarAdmins.setVisibility(View.VISIBLE);
                    OrganizationRepository.sendAdminPasswordReset(member.getEmail(), new OrganizationRepository.ActionCallback() {
                        @Override
                        public void onSuccess() {
                            progressBarAdmins.setVisibility(View.GONE);
                            Toast.makeText(AdminManagementActivity.this, "Password reset email dispatched to " + member.getEmail(), Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onFailure(String error) {
                            progressBarAdmins.setVisibility(View.GONE);
                            Toast.makeText(AdminManagementActivity.this, "Failed to send reset email: " + error, Toast.LENGTH_LONG).show();
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
                .setMessage("Are you sure you want to " + actionVerb.toLowerCase() + " " + member.getFullName() + "?\n\n"
                        + (isCurrentlyActive ? "This administrator will immediately lose access to the Admin Dashboard." : "This administrator will regain access to the Admin Dashboard."))
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
                                    Toast.makeText(AdminManagementActivity.this, "Status updated to " + targetStatus, Toast.LENGTH_SHORT).show();
                                    loadAdmins();
                                }

                                @Override
                                public void onFailure(String error) {
                                    new MaterialAlertDialogBuilder(AdminManagementActivity.this)
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
                .setMessage("Are you sure you want to remove " + member.getFullName() + " from organization administrators? This action cannot be undone.")
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
                                    Toast.makeText(AdminManagementActivity.this, "Administrator removed.", Toast.LENGTH_SHORT).show();
                                    loadAdmins();
                                }

                                @Override
                                public void onFailure(String error) {
                                    new MaterialAlertDialogBuilder(AdminManagementActivity.this)
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
    // RECYCLERVIEW ADAPTER
    // =========================================================================

    private class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.AdminViewHolder> {

        private final List<OrganizationMemberModel> list;

        public AdminAdapter(List<OrganizationMemberModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public AdminViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_member, parent, false);
            return new AdminViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull AdminViewHolder holder, int position) {
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

        class AdminViewHolder extends RecyclerView.ViewHolder {
            TextView textAdminName, textAdminId, textAdminEmail, textAdminStatus, textAdminAssignedHospital, textRoleTag, textAdminCreatedAt;
            MaterialCardView cardAdminIdBadge, adminStatusBadge, cardRoleTag;
            MaterialButton btnViewAdminDetails, btnReassignHospital, btnResetAdminPassword, btnToggleStatus, btnRemoveAdmin;

            public AdminViewHolder(@NonNull View itemView) {
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
