package com.example.medilinkchain;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * PrescriptionModel — Represents a stored medical prescription document.
 */
public class PrescriptionModel implements Serializable {

    private String id;
    private String userId;
    private String title;
    private String doctorName;
    private String hospitalName;
    private String prescriptionDate;
    private String filePath;
    private String fileType; // PDF, JPG, PNG
    private String fileSize;
    private String fileUrl;
    private String notes;
    private String createdAt;
    private String updatedAt;

    public PrescriptionModel() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }

    public String getPrescriptionDate() { return prescriptionDate; }
    public void setPrescriptionDate(String prescriptionDate) { this.prescriptionDate = prescriptionDate; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileType() { return fileType != null ? fileType.toUpperCase() : "PDF"; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isPdf() {
        return "PDF".equalsIgnoreCase(fileType) || (filePath != null && filePath.toLowerCase().endsWith(".pdf"));
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            if (id != null) json.put("id", id);
            json.put("user_id", userId);
            json.put("title", title);
            json.put("doctor_name", doctorName);
            json.put("hospital_name", hospitalName);
            json.put("prescription_date", prescriptionDate);
            json.put("file_path", filePath);
            json.put("file_type", fileType);
            if (fileSize != null) json.put("file_size", fileSize);
            if (fileUrl != null) json.put("file_url", fileUrl);
            json.put("notes", notes != null ? notes : "");
            if (createdAt != null) json.put("created_at", createdAt);
            if (updatedAt != null) json.put("updated_at", updatedAt);
        } catch (Exception ignored) {}
        return json;
    }

    public static PrescriptionModel fromJsonObject(JSONObject json) {
        PrescriptionModel p = new PrescriptionModel();
        p.setId(json.optString("id"));
        p.setUserId(json.optString("user_id"));
        p.setTitle(json.optString("title"));
        p.setDoctorName(json.optString("doctor_name"));
        p.setHospitalName(json.optString("hospital_name"));
        p.setPrescriptionDate(json.optString("prescription_date"));
        p.setFilePath(json.optString("file_path"));
        p.setFileType(json.optString("file_type", "PDF"));
        p.setFileSize(json.optString("file_size"));
        p.setFileUrl(json.optString("file_url"));
        p.setNotes(json.optString("notes"));
        p.setCreatedAt(json.optString("created_at"));
        p.setUpdatedAt(json.optString("updated_at"));
        return p;
    }
}
