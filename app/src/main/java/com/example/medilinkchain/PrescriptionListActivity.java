package com.example.medilinkchain;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * PrescriptionListActivity — Displays the list of stored medical prescriptions for the patient.
 */
public class PrescriptionListActivity extends AppCompatActivity implements PrescriptionAdapter.PrescriptionActionListener {

    private static final String TAG = "PrescriptionList";

    private ImageView backBtn;
    private ProgressBar progressBar;
    private LinearLayout emptyStateLayout;
    private RecyclerView recyclerView;
    private ExtendedFloatingActionButton fabAddPrescription;

    private PrescriptionAdapter adapter;
    private String currentUserId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prescription_list);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }

        initViews();
        setupRecyclerView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
        }
        loadPrescriptions();
    }

    private void initViews() {
        backBtn = findViewById(R.id.backBtn);
        progressBar = findViewById(R.id.prescriptionsProgressBar);
        emptyStateLayout = findViewById(R.id.prescriptionsEmptyState);
        recyclerView = findViewById(R.id.prescriptionsRecyclerView);
        fabAddPrescription = findViewById(R.id.fabAddPrescription);

        backBtn.setOnClickListener(v -> finish());

        fabAddPrescription.setOnClickListener(v -> {
            Intent intent = new Intent(PrescriptionListActivity.this, AddPrescriptionActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PrescriptionAdapter(this, this);
        recyclerView.setAdapter(adapter);
    }

    private void loadPrescriptions() {
        if (currentUserId.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);

        PrescriptionRepository.fetchPrescriptions(currentUserId, new PrescriptionRepository.Callback<List<PrescriptionModel>>() {
            @Override
            public void onSuccess(List<PrescriptionModel> list) {
                progressBar.setVisibility(View.GONE);
                if (list == null || list.isEmpty()) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                    adapter.setPrescriptions(new ArrayList<>());
                } else {
                    emptyStateLayout.setVisibility(View.GONE);
                    adapter.setPrescriptions(list);
                }
            }

            @Override
            public void onFailure(String error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load prescriptions: " + error);
                Toast.makeText(PrescriptionListActivity.this, "Unable to load prescriptions: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onView(PrescriptionModel item) {
        // If fileUrl is null or needs signed URL refresh
        if (item.getFileUrl() != null && !item.getFileUrl().isEmpty()) {
            openDocumentUrl(item.getFileUrl(), item.getFileType());
        } else if (item.getFilePath() != null && !item.getFilePath().isEmpty()) {
            SupabaseStorageHelper.createSignedUrl(item.getFilePath(), 604800, new SupabaseStorageHelper.SignedUrlCallback() {
                @Override
                public void onSuccess(String signedUrl) {
                    openDocumentUrl(signedUrl, item.getFileType());
                }

                @Override
                public void onFailure(String errorMessage) {
                    Toast.makeText(PrescriptionListActivity.this, "Unable to open document: " + errorMessage, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "Prescription file unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDocumentUrl(String url, String fileType) {
        try {
            Intent intent = new Intent(PrescriptionListActivity.this, DocumentViewerActivity.class);
            intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_URL, url);
            intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_NAME, "Prescription Document");
            intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_TYPE, fileType);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening in-app prescription viewer: " + e.getMessage(), e);
            Toast.makeText(this, "Unable to open document viewer.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDelete(PrescriptionModel item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Prescription")
                .setMessage("Delete this prescription?")
                .setPositiveButton("DELETE", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);
                    PrescriptionRepository.deletePrescription(item, new PrescriptionRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(PrescriptionListActivity.this, "Prescription deleted successfully.", Toast.LENGTH_SHORT).show();
                            loadPrescriptions();
                        }

                        @Override
                        public void onFailure(String error) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(PrescriptionListActivity.this, "Delete failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }
}
