package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class DoctorDashboardActivity extends AppCompatActivity {

    private TextView doctorNameText, doctorSpecializationText, doctorHospitalText, doctorRegIdText;
    private BottomNavigationView bottomNavigationView;
    private FirebaseAuth auth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_dashboard);

        try {
            auth = FirebaseAuth.getInstance();
            sessionManager = new SessionManager(this);

            // Role authorization check: Must be a verified Doctor or Admin
            if (!sessionManager.isDoctor() && !sessionManager.isAdmin()) {
                Toast.makeText(this, "Access Denied: Doctor authorization required.", Toast.LENGTH_SHORT).show();
                auth.signOut();
                sessionManager.logoutUser();
                Intent intent = new Intent(DoctorDashboardActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            // Bind Views
            doctorNameText = findViewById(R.id.doctorNameText);
            doctorSpecializationText = findViewById(R.id.doctorSpecializationText);
            doctorHospitalText = findViewById(R.id.doctorHospitalText);
            doctorRegIdText = findViewById(R.id.doctorRegIdText);
            bottomNavigationView = findViewById(R.id.bottomNavigationView);

            loadDoctorProfile();

            // --- TOP LOGOUT BUTTON ---
            View logoutBtn = findViewById(R.id.doctorLogoutBtn);
            if (logoutBtn != null) {
                logoutBtn.setOnClickListener(v -> {
                    auth.signOut();
                    sessionManager.logoutUser();
                    Intent intent = new Intent(DoctorDashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            // --- 4 MAIN FEATURES NAVIGATION ---

            // 1. Scan Patient QR
            View cardScanQR = findViewById(R.id.cardScanPatientQR);
            if (cardScanQR != null) {
                cardScanQR.setOnClickListener(v -> {
                    startActivity(new Intent(DoctorDashboardActivity.this, DoctorScanActivity.class));
                });
            }

            // 2. Doctor Profile
            View cardProfile = findViewById(R.id.cardDoctorProfile);
            if (cardProfile != null) {
                cardProfile.setOnClickListener(v -> {
                    startActivity(new Intent(DoctorDashboardActivity.this, DoctorProfileActivity.class));
                });
            }

            // 3. Doctor Timing
            View cardTiming = findViewById(R.id.cardDoctorTiming);
            if (cardTiming != null) {
                cardTiming.setOnClickListener(v -> {
                    startActivity(new Intent(DoctorDashboardActivity.this, DoctorTimingActivity.class));
                });
            }

            // 4. Hospital Patients
            View cardPatients = findViewById(R.id.cardHospitalPatients);
            if (cardPatients != null) {
                cardPatients.setOnClickListener(v -> {
                    startActivity(new Intent(DoctorDashboardActivity.this, DoctorHospitalPatientsActivity.class));
                });
            }

            // --- BOTTOM NAVIGATION ---
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.nav_doctor_dashboard);
                bottomNavigationView.setOnItemSelectedListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_doctor_dashboard) {
                        return true;
                    } else if (itemId == R.id.nav_doctor_scan) {
                        startActivity(new Intent(DoctorDashboardActivity.this, DoctorScanActivity.class));
                        return true;
                    } else if (itemId == R.id.nav_doctor_patients) {
                        startActivity(new Intent(DoctorDashboardActivity.this, DoctorHospitalPatientsActivity.class));
                        return true;
                    } else if (itemId == R.id.nav_doctor_profile) {
                        startActivity(new Intent(DoctorDashboardActivity.this, DoctorProfileActivity.class));
                        return true;
                    }
                    return false;
                });
            }
        } catch (Exception e) {
            Log.e("DoctorDashboard", "Fatal error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error starting doctor dashboard", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_doctor_dashboard);
        }
        loadDoctorProfile();
    }

    private void loadDoctorProfile() {
        // Fallback baseline from session
        if (doctorHospitalText != null && sessionManager.getHospitalName() != null && !sessionManager.getHospitalName().isEmpty()) {
            doctorHospitalText.setText(sessionManager.getHospitalName());
        }
        if (doctorRegIdText != null && sessionManager.getRegistrationId() != null && !sessionManager.getRegistrationId().isEmpty()) {
            doctorRegIdText.setText(sessionManager.getRegistrationId());
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String name = doc.getString("fullName");
                        if (name == null || name.isEmpty()) name = doc.getString("name");
                        if (name != null && !name.isEmpty() && doctorNameText != null) {
                            doctorNameText.setText(name);
                        }

                        String spec = doc.getString("specialization");
                        if (spec != null && !spec.isEmpty() && doctorSpecializationText != null) {
                            doctorSpecializationText.setText(spec);
                        }

                        String hosp = doc.getString("hospitalName");
                        if (hosp != null && !hosp.isEmpty() && doctorHospitalText != null) {
                            doctorHospitalText.setText(hosp);
                        }

                        String regId = doc.getString("doctorId");
                        if (regId == null || regId.isEmpty()) regId = doc.getString("doctorRegistrationId");
                        if (regId != null && !regId.isEmpty() && doctorRegIdText != null) {
                            doctorRegIdText.setText(regId);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.w("DoctorDashboard", "Could not fetch user doc: " + e.getMessage()));
    }
}
