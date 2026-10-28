package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * MedicineDashboardActivity — Comprehensive Medicine Management Hub featuring active schedules,
 * native reminders, dosage tracking, refill alerts, and full historical dose logs.
 */
public class MedicineDashboardActivity extends AppCompatActivity implements MedicationAdapter.MedicationActionListener {

    private static final String TAG = "MedicineDashboard";

    private ImageView backBtn;
    private TabLayout tabLayout;
    private ExtendedFloatingActionButton fabAddMedicine;

    // Medicines Tab Views
    private LinearLayout medicinesTabContainer;
    private ProgressBar medicinesProgressBar;
    private LinearLayout medicinesEmptyState;
    private RecyclerView medicinesRecyclerView;
    private MedicationAdapter medicationAdapter;

    // History Tab Views
    private LinearLayout historyTabContainer;
    private ProgressBar historyProgressBar;
    private LinearLayout historyEmptyState;
    private ChipGroup historyChipGroup;
    private RecyclerView historyRecyclerView;
    private MedicationHistoryAdapter historyAdapter;

    private String currentUserId = "";
    private String currentHistoryFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medicine_dashboard);

        resolveCurrentUser();
        initViews();
        setupTabs();
        setupRecyclerViews();
        setupFilterChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resolveCurrentUser();
        if (tabLayout.getSelectedTabPosition() == 0) {
            loadMedications();
        } else {
            loadHistory();
        }
    }

    private void resolveCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        tabLayout = findViewById(R.id.tabLayout);
        fabAddMedicine = findViewById(R.id.fabAddMedicine);

        medicinesTabContainer = findViewById(R.id.medicinesTabContainer);
        medicinesProgressBar = findViewById(R.id.medicinesProgressBar);
        medicinesEmptyState = findViewById(R.id.medicinesEmptyState);
        medicinesRecyclerView = findViewById(R.id.medicinesRecyclerView);

        historyTabContainer = findViewById(R.id.historyTabContainer);
        historyProgressBar = findViewById(R.id.historyProgressBar);
        historyEmptyState = findViewById(R.id.historyEmptyState);
        historyChipGroup = findViewById(R.id.historyChipGroup);
        historyRecyclerView = findViewById(R.id.historyRecyclerView);

        backBtn.setOnClickListener(v -> finish());

        fabAddMedicine.setOnClickListener(v -> {
            Intent intent = new Intent(MedicineDashboardActivity.this, AddEditMedicineActivity.class);
            startActivity(intent);
        });
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    medicinesTabContainer.setVisibility(View.VISIBLE);
                    historyTabContainer.setVisibility(View.GONE);
                    fabAddMedicine.show();
                    loadMedications();
                } else {
                    medicinesTabContainer.setVisibility(View.GONE);
                    historyTabContainer.setVisibility(View.VISIBLE);
                    fabAddMedicine.hide();
                    loadHistory();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupRecyclerViews() {
        medicinesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        medicationAdapter = new MedicationAdapter(this, this);
        medicinesRecyclerView.setAdapter(medicationAdapter);

        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new MedicationHistoryAdapter(this);
        historyRecyclerView.setAdapter(historyAdapter);
    }

    private void setupFilterChips() {
        historyChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentHistoryFilter = "ALL";
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chipAll) currentHistoryFilter = "ALL";
                else if (id == R.id.chipTaken) currentHistoryFilter = "TAKEN";
                else if (id == R.id.chipSkipped) currentHistoryFilter = "SKIPPED";
                else if (id == R.id.chipRefilled) currentHistoryFilter = "REFILLED";
            }
            loadHistory();
        });
    }

    private void loadMedications() {
        if (currentUserId.isEmpty()) {
            medicinesEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        medicinesProgressBar.setVisibility(View.VISIBLE);
        medicinesEmptyState.setVisibility(View.GONE);

        MedicationRepository.fetchMedications(currentUserId, new MedicationRepository.Callback<List<MedicationModel>>() {
            @Override
            public void onSuccess(List<MedicationModel> list) {
                medicinesProgressBar.setVisibility(View.GONE);
                if (list == null || list.isEmpty()) {
                    medicinesEmptyState.setVisibility(View.VISIBLE);
                    medicationAdapter.setMedications(new ArrayList<>());
                } else {
                    medicinesEmptyState.setVisibility(View.GONE);
                    medicationAdapter.setMedications(list);

                    // Re-schedule active reminders safely
                    for (MedicationModel m : list) {
                        if ("ACTIVE".equalsIgnoreCase(m.getStatus()) && !m.isCompletedByDate()) {
                            MedicineAlarmManager.scheduleMedicationAlarms(MedicineDashboardActivity.this, m);
                        }
                    }
                }
            }

            @Override
            public void onFailure(String error) {
                medicinesProgressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load medicines: " + error);
                Toast.makeText(MedicineDashboardActivity.this, "Unable to load medicines: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadHistory() {
        if (currentUserId.isEmpty()) {
            historyEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        historyProgressBar.setVisibility(View.VISIBLE);
        historyEmptyState.setVisibility(View.GONE);

        MedicationRepository.fetchMedicationHistory(currentUserId, currentHistoryFilter, new MedicationRepository.Callback<List<MedicationHistoryModel>>() {
            @Override
            public void onSuccess(List<MedicationHistoryModel> list) {
                historyProgressBar.setVisibility(View.GONE);
                if (list == null || list.isEmpty()) {
                    historyEmptyState.setVisibility(View.VISIBLE);
                    historyAdapter.setHistory(new ArrayList<>());
                } else {
                    historyEmptyState.setVisibility(View.GONE);
                    historyAdapter.setHistory(list);
                }
            }

            @Override
            public void onFailure(String error) {
                historyProgressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load history: " + error);
                Toast.makeText(MedicineDashboardActivity.this, "Unable to load dose history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- MedicationActionListener Callbacks ---

    @Override
    public void onTaken(MedicationModel item) {
        String nextDose = item.getNextDoseDisplay();
        medicinesProgressBar.setVisibility(View.VISIBLE);
        MedicationRepository.recordMedicationAction(item, nextDose, "TAKEN", new MedicationRepository.Callback<MedicationModel>() {
            @Override
            public void onSuccess(MedicationModel updated) {
                medicinesProgressBar.setVisibility(View.GONE);
                Toast.makeText(MedicineDashboardActivity.this, "Dose logged: TAKEN (" + updated.getRemainingTablets() + " remaining)", Toast.LENGTH_SHORT).show();
                loadMedications();
            }

            @Override
            public void onFailure(String error) {
                medicinesProgressBar.setVisibility(View.GONE);
                Toast.makeText(MedicineDashboardActivity.this, "Failed to log dose: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onSkipped(MedicationModel item) {
        String nextDose = item.getNextDoseDisplay();
        medicinesProgressBar.setVisibility(View.VISIBLE);
        MedicationRepository.recordMedicationAction(item, nextDose, "SKIPPED", new MedicationRepository.Callback<MedicationModel>() {
            @Override
            public void onSuccess(MedicationModel updated) {
                medicinesProgressBar.setVisibility(View.GONE);
                Toast.makeText(MedicineDashboardActivity.this, "Dose logged: SKIPPED", Toast.LENGTH_SHORT).show();
                loadMedications();
            }

            @Override
            public void onFailure(String error) {
                medicinesProgressBar.setVisibility(View.GONE);
                Toast.makeText(MedicineDashboardActivity.this, "Failed to log skip: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRefill(MedicationModel item) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 20");
        input.setPadding(48, 24, 48, 24);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark as Refilled")
                .setMessage("How many tablets were added?")
                .setView(input)
                .setPositiveButton("REFILL", (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    if (val.isEmpty()) return;
                    try {
                        int added = Integer.parseInt(val);
                        if (added <= 0) {
                            Toast.makeText(this, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        medicinesProgressBar.setVisibility(View.VISIBLE);
                        MedicationRepository.refillMedication(item, added, new MedicationRepository.Callback<MedicationModel>() {
                            @Override
                            public void onSuccess(MedicationModel updated) {
                                medicinesProgressBar.setVisibility(View.GONE);
                                Toast.makeText(MedicineDashboardActivity.this, "Refilled! Total remaining: " + updated.getRemainingTablets(), Toast.LENGTH_SHORT).show();
                                loadMedications();
                            }

                            @Override
                            public void onFailure(String error) {
                                medicinesProgressBar.setVisibility(View.GONE);
                                Toast.makeText(MedicineDashboardActivity.this, "Refill failed: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });

                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    @Override
    public void onEdit(MedicationModel item) {
        Intent intent = new Intent(this, AddEditMedicineActivity.class);
        intent.putExtra("EXTRA_MEDICATION", item);
        startActivity(intent);
    }

    @Override
    public void onTogglePause(MedicationModel item) {
        boolean isCurrentlyActive = "ACTIVE".equalsIgnoreCase(item.getStatus());
        String newStatus = isCurrentlyActive ? "PAUSED" : "ACTIVE";
        item.setStatus(newStatus);

        medicinesProgressBar.setVisibility(View.VISIBLE);
        MedicationRepository.updateMedication(item, new MedicationRepository.Callback<MedicationModel>() {
            @Override
            public void onSuccess(MedicationModel updated) {
                medicinesProgressBar.setVisibility(View.GONE);
                if ("ACTIVE".equalsIgnoreCase(newStatus)) {
                    MedicineAlarmManager.scheduleMedicationAlarms(MedicineDashboardActivity.this, updated);
                    Toast.makeText(MedicineDashboardActivity.this, "Reminders Resumed", Toast.LENGTH_SHORT).show();
                } else {
                    MedicineAlarmManager.cancelMedicationAlarms(MedicineDashboardActivity.this, updated);
                    Toast.makeText(MedicineDashboardActivity.this, "Reminders Paused", Toast.LENGTH_SHORT).show();
                }
                loadMedications();
            }

            @Override
            public void onFailure(String error) {
                medicinesProgressBar.setVisibility(View.GONE);
                Toast.makeText(MedicineDashboardActivity.this, "Status update failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDelete(MedicationModel item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Medicine")
                .setMessage("Are you sure you want to delete " + item.getMedicineName() + "? Scheduled reminders will be cancelled.")
                .setPositiveButton("DELETE", (dialog, which) -> {
                    // 1. Cancel Alarms
                    MedicineAlarmManager.cancelMedicationAlarms(this, item);

                    medicinesProgressBar.setVisibility(View.VISIBLE);
                    MedicationRepository.deleteMedication(currentUserId, item.getId(), new MedicationRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            medicinesProgressBar.setVisibility(View.GONE);
                            Toast.makeText(MedicineDashboardActivity.this, "Medicine deleted successfully.", Toast.LENGTH_SHORT).show();
                            loadMedications();
                        }

                        @Override
                        public void onFailure(String error) {
                            medicinesProgressBar.setVisibility(View.GONE);
                            Toast.makeText(MedicineDashboardActivity.this, "Delete failed: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }
}
