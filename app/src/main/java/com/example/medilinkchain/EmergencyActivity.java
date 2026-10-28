package com.example.medilinkchain;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class EmergencyActivity extends AppCompatActivity {

    private ImageView qrImage;
    private ImageView backBtn;
    private TextView tvPatientName;
    private TextView tvPatientId;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        qrImage = findViewById(R.id.qrImage);
        backBtn = findViewById(R.id.backBtn);
        tvPatientName = findViewById(R.id.tvPatientName);
        tvPatientId = findViewById(R.id.tvPatientId);

        backBtn.setOnClickListener(v -> finish());

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User session not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String currentUid = currentUser.getUid();
        String targetUid = getIntent().getStringExtra("patientId");
        if (targetUid == null || targetUid.trim().isEmpty()) {
            targetUid = currentUid;
        }

        // Account isolation: Only doctors/admins can view another patient's emergency QR
        boolean isDoctorOrAdmin = sessionManager.isDoctor() || sessionManager.isAdmin();
        if (!isDoctorOrAdmin && !targetUid.equals(currentUid)) {
            Toast.makeText(this, "Access denied: Unauthorized emergency card access.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadPatientAndGenerateQr(targetUid, targetUid.equals(currentUid) ? currentUser.getEmail() : null);
    }

    private void loadPatientAndGenerateQr(String uid, String emailFallback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String patientId = null;
                    String fullName = null;

                    if (doc != null && doc.exists()) {
                        patientId = doc.getString("patientId");
                        fullName = doc.getString("fullName");
                        if (fullName == null || fullName.trim().isEmpty()) {
                            fullName = doc.getString("name");
                        }
                    }

                    // Fallback to standard patient ID format derived from UID
                    if (patientId == null || patientId.trim().isEmpty()) {
                        patientId = "MLC" + uid.substring(0, Math.min(uid.length(), 8)).toUpperCase();
                        // Persist to user record for future consistency
                        db.collection("users").document(uid).update("patientId", patientId);
                    }

                    if (fullName == null || fullName.trim().isEmpty()) {
                        if (emailFallback != null && emailFallback.contains("@")) {
                            fullName = emailFallback.split("@")[0];
                            fullName = fullName.substring(0, 1).toUpperCase() + fullName.substring(1);
                        } else {
                            fullName = "Patient";
                        }
                    }

                    if (tvPatientName != null) tvPatientName.setText(fullName);
                    if (tvPatientId != null) tvPatientId.setText("Patient ID: " + patientId);

                    // Unified QR payload format: PATIENT_ID:P26H0001
                    String qrPayload = "PATIENT_ID:" + patientId;
                    generateCleanQRCode(qrPayload);
                })
                .addOnFailureListener(e -> {
                    String fallbackId = "MLC" + uid.substring(0, Math.min(uid.length(), 8)).toUpperCase();
                    if (tvPatientId != null) tvPatientId.setText("Patient ID: " + fallbackId);
                    generateCleanQRCode("PATIENT_ID:" + fallbackId);
                });
    }

    private void generateCleanQRCode(String data) {
        try {
            int size = 512;
            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    data,
                    BarcodeFormat.QR_CODE,
                    size,
                    size
            );

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            qrImage.setImageBitmap(bitmap);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating QR Code", Toast.LENGTH_SHORT).show();
        }
    }
}
