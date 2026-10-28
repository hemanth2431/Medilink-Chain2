package com.example.medilinkchain;

import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MedicationHistoryModel — Represents an action event (TAKEN, SKIPPED, REFILLED) in medicine history.
 */
public class MedicationHistoryModel implements Serializable {

    private String id;
    private String medicationId;
    private String userId;
    private String medicineName;
    private String dosage;
    private String scheduledTime;
    private String action; // "TAKEN", "SKIPPED", "REFILLED"
    private int quantityUsed; // 1 for taken, 0 for skipped, +N for refilled
    private String createdAt;

    public MedicationHistoryModel() {}

    public MedicationHistoryModel(String id, String medicationId, String userId, String medicineName, String dosage, String scheduledTime, String action, int quantityUsed, String createdAt) {
        this.id = id;
        this.medicationId = medicationId;
        this.userId = userId;
        this.medicineName = medicineName;
        this.dosage = dosage;
        this.scheduledTime = scheduledTime;
        this.action = action;
        this.quantityUsed = quantityUsed;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMedicationId() { return medicationId; }
    public void setMedicationId(String medicationId) { this.medicationId = medicationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getAction() { return action != null ? action : "TAKEN"; }
    public void setAction(String action) { this.action = action; }

    public int getQuantityUsed() { return quantityUsed; }
    public void setQuantityUsed(int quantityUsed) { this.quantityUsed = quantityUsed; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /**
     * Formats createdAt ISO string into human readable display date (e.g. "17 Aug 2026").
     */
    public String getFormattedDate() {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            // Parses ISO instant e.g. 2026-08-17T09:00:00Z or similar
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant instant = java.time.Instant.parse(createdAt);
                java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
                return zdt.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US));
            }
        } catch (Exception ignored) {}
        return createdAt;
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            if (id != null) json.put("id", id);
            json.put("medication_id", medicationId);
            json.put("user_id", userId);
            json.put("medicine_name", medicineName);
            json.put("dosage", dosage);
            json.put("scheduled_time", scheduledTime);
            json.put("action", action);
            json.put("quantity_used", quantityUsed);
            if (createdAt != null) json.put("created_at", createdAt);
        } catch (Exception ignored) {}
        return json;
    }

    public static MedicationHistoryModel fromJsonObject(JSONObject json) {
        MedicationHistoryModel m = new MedicationHistoryModel();
        m.setId(json.optString("id"));
        m.setMedicationId(json.optString("medication_id"));
        m.setUserId(json.optString("user_id"));
        m.setMedicineName(json.optString("medicine_name"));
        m.setDosage(json.optString("dosage"));
        m.setScheduledTime(json.optString("scheduled_time"));
        m.setAction(json.optString("action", "TAKEN"));
        m.setQuantityUsed(json.optInt("quantity_used", 0));
        m.setCreatedAt(json.optString("created_at"));
        return m;
    }
}
