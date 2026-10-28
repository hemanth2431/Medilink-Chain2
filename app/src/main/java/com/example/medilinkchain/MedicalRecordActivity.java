package com.example.medilinkchain;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MedicalRecordActivity extends AppCompatActivity {

    private static final int OPEN_DOCUMENT_REQUEST = 1002;
    private static final String TAG = "MedicalRecordActivity";

    private AutoCompleteTextView typeDropdown;
    private EditText dateEditText;
    private EditText hospitalEditText;
    private EditText descriptionEditText;

    private MaterialButton btnAddFile;
    private MaterialCardView selectedFileCard;
    private TextView fileIconText, fileNameText, fileMetaText;
    private ImageView removeFileBtn;

    private LinearLayout progressLayout;
    private ProgressBar uploadProgressBar;
    private TextView progressPercentageText;
    private MaterialButton saveBtn;
    private ImageView backBtn;

    private Uri selectedFileUri;
    private String selectedFileName = "";
    private String selectedFileType = "";
    private String formattedFileSize = "";
    private long selectedFileSizeBytes = 0;
    private Calendar calendar;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String[] recordTypes = {"Lab Report", "X-Ray", "Medical Report", "Prescription", "Scan / Imaging", "Other"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_record);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        calendar = Calendar.getInstance();

        typeDropdown = findViewById(R.id.typeDropdown);
        dateEditText = findViewById(R.id.dateEditText);
        hospitalEditText = findViewById(R.id.hospitalEditText);
        descriptionEditText = findViewById(R.id.descriptionEditText);

        btnAddFile = findViewById(R.id.btnAddFile);
        selectedFileCard = findViewById(R.id.selectedFileCard);
        fileIconText = findViewById(R.id.fileIconText);
        fileNameText = findViewById(R.id.fileNameText);
        fileMetaText = findViewById(R.id.fileMetaText);
        removeFileBtn = findViewById(R.id.removeFileBtn);

        progressLayout = findViewById(R.id.progressLayout);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        progressPercentageText = findViewById(R.id.progressPercentageText);
        saveBtn = findViewById(R.id.saveBtn);
        backBtn = findViewById(R.id.backBtn);

        backBtn.setOnClickListener(v -> finish());

        // Setup Dropdown for Record Type
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, recordTypes);
        typeDropdown.setAdapter(typeAdapter);

        // Date Picker Action
        dateEditText.setOnClickListener(v -> showDatePicker());

        // Storage Access Framework File Picker
        btnAddFile.setOnClickListener(v -> openDocumentPicker());

        // Remove File Action
        removeFileBtn.setOnClickListener(v -> clearSelectedFile());

        // Upload & Save Action
        saveBtn.setOnClickListener(v -> validateAndUpload());
    }

    private void showDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            String format = "dd MMM yyyy";
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
            dateEditText.setText(sdf.format(calendar.getTime()));
        };

        new DatePickerDialog(this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "image/jpeg", "image/png", "image/jpg"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, OPEN_DOCUMENT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == OPEN_DOCUMENT_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();

            // Take persistable URI read permission if available
            try {
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (Exception ignored) {}

            parseAndValidateFile(uri);
        }
    }

    private void parseAndValidateFile(Uri uri) {
        selectedFileUri = uri;
        getFileMetadata(uri);

        String lowerName = selectedFileName.toLowerCase(Locale.ROOT);
        String mimeType = getContentResolver().getType(uri);

        boolean isPdf = lowerName.endsWith(".pdf") || (mimeType != null && mimeType.contains("pdf"));
        boolean isJpg = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || (mimeType != null && (mimeType.contains("jpeg") || mimeType.contains("jpg")));
        boolean isPng = lowerName.endsWith(".png") || (mimeType != null && mimeType.contains("png"));

        if (!isPdf && !isJpg && !isPng) {
            clearSelectedFile();
            Toast.makeText(this, "File type not supported. Please select PDF, JPG, or PNG.", Toast.LENGTH_LONG).show();
            return;
        }

        if (selectedFileSizeBytes > SupabaseConfig.MAX_FILE_SIZE_BYTES) {
            clearSelectedFile();
            Toast.makeText(this, "File size exceeds 10 MB limit.", Toast.LENGTH_LONG).show();
            return;
        }

        if (isPdf) {
            selectedFileType = "PDF";
            fileIconText.setText("📄");
        } else if (isJpg) {
            selectedFileType = "JPG";
            fileIconText.setText("🖼");
        } else {
            selectedFileType = "PNG";
            fileIconText.setText("🖼");
        }

        double sizeInMb = (double) selectedFileSizeBytes / (1024 * 1024);
        if (sizeInMb >= 0.1) {
            formattedFileSize = String.format(Locale.getDefault(), "%.1f MB", sizeInMb);
        } else {
            long sizeInKb = selectedFileSizeBytes / 1024;
            formattedFileSize = sizeInKb + " KB";
        }

        fileNameText.setText(selectedFileName);
        fileMetaText.setText(selectedFileType + " • " + formattedFileSize);

        selectedFileCard.setVisibility(View.VISIBLE);
        btnAddFile.setVisibility(View.GONE);
    }

    private void getFileMetadata(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex != -1) {
                    selectedFileName = cursor.getString(nameIndex);
                }
                if (sizeIndex != -1) {
                    selectedFileSizeBytes = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving metadata from ContentResolver", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (selectedFileName == null || selectedFileName.isEmpty()) {
            selectedFileName = uri.getLastPathSegment();
            if (selectedFileName == null || selectedFileName.isEmpty()) {
                selectedFileName = "medical_document";
            }
        }
    }

    private void clearSelectedFile() {
        selectedFileUri = null;
        selectedFileName = "";
        selectedFileType = "";
        formattedFileSize = "";
        selectedFileSizeBytes = 0;

        selectedFileCard.setVisibility(View.GONE);
        btnAddFile.setVisibility(View.VISIBLE);
    }

    private void validateAndUpload() {
        String recordType = typeDropdown.getText().toString().trim();
        String dateOfRecord = dateEditText.getText().toString().trim();
        String hospitalClinic = hospitalEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();

        if (recordType.isEmpty()) {
            typeDropdown.setError("Please select record type");
            return;
        }

        if (dateOfRecord.isEmpty()) {
            dateEditText.setError("Please select date of record");
            return;
        }

        if (hospitalClinic.isEmpty()) {
            hospitalEditText.setError("Please enter hospital or clinic name");
            return;
        }

        if (selectedFileUri == null) {
            Toast.makeText(this, "Please select a medical document file", Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Authentication required. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }

        uploadFileToSupabase(currentUser.getUid(), recordType, dateOfRecord, hospitalClinic, description);
    }

    /**
     * Uploads the selected file to Supabase Storage using the anon key.
     *
     * Authentication note: This app uses Firebase Auth; the Supabase anon key is used
     * for storage access. Ensure the Supabase bucket policy allows the 'anon' role.
     */
    private void uploadFileToSupabase(String firebaseUID, String recordType, String dateOfRecord, String hospitalClinic, String description) {
        // Determine MIME type
        String mimeType = getContentResolver().getType(selectedFileUri);
        if (mimeType == null || mimeType.isEmpty()) {
            if ("PDF".equalsIgnoreCase(selectedFileType)) mimeType = "application/pdf";
            else if ("PNG".equalsIgnoreCase(selectedFileType)) mimeType = "image/png";
            else mimeType = "image/jpeg";
        }

        // Reject unsupported MIME types
        if (!mimeType.equals("application/pdf")
                && !mimeType.equals("image/jpeg")
                && !mimeType.equals("image/png")
                && !mimeType.contains("jpeg")
                && !mimeType.contains("png")
                && !mimeType.contains("pdf")) {
            Toast.makeText(this, "File type not supported. Please select PDF, JPG, or PNG.", Toast.LENGTH_LONG).show();
            return;
        }

        // Reject oversized files
        if (selectedFileSizeBytes > SupabaseConfig.MAX_FILE_SIZE_BYTES) {
            Toast.makeText(this, "File size exceeds 10 MB limit.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "=== SUPABASE STORAGE UPLOAD START ===");
        Log.d(TAG, "Firebase UID: " + firebaseUID);
        Log.d(TAG, "File: " + selectedFileName + " (" + selectedFileSizeBytes + " bytes, " + mimeType + ")");

        // Show progress UI
        progressLayout.setVisibility(View.VISIBLE);
        uploadProgressBar.setProgress(0);
        progressPercentageText.setText("Preparing upload...");
        saveBtn.setEnabled(false);
        btnAddFile.setEnabled(false);

        final String finalMimeType = mimeType;

        SupabaseStorageHelper.uploadFile(
                this,
                selectedFileUri,
                selectedFileName,
                "medical-records",
                finalMimeType,
                firebaseUID,
                new SupabaseStorageHelper.UploadCallback() {
                    @Override
                    public void onProgress(int percent) {
                        uploadProgressBar.setProgress(percent);
                        progressPercentageText.setText("Uploading document... " + percent + "%");
                    }

                    @Override
                    public void onSuccess(String fileUrl, String storagePath) {
                        Log.d(TAG, "Upload SUCCESS. Saving to Firestore. Path: " + storagePath + " URL: " + fileUrl);
                        progressPercentageText.setText("Saving record...");
                        saveToFirestore(firebaseUID, recordType, dateOfRecord, hospitalClinic, description, fileUrl, storagePath);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "Supabase upload failed: " + errorMessage);
                        progressLayout.setVisibility(View.GONE);
                        saveBtn.setEnabled(true);
                        btnAddFile.setEnabled(true);
                        Toast.makeText(MedicalRecordActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void saveToFirestore(String patientId, String recordType, String dateOfRecord, String hospitalClinic, String description, String fileUrl, String storagePath) {
        Map<String, Object> data = new HashMap<>();
        data.put("patientId", patientId);
        data.put("recordType", recordType);
        data.put("dateOfRecord", dateOfRecord);
        data.put("hospitalClinic", hospitalClinic);
        data.put("description", description);
        data.put("fileName", selectedFileName);
        data.put("fileType", selectedFileType);
        data.put("fileSize", formattedFileSize);
        data.put("fileUrl", fileUrl);
        data.put("storagePath", storagePath);
        data.put("createdAt", new Date());

        // Backwards compatibility legacy fields
        data.put("name", selectedFileName);
        data.put("bloodGroup", recordType);
        data.put("dob", dateOfRecord);
        data.put("contact", hospitalClinic);
        data.put("reportUploaded", "Yes");

        db.collection("users").document(patientId).collection("medicalRecords")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    progressLayout.setVisibility(View.GONE);
                    Toast.makeText(MedicalRecordActivity.this, "Medical record uploaded successfully.", Toast.LENGTH_SHORT).show();

                    // Open ViewRecordsActivity to see updated list
                    Intent intent = new Intent(MedicalRecordActivity.this, ViewRecordsActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore write failed", e);
                    progressLayout.setVisibility(View.GONE);
                    saveBtn.setEnabled(true);
                    btnAddFile.setEnabled(true);
                    Toast.makeText(MedicalRecordActivity.this, "Failed to save record metadata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}