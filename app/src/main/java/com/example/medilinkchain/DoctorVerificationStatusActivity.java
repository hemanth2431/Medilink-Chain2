package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * DoctorVerificationStatusActivity — Displayed to Doctors awaiting admin review (PENDING)
 * or whose registration was rejected (REJECTED).
 */
public class DoctorVerificationStatusActivity extends AppCompatActivity {

    private TextView textStatusEmoji;
    private TextView textVerificationTitle;
    private TextView textVerificationMessage;
    private TextView textDocStatusRegId;
    private TextView textDocStatusHospital;
    private View layoutDoctorRejectionBox;
    private TextView textDocStatusRejectionReason;
    private MaterialCardView statusIconBgCard;
    private View btnCheckStatus;
    private View btnSignOutStatus;

    private FirebaseAuth auth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_verification_status);

        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        initViews();
        setupListeners();
        checkAndDisplayStatus();
    }

    private void initViews() {
        textStatusEmoji = findViewById(R.id.textStatusEmoji);
        textVerificationTitle = findViewById(R.id.textVerificationTitle);
        textVerificationMessage = findViewById(R.id.textVerificationMessage);
        textDocStatusRegId = findViewById(R.id.textDocStatusRegId);
        textDocStatusHospital = findViewById(R.id.textDocStatusHospital);
        layoutDoctorRejectionBox = findViewById(R.id.layoutDoctorRejectionBox);
        textDocStatusRejectionReason = findViewById(R.id.textDocStatusRejectionReason);
        statusIconBgCard = findViewById(R.id.statusIconBgCard);
        btnCheckStatus = findViewById(R.id.btnCheckStatus);
        btnSignOutStatus = findViewById(R.id.btnSignOutStatus);
    }

    private void setupListeners() {
        btnCheckStatus.setOnClickListener(v -> checkAndDisplayStatus());
        btnSignOutStatus.setOnClickListener(v -> handleSignOut());
    }

    private void checkAndDisplayStatus() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            handleSignOut();
            return;
        }

        String uid = currentUser.getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String regId = documentSnapshot.getString("doctorRegistrationId");
                        String hospital = documentSnapshot.getString("hospitalName");
                        String vStatus = documentSnapshot.getString("verificationStatus");
                        String reason = documentSnapshot.getString("rejectionReason");

                        textDocStatusRegId.setText(regId != null ? regId : "Pending ID");
                        textDocStatusHospital.setText(hospital != null ? hospital : "Hospital");

                        if ("APPROVED".equalsIgnoreCase(vStatus)) {
                            Toast.makeText(DoctorVerificationStatusActivity.this, "Your registration has been approved!", Toast.LENGTH_LONG).show();
                            sessionManager.createLoginSession(currentUser.getEmail(), "doctor");
                            Intent intent = new Intent(DoctorVerificationStatusActivity.this, DoctorDashboardActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else if ("REJECTED".equalsIgnoreCase(vStatus)) {
                            textStatusEmoji.setText("❌");
                            statusIconBgCard.setCardBackgroundColor(0xFFFEE2E2);
                            textVerificationTitle.setText("Registration Rejected");
                            textVerificationTitle.setTextColor(0xFFDC2626);
                            textVerificationMessage.setText("Your application to join MediLinkChain network could not be verified by the administration.");
                            layoutDoctorRejectionBox.setVisibility(View.VISIBLE);
                            textDocStatusRejectionReason.setText(reason != null && !reason.isEmpty()
                                    ? reason
                                    : "Doctor registration details could not be verified.");
                        } else {
                            // PENDING
                            textStatusEmoji.setText("⏳");
                            statusIconBgCard.setCardBackgroundColor(0xFFFEF3C7);
                            textVerificationTitle.setText("Verification Under Review");
                            textVerificationTitle.setTextColor(0xFF1E293B);
                            textVerificationMessage.setText("Your medical credentials and registration details are currently undergoing verification by the organization administration. You will gain full Doctor Portal access once approved.");
                            layoutDoctorRejectionBox.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(DoctorVerificationStatusActivity.this, "Failed to load status: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void handleSignOut() {
        auth.signOut();
        sessionManager.logoutUser();
        Intent intent = new Intent(DoctorVerificationStatusActivity.this, GetStartedActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
