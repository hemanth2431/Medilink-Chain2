package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView backArrow;
    private TextView profileEmailDisplay;
    private EditText inputFullName, inputAge, inputContact, inputEmergencyName, inputEmergencyNumber;
    private EditText inputHeight, inputWeight, inputBloodPressure, inputAddress;
    private Spinner spinnerGender, spinnerBloodGroup, spinnerDiabetes;
    private MaterialButton btnSaveChanges;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String email = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind views
        backArrow = findViewById(R.id.backArrow);
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
        btnSaveChanges = findViewById(R.id.btnSaveChanges);

        backArrow.setOnClickListener(v -> finish());

        setupSpinners();
        loadProfileData();

        btnSaveChanges.setOnClickListener(v -> saveProfileChanges());
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

    private void loadProfileData() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        email = currentUser.getEmail();
        profileEmailDisplay.setText(email);

        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("fullName");
                        Long ageVal = documentSnapshot.getLong("age");
                        String gender = documentSnapshot.getString("gender");
                        String bloodGroup = documentSnapshot.getString("bloodGroup");
                        String contact = documentSnapshot.getString("contactNumber");
                        String emergencyName = documentSnapshot.getString("emergencyContactName");
                        String emergencyNumber = documentSnapshot.getString("emergencyContactNumber");
                        Double height = documentSnapshot.getDouble("height");
                        Double weight = documentSnapshot.getDouble("weight");
                        String diabetes = documentSnapshot.getString("diabetes");
                        String bloodPressure = documentSnapshot.getString("bloodPressure");
                        String address = documentSnapshot.getString("address");

                        if (fullName != null) inputFullName.setText(fullName);
                        if (ageVal != null) inputAge.setText(String.valueOf(ageVal));
                        if (contact != null) inputContact.setText(contact);
                        if (emergencyName != null) inputEmergencyName.setText(emergencyName);
                        if (emergencyNumber != null) inputEmergencyNumber.setText(emergencyNumber);
                        if (height != null) inputHeight.setText(String.valueOf(height));
                        if (weight != null) inputWeight.setText(String.valueOf(weight));
                        if (bloodPressure != null) inputBloodPressure.setText(bloodPressure);
                        if (address != null) inputAddress.setText(address);

                        // Set spinner selections
                        setSpinnerSelection(spinnerGender, gender);
                        setSpinnerSelection(spinnerBloodGroup, bloodGroup);
                        setSpinnerSelection(spinnerDiabetes, diabetes);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(EditProfileActivity.this, "Failed to load profile details", Toast.LENGTH_SHORT).show());
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        if (value == null) return;
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        if (adapter != null) {
            int position = adapter.getPosition(value);
            if (position >= 0) {
                spinner.setSelection(position);
            }
        }
    }

    private void saveProfileChanges() {
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
            Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = user.getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("age", age);
        updates.put("gender", gender);
        updates.put("bloodGroup", bloodGroup);
        updates.put("contactNumber", contact);
        updates.put("emergencyContactName", emergencyName);
        updates.put("emergencyContactNumber", emergencyNumber);
        updates.put("height", height);
        updates.put("weight", weight);
        updates.put("diabetes", diabetes);
        updates.put("bloodPressure", bloodPressure);
        updates.put("address", address);

        btnSaveChanges.setEnabled(false);
        db.collection("users").document(uid)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSaveChanges.setEnabled(true);
                    Toast.makeText(EditProfileActivity.this, "Failed to update profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
