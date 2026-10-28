package com.example.medilinkchain;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AuditLogActivity — Real-time immutable audit trail screen for tracking all administrative actions.
 */
public class AuditLogActivity extends AppCompatActivity {

    private RecyclerView recyclerViewAuditLogs;
    private View layoutEmptyAudit;
    private ProgressBar progressBarAudit;
    private EditText inputSearchAudit;
    private ChipGroup chipGroupAuditFilter;

    private final List<AuditLogModel> allLogsList = new ArrayList<>();
    private final List<AuditLogModel> filteredLogsList = new ArrayList<>();
    private AuditAdapter adapter;

    private String currentCategoryFilter = "ALL";

    private SessionManager sessionManager;
    private String currentOrgId;
    private boolean isSuperAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audit_logs);

        sessionManager = new SessionManager(this);
        currentOrgId = getIntent().getStringExtra("orgId");
        if (currentOrgId == null || currentOrgId.isEmpty()) {
            currentOrgId = sessionManager.getOrgId();
        }
        isSuperAdmin = getIntent().getBooleanExtra("isSuperAdmin", sessionManager.isSuperAdmin());

        initViews();
        setupListeners();
        loadAuditLogs();
    }

    private void initViews() {
        recyclerViewAuditLogs = findViewById(R.id.recyclerViewAuditLogs);
        layoutEmptyAudit = findViewById(R.id.layoutEmptyAudit);
        progressBarAudit = findViewById(R.id.progressBarAudit);
        inputSearchAudit = findViewById(R.id.inputSearchAudit);
        chipGroupAuditFilter = findViewById(R.id.chipGroupAuditFilter);

        recyclerViewAuditLogs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AuditAdapter(filteredLogsList);
        recyclerViewAuditLogs.setAdapter(adapter);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        inputSearchAudit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroupAuditFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipAuditDoctors) {
                currentCategoryFilter = "DOCTOR";
            } else if (checkedId == R.id.chipAuditAdmins) {
                currentCategoryFilter = "ADMIN";
            } else if (checkedId == R.id.chipAuditPatients) {
                currentCategoryFilter = "PATIENT";
            } else {
                currentCategoryFilter = "ALL";
            }
            applyFilters();
        });
    }

    private void loadAuditLogs() {
        progressBarAudit.setVisibility(View.VISIBLE);
        layoutEmptyAudit.setVisibility(View.GONE);

        String orgToUse = (currentOrgId != null && !currentOrgId.isEmpty()) ? currentOrgId : OrganizationRepository.DEFAULT_ORG_ID;

        OrganizationRepository.listAuditLogs(orgToUse, isSuperAdmin, new OrganizationRepository.Callback<List<AuditLogModel>>() {
            @Override
            public void onSuccess(List<AuditLogModel> list) {
                progressBarAudit.setVisibility(View.GONE);
                allLogsList.clear();
                allLogsList.addAll(list);
                applyFilters();
            }

            @Override
            public void onFailure(String error) {
                progressBarAudit.setVisibility(View.GONE);
                Toast.makeText(AuditLogActivity.this, "Failed to load audit trail: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilters() {
        filteredLogsList.clear();
        String query = inputSearchAudit.getText().toString().trim().toLowerCase(Locale.ROOT);

        for (AuditLogModel log : allLogsList) {
            // Category match
            boolean categoryMatches;
            if ("ALL".equalsIgnoreCase(currentCategoryFilter)) {
                categoryMatches = true;
            } else if ("DOCTOR".equalsIgnoreCase(currentCategoryFilter)) {
                categoryMatches = log.getAction().contains("DOCTOR") || "DOCTOR".equalsIgnoreCase(log.getTargetType());
            } else if ("ADMIN".equalsIgnoreCase(currentCategoryFilter)) {
                categoryMatches = log.getAction().contains("ADMIN") || "ADMIN".equalsIgnoreCase(log.getTargetType()) || log.getAction().contains("ORGANIZATION");
            } else if ("PATIENT".equalsIgnoreCase(currentCategoryFilter)) {
                categoryMatches = log.getAction().contains("PATIENT") || "PATIENT".equalsIgnoreCase(log.getTargetType()) || log.getAction().contains("RECORD");
            } else {
                categoryMatches = true;
            }

            if (!categoryMatches) continue;

            // Search match
            boolean searchMatches = query.isEmpty()
                    || (log.getAction() != null && log.getAction().toLowerCase(Locale.ROOT).contains(query))
                    || (log.getActorEmail() != null && log.getActorEmail().toLowerCase(Locale.ROOT).contains(query))
                    || (log.getTargetId() != null && log.getTargetId().toLowerCase(Locale.ROOT).contains(query))
                    || (log.getDescription() != null && log.getDescription().toLowerCase(Locale.ROOT).contains(query));

            if (searchMatches) {
                filteredLogsList.add(log);
            }
        }

        adapter.notifyDataSetChanged();
        layoutEmptyAudit.setVisibility(filteredLogsList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =========================================================================
    // RECYCLERVIEW ADAPTER
    // =========================================================================

    private static class AuditAdapter extends RecyclerView.Adapter<AuditAdapter.AuditViewHolder> {

        private final List<AuditLogModel> list;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.US);

        public AuditAdapter(List<AuditLogModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public AuditViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_audit_log, parent, false);
            return new AuditViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull AuditViewHolder holder, int position) {
            AuditLogModel log = list.get(position);

            holder.textAuditAction.setText(log.getAction());
            holder.textAuditDescription.setText(log.getDescription());
            holder.textAuditActor.setText(log.getActorEmail());
            holder.textAuditTarget.setText("Target: " + (log.getTargetId() != null ? log.getTargetId() : "--"));

            if (log.getCreatedAt() != null) {
                holder.textAuditTime.setText(dateFormat.format(log.getCreatedAt()));
            } else {
                holder.textAuditTime.setText("14 Aug 2026");
            }

            // Action Badge coloring
            String action = log.getAction();
            if (action.contains("APPROVED") || action.contains("ENABLED") || action.contains("CREATED")) {
                holder.actionBadgeCard.setCardBackgroundColor(0xFFDCFCE7);
                holder.textAuditAction.setTextColor(0xFF166534);
            } else if (action.contains("REJECTED") || action.contains("DISABLED") || action.contains("REMOVED")) {
                holder.actionBadgeCard.setCardBackgroundColor(0xFFFEE2E2);
                holder.textAuditAction.setTextColor(0xFF991B1B);
            } else {
                holder.actionBadgeCard.setCardBackgroundColor(0xFFEFF6FF);
                holder.textAuditAction.setTextColor(0xFF1D4ED8);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class AuditViewHolder extends RecyclerView.ViewHolder {
            TextView textAuditAction, textAuditTime, textAuditDescription, textAuditActor, textAuditTarget;
            MaterialCardView actionBadgeCard;

            public AuditViewHolder(@NonNull View itemView) {
                super(itemView);
                textAuditAction = itemView.findViewById(R.id.textAuditAction);
                textAuditTime = itemView.findViewById(R.id.textAuditTime);
                textAuditDescription = itemView.findViewById(R.id.textAuditDescription);
                textAuditActor = itemView.findViewById(R.id.textAuditActor);
                textAuditTarget = itemView.findViewById(R.id.textAuditTarget);
                actionBadgeCard = itemView.findViewById(R.id.actionBadgeCard);
            }
        }
    }
}
