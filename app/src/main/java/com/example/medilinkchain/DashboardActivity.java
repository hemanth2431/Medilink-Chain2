package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.util.Log;

public class DashboardActivity extends AppCompatActivity {

    CardView emergencyLayout;
    CardView medicalRecordsLayout;
    CardView prescriptionsLayout;
    CardView medicineManagementLayout;
    CardView viewRecordsLayout;
    CardView healthRiskLayout;
    CardView hospitalLayout;

    ImageView menuBtn;
    ImageView notificationBtn;

    TextView userNameTextView;
    BottomNavigationView bottomNavigationView;
    DrawerLayout drawerLayout;

    FirebaseAuth auth;
    FirebaseFirestore db;
    private com.google.firebase.firestore.ListenerRegistration profileListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        try {
            // Initialize Firebase & Session
            auth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            SessionManager sessionManager = new SessionManager(this);

            // Bind Views
            emergencyLayout = findViewById(R.id.emergencyLayout);
            medicalRecordsLayout = findViewById(R.id.medicalRecordsLayout);
            prescriptionsLayout = findViewById(R.id.prescriptionsLayout);
            medicineManagementLayout = findViewById(R.id.medicineManagementLayout);
            viewRecordsLayout = findViewById(R.id.viewRecordsLayout);
            healthRiskLayout = findViewById(R.id.healthRiskLayout);
            hospitalLayout = findViewById(R.id.hospitalLayout);

            menuBtn = findViewById(R.id.menuBtn);
            notificationBtn = findViewById(R.id.notificationBtn);

            userNameTextView = findViewById(R.id.userNameTextView);
            bottomNavigationView = findViewById(R.id.bottomNavigationView);
            drawerLayout = findViewById(R.id.drawerLayout);

            // Session isolation & verification check
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null || !sessionManager.isLoggedIn()) {
                Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            // Role-based navigation guard: Ensure non-patients are routed to their designated consoles
            if (sessionManager.isSuperAdmin() || sessionManager.isHospitalAdmin() || sessionManager.isAdmin()) {
                Intent intent = new Intent(DashboardActivity.this, AdminDashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            } else if (sessionManager.isDoctor()) {
                Intent intent = new Intent(DashboardActivity.this, DoctorDashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            // Mandatory email verification gating is removed: Patients directly access Dashboard
            if (currentUser != null) {
                String email = currentUser.getEmail();
                if (email != null && userNameTextView != null) {
                    String nameFallback = email.split("@")[0];
                    if (nameFallback.length() > 0) {
                        nameFallback = nameFallback.substring(0, 1).toUpperCase() + nameFallback.substring(1);
                    }
                    userNameTextView.setText(nameFallback);
                }
                
                // Dynamic Firebase Integration: Fetch user's name
                String patientId = currentUser.getUid();
                
                // Get name from profile
                profileListener = db.collection("users").document(patientId)
                        .addSnapshotListener((doc, err) -> {
                            if (err != null) return;
                            if (doc != null && doc.exists() && userNameTextView != null) {
                                String name = doc.getString("fullName");
                                if (name != null && !name.isEmpty()) {
                                    userNameTextView.setText(name);
                                }
                            }
                        });
            }

            // --- BUTTON CLICK LISTENERS ---

            // 1. Emergency Access -> EmergencyActivity
            if (emergencyLayout != null) {
                emergencyLayout.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(DashboardActivity.this, EmergencyActivity.class));
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Failed to start EmergencyActivity", e);
                        Toast.makeText(this, "Emergency details unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 2. Medical Records -> MedicalRecordActivity
            if (medicalRecordsLayout != null) {
                medicalRecordsLayout.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(DashboardActivity.this, MedicalRecordActivity.class));
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Failed to start MedicalRecordActivity", e);
                        Toast.makeText(this, "Medical Record service unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 3. Prescriptions -> PrescriptionListActivity
            if (prescriptionsLayout != null) {
                prescriptionsLayout.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(DashboardActivity.this, PrescriptionListActivity.class));
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Failed to start PrescriptionListActivity", e);
                        Toast.makeText(this, "Prescription service unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 4. Medicine Management -> MedicineDashboardActivity
            if (medicineManagementLayout != null) {
                medicineManagementLayout.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(DashboardActivity.this, MedicineDashboardActivity.class));
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Failed to start MedicineDashboardActivity", e);
                        Toast.makeText(this, "Medicine service unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 5. View Records -> ViewRecordsActivity
            if (viewRecordsLayout != null) {
                viewRecordsLayout.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(DashboardActivity.this, ViewRecordsActivity.class));
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Failed to start ViewRecordsActivity", e);
                        Toast.makeText(this, "View Records service unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 6. Health Assessment -> HealthAssessmentActivity
            if (healthRiskLayout != null) {
                healthRiskLayout.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(DashboardActivity.this, HealthAssessmentActivity.class));
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Failed to start HealthAssessmentActivity", e);
                        Toast.makeText(this, "Health Assessment service unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 7. Nearby Hospitals -> HospitalActivity
            if (hospitalLayout != null) {
                hospitalLayout.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(DashboardActivity.this, HospitalActivity.class));
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Failed to start HospitalActivity", e);
                        Toast.makeText(this, "Hospital finder unavailable", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Drawer and Menu actions
            if (menuBtn != null && drawerLayout != null) {
                menuBtn.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
            }

            View menuProfile = findViewById(R.id.menuProfile);
            if (menuProfile != null) {
                menuProfile.setOnClickListener(v -> {
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                    startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
                });
            }

            View menuSettings = findViewById(R.id.menuSettings);
            if (menuSettings != null) {
                menuSettings.setOnClickListener(v -> {
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                    startActivity(new Intent(DashboardActivity.this, SettingsActivity.class));
                });
            }

            View menuLogout = findViewById(R.id.menuLogout);
            if (menuLogout != null) {
                menuLogout.setOnClickListener(v -> {
                    auth.signOut();
                    new SessionManager(this).logoutUser();
                    Intent intent = new Intent(DashboardActivity.this, GetStartedActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            // --- BOTTOM NAVIGATION BAR FUNCTIONALITY ---
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.nav_dashboard);
                bottomNavigationView.setOnItemSelectedListener(item -> {
                    try {
                        int itemId = item.getItemId();
                        if (itemId == R.id.nav_dashboard) {
                            return true;
                        } else if (itemId == R.id.nav_records) {
                            startActivity(new Intent(DashboardActivity.this, ViewRecordsActivity.class));
                            return true;
                        } else if (itemId == R.id.nav_hospitals) {
                            startActivity(new Intent(DashboardActivity.this, HospitalActivity.class));
                            return true;
                        } else if (itemId == R.id.nav_profile) {
                            startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
                            return true;
                        }
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Bottom navigation failed: " + e.getMessage(), e);
                    }
                    return false;
                });
            }
        } catch (Exception e) {
            Log.e("DashboardActivity", "Fatal error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error starting dashboard, resetting session...", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }
    }
}
