package com.example.medilinkchain;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DoctorScanActivity extends AppCompatActivity {

    private static final String TAG = "DoctorScanActivity";

    private DecoratedBarcodeView barcodeScannerView;
    private View scannerLaser;
    private boolean isFlashOn = false;
    private ObjectAnimator laserAnimator;
    private final AtomicBoolean isProcessingScan = new AtomicBoolean(false);

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private SessionManager sessionManager;

    private AlertDialog currentErrorDialog = null;

    // Camera Permission Launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    initScanner();
                } else {
                    showCameraPermissionDeniedDialog();
                }
            }
    );

    // Gallery Import Launcher
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                        String qrValue = decodeQRFromBitmap(bitmap);
                        if (qrValue != null && !qrValue.trim().isEmpty()) {
                            processScannedContent(qrValue);
                        } else {
                            showScanErrorDialog("Invalid MediLinkChain Patient QR",
                                    "The selected image does not contain a valid MediLinkChain QR code.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to read image from gallery: " + e.getMessage());
                        showScanErrorDialog("Image Error", "Could not process the selected image. Please try scanning directly with the camera.");
                    }
                } else {
                    // Re-arm scanner if user cancelled gallery picker
                    resumeScanner();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_scan);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        sessionManager = new SessionManager(this);

        barcodeScannerView = findViewById(R.id.barcodeScannerView);
        scannerLaser = findViewById(R.id.scannerLaser);

        if (barcodeScannerView != null) {
            barcodeScannerView.setStatusText("");
        }

        // Action Buttons
        findViewById(R.id.btnToggleFlashlight).setOnClickListener(v -> toggleFlashlight());
        findViewById(R.id.btnImportGallery).setOnClickListener(v -> importFromGallery());
        findViewById(R.id.backArrow).setOnClickListener(v -> finish());

        // Setup bottom navigation bar
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_doctor_scan);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_doctor_dashboard) {
                    finish();
                    return true;
                }
                return true;
            });
        }

        startLaserAnimation();

        // Check camera permission on launch
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initScanner();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void initScanner() {
        if (barcodeScannerView == null) return;
        isProcessingScan.set(false);
        barcodeScannerView.resume();

        barcodeScannerView.decodeSingle(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result != null && result.getText() != null) {
                    processScannedContent(result.getText());
                } else {
                    showScanErrorDialog("Invalid MediLinkChain Patient QR",
                            "Could not read QR code. Please position the code inside the frame.");
                }
            }

            @Override
            public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {}
        });
    }

    private void resumeScanner() {
        if (currentErrorDialog != null && currentErrorDialog.isShowing()) {
            currentErrorDialog.dismiss();
        }
        isProcessingScan.set(false);
        initScanner();
    }

    // =========================================================================
    // QR Content Parsing, Extraction & Validation
    // =========================================================================

    private void processScannedContent(String rawContent) {
        // Prevent duplicate scans
        if (isProcessingScan.getAndSet(true)) {
            return;
        }

        // Pause camera immediately upon detection
        if (barcodeScannerView != null) {
            barcodeScannerView.pause();
        }

        if (rawContent == null || rawContent.trim().isEmpty()) {
            showScanErrorDialog("Invalid MediLinkChain Patient QR", "The scanned QR code is empty or unreadable.");
            return;
        }

        final String scannedText = rawContent.trim();
        Log.d(TAG, "Scanned QR Code raw content: " + scannedText);

        // 1. Format validation: Check for invalid web URLs or non-MediLinkChain QRs
        if (scannedText.startsWith("http://") || scannedText.startsWith("https://") || scannedText.startsWith("www.")) {
            showScanErrorDialog("Invalid MediLinkChain Patient QR",
                    "The scanned QR code is a website URL and not a valid MediLinkChain Patient identifier.");
            return;
        }

        // 2. Extract Patient ID (e.g. from "PATIENT_ID:P26H0001" or raw "P26H0001" / "MLC...")
        String extractedPatientId = scannedText;
        if (scannedText.toUpperCase().startsWith("PATIENT_ID:")) {
            extractedPatientId = scannedText.substring("PATIENT_ID:".length()).trim();
        }

        if (extractedPatientId.isEmpty()) {
            showScanErrorDialog("Invalid MediLinkChain Patient QR",
                    "Invalid MediLinkChain QR code format. Missing patient identifier.");
            return;
        }

        final String targetId = extractedPatientId;

        // 3. Database Lookup & Doctor Authorization Verification
        lookupPatientInDatabase(targetId);
    }

    private void lookupPatientInDatabase(final String targetId) {
        // Query 1: Lookup by patientId field in 'users' collection
        db.collection("users").whereEqualTo("patientId", targetId).limit(1).get()
                .addOnSuccessListener(querySnap -> {
                    if (querySnap != null && !querySnap.isEmpty()) {
                        DocumentSnapshot patientDoc = querySnap.getDocuments().get(0);
                        verifyDoctorAuthorizationAndProceed(patientDoc);
                    } else {
                        // Query 2: Lookup by Document ID (Firebase UID)
                        db.collection("users").document(targetId).get()
                                .addOnSuccessListener(docSnap -> {
                                    if (docSnap != null && docSnap.exists()) {
                                        verifyDoctorAuthorizationAndProceed(docSnap);
                                    } else {
                                        // Query 3: Lookup by email
                                        db.collection("users").whereEqualTo("email", targetId).limit(1).get()
                                                .addOnSuccessListener(emailSnap -> {
                                                    if (emailSnap != null && !emailSnap.isEmpty()) {
                                                        DocumentSnapshot docByEmail = emailSnap.getDocuments().get(0);
                                                        verifyDoctorAuthorizationAndProceed(docByEmail);
                                                    } else {
                                                        // Patient Not Found
                                                        showScanErrorDialog("Patient not found",
                                                                "No patient record matches identifier: " + targetId);
                                                    }
                                                })
                                                .addOnFailureListener(e -> showScanErrorDialog("Database Error",
                                                        "Unable to reach the database. Please check your internet connection."));
                                    }
                                })
                                .addOnFailureListener(e -> showScanErrorDialog("Database Error",
                                        "Unable to reach the database. Please check your internet connection."));
                    }
                })
                .addOnFailureListener(e -> showScanErrorDialog("Database Error",
                        "Unable to reach the database. Please check your internet connection."));
    }

    // =========================================================================
    // Doctor Access Control & Hospital Scoping
    // =========================================================================

    private void verifyDoctorAuthorizationAndProceed(@NonNull DocumentSnapshot patientDoc) {
        FirebaseUser currentDoctorUser = auth.getCurrentUser();
        if (currentDoctorUser == null) {
            showScanErrorDialog("Unauthorized", "Doctor session expired. Please log in again.");
            return;
        }

        String patientStatus = patientDoc.getString("status");
        if ("DISABLED".equalsIgnoreCase(patientStatus)) {
            showScanErrorDialog("Patient Account Disabled",
                    "This patient's account is currently inactive or disabled.");
            return;
        }

        // Fetch Doctor's profile to verify hospital authorization
        String doctorUid = currentDoctorUser.getUid();
        db.collection("users").document(doctorUid).get()
                .addOnSuccessListener(docSnap -> {
                    String doctorRole = "doctor";
                    String doctorOrgId = sessionManager.getOrgId();
                    String doctorHospitalName = sessionManager.getHospitalName();

                    if (docSnap != null && docSnap.exists()) {
                        String r = docSnap.getString("role");
                        if (r != null && !r.isEmpty()) doctorRole = r.toLowerCase();

                        String org = docSnap.getString("organizationId");
                        if (org != null && !org.isEmpty()) doctorOrgId = org;

                        String hosp = docSnap.getString("hospitalName");
                        if (hosp != null && !hosp.isEmpty()) doctorHospitalName = hosp;
                    }

                    // Super Admins have universal emergency access
                    if ("super_admin".equalsIgnoreCase(doctorRole)) {
                        launchPatientDetails(patientDoc);
                        return;
                    }

                    // Hospital restriction check
                    String patientOrgId = patientDoc.getString("organizationId");
                    String patientHospitalName = patientDoc.getString("hospitalName");

                    boolean hasHospitalRestriction = doctorOrgId != null && !doctorOrgId.isEmpty() && !"default_org".equals(doctorOrgId);
                    boolean patientHasOrg = patientOrgId != null && !patientOrgId.isEmpty() && !"default_org".equals(patientOrgId);

                    if (hasHospitalRestriction && patientHasOrg) {
                        // Check if doctor and patient belong to the same hospital/organization
                        boolean orgMatches = doctorOrgId.equalsIgnoreCase(patientOrgId);
                        boolean nameMatches = doctorHospitalName != null && patientHospitalName != null &&
                                doctorHospitalName.equalsIgnoreCase(patientHospitalName);

                        if (!orgMatches && !nameMatches) {
                            showScanErrorDialog("Access denied for this patient.",
                                    "You are only authorized to access patients from your assigned hospital (" +
                                            (doctorHospitalName != null && !doctorHospitalName.isEmpty() ? doctorHospitalName : "Assigned Hospital") + ").");
                            return;
                        }
                    }

                    // Access Authorized
                    launchPatientDetails(patientDoc);
                })
                .addOnFailureListener(e -> {
                    // Fallback to launch if doctor doc fetch had a minor network glitch but session is valid
                    launchPatientDetails(patientDoc);
                });
    }

    private void launchPatientDetails(@NonNull DocumentSnapshot patientDoc) {
        Toast.makeText(this, "Patient QR scanned successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(DoctorScanActivity.this, PatientDetailsActivity.class);
        intent.putExtra("patientId", patientDoc.getId());
        intent.putExtra("patientCode", patientDoc.getString("patientId"));
        intent.putExtra("patientName", patientDoc.getString("fullName"));
        intent.putExtra("patientBlood", patientDoc.getString("bloodGroup"));
        intent.putExtra("patientContact", patientDoc.getString("contactNumber"));

        startActivity(intent);
        finish();
    }

    // =========================================================================
    // Error Handling & Scan Again Dialogs
    // =========================================================================

    private void showScanErrorDialog(String title, String message) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            if (currentErrorDialog != null && currentErrorDialog.isShowing()) {
                currentErrorDialog.dismiss();
            }

            currentErrorDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("Scan Again", (dialog, which) -> {
                        dialog.dismiss();
                        resumeScanner();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .show();
        });
    }

    private void showCameraPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Camera Permission Required")
                .setMessage("Camera permission is required to scan patient QR codes. Please allow camera access in Settings.")
                .setCancelable(false)
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .show();
    }

    // =========================================================================
    // UI Helpers & Laser
    // =========================================================================

    private void startLaserAnimation() {
        if (scannerLaser == null) return;
        scannerLaser.post(() -> {
            laserAnimator = ObjectAnimator.ofFloat(scannerLaser, "translationY", 0f, 600f);
            laserAnimator.setDuration(2500);
            laserAnimator.setRepeatCount(ValueAnimator.INFINITE);
            laserAnimator.setRepeatMode(ValueAnimator.REVERSE);
            laserAnimator.start();
        });
    }

    private void toggleFlashlight() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (barcodeScannerView == null) return;

        if (isFlashOn) {
            barcodeScannerView.setTorchOff();
            isFlashOn = false;
            Toast.makeText(this, "Flashlight turned off", Toast.LENGTH_SHORT).show();
        } else {
            barcodeScannerView.setTorchOn();
            isFlashOn = true;
            Toast.makeText(this, "Flashlight turned on", Toast.LENGTH_SHORT).show();
        }
    }

    private void importFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private String decodeQRFromBitmap(Bitmap bitmap) {
        try {
            int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            LuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(binaryBitmap);
            return result.getText();
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (!isProcessingScan.get() && (currentErrorDialog == null || !currentErrorDialog.isShowing())) {
                initScanner();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeScannerView != null) {
            barcodeScannerView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (laserAnimator != null) {
            laserAnimator.cancel();
        }
        if (currentErrorDialog != null && currentErrorDialog.isShowing()) {
            currentErrorDialog.dismiss();
        }
    }
}