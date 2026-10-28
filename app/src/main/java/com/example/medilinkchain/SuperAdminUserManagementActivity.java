package com.example.medilinkchain;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SuperAdminUserManagementActivity — Global user management console for Super Admin.
 * Displays all Patients, Doctors, and Administrators across all hospitals with
 * real-time filtering, searching, and account activation/deactivation controls.
 */
public class SuperAdminUserManagementActivity extends AppCompatActivity {

    public static class UserAccountModel {
        public String id;
        public String name;
        public String email;
        public String role;
        public String status;
        public String organizationId;
        public String hospitalName;
        public String phone;

        public boolean isActive() {
            return !"DISABLED".equalsIgnoreCase(status) && !"SUSPENDED".equalsIgnoreCase(status) && !"REJECTED".equalsIgnoreCase(status);
        }
    }

    private RecyclerView recyclerViewUsers;
    private View layoutEmptyUsers;
    private ProgressBar progressBarUsers;
    private EditText inputSearchUser;
    private Spinner spinnerRoleFilter;
    private Spinner spinnerHospitalFilter;
    private TextView textUserCountSummary;

    private final List<UserAccountModel> allUsers = new ArrayList<>();
    private final List<UserAccountModel> filteredUsers = new ArrayList<>();
    private final List<OrganizationModel> hospitalsList = new ArrayList<>();
    private final List<String> hospitalNames = new ArrayList<>();

    private UserAdapter adapter;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SessionManager sessionManager;
    private FirebaseFirestore db;

