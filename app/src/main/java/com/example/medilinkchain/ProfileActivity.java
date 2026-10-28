package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private static final int EDIT_PROFILE_REQUEST = 1001;

    private TextView profilePatientName,
            profilePatientId,
            infoFullName,
            infoAge,
            infoContact;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Firebase Initialization
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        profilePatientName = findViewById(R.id.profilePatientName);
        profilePatientId = findViewById(R.id.profilePatientId);
        infoFullName = findViewById(R.id.infoFullName);
        infoAge = findViewById(R.id.infoAge);
        infoContact = findViewById(R.id.infoContact);

        bottomNavigationView = findViewById(R.id.bottomNavigationView);

        // Load profile data from Firestore
        loadUserProfile();

        // --- CLICK LISTENERS ---

        // Back Arrow
        findViewById(R.id.backArrow).setOnClickListener(v -> finish());

        // Header notification
        findViewById(R.id.notificationBtn).setOnClickListener(v -> {
            Toast.makeText(this, "No new security notifications.", Toast.LENGTH_SHORT).show();
        });

        // Edit Profile Action
        findViewById(R.id.btnPrimaryEditProfile).setOnClickListener(v -> openEditProfile());

        // Logout
        findViewById(R.id.btnSecondaryLogout).setOnClickListener(v -> handleSignOut());

        // --- BOTTOM NAVIGATION BAR FUNCTIONALITY ---
        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) {
                startActivity(new Intent(ProfileActivity.this, DashboardActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_records) {
                startActivity(new Intent(ProfileActivity.this, ViewRecordsActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_hospitals) {
                startActivity(new Intent(ProfileActivity.this, HospitalActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            handleSignOut();
            return;
        }

        String email = currentUser.getEmail();
        String uid = currentUser.getUid();
        
        // Generate Patient ID from first 8 characters of Firebase UID
        String patientIdString = "MLC" + uid.substring(0, Math.min(uid.length(), 8)).toUpperCase();
        profilePatientId.setText("Patient ID: " + patientIdString);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        Long ageVal = documentSnapshot.getLong("age");
                        String contact = documentSnapshot.getString("contactNumber");

                        if (fullName != null && !fullName.isEmpty()) {
                            profilePatientName.setText(fullName);
                            infoFullName.setText(fullName);
                        } else if (email != null) {
                            String nameFallback = email.split("@")[0];
                            if (nameFallback.length() > 0) {
                                nameFallback = nameFallback.substring(0, 1).toUpperCase() + nameFallback.substring(1);
                            }
                            profilePatientName.setText(nameFallback);
                            infoFullName.setText(nameFallback);
                        }

                        if (ageVal != null) {
                            infoAge.setText(ageVal + " Years");
                        } else {
                            infoAge.setText("--");
                        }

                        if (contact != null) {
                            infoContact.setText(contact);
                        } else {
                            infoContact.setText("--");
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(ProfileActivity.this, "Failed to load profile from database", Toast.LENGTH_SHORT).show());
    }

    private void openEditProfile() {
        Intent intent = new Intent(ProfileActivity.this, EditProfileActivity.class);
        startActivityForResult(intent, EDIT_PROFILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EDIT_PROFILE_REQUEST && resultCode == RESULT_OK) {
            // Refresh patient data immediately
            loadUserProfile();
        }
    }

    private void handleSignOut() {
        auth.signOut();
        new SessionManager(this).logoutUser();
        Toast.makeText(this, "Logged Out Successfully.", Toast.LENGTH_SHORT).show();
        Intent logoutIntent = new Intent(ProfileActivity.this, GetStartedActivity.class);
        logoutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(logoutIntent);
        finish();
    }
}
