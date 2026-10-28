package com.example.medilinkchain;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DoctorTimingActivity extends AppCompatActivity {

    private Chip chipMonday, chipTuesday, chipWednesday, chipThursday, chipFriday, chipSaturday, chipSunday;
    private View layoutStartTimePicker, layoutEndTimePicker;
    private TextView textStartTime, textEndTime;
    private MaterialButton btnSaveTiming;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String doctorUid;

    private int startHour = 9;
    private int startMinute = 0;
    private int endHour = 17;
    private int endMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_timing);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupListeners();
        loadDoctorTiming();
    }

    private void initViews() {
        chipMonday = findViewById(R.id.chipMonday);
        chipTuesday = findViewById(R.id.chipTuesday);
        chipWednesday = findViewById(R.id.chipWednesday);
        chipThursday = findViewById(R.id.chipThursday);
        chipFriday = findViewById(R.id.chipFriday);
        chipSaturday = findViewById(R.id.chipSaturday);
        chipSunday = findViewById(R.id.chipSunday);

        layoutStartTimePicker = findViewById(R.id.layoutStartTimePicker);
        layoutEndTimePicker = findViewById(R.id.layoutEndTimePicker);
        textStartTime = findViewById(R.id.textStartTime);
        textEndTime = findViewById(R.id.textEndTime);
        btnSaveTiming = findViewById(R.id.btnSaveTiming);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        layoutStartTimePicker.setOnClickListener(v -> showTimePicker(true));
        layoutEndTimePicker.setOnClickListener(v -> showTimePicker(false));

        btnSaveTiming.setOnClickListener(v -> saveTiming());
    }

    private void showTimePicker(boolean isStartTime) {
        int initialHour = isStartTime ? startHour : endHour;
        int initialMin = isStartTime ? startMinute : endMinute;

        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    String formatted = formatTime(hourOfDay, minute);
                    if (isStartTime) {
                        startHour = hourOfDay;
                        startMinute = minute;
                        textStartTime.setText(formatted);
                    } else {
                        endHour = hourOfDay;
                        endMinute = minute;
                        textEndTime.setText(formatted);
                    }
                },
                initialHour,
                initialMin,
                false
        );
        timePicker.show();
    }

    private String formatTime(int hourOfDay, int minute) {
        String amPm = hourOfDay >= 12 ? "PM" : "AM";
        int hour12 = hourOfDay % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.US, "%02d:%02d %s", hour12, minute, amPm);
    }

    private void loadDoctorTiming() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        doctorUid = user.getUid();

        db.collection("users").document(doctorUid).get().addOnSuccessListener(doc -> {
            if (doc != null && doc.exists()) {
                String startTime = doc.getString("startTime");
                String endTime = doc.getString("endTime");
                List<String> days = (List<String>) doc.get("availableDays");

                if (startTime != null && !startTime.isEmpty()) {
                    textStartTime.setText(startTime);
                }
                if (endTime != null && !endTime.isEmpty()) {
                    textEndTime.setText(endTime);
                }

                if (days != null && !days.isEmpty()) {
                    chipMonday.setChecked(days.contains("Monday"));
                    chipTuesday.setChecked(days.contains("Tuesday"));
                    chipWednesday.setChecked(days.contains("Wednesday"));
                    chipThursday.setChecked(days.contains("Thursday"));
                    chipFriday.setChecked(days.contains("Friday"));
                    chipSaturday.setChecked(days.contains("Saturday"));
                    chipSunday.setChecked(days.contains("Sunday"));
                }
            }
        });
    }

    private void saveTiming() {
        List<String> selectedDays = new ArrayList<>();
        if (chipMonday.isChecked()) selectedDays.add("Monday");
        if (chipTuesday.isChecked()) selectedDays.add("Tuesday");
        if (chipWednesday.isChecked()) selectedDays.add("Wednesday");
        if (chipThursday.isChecked()) selectedDays.add("Thursday");
        if (chipFriday.isChecked()) selectedDays.add("Friday");
        if (chipSaturday.isChecked()) selectedDays.add("Saturday");
        if (chipSunday.isChecked()) selectedDays.add("Sunday");

        if (selectedDays.isEmpty()) {
            Toast.makeText(this, "Please select at least one available working day", Toast.LENGTH_SHORT).show();
            return;
        }

        String start = textStartTime.getText().toString().trim();
        String end = textEndTime.getText().toString().trim();

        if (doctorUid == null) return;

        btnSaveTiming.setEnabled(false);

        Map<String, Object> timingData = new HashMap<>();
        timingData.put("availableDays", selectedDays);
        timingData.put("startTime", start);
        timingData.put("endTime", end);

        db.collection("users").document(doctorUid).set(timingData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    // Also mirror in doctor_profiles
                    db.collection("doctor_profiles").document(doctorUid).set(timingData, SetOptions.merge());

                    btnSaveTiming.setEnabled(true);
                    Toast.makeText(DoctorTimingActivity.this, "Doctor timing updated successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    btnSaveTiming.setEnabled(true);
                    Toast.makeText(DoctorTimingActivity.this, "Failed to update timing: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
