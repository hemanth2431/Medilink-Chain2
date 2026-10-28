package com.example.medilinkchain;

import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DoctorHospitalPatientsActivity extends AppCompatActivity {

    private RecyclerView recyclerHospitalPatients;
    private View layoutEmptyPatients;
    private TextView textEmptyPatientsTitle, textDoctorHospitalSubtitle, textPatientCount, textHospitalScopeTag;
    private ProgressBar progressBarPatients;
    private EditText inputSearchHospitalPatients;

    private final List<PatientProfileModel> allPatients = new ArrayList<>();
    private final List<PatientProfileModel> filteredPatients = new ArrayList<>();
    private PatientAdapter adapter;

    private SessionManager sessionManager;
    private String doctorOrgId;
    private String doctorHospitalName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_hospital_patients);

        sessionManager = new SessionManager(this);
        doctorOrgId = sessionManager.getOrgId();
        doctorHospitalName = sessionManager.getHospitalName();

        initViews();
        setupListeners();
        loadDoctorHospitalAndPatients();
    }

    private void initViews() {
        recyclerHospitalPatients = findViewById(R.id.recyclerHospitalPatients);
        layoutEmptyPatients = findViewById(R.id.layoutEmptyPatients);
        textEmptyPatientsTitle = findViewById(R.id.textEmptyPatientsTitle);
        textDoctorHospitalSubtitle = findViewById(R.id.textDoctorHospitalSubtitle);
        textPatientCount = findViewById(R.id.textPatientCount);
        textHospitalScopeTag = findViewById(R.id.textHospitalScopeTag);
        progressBarPatients = findViewById(R.id.progressBarPatients);
        inputSearchHospitalPatients = findViewById(R.id.inputSearchHospitalPatients);

        recyclerHospitalPatients.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PatientAdapter(filteredPatients);
        recyclerHospitalPatients.setAdapter(adapter);

        if (doctorHospitalName != null && !doctorHospitalName.isEmpty()) {
            textDoctorHospitalSubtitle.setText(doctorHospitalName);
        }
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        inputSearchHospitalPatients.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadDoctorHospitalAndPatients() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc != null && doc.exists()) {
                            String org = doc.getString("organizationId");
                            String hosp = doc.getString("hospitalName");
                            if (org != null && !org.isEmpty()) doctorOrgId = org;
                            if (hosp != null && !hosp.isEmpty()) {
                                doctorHospitalName = hosp;
                                textDoctorHospitalSubtitle.setText(doctorHospitalName);
                            }
                        }
                        fetchPatientsForHospital();
                    })
                    .addOnFailureListener(e -> fetchPatientsForHospital());
        } else {
            fetchPatientsForHospital();
        }
    }

    private void fetchPatientsForHospital() {
        progressBarPatients.setVisibility(View.VISIBLE);
        layoutEmptyPatients.setVisibility(View.GONE);

        // Strictly scoped to the doctor's assigned hospital (isSuperAdmin = false)
        OrganizationRepository.listPatients(doctorOrgId, false, new OrganizationRepository.Callback<List<PatientProfileModel>>() {
            @Override
            public void onSuccess(List<PatientProfileModel> list) {
                progressBarPatients.setVisibility(View.GONE);
                allPatients.clear();
                if (list != null) {
                    allPatients.addAll(list);
                }
                applyFilter();
            }

            @Override
            public void onFailure(String error) {
                progressBarPatients.setVisibility(View.GONE);
                Toast.makeText(DoctorHospitalPatientsActivity.this, "Failed to load hospital patients: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilter() {
        filteredPatients.clear();
        String query = inputSearchHospitalPatients.getText().toString().trim().toLowerCase(Locale.ROOT);

        for (PatientProfileModel p : allPatients) {
            boolean matches = query.isEmpty()
                    || p.getFullName().toLowerCase(Locale.ROOT).contains(query)
                    || p.getPatientId().toLowerCase(Locale.ROOT).contains(query)
                    || p.getEmail().toLowerCase(Locale.ROOT).contains(query);

            if (matches) {
                filteredPatients.add(p);
            }
        }

        textPatientCount.setText("Patients (" + filteredPatients.size() + ")");
        adapter.notifyDataSetChanged();
        layoutEmptyPatients.setVisibility(filteredPatients.isEmpty() ? View.VISIBLE : View.GONE);
        textEmptyPatientsTitle.setText("No patients found in " + (doctorHospitalName != null ? doctorHospitalName : "assigned hospital"));
    }

    private class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {

        private final List<PatientProfileModel> list;

        public PatientAdapter(List<PatientProfileModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hospital_patient, parent, false);
            return new PatientViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
            PatientProfileModel patient = list.get(position);

            holder.textPatientName.setText(patient.getFullName());
            holder.textPatientId.setText("ID: " + (patient.getPatientId().isEmpty() ? patient.getUserId() : patient.getPatientId()));
            
            String ageStr = patient.getAge() > 0 ? (patient.getAge() + " Yrs") : "Age: N/A";
            String genderStr = !patient.getGender().isEmpty() ? patient.getGender() : "Gender: N/A";
            holder.textPatientAgeGender.setText(ageStr + " • " + genderStr);

            holder.textPatientBloodGroup.setText("Blood: " + (!patient.getBloodGroup().isEmpty() ? patient.getBloodGroup() : "N/A"));
            holder.textPatientContact.setText("Contact: " + (!patient.getContactNumber().isEmpty() ? patient.getContactNumber() : patient.getEmail()));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(DoctorHospitalPatientsActivity.this, PatientDetailsActivity.class);
                intent.putExtra("patientId", patient.getUserId().isEmpty() ? patient.getPatientId() : patient.getUserId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class PatientViewHolder extends RecyclerView.ViewHolder {
            TextView textPatientName, textPatientId, textPatientAgeGender, textPatientBloodGroup, textPatientContact;

            public PatientViewHolder(@NonNull View itemView) {
                super(itemView);
                textPatientName = itemView.findViewById(R.id.textPatientName);
                textPatientId = itemView.findViewById(R.id.textPatientId);
                textPatientAgeGender = itemView.findViewById(R.id.textPatientAgeGender);
                textPatientBloodGroup = itemView.findViewById(R.id.textPatientBloodGroup);
                textPatientContact = itemView.findViewById(R.id.textPatientContact);
            }
        }
    }
}