    private String selectedRoleFilter = "ALL";
    private String selectedHospitalId = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin_users);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        sessionManager = new SessionManager(this);
        db = FirebaseFirestore.getInstance();

        // Strict Super Admin Access Authorization
        if (!sessionManager.isLoggedIn() || !sessionManager.isSuperAdmin()) {
            Toast.makeText(this, "Access Denied: Super Admin authorization required.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
        loadHospitals();
        loadAllUsers();
    }

    private void initViews() {
        recyclerViewUsers = findViewById(R.id.recyclerViewUsers);
        layoutEmptyUsers = findViewById(R.id.layoutEmptyUsers);
        progressBarUsers = findViewById(R.id.progressBarUsers);
        inputSearchUser = findViewById(R.id.inputSearchUser);
        spinnerRoleFilter = findViewById(R.id.spinnerRoleFilter);
        spinnerHospitalFilter = findViewById(R.id.spinnerHospitalFilter);
        textUserCountSummary = findViewById(R.id.textUserCountSummary);

        recyclerViewUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter(filteredUsers);
        recyclerViewUsers.setAdapter(adapter);

        // Role Filter Setup
        String[] roles = {"ALL", "PATIENT", "DOCTOR", "ADMIN"};
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, roles);
        spinnerRoleFilter.setAdapter(roleAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        inputSearchUser.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        spinnerRoleFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedRoleFilter = parent.getItemAtPosition(position).toString();
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerHospitalFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedHospitalId = "ALL";
                } else if (position - 1 < hospitalsList.size()) {
                    selectedHospitalId = hospitalsList.get(position - 1).getId();
                }
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadHospitals() {
        OrganizationRepository.listOrganizations(new OrganizationRepository.Callback<List<OrganizationModel>>() {
            @Override
            public void onSuccess(List<OrganizationModel> result) {
                hospitalsList.clear();
                hospitalsList.addAll(result);

                hospitalNames.clear();
                hospitalNames.add("All Hospitals");
                for (OrganizationModel org : result) {
                    hospitalNames.add(org.getName() + " (" + org.getCode() + ")");
                }

                ArrayAdapter<String> hospAdapter = new ArrayAdapter<>(SuperAdminUserManagementActivity.this, android.R.layout.simple_spinner_dropdown_item, hospitalNames);
                spinnerHospitalFilter.setAdapter(hospAdapter);
            }

            @Override
            public void onFailure(String error) {
                // Ignore hospital filter loading errors
            }
        });
    }

    private void loadAllUsers() {
        progressBarUsers.setVisibility(View.VISIBLE);
        layoutEmptyUsers.setVisibility(View.GONE);

        db.collection("users").get().addOnCompleteListener(task -> {
            progressBarUsers.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                allUsers.clear();
                for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                    UserAccountModel user = new UserAccountModel();
                    user.id = doc.getId();
                    user.email = doc.getString("email") != null ? doc.getString("email") : "No Email";

                    String name = doc.getString("fullName");
                    if (name == null || name.isEmpty()) name = doc.getString("name");
                    if (name == null || name.isEmpty()) name = doc.getString("patientName");
                    if (name == null || name.isEmpty()) name = doc.getString("doctorName");
                    user.name = (name != null && !name.isEmpty()) ? name : user.email;

                    String r = doc.getString("role");
                    user.role = (r != null && !r.isEmpty()) ? r.toUpperCase() : "PATIENT";

                    String s = doc.getString("status");
                    if (s == null || s.isEmpty()) s = doc.getString("accountStatus");
                    user.status = (s != null && !s.isEmpty()) ? s.toUpperCase() : "ACTIVE";

                    user.organizationId = doc.getString("organizationId");
                    user.hospitalName = doc.getString("hospitalName");
                    if (user.hospitalName == null || user.hospitalName.isEmpty()) {
                        user.hospitalName = "MediLinkChain Network";
                    }

                    allUsers.add(user);
                }
                applyFilters();
            } else {
                Toast.makeText(this, "Failed to load users.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilters() {
        filteredUsers.clear();
        String query = inputSearchUser.getText().toString().trim().toLowerCase(Locale.getDefault());

        for (UserAccountModel user : allUsers) {
            // 1. Role Filter
            if (!"ALL".equalsIgnoreCase(selectedRoleFilter)) {
                if (!selectedRoleFilter.equalsIgnoreCase(user.role)) {
                    continue;
                }
            }

            // 2. Hospital Filter
            if (!"ALL".equalsIgnoreCase(selectedHospitalId)) {
                if (user.organizationId == null || !user.organizationId.equalsIgnoreCase(selectedHospitalId)) {
                    continue;
                }
            }

            // 3. Search Query Filter
            if (!query.isEmpty()) {
                boolean matchesName = user.name != null && user.name.toLowerCase(Locale.getDefault()).contains(query);
                boolean matchesEmail = user.email != null && user.email.toLowerCase(Locale.getDefault()).contains(query);
                boolean matchesRole = user.role != null && user.role.toLowerCase(Locale.getDefault()).contains(query);
                if (!matchesName && !matchesEmail && !matchesRole) {
                    continue;
                }
            }

            filteredUsers.add(user);
        }

        textUserCountSummary.setText(filteredUsers.size() + " USERS");
        layoutEmptyUsers.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void toggleUserStatus(UserAccountModel user) {
        String newStatus = user.isActive() ? "DISABLED" : "ACTIVE";
        String actionLabel = user.isActive() ? "Disable" : "Activate";

        new MaterialAlertDialogBuilder(this)
                .setTitle(actionLabel + " Account")
                .setMessage("Are you sure you want to " + actionLabel.toLowerCase() + " account for " + user.name + " (" + user.email + ")?")
                .setPositiveButton(actionLabel.toUpperCase(), (dialog, which) -> {
                    progressBarUsers.setVisibility(View.VISIBLE);

                    Map<String, Object> update = new HashMap<>();
                    update.put("status", newStatus);
                    update.put("accountStatus", newStatus);

                    db.collection("users").document(user.id).update(update)
                            .addOnSuccessListener(aVoid -> {
                                progressBarUsers.setVisibility(View.GONE);
                                user.status = newStatus;
                                adapter.notifyDataSetChanged();
                                Toast.makeText(SuperAdminUserManagementActivity.this, "User status updated to " + newStatus, Toast.LENGTH_SHORT).show();

                                AuditLogManager.log(
                                        user.organizationId != null ? user.organizationId : OrganizationRepository.DEFAULT_ORG_ID,
                                        currentUser != null ? currentUser.getUid() : "super_admin",
                                        currentUser != null ? currentUser.getEmail() : "superadmin@medilinkchain.org",
                                        "SUPER_ADMIN",
                                        "USER_STATUS_" + newStatus,
                                        "USER",
                                        user.id,
                                        "Super Admin set account status of " + user.email + " to " + newStatus
                                );
                            })
                            .addOnFailureListener(e -> {
                                progressBarUsers.setVisibility(View.GONE);
                                Toast.makeText(SuperAdminUserManagementActivity.this, "Failed to update user status: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // RECYCLERVIEW ADAPTER
    // =========================================================================

    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        private final List<UserAccountModel> list;

        public UserAdapter(List<UserAccountModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_super_admin_user, parent, false);
            return new UserViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            UserAccountModel user = list.get(position);

            holder.textUserName.setText(user.name);
            holder.textUserEmail.setText(user.email);
            holder.textUserRole.setText(user.role);
            holder.textUserHospital.setText("Hospital: " + user.hospitalName);

            // Role styling
            if ("DOCTOR".equalsIgnoreCase(user.role)) {
                holder.userRoleBadge.setCardBackgroundColor(0xFFECFDF5);
                holder.textUserRole.setTextColor(0xFF059669);
                holder.iconUserRole.setImageResource(R.drawable.ic_shield_check_custom);
                holder.iconUserRole.setColorFilter(0xFF059669);
            } else if ("ADMIN".equalsIgnoreCase(user.role) || "HOSPITAL_ADMIN".equalsIgnoreCase(user.role) || "SUPER_ADMIN".equalsIgnoreCase(user.role)) {
                holder.userRoleBadge.setCardBackgroundColor(0xFFEDE9FE);
                holder.textUserRole.setTextColor(0xFF7C3AED);
                holder.iconUserRole.setImageResource(R.drawable.ic_key_custom);
                holder.iconUserRole.setColorFilter(0xFF7C3AED);
            } else {
                holder.userRoleBadge.setCardBackgroundColor(0xFFE0F2FE);
                holder.textUserRole.setTextColor(0xFF0288D1);
                holder.iconUserRole.setImageResource(R.drawable.ic_user_custom);
                holder.iconUserRole.setColorFilter(0xFF0288D1);
            }

            // Status styling
            if (user.isActive()) {
                holder.textUserStatus.setText("Status: ACTIVE");
                holder.textUserStatus.setTextColor(0xFF166534);
                holder.btnToggleUserStatus.setText("Disable");
                holder.btnToggleUserStatus.setTextColor(0xFFEF4444);
                holder.btnToggleUserStatus.setStrokeColor(ColorStateList.valueOf(0xFFEF4444));
            } else {
                holder.textUserStatus.setText("Status: DISABLED");
                holder.textUserStatus.setTextColor(0xFF991B1B);
                holder.btnToggleUserStatus.setText("Activate");
                holder.btnToggleUserStatus.setTextColor(0xFF16A34A);
                holder.btnToggleUserStatus.setStrokeColor(ColorStateList.valueOf(0xFF16A34A));
            }

            holder.btnToggleUserStatus.setOnClickListener(v -> toggleUserStatus(user));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView textUserName, textUserEmail, textUserRole, textUserHospital, textUserStatus;
            MaterialCardView userRoleBadge, cardUserAvatar;
            ImageView iconUserRole;
            MaterialButton btnToggleUserStatus;

            public UserViewHolder(@NonNull View itemView) {
                super(itemView);
                textUserName = itemView.findViewById(R.id.textUserName);
                textUserEmail = itemView.findViewById(R.id.textUserEmail);
                textUserRole = itemView.findViewById(R.id.textUserRole);
                textUserHospital = itemView.findViewById(R.id.textUserHospital);
                textUserStatus = itemView.findViewById(R.id.textUserStatus);
                userRoleBadge = itemView.findViewById(R.id.userRoleBadge);
                cardUserAvatar = itemView.findViewById(R.id.cardUserAvatar);
                iconUserRole = itemView.findViewById(R.id.iconUserRole);
                btnToggleUserStatus = itemView.findViewById(R.id.btnToggleUserStatus);
            }
        }
    }
}
