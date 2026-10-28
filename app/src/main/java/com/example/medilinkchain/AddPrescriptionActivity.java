package com.example.medilinkchain;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * AddPrescriptionActivity — Form to select and upload a medical prescription file (PDF, JPG, PNG) to Supabase.
 */
public class AddPrescriptionActivity extends AppCompatActivity {

    private static final String TAG = "AddPrescription";

    private ImageView backBtn;
    private TextInputEditText prescriptionTitleInput, doctorNameInput, hospitalNameInput, prescriptionDateInput, prescriptionNotesInput;
    private MaterialButton btnSelectFile, btnUploadPrescription;
    private MaterialCardView selectedFileCard;
    private TextView fileIconBadge, selectedFileNameText, selectedFileSizeText;
    private ImageView removeFileBtn;

    private LinearLayout uploadProgressLayout;
    private ProgressBar uploadProgressBar;
    private TextView uploadProgressPercentageText;

    private Uri selectedFileUri = null;
    private String selectedFileName = "";
    private String selectedFileType = "PDF";
    private long selectedFileSizeBytes = 0;

    private Calendar calendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.US);

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private String currentUserId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_prescription);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }

        initViews();
        setupFilePicker();
        setupDatePicker();
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        prescriptionTitleInput = findViewById(R.id.prescriptionTitleInput);
        doctorNameInput = findViewById(R.id.doctorNameInput);
        hospitalNameInput = findViewById(R.id.hospitalNameInput);
        prescriptionDateInput = findViewById(R.id.prescriptionDateInput);
        prescriptionNotesInput = findViewById(R.id.prescriptionNotesInput);

        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnUploadPrescription = findViewById(R.id.btnUploadPrescription);
        selectedFileCard = findViewById(R.id.selectedFileCard);
        fileIconBadge = findViewById(R.id.fileIconBadge);
        selectedFileNameText = findViewById(R.id.selectedFileNameText);
        selectedFileSizeText = findViewById(R.id.selectedFileSizeText);
        removeFileBtn = findViewById(R.id.removeFileBtn);

        uploadProgressLayout = findViewById(R.id.uploadProgressLayout);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        uploadProgressPercentageText = findViewById(R.id.uploadProgressPercentageText);

        prescriptionDateInput.setText(dateFormat.format(calendar.getTime()));

        backBtn.setOnClickListener(v -> finish());
        btnSelectFile.setOnClickListener(v -> openFilePicker());
        removeFileBtn.setOnClickListener(v -> clearSelectedFile());
        btnUploadPrescription.setOnClickListener(v -> validateAndUpload());
    }

    private void setupDatePicker() {
        prescriptionDateInput.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                prescriptionDateInput.setText(dateFormat.format(calendar.getTime()));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
        );
    }

    private void openFilePicker() {
        String[] mimeTypes = {"application/pdf", "image/jpeg", "image/png", "image/jpg"};
        filePickerLauncher.launch(mimeTypes);
    }

    private void handleSelectedFile(Uri uri) {
        selectedFileUri = uri;
        resolveFileMetadata(uri);

        // Check file size (Max 10 MB)
        if (selectedFileSizeBytes > SupabaseConfig.MAX_FILE_SIZE_BYTES) {
            Toast.makeText(this, "File is too large. Please select a smaller file.", Toast.LENGTH_LONG).show();
            clearSelectedFile();
            return;
        }

        // Validate supported file type
        String mime = getContentResolver().getType(uri);
        String nameLower = selectedFileName.toLowerCase(Locale.ROOT);
        if (nameLower.endsWith(".pdf") || (mime != null && mime.contains("pdf"))) {
            selectedFileType = "PDF";
        } else if (nameLower.endsWith(".png") || (mime != null && mime.contains("png"))) {
            selectedFileType = "PNG";
        } else if (nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || (mime != null && (mime.contains("jpeg") || mime.contains("jpg")))) {
            selectedFileType = "JPG";
        } else {
            Toast.makeText(this, "File must be: PDF, JPG, JPEG, or PNG.", Toast.LENGTH_LONG).show();
            clearSelectedFile();
            return;
        }

        fileIconBadge.setText(selectedFileType);
        selectedFileNameText.setText(selectedFileName);
        selectedFileSizeText.setText(selectedFileType + " • " + formatFileSize(selectedFileSizeBytes));

        selectedFileCard.setVisibility(View.VISIBLE);
        btnSelectFile.setVisibility(View.GONE);
    }

    private void clearSelectedFile() {
        selectedFileUri = null;
        selectedFileName = "";
        selectedFileType = "PDF";
        selectedFileSizeBytes = 0;

        selectedFileCard.setVisibility(View.GONE);
        btnSelectFile.setVisibility(View.VISIBLE);
    }

    private void resolveFileMetadata(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex != -1) selectedFileName = cursor.getString(nameIndex);
                if (sizeIndex != -1) selectedFileSizeBytes = cursor.getLong(sizeIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading file metadata", e);
        } finally {
            if (cursor != null) cursor.close();
        }

        if (selectedFileName == null || selectedFileName.isEmpty()) {
            selectedFileName = "prescription_document";
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 KB";
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        } else {
            double mb = kb / 1024.0;
            return String.format(Locale.US, "%.1f MB", mb);
        }
    }

    private void validateAndUpload() {
        String title = prescriptionTitleInput.getText() != null ? prescriptionTitleInput.getText().toString().trim() : "";
        String doctorName = doctorNameInput.getText() != null ? doctorNameInput.getText().toString().trim() : "";
        String hospitalName = hospitalNameInput.getText() != null ? hospitalNameInput.getText().toString().trim() : "";
        String prescriptionDate = prescriptionDateInput.getText() != null ? prescriptionDateInput.getText().toString().trim() : "";
        String notes = prescriptionNotesInput.getText() != null ? prescriptionNotesInput.getText().toString().trim() : "";

        if (title.isEmpty()) {
            prescriptionTitleInput.setError("Prescription title cannot be empty");
            prescriptionTitleInput.requestFocus();
            return;
        }

        if (doctorName.isEmpty()) {
            doctorNameInput.setError("Doctor name cannot be empty");
            doctorNameInput.requestFocus();
            return;
        }

        if (hospitalName.isEmpty()) {
            hospitalNameInput.setError("Hospital / clinic name cannot be empty");
            hospitalNameInput.requestFocus();
            return;
        }

        if (prescriptionDate.isEmpty()) {
            prescriptionDateInput.setError("Prescription date cannot be empty");
            return;
        }

        if (selectedFileUri == null) {
            Toast.makeText(this, "Please select a prescription file", Toast.LENGTH_LONG).show();
            return;
        }

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Authentication required. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }

        // Determine MIME type
        String mimeType = getContentResolver().getType(selectedFileUri);
        if (mimeType == null || mimeType.isEmpty()) {
            if ("PDF".equalsIgnoreCase(selectedFileType)) mimeType = "application/pdf";
            else if ("PNG".equalsIgnoreCase(selectedFileType)) mimeType = "image/png";
            else mimeType = "image/jpeg";
        }

        uploadProgressLayout.setVisibility(View.VISIBLE);
        uploadProgressBar.setProgress(0);
        uploadProgressPercentageText.setText("Preparing upload...");
        btnUploadPrescription.setEnabled(false);
        btnSelectFile.setEnabled(false);

        PrescriptionModel model = new PrescriptionModel();
        model.setUserId(currentUserId);
        model.setTitle(title);
        model.setDoctorName(doctorName);
        model.setHospitalName(hospitalName);
        model.setPrescriptionDate(prescriptionDate);
        model.setFileType(selectedFileType);
        model.setFileSize(formatFileSize(selectedFileSizeBytes));
        model.setNotes(notes);

        PrescriptionRepository.uploadPrescription(
                this,
                selectedFileUri,
                selectedFileName,
                mimeType,
                model,
                new PrescriptionRepository.UploadProgressCallback() {
                    @Override
                    public void onProgress(int percent) {
                        uploadProgressBar.setProgress(percent);
                        uploadProgressPercentageText.setText("Uploading prescription... " + percent + "%");
                    }

                    @Override
                    public void onSuccess(PrescriptionModel savedModel) {
                        uploadProgressLayout.setVisibility(View.GONE);
                        Toast.makeText(AddPrescriptionActivity.this, "Prescription uploaded successfully.", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onFailure(String error) {
                        uploadProgressLayout.setVisibility(View.GONE);
                        btnUploadPrescription.setEnabled(true);
                        btnSelectFile.setEnabled(true);
                        Log.e(TAG, "Prescription upload error: " + error);
                        Toast.makeText(AddPrescriptionActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }
}
