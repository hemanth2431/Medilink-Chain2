package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CompleteProfileActivity extends AppCompatActivity {

    private TextView profileEmailDisplay;
    private EditText inputFullName, inputAge, inputContact, inputEmergencyName, inputEmergencyNumber;
    private EditText inputHeight, inputWeight, inputBloodPressure, inputAddress;
    private Spinner spinnerGender, spinnerBloodGroup, spinnerDiabetes;
    private MaterialButton btnCompleteProfile;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String email = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind views
        profileEmailDisplay = findViewById(R.id.profileEmailDisplay);
        inputFullName = findViewById(R.id.inputFullName);
        inputAge = findViewById(R.id.inputAge);
        inputContact = findViewById(R.id.inputContact);
        inputEmergencyName = findViewById(R.id.inputEmergencyName);
        inputEmergencyNumber = findViewById(R.id.inputEmergencyNumber);
        inputHeight = findViewById(R.id.inputHeight);
        inputWeight = findViewById(R.id.inputWeight);
        inputBloodPressure = findViewById(R.id.inputBloodPressure);
        inputAddress = findViewById(R.id.inputAddress);

        spinnerGender = findViewById(R.id.spinnerGender);
        spinnerBloodGroup = findViewById(R.id.spinnerBloodGroup);
        spinnerDiabetes = findViewById(R.id.spinnerDiabetes);
        btnCompleteProfile = findViewById(R.id.btnCompleteProfile);

        // Prepopulate email from Firebase user
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            email = currentUser.getEmail();
            profileEmailDisplay.setText(email);
        } else {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setupSpinners();

        btnCompleteProfile.setOnClickListener(v -> saveProfile());
    }

    private void setupSpinners() {
        // Gender spinner setup
        String[] genders = {"Male", "Female", "Other"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        // Blood group spinner setup
        String[] bloodGroups = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
        ArrayAdapter<String> bloodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bloodGroups);
        bloodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBloodGroup.setAdapter(bloodAdapter);

        // Diabetes spinner setup
        String[] diabetesOptions = {"No", "Yes"};
        ArrayAdapter<String> diabetesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, diabetesOptions);
        diabetesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDiabetes.setAdapter(diabetesAdapter);
    }

    private void saveProfile() {
        String fullName = inputFullName.getText().toString().trim();
        String ageStr = inputAge.getText().toString().trim();
        String contact = inputContact.getText().toString().trim();
        String emergencyName = inputEmergencyName.getText().toString().trim();
        String emergencyNumber = inputEmergencyNumber.getText().toString().trim();
        String heightStr = inputHeight.getText().toString().trim();
        String weightStr = inputWeight.getText().toString().trim();
        String bloodPressure = inputBloodPressure.getText().toString().trim();
        String address = inputAddress.getText().toString().trim();

        String gender = spinnerGender.getSelectedItem().toString();
        String bloodGroup = spinnerBloodGroup.getSelectedItem().toString();
        String diabetes = spinnerDiabetes.getSelectedItem().toString();

        // Validation
        if (fullName.isEmpty()) {
            inputFullName.setError("Full name is required");
            return;
        }
        if (fullName.length() < 2) {
            inputFullName.setError("Full name must be at least 2 characters");
            return;
        }

        if (ageStr.isEmpty()) {
            inputAge.setError("Age is required");
            return;
        }
        int age;
        try {
            age = Integer.parseInt(ageStr);
            if (age < 0 || age > 120) {
                inputAge.setError("Please enter a valid age (0-120)");
                return;
            }
        } catch (NumberFormatException e) {
            inputAge.setError("Invalid age value");
            return;
        }

        if (contact.isEmpty()) {
            inputContact.setError("Contact number is required");
            return;
        }

        if (emergencyName.isEmpty()) {
            inputEmergencyName.setError("Emergency contact name is required");
            return;
        }

        if (emergencyNumber.isEmpty()) {
            inputEmergencyNumber.setError("Emergency contact number is required");
            return;
        }

        double height = 0.0;
        if (!heightStr.isEmpty()) {
            try {
                height = Double.parseDouble(heightStr);
                if (height <= 0) {
                    inputHeight.setError("Height must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                inputHeight.setError("Invalid height value");
                return;
            }
        } else {
            inputHeight.setError("Height is required");
            return;
        }

        double weight = 0.0;
        if (!weightStr.isEmpty()) {
            try {
                weight = Double.parseDouble(weightStr);
                if (weight <= 0) {
                    inputWeight.setError("Weight must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                inputWeight.setError("Invalid weight value");
                return;
            }
        } else {
            inputWeight.setError("Weight is required");
            return;
        }

        if (bloodPressure.isEmpty()) {
            inputBloodPressure.setError("Blood pressure is required");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Auth session lost. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String uid = user.getUid();

        Map<String, Object> profileData = new HashMap<>();
        profileData.put("email", email);
        profileData.put("role", "patient");
        profileData.put("fullName", fullName);
        profileData.put("age", age);
        profileData.put("gender", gender);
        profileData.put("bloodGroup", bloodGroup);
        profileData.put("contactNumber", contact);
        profileData.put("emergencyContactName", emergencyName);
        profileData.put("emergencyContactNumber", emergencyNumber);
        profileData.put("height", height);
        profileData.put("weight", weight);
        profileData.put("diabetes", diabetes);
        profileData.put("bloodPressure", bloodPressure);
        profileData.put("address", address);
        profileData.put("profileComplete", true);
        profileData.put("createdAt", new Date());

        btnCompleteProfile.setEnabled(false);
        db.collection("users").document(uid)
                .set(profileData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(CompleteProfileActivity.this, "Profile completed successfully!", Toast.LENGTH_SHORT).show();
                    // Go to Patient Dashboard and establish session manager session
                    new SessionManager(CompleteProfileActivity.this).createLoginSession(email, "patient");
                    Intent intent = new Intent(CompleteProfileActivity.this, DashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnCompleteProfile.setEnabled(true);
                    Toast.makeText(CompleteProfileActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
