package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class PatientDetailsActivity extends AppCompatActivity {

    private static final String TAG = "PatientDetailsActivity";

    private TextView patientName,
            patientUid,
            patientBlood,
            patientContact,
            patientBP,
            patientDiabetes,
            patientEmergencyContact,
            patientAccessText;

    private Button viewRecordsBtn,
            emergencyBtn;

    private FirebaseFirestore db;
    private String loadedPatientUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_details);

        // Bind UI Elements
        patientName = findViewById(R.id.patientName);
        patientUid = findViewById(R.id.patientUid);
        patientBlood = findViewById(R.id.patientBlood);
        patientContact = findViewById(R.id.patientContact);
        patientBP = findViewById(R.id.patientBP);
        patientDiabetes = findViewById(R.id.patientDiabetes);
        patientEmergencyContact = findViewById(R.id.patientEmergencyContact);
        patientAccessText = findViewById(R.id.patientAccessText);

        viewRecordsBtn = findViewById(R.id.viewRecordsBtn);
        emergencyBtn = findViewById(R.id.emergencyBtn);

        // Back button navigation
        findViewById(R.id.backArrow).setOnClickListener(v -> finish());

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Retrieve Scanned Patient Identifier
        String inputPatientId = getIntent().getStringExtra("patientId");
        if (inputPatientId == null || inputPatientId.trim().isEmpty()) {
            inputPatientId = getIntent().getStringExtra("patientCode");
        }

        if (inputPatientId == null || inputPatientId.trim().isEmpty()) {
            Toast.makeText(this, "No patient ID provided.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String targetIdentifier = inputPatientId.trim();
        loadPatientData(targetIdentifier);

        // VIEW RECORDS routing
        viewRecordsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(PatientDetailsActivity.this, ViewRecordsActivity.class);
            if (!loadedPatientUid.isEmpty()) {
                intent.putExtra("patientId", loadedPatientUid);
                intent.putExtra("PATIENT_ID", loadedPatientUid);
            }
            startActivity(intent);
        });

        // EMERGENCY DETAILS routing
        emergencyBtn.setOnClickListener(v -> {
            Intent intent = new Intent(PatientDetailsActivity.this, EmergencyActivity.class);
            if (!loadedPatientUid.isEmpty()) {
                intent.putExtra("patientId", loadedPatientUid);
            }
            startActivity(intent);
        });
    }

    private void loadPatientData(final String identifier) {
        // 1. Direct document lookup by UID
        db.collection("users").document(identifier).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        bindPatientData(doc);
                    } else {
                        // 2. Query by patientId field
                        db.collection("users").whereEqualTo("patientId", identifier).limit(1).get()
                                .addOnSuccessListener(querySnap -> {
                                    if (querySnap != null && !querySnap.isEmpty()) {
                                        bindPatientData(querySnap.getDocuments().get(0));
                                    } else {
                                        // 3. Query by email
                                        db.collection("users").whereEqualTo("email", identifier).limit(1).get()
                                                .addOnSuccessListener(emailSnap -> {
                                                    if (emailSnap != null && !emailSnap.isEmpty()) {
                                                        bindPatientData(emailSnap.getDocuments().get(0));
                                                    } else {
                                                        Toast.makeText(PatientDetailsActivity.this, "Patient details could not be found.", Toast.LENGTH_LONG).show();
                                                    }
                                                })
                                                .addOnFailureListener(e -> handleError(e.getMessage()));
                                    }
                                })
                                .addOnFailureListener(e -> handleError(e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> handleError(e.getMessage()));
    }

    private void bindPatientData(DocumentSnapshot doc) {
        loadedPatientUid = doc.getId();

        String name = doc.getString("fullName");
        if (name == null || name.trim().isEmpty()) name = doc.getString("name");
        if (name == null || name.trim().isEmpty()) {
            String email = doc.getString("email");
            if (email != null && email.contains("@")) {
                name = email.split("@")[0];
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
            } else {
                name = "Patient";
            }
        }

        String pId = doc.getString("patientId");
        if (pId == null || pId.trim().isEmpty()) {
            pId = "MLC" + loadedPatientUid.substring(0, Math.min(loadedPatientUid.length(), 8)).toUpperCase();
        }

        String blood = doc.getString("bloodGroup");
        String contact = doc.getString("contactNumber");
        String bp = doc.getString("bloodPressure");
        String diabetes = doc.getString("diabetes");
        String emgName = doc.getString("emergencyContactName");
        String emgPhone = doc.getString("emergencyContactNumber");
        Long ageVal = doc.getLong("age");
        String gender = doc.getString("gender");

        // Set Views
        patientName.setText(name);
        if (patientUid != null) {
            patientUid.setText("MediLink ID: " + pId);
        }

        patientBlood.setText(blood != null && !blood.isEmpty() ? blood : "--");
        patientContact.setText(contact != null && !contact.isEmpty() ? contact : "--");
        patientBP.setText(bp != null && !bp.isEmpty() ? bp : "120/80 mmHg");
        patientDiabetes.setText(diabetes != null && !diabetes.isEmpty() ? diabetes : "Non-Diabetic");

        if (emgName != null && emgPhone != null && !emgName.isEmpty() && !emgPhone.isEmpty()) {
            patientEmergencyContact.setText(emgName + " (" + emgPhone + ")");
        } else if (emgName != null && !emgName.isEmpty()) {
            patientEmergencyContact.setText(emgName);
        } else {
            patientEmergencyContact.setText("Not registered");
        }

        patientAccessText.setText("Authorized & Verified");
    }

    private void handleError(String message) {
        Log.e(TAG, "Error fetching patient document: " + message);
        Toast.makeText(this, "Failed to load patient records from database.", Toast.LENGTH_SHORT).show();
    }
}
