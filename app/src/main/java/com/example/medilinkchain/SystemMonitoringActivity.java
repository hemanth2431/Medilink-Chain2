package com.example.medilinkchain;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * SystemMonitoringActivity — Real-time organizational monitoring and activity telemetry.
 */
public class SystemMonitoringActivity extends AppCompatActivity {

    private static final String TAG = "SystemMonitoring";

    private View scrollMonitoringContent;
    private View layoutMonitoringLoading;
    private View layoutMonitoringError;

    private TextView textTodayPatients;
    private TextView textTodayDoctors;
    private TextView textTodayRecords;
    private TextView textBreakdownApproved;
    private TextView textBreakdownPending;
    private TextView textBreakdownRejected;
    private TextView textMonitoringTotalRecords;
    private TextView textMonitoringActiveAdmins;
    private TextView textMonitoringTotalActions;

    private SessionManager sessionManager;
    private String currentOrgId;
    private boolean isSuperAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_monitoring);

        sessionManager = new SessionManager(this);
        currentOrgId = getIntent().getStringExtra("orgId");
        if (currentOrgId == null || currentOrgId.isEmpty()) {
            currentOrgId = sessionManager.getOrgId();
        }
        isSuperAdmin = getIntent().getBooleanExtra("isSuperAdmin", sessionManager.isSuperAdmin());

        initViews();
        setupListeners();
        loadTelemetry();
    }

    private void initViews() {
        scrollMonitoringContent = findViewById(R.id.scrollMonitoringContent);
        layoutMonitoringLoading = findViewById(R.id.layoutMonitoringLoading);
        layoutMonitoringError = findViewById(R.id.layoutMonitoringError);

        textTodayPatients = findViewById(R.id.textTodayPatients);
        textTodayDoctors = findViewById(R.id.textTodayDoctors);
        textTodayRecords = findViewById(R.id.textTodayRecords);
        textBreakdownApproved = findViewById(R.id.textBreakdownApproved);
        textBreakdownPending = findViewById(R.id.textBreakdownPending);
        textBreakdownRejected = findViewById(R.id.textBreakdownRejected);
        textMonitoringTotalRecords = findViewById(R.id.textMonitoringTotalRecords);
        textMonitoringActiveAdmins = findViewById(R.id.textMonitoringActiveAdmins);
        textMonitoringTotalActions = findViewById(R.id.textMonitoringTotalActions);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefreshMonitoring).setOnClickListener(v -> loadTelemetry());
        findViewById(R.id.btnRetryMonitoring).setOnClickListener(v -> loadTelemetry());
    }

    private void loadTelemetry() {
        scrollMonitoringContent.setVisibility(View.GONE);
        layoutMonitoringError.setVisibility(View.GONE);
        layoutMonitoringLoading.setVisibility(View.VISIBLE);

        String orgToUse = (currentOrgId != null && !currentOrgId.isEmpty()) ? currentOrgId : OrganizationRepository.DEFAULT_ORG_ID;

        OrganizationRepository.getSystemStats(orgToUse, isSuperAdmin, new OrganizationRepository.Callback<OrganizationRepository.SystemStats>() {
            @Override
            public void onSuccess(OrganizationRepository.SystemStats stats) {
                layoutMonitoringLoading.setVisibility(View.GONE);
                layoutMonitoringError.setVisibility(View.GONE);
                scrollMonitoringContent.setVisibility(View.VISIBLE);

                textTodayPatients.setText(String.valueOf(stats.todayPatients));
                textTodayDoctors.setText(String.valueOf(stats.todayDoctors));
                textTodayRecords.setText(String.valueOf(stats.todayRecords));

                textBreakdownApproved.setText(stats.approvedDoctors + " Doctors");
                textBreakdownPending.setText(stats.pendingDoctors + " Pending");
                textBreakdownRejected.setText(stats.rejectedDoctors + " Rejected");

                textMonitoringTotalRecords.setText(String.valueOf(stats.totalMedicalRecords));
                textMonitoringActiveAdmins.setText(String.valueOf(stats.activeAdministrators));
                textMonitoringTotalActions.setText(String.valueOf(stats.adminActionsCount));
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Telemetry error: " + error);
                layoutMonitoringLoading.setVisibility(View.GONE);
                scrollMonitoringContent.setVisibility(View.GONE);
                layoutMonitoringError.setVisibility(View.VISIBLE);
                Toast.makeText(SystemMonitoringActivity.this, "Unable to load system statistics.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
