package com.example.medilinkchain;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AddEditMedicineActivity — Form for creating or updating a scheduled medication.
 */
public class AddEditMedicineActivity extends AppCompatActivity {

    private static final String[] FREQUENCY_OPTIONS = {
            "Once Daily",
            "Twice Daily",
            "Three Times Daily",
            "Four Times Daily",
            "Custom"
    };

    private ImageView backBtn;
    private TextView formTitleText;
    private TextInputEditText medicineNameInput, dosageInput, startDateInput, endDateInput, tabletsInput, refillThresholdInput, notesInput;
    private AutoCompleteTextView frequencyDropdown;
    private LinearLayout timesContainer;
    private MaterialButton btnAddCustomTime, btnSaveMedicine;
    private ProgressBar saveProgressBar;

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.US);
    private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);

    private List<String> currentTimes = new ArrayList<>();
    private MedicationModel editingModel = null;
    private String currentUserId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_medicine);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }

        if (getIntent().hasExtra("extra_medication")) {
            editingModel = (MedicationModel) getIntent().getSerializableExtra("extra_medication");
        }

        initViews();
        setupFrequencyDropdown();
        setupDatePickers();

        if (editingModel != null) {
            populateExistingData(editingModel);
        } else {
            // Defaults for new medicine
            startDateInput.setText(dateFormat.format(startCalendar.getTime()));
            endCalendar.add(Calendar.DAY_OF_YEAR, 7);
            endDateInput.setText(dateFormat.format(endCalendar.getTime()));
            tabletsInput.setText("20");
            refillThresholdInput.setText("5");
            frequencyDropdown.setText(FREQUENCY_OPTIONS[1], false);
            setupTimesForFrequency(FREQUENCY_OPTIONS[1]);
        }
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        formTitleText = findViewById(R.id.formTitleText);
        medicineNameInput = findViewById(R.id.medicineNameInput);
        dosageInput = findViewById(R.id.dosageInput);
        startDateInput = findViewById(R.id.startDateInput);
        endDateInput = findViewById(R.id.endDateInput);
        tabletsInput = findViewById(R.id.tabletsInput);
        refillThresholdInput = findViewById(R.id.refillThresholdInput);
        notesInput = findViewById(R.id.notesInput);
        frequencyDropdown = findViewById(R.id.frequencyDropdown);
        timesContainer = findViewById(R.id.timesContainer);
        btnAddCustomTime = findViewById(R.id.btnAddCustomTime);
        btnSaveMedicine = findViewById(R.id.btnSaveMedicine);
        saveProgressBar = findViewById(R.id.saveProgressBar);

        backBtn.setOnClickListener(v -> finish());
        btnAddCustomTime.setOnClickListener(v -> showTimePickerForNewTime());
        btnSaveMedicine.setOnClickListener(v -> validateAndSave());
    }

    private void setupFrequencyDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, FREQUENCY_OPTIONS);
        frequencyDropdown.setAdapter(adapter);

        frequencyDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selected = FREQUENCY_OPTIONS[position];
            setupTimesForFrequency(selected);
        });
    }

    private void setupDatePickers() {
        startDateInput.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                startCalendar.set(Calendar.YEAR, year);
                startCalendar.set(Calendar.MONTH, month);
                startCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                startDateInput.setText(dateFormat.format(startCalendar.getTime()));
            }, startCalendar.get(Calendar.YEAR), startCalendar.get(Calendar.MONTH), startCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        endDateInput.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                endCalendar.set(Calendar.YEAR, year);
                endCalendar.set(Calendar.MONTH, month);
                endCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                endDateInput.setText(dateFormat.format(endCalendar.getTime()));
            }, endCalendar.get(Calendar.YEAR), endCalendar.get(Calendar.MONTH), endCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupTimesForFrequency(String frequency) {
        currentTimes.clear();
        if ("Once Daily".equalsIgnoreCase(frequency)) {
            currentTimes.add("09:00 AM");
            btnAddCustomTime.setVisibility(View.GONE);
        } else if ("Twice Daily".equalsIgnoreCase(frequency)) {
            currentTimes.add("09:00 AM");
            currentTimes.add("09:00 PM");
            btnAddCustomTime.setVisibility(View.GONE);
        } else if ("Three Times Daily".equalsIgnoreCase(frequency)) {
            currentTimes.add("08:00 AM");
            currentTimes.add("01:00 PM");
            currentTimes.add("08:00 PM");
            btnAddCustomTime.setVisibility(View.GONE);
        } else if ("Four Times Daily".equalsIgnoreCase(frequency)) {
            currentTimes.add("08:00 AM");
            currentTimes.add("12:00 PM");
            currentTimes.add("04:00 PM");
            currentTimes.add("08:00 PM");
            btnAddCustomTime.setVisibility(View.GONE);
        } else { // Custom
            currentTimes.add("08:00 AM");
            currentTimes.add("01:00 PM");
            currentTimes.add("08:00 PM");
            btnAddCustomTime.setVisibility(View.VISIBLE);
        }
        renderTimesViews();
    }

    private void renderTimesViews() {
        timesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < currentTimes.size(); i++) {
            final int index = i;
            String timeStr = currentTimes.get(i);

            View row = inflater.inflate(android.R.layout.simple_list_item_1, timesContainer, false);
            TextView tv = row.findViewById(android.R.id.text1);
            tv.setText("⏰ Dose " + (i + 1) + ": " + timeStr);
            tv.setTextColor(getResources().getColor(R.color.colorMedicalBlue));
            tv.setTextSize(14);
            tv.setBackgroundResource(R.drawable.bg_pill_time);
            tv.setPadding(32, 20, 32, 20);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, 16);
            row.setLayoutParams(lp);

            row.setOnClickListener(v -> showTimePickerForIndex(index));
            timesContainer.addView(row);
        }
    }

    private void showTimePickerForIndex(int index) {
        Calendar cal = Calendar.getInstance();
        try {
            Date d = timeFormat.parse(currentTimes.get(index));
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {}

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.HOUR_OF_DAY, hourOfDay);
            chosen.set(Calendar.MINUTE, minute);
            currentTimes.set(index, timeFormat.format(chosen.getTime()));
            renderTimesViews();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
    }

    private void showTimePickerForNewTime() {
        Calendar cal = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.HOUR_OF_DAY, hourOfDay);
            chosen.set(Calendar.MINUTE, minute);
            currentTimes.add(timeFormat.format(chosen.getTime()));
            renderTimesViews();
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
    }

    private void populateExistingData(MedicationModel med) {
        formTitleText.setText("Edit Medicine");
        btnSaveMedicine.setText("UPDATE MEDICINE");

        medicineNameInput.setText(med.getMedicineName());
        dosageInput.setText(med.getDosage());
        frequencyDropdown.setText(med.getFrequency(), false);
        startDateInput.setText(med.getStartDate());
        endDateInput.setText(med.getEndDate());
        tabletsInput.setText(String.valueOf(med.getTabletQuantity()));
        refillThresholdInput.setText(String.valueOf(med.getRefillThreshold()));
        notesInput.setText(med.getNotes());

        if (med.getTimes() != null && !med.getTimes().isEmpty()) {
            currentTimes = new ArrayList<>(med.getTimes());
            if ("Custom".equalsIgnoreCase(med.getFrequency())) {
                btnAddCustomTime.setVisibility(View.VISIBLE);
            } else {
                btnAddCustomTime.setVisibility(View.GONE);
            }
            renderTimesViews();
        } else {
            setupTimesForFrequency(med.getFrequency());
        }
    }

    private void validateAndSave() {
        String medicineName = medicineNameInput.getText() != null ? medicineNameInput.getText().toString().trim() : "";
        String dosage = dosageInput.getText() != null ? dosageInput.getText().toString().trim() : "";
        String frequency = frequencyDropdown.getText() != null ? frequencyDropdown.getText().toString().trim() : "";
        String startDate = startDateInput.getText() != null ? startDateInput.getText().toString().trim() : "";
        String endDate = endDateInput.getText() != null ? endDateInput.getText().toString().trim() : "";
        String tabletsStr = tabletsInput.getText() != null ? tabletsInput.getText().toString().trim() : "";
        String refillStr = refillThresholdInput.getText() != null ? refillThresholdInput.getText().toString().trim() : "";
        String notes = notesInput.getText() != null ? notesInput.getText().toString().trim() : "";

        // Validations
        if (medicineName.isEmpty()) {
            medicineNameInput.setError("Medicine name cannot be empty");
            medicineNameInput.requestFocus();
            return;
        }

        if (dosage.isEmpty()) {
            dosageInput.setError("Dosage cannot be empty");
            dosageInput.requestFocus();
            return;
        }

        if (frequency.isEmpty()) {
            frequencyDropdown.setError("Please select frequency");
            return;
        }

        if (startDate.isEmpty()) {
            startDateInput.setError("Please select start date");
            return;
        }

        if (endDate.isEmpty()) {
            endDateInput.setError("Please select end date");
            return;
        }

        // Validate end date not before start date
        try {
            Date s = dateFormat.parse(startDate);
            Date e = dateFormat.parse(endDate);
            if (s != null && e != null && e.before(s)) {
                endDateInput.setError("End date must not be before start date");
                Toast.makeText(this, "End date must not be before start date", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (ParseException ignored) {}

        int tablets = 0;
        try {
            tablets = Integer.parseInt(tabletsStr);
            if (tablets <= 0) {
                tabletsInput.setError("Tablet quantity must be greater than zero");
                tabletsInput.requestFocus();
                return;
            }
        } catch (NumberFormatException ex) {
            tabletsInput.setError("Enter a valid number of tablets");
            tabletsInput.requestFocus();
            return;
        }

        int refillThreshold = 0;
        try {
            refillThreshold = Integer.parseInt(refillStr);
            if (refillThreshold < 0) {
                refillThresholdInput.setError("Refill threshold cannot be negative");
                refillThresholdInput.requestFocus();
                return;
            }
            if (refillThreshold > tablets) {
                refillThresholdInput.setError("Threshold cannot exceed total tablets");
                refillThresholdInput.requestFocus();
                return;
            }
        } catch (NumberFormatException ex) {
            refillThresholdInput.setError("Enter a valid threshold");
            refillThresholdInput.requestFocus();
            return;
        }

        if (currentTimes.isEmpty()) {
            Toast.makeText(this, "Please schedule at least one reminder time", Toast.LENGTH_LONG).show();
            return;
        }

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Authentication required. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }

        saveProgressBar.setVisibility(View.VISIBLE);
        btnSaveMedicine.setEnabled(false);

        final MedicationModel modelToSave = (editingModel != null) ? editingModel : new MedicationModel();
        modelToSave.setUserId(currentUserId);
        modelToSave.setMedicineName(medicineName);
        modelToSave.setDosage(dosage);
        modelToSave.setFrequency(frequency);
        modelToSave.setTimes(new ArrayList<>(currentTimes));
        modelToSave.setStartDate(startDate);
        modelToSave.setEndDate(endDate);
        modelToSave.setTabletQuantity(tablets);
        modelToSave.setRefillThreshold(refillThreshold);
        modelToSave.setNotes(notes);

        if (editingModel == null) {
            modelToSave.setRemainingTablets(tablets);
            modelToSave.setStatus("ACTIVE");

            MedicationRepository.saveMedication(modelToSave, new MedicationRepository.Callback<MedicationModel>() {
                @Override
                public void onSuccess(MedicationModel saved) {
                    saveProgressBar.setVisibility(View.GONE);
                    MedicineAlarmManager.scheduleMedicationAlarms(AddEditMedicineActivity.this, saved);
                    Toast.makeText(AddEditMedicineActivity.this, "Medicine added and reminder scheduled!", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onFailure(String error) {
                    saveProgressBar.setVisibility(View.GONE);
                    btnSaveMedicine.setEnabled(true);
                    Toast.makeText(AddEditMedicineActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });

        } else {
            // If total tablets increased, adjust remaining if needed
            if (modelToSave.getRemainingTablets() > tablets) {
                modelToSave.setRemainingTablets(tablets);
            }

            MedicationRepository.updateMedication(modelToSave, new MedicationRepository.Callback<MedicationModel>() {
                @Override
                public void onSuccess(MedicationModel updated) {
                    saveProgressBar.setVisibility(View.GONE);
                    // Cancel old alarms and schedule updated alarms
                    MedicineAlarmManager.cancelMedicationAlarms(AddEditMedicineActivity.this, updated);
                    MedicineAlarmManager.scheduleMedicationAlarms(AddEditMedicineActivity.this, updated);
                    Toast.makeText(AddEditMedicineActivity.this, "Medicine updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onFailure(String error) {
                    saveProgressBar.setVisibility(View.GONE);
                    btnSaveMedicine.setEnabled(true);
                    Toast.makeText(AddEditMedicineActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
