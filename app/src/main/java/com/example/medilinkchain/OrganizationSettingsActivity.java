package com.example.medilinkchain;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * OrganizationSettingsActivity — Super Admin exclusive module for viewing and updating organization settings.
 */
public class OrganizationSettingsActivity extends AppCompatActivity {

    private EditText inputOrgName;
    private EditText inputOrgId;
    private TextView textOrgCreatedAt;
    private TextView textOrgStatus;
    private MaterialButton btnSaveOrgSettings;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_settings);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        initViews();
        setupListeners();
        loadOrganizationDetails();
    }

    private void initViews() {
        inputOrgName = findViewById(R.id.inputOrgName);
        inputOrgId = findViewById(R.id.inputOrgId);
        textOrgCreatedAt = findViewById(R.id.textOrgCreatedAt);
        textOrgStatus = findViewById(R.id.textOrgStatus);
        btnSaveOrgSettings = findViewById(R.id.btnSaveOrgSettings);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnSaveOrgSettings.setOnClickListener(v -> saveOrganizationDetails());
    }

    private void loadOrganizationDetails() {
        OrganizationRepository.getOrganization(OrganizationRepository.DEFAULT_ORG_ID, new OrganizationRepository.Callback<OrganizationModel>() {
            @Override
            public void onSuccess(OrganizationModel org) {
                inputOrgName.setText(org.getOrganizationName());
                inputOrgId.setText(org.getId());
                textOrgStatus.setText(org.getStatus());
                if (org.getCreatedAt() != null) {
                    textOrgCreatedAt.setText(dateFormat.format(org.getCreatedAt()));
                } else {
                    textOrgCreatedAt.setText("14 Aug 2026");
                }
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(OrganizationSettingsActivity.this, "Failed to load organization settings: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveOrganizationDetails() {
        String newName = inputOrgName.getText().toString().trim();
        if (newName.isEmpty()) {
            Toast.makeText(this, "Organization name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveOrgSettings.setEnabled(false);
        String actorId = (currentUser != null) ? currentUser.getUid() : "super_admin";
        String actorEmail = (currentUser != null && currentUser.getEmail() != null) ? currentUser.getEmail() : "superadmin@medilinkchain.org";

        OrganizationRepository.updateOrganizationName(
                OrganizationRepository.DEFAULT_ORG_ID,
                newName,
                actorId,
                actorEmail,
                new OrganizationRepository.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        btnSaveOrgSettings.setEnabled(true);
                        Toast.makeText(OrganizationSettingsActivity.this, "Organization settings updated successfully!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String error) {
                        btnSaveOrgSettings.setEnabled(true);
                        Toast.makeText(OrganizationSettingsActivity.this, "Update failed: " + error, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }
}
