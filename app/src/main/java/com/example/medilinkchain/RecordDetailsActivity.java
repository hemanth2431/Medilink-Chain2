package com.example.medilinkchain;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RecordDetailsActivity extends AppCompatActivity {

    private static final int PICK_NEW_FILE_REQUEST = 1003;
    private static final String TAG = "RecordDetailsActivity";

    private ImageView backBtn;
    private TextView detailsTitleText;

    // Read Mode Elements
    private LinearLayout readModeLayout;
    private TextView viewRecordType, viewRecordDate, viewRecordHospital, viewRecordDescription, viewFileName;
    private MaterialButton btnViewDocument, btnEditRecord, btnDeleteRecord;

    // Edit Mode Elements
    private LinearLayout editModeLayout;
    private AutoCompleteTextView editRecordTypeDropdown;
    private EditText editRecordDate, editRecordHospital, editRecordDescription;
    private TextView editRecordFileName;
    private MaterialButton btnReplaceDocument, btnSaveRecord, btnCancelEdit;

    // Firebase & Session
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SessionManager sessionManager;

    // Flow State Variables
    private String mode = "VIEW"; // "VIEW" or "CREATE"
    private String recordId = "";
    private String recordType = "";
    private String fileUrl = "";
    private String storagePath = "";
    private String fileName = "";
    private String targetPatientId = "";

    private Uri newFileUri;
    private Calendar calendar;

    private String[] recordTypes = {"Lab Report", "X-Ray", "Medical Report", "Prescription", "Scan / Imaging", "Other"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_details);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        sessionManager = new SessionManager(this);

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String currentUid = auth.getCurrentUser().getUid();

        // Resolve target patient ID
        targetPatientId = getIntent().getStringExtra("PATIENT_ID");
        if (targetPatientId == null || targetPatientId.trim().isEmpty()) {
            targetPatientId = getIntent().getStringExtra("patientId");
        }
        if (targetPatientId == null || targetPatientId.trim().isEmpty()) {
            targetPatientId = currentUid;
        }

        // Enforce account isolation
        boolean isDoctorOrAdmin = sessionManager.isDoctor() || sessionManager.isAdmin();
        if (!isDoctorOrAdmin && !targetPatientId.equals(currentUid)) {
            Toast.makeText(this, "Access denied: Unauthorized patient access.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Bind Views
        backBtn = findViewById(R.id.backBtn);
        detailsTitleText = findViewById(R.id.detailsTitleText);

        // Read Mode Bindings
        readModeLayout = findViewById(R.id.readModeLayout);
        viewRecordType = findViewById(R.id.viewRecordType);
        viewRecordDate = findViewById(R.id.viewRecordDate);
        viewRecordHospital = findViewById(R.id.viewRecordHospital);
        viewRecordDescription = findViewById(R.id.viewRecordDescription);
        viewFileName = findViewById(R.id.viewFileName);
        btnViewDocument = findViewById(R.id.btnViewDocument);
        btnEditRecord = findViewById(R.id.btnEditRecord);
        btnDeleteRecord = findViewById(R.id.btnDeleteRecord);

        // Edit Mode Bindings
        editModeLayout = findViewById(R.id.editModeLayout);
        editRecordTypeDropdown = findViewById(R.id.editRecordTypeDropdown);
        editRecordDate = findViewById(R.id.editRecordDate);
        editRecordHospital = findViewById(R.id.editRecordHospital);
        editRecordDescription = findViewById(R.id.editRecordDescription);
        editRecordFileName = findViewById(R.id.editRecordFileName);
        btnReplaceDocument = findViewById(R.id.btnReplaceDocument);
        btnSaveRecord = findViewById(R.id.btnSaveRecord);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);

        calendar = Calendar.getInstance();

        // If doctor/admin is viewing another patient's records, disable editing to preserve record integrity
        if (isDoctorOrAdmin && !targetPatientId.equals(currentUid)) {
            btnEditRecord.setVisibility(View.GONE);
            btnDeleteRecord.setVisibility(View.GONE);
        }

        // Back action
        backBtn.setOnClickListener(v -> finish());

        // Date selection
        View.OnClickListener dateClickListener = v -> showDatePicker();
        editRecordDate.setOnClickListener(dateClickListener);

        // Setup drop down adapter for type
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, recordTypes);
        editRecordTypeDropdown.setAdapter(typeAdapter);

        // Parse entry mode
        mode = getIntent().getStringExtra("MODE");
        if (mode == null) mode = "VIEW";

        if ("CREATE".equals(mode)) {
            recordType = getIntent().getStringExtra("RECORD_TYPE");
            fileName = getIntent().getStringExtra("FILE_NAME");
            fileUrl = getIntent().getStringExtra("FILE_URL");

            detailsTitleText.setText("Add Record Details");
            switchToEditMode();
            btnCancelEdit.setVisibility(View.GONE); // No cancel on initial creation
        } else {
            recordId = getIntent().getStringExtra("RECORD_ID");
            detailsTitleText.setText("Record Details");
            switchToReadMode();
            loadRecordFromFirestore();
        }

        // Action Handlers
        btnViewDocument.setOnClickListener(v -> viewAttachedDocument());
        btnEditRecord.setOnClickListener(v -> switchToEditMode());
        btnCancelEdit.setOnClickListener(v -> switchToReadMode());
        btnDeleteRecord.setOnClickListener(v -> confirmDeleteRecord());
        btnReplaceDocument.setOnClickListener(v -> openFilePickerForReplace());
        btnSaveRecord.setOnClickListener(v -> saveRecordDetails());
    }

    private void showDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.US);
            editRecordDate.setText(sdf.format(calendar.getTime()));
        };

        new DatePickerDialog(this, dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void switchToReadMode() {
        readModeLayout.setVisibility(View.VISIBLE);
        editModeLayout.setVisibility(View.GONE);
    }

    private void switchToEditMode() {
        readModeLayout.setVisibility(View.GONE);
        editModeLayout.setVisibility(View.VISIBLE);

        editRecordTypeDropdown.setText(recordType, false);
        editRecordFileName.setText(fileName);
    }

    private void loadRecordFromFirestore() {
        if (auth.getCurrentUser() == null || recordId == null || recordId.isEmpty()) return;

        db.collection("users").document(targetPatientId).collection("medicalRecords").document(recordId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        recordType = documentSnapshot.getString("recordType");
                        String date = documentSnapshot.getString("dateOfRecord");
                        String hospital = documentSnapshot.getString("hospitalClinic");
                        String description = documentSnapshot.getString("description");
                        fileName = documentSnapshot.getString("fileName");
                        fileUrl = documentSnapshot.getString("fileUrl");
                        storagePath = documentSnapshot.getString("storagePath");

                        viewRecordType.setText(recordType);
                        viewRecordDate.setText(date);
                        viewRecordHospital.setText(hospital);
                        viewRecordDescription.setText(description != null && !description.isEmpty() ? description : "No description provided.");
                        viewFileName.setText(fileName);

                        // Pre-populate edit fields
                        editRecordTypeDropdown.setText(recordType, false);
                        editRecordDate.setText(date);
                        editRecordHospital.setText(hospital);
                        editRecordDescription.setText(description);
                        editRecordFileName.setText(fileName);
                    } else {
                        Toast.makeText(RecordDetailsActivity.this, "Medical record not found.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(RecordDetailsActivity.this, "Failed to load record details", Toast.LENGTH_SHORT).show());
    }

    private void viewAttachedDocument() {
        if ((fileUrl == null || fileUrl.isEmpty()) && (storagePath == null || storagePath.isEmpty())) {
            Toast.makeText(this, "No document attached", Toast.LENGTH_SHORT).show();
            return;
        }

        String displayName = (fileName != null && !fileName.trim().isEmpty()) ? fileName : (recordType != null ? recordType : "Medical Document");
        String displayDate = (viewRecordDate != null && viewRecordDate.getText() != null) ? viewRecordDate.getText().toString() : "";

        if (storagePath != null && !storagePath.isEmpty()) {
            SupabaseStorageHelper.createSignedUrl(storagePath, 3600, new SupabaseStorageHelper.SignedUrlCallback() {
                @Override
                public void onSuccess(String signedUrl) {
                    runOnUiThread(() -> openViewer(signedUrl, displayName, displayDate));
                }

                @Override
                public void onFailure(String errorMessage) {
                    runOnUiThread(() -> {
                        if (fileUrl != null && !fileUrl.isEmpty()) {
                            openViewer(fileUrl, displayName, displayDate);
                        } else {
                            Toast.makeText(RecordDetailsActivity.this,
                                    "Unable to load document: " + errorMessage,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        } else if (fileUrl != null && !fileUrl.isEmpty()) {
            openViewer(fileUrl, displayName, displayDate);
        }
    }

    private void openViewer(String url, String name, String date) {
        Intent intent = new Intent(this, DocumentViewerActivity.class);
        intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_URL, url);
        intent.putExtra(DocumentViewerActivity.EXTRA_STORAGE_PATH, storagePath != null ? storagePath : "");
        intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_NAME, name);
        intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_TYPE, recordType != null ? recordType : "Other");
        intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_DATE, date);
        startActivity(intent);
    }

    private void openFilePickerForReplace() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "image/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "Select Replacement Document"), PICK_NEW_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_NEW_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            newFileUri = data.getData();
            String name = getFileName(newFileUri);

            // Update UI showing newly selected filename
            editRecordFileName.setText(name + " (Ready to upload)");
            Toast.makeText(this, "Replacement attached: " + name, Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void saveRecordDetails() {
        String selectedType = editRecordTypeDropdown.getText().toString().trim();
        String date = editRecordDate.getText().toString().trim();
        String hospital = editRecordHospital.getText().toString().trim();
        String description = editRecordDescription.getText().toString().trim();

        if (selectedType.isEmpty()) {
            editRecordTypeDropdown.setError("Please select record type");
            return;
        }
        if (date.isEmpty()) {
            editRecordDate.setError("Please select date of record");
            return;
        }
        if (hospital.isEmpty()) {
            editRecordHospital.setError("Please enter hospital or clinic name");
            return;
        }

        recordType = selectedType;
        btnSaveRecord.setEnabled(false);

        if (newFileUri != null) {
            // Replace old file in storage, then save record details
            uploadReplacementFile(date, hospital, description);
        } else {
            // Simply update fields
            writeRecordToFirestore(date, hospital, description);
        }
    }

    /**
     * Uploads a replacement file to Supabase Storage.
     * Uses target patient ID to namespace the file path in the bucket.
     */
    private void uploadReplacementFile(String date, String hospital, String description) {
        if (auth.getCurrentUser() == null) return;

        String rawName = getFileName(newFileUri);

        // Delete previous file from storage if present
        if (storagePath != null && !storagePath.isEmpty()) {
            SupabaseStorageHelper.deleteFile(storagePath, null);
        }

        // Determine MIME type
        String mimeType = getContentResolver().getType(newFileUri);
        if (mimeType == null || mimeType.isEmpty()) {
            String lower = rawName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf")) mimeType = "application/pdf";
            else if (lower.endsWith(".png")) mimeType = "image/png";
            else mimeType = "image/jpeg";
        }

        Log.d(TAG, "Uploading replacement file to Supabase: " + rawName);
        final String finalMimeType = mimeType;
        final String finalRawName = rawName;

        SupabaseStorageHelper.uploadFile(
                this,
                newFileUri,
                rawName,
                "medical-records",
                mimeType,
                targetPatientId,
                new SupabaseStorageHelper.UploadCallback() {
                    @Override
                    public void onProgress(int percent) {
                        Log.d(TAG, "Replacement upload progress: " + percent + "%");
                    }

                    @Override
                    public void onSuccess(String newUrl, String newStoragePath) {
                        Log.d(TAG, "Replacement upload SUCCESS. Path: " + newStoragePath + " URL: " + newUrl);
                        fileUrl = newUrl;
                        storagePath = newStoragePath;
                        fileName = finalRawName.replaceAll("[^a-zA-Z0-9._-]", "_");
                        newFileUri = null;
                        writeRecordToFirestore(date, hospital, description);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "Replacement upload failed: " + errorMessage);
                        btnSaveRecord.setEnabled(true);
                        Toast.makeText(RecordDetailsActivity.this,
                                "Supabase Storage upload failed. " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void writeRecordToFirestore(String date, String hospital, String description) {
        if (auth.getCurrentUser() == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("patientId", targetPatientId);
        data.put("recordType", recordType);
        data.put("dateOfRecord", date);
        data.put("hospitalClinic", hospital);
        data.put("description", description);
        data.put("fileName", fileName);
        data.put("fileUrl", fileUrl);
        data.put("storagePath", storagePath);
        data.put("createdAt", new Date());

        // Backwards compatibility legacy fields
        data.put("bloodGroup", recordType);
        data.put("dob", date);
        data.put("contact", hospital);
        data.put("reportUploaded", "Yes");

        if ("CREATE".equals(mode)) {
            // Add a new record
            db.collection("users").document(targetPatientId).collection("medicalRecords")
                    .add(data)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(RecordDetailsActivity.this, "Record saved successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSaveRecord.setEnabled(true);
                        Toast.makeText(RecordDetailsActivity.this, "Failed to save record: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            // Edit existing record
            db.collection("users").document(targetPatientId).collection("medicalRecords").document(recordId)
                    .update(data)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(RecordDetailsActivity.this, "Changes saved successfully!", Toast.LENGTH_SHORT).show();
                        btnSaveRecord.setEnabled(true);
                        switchToReadMode();
                        loadRecordFromFirestore();
                    })
                    .addOnFailureListener(e -> {
                        btnSaveRecord.setEnabled(true);
                        Toast.makeText(RecordDetailsActivity.this, "Failed to update record: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void confirmDeleteRecord() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Medical Record")
                .setMessage("Are you sure you want to delete this medical record?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteRecord();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRecord() {
        if (auth.getCurrentUser() == null || recordId.isEmpty()) return;

        // Delete from Supabase Storage first if storagePath is present
        if (storagePath != null && !storagePath.isEmpty()) {
            SupabaseStorageHelper.deleteFile(storagePath, null);
        }

        db.collection("users").document(targetPatientId).collection("medicalRecords").document(recordId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RecordDetailsActivity.this, "Record deleted successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(RecordDetailsActivity.this, "Failed to delete record", Toast.LENGTH_SHORT).show());
    }
}
