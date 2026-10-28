package com.example.medilinkchain;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PatientManagementActivity — Admin module for searching patients and managing account activation status.
 */
public class PatientManagementActivity extends AppCompatActivity {

    private RecyclerView recyclerViewPatients;
    private View layoutEmptyPatients;
    private ProgressBar progressBarPatients;
    private EditText inputSearchPatients;
    private TextView textPatientCountSummary;

    private final List<PatientProfileModel> allPatientsList = new ArrayList<>();
    private final List<PatientProfileModel> filteredPatientsList = new ArrayList<>();
    private PatientAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SessionManager sessionManager;
    private String currentOrgId;
    private boolean isSuperAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_management);

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
        loadPatients();
    }

    private void initViews() {
        recyclerViewPatients = findViewById(R.id.recyclerViewPatients);
        layoutEmptyPatients = findViewById(R.id.layoutEmptyPatients);
        progressBarPatients = findViewById(R.id.progressBarPatients);
        inputSearchPatients = findViewById(R.id.inputSearchPatients);
        textPatientCountSummary = findViewById(R.id.textPatientCountSummary);

        recyclerViewPatients.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PatientAdapter(filteredPatientsList);
        recyclerViewPatients.setAdapter(adapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        inputSearchPatients.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterPatients(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadPatients() {
        progressBarPatients.setVisibility(View.VISIBLE);
        layoutEmptyPatients.setVisibility(View.GONE);

        OrganizationRepository.listPatients(currentOrgId, isSuperAdmin, new OrganizationRepository.Callback<List<PatientProfileModel>>() {
            @Override
            public void onSuccess(List<PatientProfileModel> list) {
                progressBarPatients.setVisibility(View.GONE);
                allPatientsList.clear();
                allPatientsList.addAll(list);
                filterPatients(inputSearchPatients.getText().toString().trim());
            }

            @Override
            public void onFailure(String error) {
                progressBarPatients.setVisibility(View.GONE);
                Toast.makeText(PatientManagementActivity.this, "Failed to load patients: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterPatients(String query) {
        filteredPatientsList.clear();
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        for (PatientProfileModel p : allPatientsList) {
            boolean matches = lowerQuery.isEmpty()
                    || p.getFullName().toLowerCase(Locale.ROOT).contains(lowerQuery)
                    || p.getPatientId().toLowerCase(Locale.ROOT).contains(lowerQuery)
                    || p.getEmail().toLowerCase(Locale.ROOT).contains(lowerQuery)
                    || p.getContactNumber().contains(lowerQuery);

            if (matches) {
                filteredPatientsList.add(p);
            }
        }

        adapter.notifyDataSetChanged();
        layoutEmptyPatients.setVisibility(filteredPatientsList.isEmpty() ? View.VISIBLE : View.GONE);
        textPatientCountSummary.setText(filteredPatientsList.size() + " Registered Patients");
    }

    private void confirmTogglePatientStatus(PatientProfileModel patient) {
        boolean isCurrentlyActive = patient.isActive();
        String targetStatus = isCurrentlyActive ? "DISABLED" : "ACTIVE";
        String actionVerb = isCurrentlyActive ? "Disable" : "Enable";

        new MaterialAlertDialogBuilder(this)
                .setTitle(actionVerb + " Patient Account")
                .setMessage("Are you sure you want to " + actionVerb.toLowerCase() + " " + patient.getFullName() + " (" + patient.getPatientId() + ")?\n\n"
                        + (isCurrentlyActive ? "The patient will not be able to access protected application features until re-enabled." : "The patient will regain full access to their dashboard and medical records."))
                .setPositiveButton(actionVerb, (dialog, which) -> {
                    progressBarPatients.setVisibility(View.VISIBLE);
                    String actorId = (currentUser != null) ? currentUser.getUid() : "admin";
                    String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "admin@medilinkchain.org";

                    String actorRole = isSuperAdmin ? "SUPER_ADMIN" : "HOSPITAL_ADMIN";
                    String orgToUse = currentOrgId != null ? currentOrgId : OrganizationRepository.DEFAULT_ORG_ID;

                    OrganizationRepository.updatePatientStatus(
                            patient.getUserId(),
                            patient.getPatientId(),
                            orgToUse,
                            targetStatus,
                            actorId,
                            actorEmail,
                            actorRole,
                            new OrganizationRepository.ActionCallback() {
                                @Override
                                public void onSuccess() {
                                    progressBarPatients.setVisibility(View.GONE);
                                    Toast.makeText(PatientManagementActivity.this, "Patient status updated to " + targetStatus, Toast.LENGTH_SHORT).show();
                                    loadPatients();
                                }

                                @Override
                                public void onFailure(String error) {
                                    progressBarPatients.setVisibility(View.GONE);
                                    Toast.makeText(PatientManagementActivity.this, "Status update failed: " + error, Toast.LENGTH_LONG).show();
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

    private class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {

        private final List<PatientProfileModel> list;

        public PatientAdapter(List<PatientProfileModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient_admin, parent, false);
            return new PatientViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
            PatientProfileModel patient = list.get(position);

            holder.textPatientName.setText(patient.getFullName());
            holder.textPatientId.setText("ID: " + patient.getPatientId());
            holder.textPatientEmail.setText(patient.getEmail());
            holder.textPatientContact.setText(patient.getContactNumber());

            if (patient.isActive()) {
                holder.patientStatusBadgeCard.setCardBackgroundColor(0xFFDCFCE7);
                holder.textPatientStatusBadge.setText("ACTIVE");
                holder.textPatientStatusBadge.setTextColor(0xFF166534);
                holder.btnTogglePatientStatus.setText("Disable Account");
                holder.btnTogglePatientStatus.setTextColor(0xFFDC2626);
                holder.btnTogglePatientStatus.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFFDC2626));
            } else {
                holder.patientStatusBadgeCard.setCardBackgroundColor(0xFFFEE2E2);
                holder.textPatientStatusBadge.setText("DISABLED");
                holder.textPatientStatusBadge.setTextColor(0xFF991B1B);
                holder.btnTogglePatientStatus.setText("Enable Account");
                holder.btnTogglePatientStatus.setTextColor(0xFF059669);
                holder.btnTogglePatientStatus.setStrokeColor(android.content.res.ColorStateList.valueOf(0xFF059669));
            }

            holder.btnTogglePatientStatus.setOnClickListener(v -> confirmTogglePatientStatus(patient));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class PatientViewHolder extends RecyclerView.ViewHolder {
            TextView textPatientName, textPatientId, textPatientStatusBadge, textPatientEmail, textPatientContact;
            MaterialCardView patientStatusBadgeCard;
            MaterialButton btnTogglePatientStatus;

            public PatientViewHolder(@NonNull View itemView) {
                super(itemView);
                textPatientName = itemView.findViewById(R.id.textPatientName);
                textPatientId = itemView.findViewById(R.id.textPatientId);
                textPatientStatusBadge = itemView.findViewById(R.id.textPatientStatusBadge);
                textPatientEmail = itemView.findViewById(R.id.textPatientEmail);
                textPatientContact = itemView.findViewById(R.id.textPatientContact);
                patientStatusBadgeCard = itemView.findViewById(R.id.patientStatusBadgeCard);
                btnTogglePatientStatus = itemView.findViewById(R.id.btnTogglePatientStatus);
            }
        }
    }
}
