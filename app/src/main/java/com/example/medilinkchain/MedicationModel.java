package com.example.medilinkchain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MedicationModel — Represents a scheduled medication for a patient.
 */
public class MedicationModel implements Serializable {

    private String id;
    private String userId;
    private String medicineName;
    private String dosage;
    private String frequency;
    private List<String> times = new ArrayList<>();
    private String startDate;
    private String endDate;
    private String notes;
    private int tabletQuantity;
    private int remainingTablets;
    private int refillThreshold;
    private String status; // "ACTIVE", "PAUSED", "COMPLETED"
    private String createdAt;
    private String updatedAt;

    public MedicationModel() {
        this.status = "ACTIVE";
        this.times = new ArrayList<>();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public List<String> getTimes() { return times != null ? times : new ArrayList<>(); }
    public void setTimes(List<String> times) { this.times = times; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getTabletQuantity() { return tabletQuantity; }
    public void setTabletQuantity(int tabletQuantity) { this.tabletQuantity = tabletQuantity; }

    public int getRemainingTablets() { return remainingTablets; }
    public void setRemainingTablets(int remainingTablets) { this.remainingTablets = remainingTablets; }

    public int getRefillThreshold() { return refillThreshold; }
    public void setRefillThreshold(int refillThreshold) { this.refillThreshold = refillThreshold; }

    public String getStatus() { return status != null ? status : "ACTIVE"; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isLowStock() {
        return remainingTablets <= refillThreshold && remainingTablets >= 0;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status) && !isCompletedByDate();
    }

    public boolean isPaused() {
        return "PAUSED".equalsIgnoreCase(status);
    }

    public boolean isRefillNeeded() {
        return remainingTablets <= refillThreshold;
    }

    /**
     * Checks if the medicine's end date has passed.
     */
    public boolean isCompletedByDate() {
        if (endDate == null || endDate.trim().isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy", Locale.US);
            Date end = sdf.parse(endDate.trim());
            if (end != null) {
                Calendar endCal = Calendar.getInstance();
                endCal.setTime(end);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                return Calendar.getInstance().after(endCal);
            }
        } catch (ParseException ignored) {
            try {
                SimpleDateFormat sdfIso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date end = sdfIso.parse(endDate.trim());
                if (end != null) {
                    Calendar endCal = Calendar.getInstance();
                    endCal.setTime(end);
                    endCal.set(Calendar.HOUR_OF_DAY, 23);
                    endCal.set(Calendar.MINUTE, 59);
                    endCal.set(Calendar.SECOND, 59);
                    return Calendar.getInstance().after(endCal);
                }
            } catch (Exception ignored2) {}
        }
        return false;
    }

    /**
     * Finds the next scheduled dose time string formatted for display.
     */
    public String getNextDoseDisplay() {
        if (times == null || times.isEmpty()) return "No times scheduled";
        if (isCompletedByDate()) return "Course Completed";
        if ("PAUSED".equalsIgnoreCase(status)) return "Paused";

        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        String nextToday = null;
        int nextDiff = Integer.MAX_VALUE;

        for (String t : times) {
            try {
                Date d = timeFormat.parse(t.trim());
                if (d != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(d);
                    int tMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                    if (tMinutes >= nowMinutes && (tMinutes - nowMinutes) < nextDiff) {
                        nextDiff = tMinutes - nowMinutes;
                        nextToday = t.trim();
                    }
                }
            } catch (ParseException ignored) {}
        }

        if (nextToday != null) {
            return nextToday;
        } else {
            // First dose tomorrow
            return times.get(0).trim() + " (Tomorrow)";
        }
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            if (id != null) json.put("id", id);
            json.put("user_id", userId);
            json.put("medicine_name", medicineName);
            json.put("dosage", dosage);
            json.put("frequency", frequency);

            JSONArray timesArr = new JSONArray();
            if (times != null) {
                for (String t : times) timesArr.put(t);
            }
            json.put("scheduled_times", timesArr);
            json.put("times", timesArr);

            json.put("start_date", startDate);
            json.put("end_date", endDate);
            json.put("notes", notes != null ? notes : "");
            json.put("tablet_quantity", tabletQuantity);
            json.put("remaining_tablets", remainingTablets);
            json.put("refill_threshold", refillThreshold);
            json.put("status", status != null ? status : "ACTIVE");
            if (createdAt != null) json.put("created_at", createdAt);
            if (updatedAt != null) json.put("updated_at", updatedAt);
        } catch (Exception ignored) {}
        return json;
    }

    public static MedicationModel fromJsonObject(JSONObject json) {
        MedicationModel m = new MedicationModel();
        m.setId(json.optString("id"));
        m.setUserId(json.optString("user_id"));
        m.setMedicineName(json.optString("medicine_name"));
        m.setDosage(json.optString("dosage"));
        m.setFrequency(json.optString("frequency"));

        List<String> timesList = new ArrayList<>();
        JSONArray timesArr = json.optJSONArray("scheduled_times");
        if (timesArr == null) {
            timesArr = json.optJSONArray("times");
        }

        if (timesArr != null) {
            for (int i = 0; i < timesArr.length(); i++) {
                timesList.add(timesArr.optString(i));
            }
        } else {
            String rawTimes = json.optString("scheduled_times", "");
            if (rawTimes.isEmpty()) {
                rawTimes = json.optString("times", "");
            }
            if (!rawTimes.isEmpty()) {
                // e.g. "{09:00 AM,09:00 PM}" from PostgreSQL array format
                rawTimes = rawTimes.replace("{", "").replace("}", "").replace("\"", "");
                for (String part : rawTimes.split(",")) {
                    if (!part.trim().isEmpty()) timesList.add(part.trim());
                }
            }
        }
        m.setTimes(timesList);

        m.setStartDate(json.optString("start_date"));
        m.setEndDate(json.optString("end_date"));
        m.setNotes(json.optString("notes"));
        m.setTabletQuantity(json.optInt("tablet_quantity", 1));
        m.setRemainingTablets(json.optInt("remaining_tablets", 1));
        m.setRefillThreshold(json.optInt("refill_threshold", 0));
        m.setStatus(json.optString("status", "ACTIVE"));
        m.setCreatedAt(json.optString("created_at"));
        m.setUpdatedAt(json.optString("updated_at"));
        return m;
    }
}
