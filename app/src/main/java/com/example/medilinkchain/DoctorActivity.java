package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class DoctorActivity extends AppCompatActivity {

    Button scanQrBtn,
            logoutBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor);

        scanQrBtn =
                findViewById(R.id.scanQrBtn);

        logoutBtn =
                findViewById(R.id.logoutBtn);

        // SCAN PATIENT QR

        scanQrBtn.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DoctorActivity.this,
                            DoctorScanActivity.class
                    );

            startActivity(intent);

        });

        // LOGOUT

        logoutBtn.setOnClickListener(v -> {

            FirebaseAuth.getInstance().signOut();
            new SessionManager(DoctorActivity.this).logoutUser();

            Toast.makeText(
                    DoctorActivity.this,
                    "Logged Out Successfully",
                    Toast.LENGTH_SHORT
            ).show();

            Intent intent =
                    new Intent(
                            DoctorActivity.this,
                            GetStartedActivity.class
                    );

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);

            finish();

        });

    }
}