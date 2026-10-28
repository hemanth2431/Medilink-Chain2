package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class DoctorProfileActivity extends AppCompatActivity {

    private TextView textProfileDoctorName, textProfileDoctorRole;
    private EditText inputProfileDoctorId, inputProfileHospital, inputProfileFullName, inputProfileSpecialization, inputProfilePhone;
    private MaterialButton btnSaveProfile, btnLogoutDoctor;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private SessionManager sessionManager;
    private String doctorUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        initViews();
        loadProfile();
        setupListeners();
    }

    private void initViews() {
        textProfileDoctorName = findViewById(R.id.textProfileDoctorName);
        textProfileDoctorRole = findViewById(R.id.textProfileDoctorRole);
        inputProfileDoctorId = findViewById(R.id.inputProfileDoctorId);
        inputProfileHospital = findViewById(R.id.inputProfileHospital);
        inputProfileFullName = findViewById(R.id.inputProfileFullName);
        inputProfileSpecialization = findViewById(R.id.inputProfileSpecialization);
        inputProfilePhone = findViewById(R.id.inputProfilePhone);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogoutDoctor = findViewById(R.id.btnLogoutDoctor);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnSaveProfile.setOnClickListener(v -> saveProfile());

        btnLogoutDoctor.setOnClickListener(v -> {
            auth.signOut();
            sessionManager.logoutUser();
            Intent intent = new Intent(DoctorProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        doctorUid = user.getUid();

        // Baseline from session
        if (inputProfileHospital != null) {
            inputProfileHospital.setText(sessionManager.getHospitalName().isEmpty() ? "MediLinkChain Central Network" : sessionManager.getHospitalName());
        }
        if (inputProfileDoctorId != null) {
            inputProfileDoctorId.setText(sessionManager.getRegistrationId().isEmpty() ? "DR26H001" : sessionManager.getRegistrationId());
        }

        db.collection("users").document(doctorUid).get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                String name = doc.getString("fullName");
                if (name == null || name.isEmpty()) name = doc.getString("name");
                String dId = doc.getString("doctorId");
                if (dId == null || dId.isEmpty()) dId = doc.getString("doctorRegistrationId");
                String hosp = doc.getString("hospitalName");
                String spec = doc.getString("specialization");
                String phone = doc.getString("phone");

                if (name != null && !name.isEmpty()) {
                    textProfileDoctorName.setText(name);
                    inputProfileFullName.setText(name);
                }
                if (dId != null && !dId.isEmpty()) {
                    inputProfileDoctorId.setText(dId);
                }
                if (hosp != null && !hosp.isEmpty()) {
                    inputProfileHospital.setText(hosp);
                }
                if (spec != null && !spec.isEmpty()) {
                    inputProfileSpecialization.setText(spec);
                }
                if (phone != null && !phone.isEmpty()) {
                    inputProfilePhone.setText(phone);
                }
            }
        });
    }

    private void saveProfile() {
        String newName = inputProfileFullName.getText().toString().trim();
        String newSpec = inputProfileSpecialization.getText().toString().trim();
        String newPhone = inputProfilePhone.getText().toString().trim();

        if (newName.isEmpty()) {
            inputProfileFullName.setError("Doctor Name is required");
            inputProfileFullName.requestFocus();
            return;
        }

        if (doctorUid == null) return;

        btnSaveProfile.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", newName);
        updates.put("name", newName);
        updates.put("specialization", newSpec.isEmpty() ? "General Medicine" : newSpec);
        updates.put("phone", newPhone);

        db.collection("users").document(doctorUid).set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Also update doctor_profiles
                    db.collection("doctor_profiles").document(doctorUid).set(updates, SetOptions.merge());
                    // Also update organization_members
                    db.collection("organization_members").document("member_" + doctorUid).set(updates, SetOptions.merge());

                    btnSaveProfile.setEnabled(true);
                    textProfileDoctorName.setText(newName);
                    Toast.makeText(DoctorProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    btnSaveProfile.setEnabled(true);
                    Toast.makeText(DoctorProfileActivity.this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
