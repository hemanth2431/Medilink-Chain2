package com.example.medilinkchain;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class ViewRecordsActivity extends AppCompatActivity {

    private static final String TAG = "ViewRecordsActivity";

    private RecyclerView recordsRecyclerView;
    private View emptyStateLayout;
    private Button btnUploadFirstRecord;
    private ImageView backBtn;
    private ImageView filterBtn;
    private EditText searchEditText;
    private ChipGroup categoryChipGroup;
    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabAddRecord;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration firestoreListener;

    private ArrayList<MedicalRecordModel> allRecordsList = new ArrayList<>();
    private ArrayList<TimelineItem> timelineItemList = new ArrayList<>();
    private TimelineAdapter timelineAdapter;

    private String searchQuery = "";
    private String selectedCategory = "All";
    private String activePatientId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_records);

        // Firebase Initialization
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Bind Views
        recordsRecyclerView = findViewById(R.id.recordsRecyclerView);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        btnUploadFirstRecord = findViewById(R.id.btnUploadFirstRecord);
        backBtn = findViewById(R.id.backBtn);
        filterBtn = findViewById(R.id.filterBtn);
        searchEditText = findViewById(R.id.searchEditText);
        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        fabAddRecord = findViewById(R.id.fabAddRecord);

        // Setup RecyclerView
        recordsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        timelineAdapter = new TimelineAdapter(timelineItemList);
        recordsRecyclerView.setAdapter(timelineAdapter);

        // Back button action
        backBtn.setOnClickListener(v -> finish());

        // Add Record Action
        fabAddRecord.setOnClickListener(v -> {
            startActivity(new Intent(ViewRecordsActivity.this, MedicalRecordActivity.class));
        });

        if (btnUploadFirstRecord != null) {
            btnUploadFirstRecord.setOnClickListener(v -> {
                startActivity(new Intent(ViewRecordsActivity.this, MedicalRecordActivity.class));
            });
        }

        // Filter button action
        filterBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Filters applied to timeline view.", Toast.LENGTH_SHORT).show();
        });

        // Search text watcher
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase();
                filterAndGroupRecords();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Chips listener
        categoryChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipAll) {
                selectedCategory = "All";
            } else if (checkedId == R.id.chipLab) {
                selectedCategory = "Lab Reports";
            } else if (checkedId == R.id.chipXRay) {
                selectedCategory = "X-Rays";
            } else if (checkedId == R.id.chipMedicalReport) {
                selectedCategory = "Medical Reports";
            } else if (checkedId == R.id.chipPrescriptions) {
                selectedCategory = "Prescriptions";
            } else if (checkedId == R.id.chipImaging) {
                selectedCategory = "Imaging";
            } else if (checkedId == R.id.chipOthers) {
                selectedCategory = "Other";
            }
            filterAndGroupRecords();
        });

        // Load Firestore Vitals dynamically with snapshot listener (real-time)
        setupRealtimeFirestoreListener();

        // Bottom Navigation Bar Setup (Records tab selected)
        bottomNavigationView.setSelectedItemId(R.id.nav_records);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) {
                startActivity(new Intent(ViewRecordsActivity.this, DashboardActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_records) {
                return true;
            } else if (itemId == R.id.nav_hospitals) {
                startActivity(new Intent(ViewRecordsActivity.this, HospitalActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(ViewRecordsActivity.this, ProfileActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recordsRecyclerView != null) {
            recordsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void setupRealtimeFirestoreListener() {
        if (auth.getCurrentUser() == null) {
            showEmptyState(true);
            return;
        }

        String patientId = getIntent().getStringExtra("patientId");
        if (patientId == null || patientId.trim().isEmpty()) {
            patientId = getIntent().getStringExtra("PATIENT_ID");
        }
        if (patientId == null || patientId.trim().isEmpty()) {
            if (auth.getCurrentUser() != null) {
                patientId = auth.getCurrentUser().getUid();
            }
        }

        if (patientId == null || patientId.trim().isEmpty()) {
            showEmptyState(true);
            return;
        }

        activePatientId = patientId;

        // Fetch records exclusively from subcollection: users/{uid}/medicalRecords
        firestoreListener = db.collection("users").document(patientId).collection("medicalRecords")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Failed to load database records: " + error.getMessage());
                        showEmptyState(true);
                        return;
                    }

                    allRecordsList.clear();
                    if (value != null && !value.isEmpty()) {
                        for (QueryDocumentSnapshot document : value) {
                            String type = document.getString("recordType");
                            String title = document.getString("recordTitle");
                            String date = document.getString("dateOfRecord");
                            String hospital = document.getString("hospitalClinic");

                            // Fallback checks for legacy documents
                            if (type == null) type = document.getString("bloodGroup");
                            if (title == null) title = document.getString("name");
                            if (date == null) date = document.getString("dob");
                            if (hospital == null) hospital = document.getString("contact");

                            if (type == null) type = "Other";
                            if (title == null || title.isEmpty()) {
                                title = document.getString("fileName");
                            }
                            if (title == null || title.isEmpty()) {
                                title = type;
                            }
                            if (date == null) date = "Recent";
                            if (hospital == null) hospital = "MediLink Clinical Node";
                            boolean isVerified = true;

                            allRecordsList.add(new MedicalRecordModel(
                                    title,
                                    type,
                                    date,
                                    hospital,
                                    isVerified,
                                    document.getId()
                            ));
                        }
                    }

                    if (allRecordsList.isEmpty()) {
                        showEmptyState(true);
                    } else {
                        showEmptyState(false);
                        filterAndGroupRecords();
                    }
                });
    }

    private Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("dd MMM yyyy", Locale.US).parse(dateStr);
        } catch (Exception e) {
            try {
                return new SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(dateStr);
            } catch (Exception ex) {
                return new Date(0); // Epoch fallback
            }
        }
    }

    private String getMonthYearHeader(String dateStr) {
        try {
            Date d = parseDate(dateStr);
            return new SimpleDateFormat("MMMM yyyy", Locale.US).format(d);
        } catch (Exception e) {
            return "Other Dates";
        }
    }

    private void filterAndGroupRecords() {
        ArrayList<MedicalRecordModel> filteredList = new ArrayList<>();

        for (MedicalRecordModel record : allRecordsList) {
            // Apply Search Query Filter
            boolean matchesSearch = searchQuery.isEmpty() ||
                    record.reportTitle.toLowerCase().contains(searchQuery) ||
                    record.originInfo.toLowerCase().contains(searchQuery) ||
                    record.categoryTag.toLowerCase().contains(searchQuery);

            if (!matchesSearch) continue;

            // Apply Category Chip Filter
            boolean matchesCategory = false;
            if (selectedCategory.equals("All")) {
                matchesCategory = true;
            } else if (selectedCategory.equals("Lab Reports")) {
                matchesCategory = record.categoryTag.equalsIgnoreCase("Lab Report") || record.categoryTag.equalsIgnoreCase("Lab Reports");
            } else if (selectedCategory.equals("X-Rays")) {
                matchesCategory = record.categoryTag.equalsIgnoreCase("X-Ray") || record.categoryTag.equalsIgnoreCase("X-Rays");
            } else if (selectedCategory.equals("Medical Reports")) {
                matchesCategory = record.categoryTag.equalsIgnoreCase("Medical Report") || record.categoryTag.equalsIgnoreCase("Medical Reports");
            } else if (selectedCategory.equals("Prescriptions")) {
                matchesCategory = record.categoryTag.equalsIgnoreCase("Prescription") || record.categoryTag.equalsIgnoreCase("Prescriptions");
            } else if (selectedCategory.equals("Imaging")) {
                matchesCategory = record.categoryTag.equalsIgnoreCase("Scan / Imaging") || record.categoryTag.equalsIgnoreCase("Imaging");
            } else if (selectedCategory.equals("Other")) {
                matchesCategory = record.categoryTag.equalsIgnoreCase("Other") || record.categoryTag.equalsIgnoreCase("Others");
            }

            if (matchesCategory) {
                filteredList.add(record);
            }
        }

        if (filteredList.isEmpty()) {
            timelineItemList.clear();
            timelineAdapter.notifyDataSetChanged();
            showEmptyState(true);
            return;
        }

        showEmptyState(false);

        // Sort by Date (newest first)
        Collections.sort(filteredList, (r1, r2) -> parseDate(r2.reportDate).compareTo(parseDate(r1.reportDate)));

        // Group by Month & Year to build the Timeline
        timelineItemList.clear();
        String currentMonthHeader = "";

        for (int i = 0; i < filteredList.size(); i++) {
            MedicalRecordModel record = filteredList.get(i);
            String monthHeader = getMonthYearHeader(record.reportDate);

            if (!monthHeader.equals(currentMonthHeader)) {
                currentMonthHeader = monthHeader;
                timelineItemList.add(new TimelineItem(TimelineItem.VIEW_TYPE_HEADER, currentMonthHeader, null));
            }
            timelineItemList.add(new TimelineItem(TimelineItem.VIEW_TYPE_RECORD, null, record));
        }

        timelineAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
    }

    // --- REPRESENTATION MODELS FOR ADAPTER ---

    public static class MedicalRecordModel {
        public String reportTitle;
        public String categoryTag;
        public String reportDate;
        public String originInfo;
        public boolean isVerified;
        public String documentId;

        public MedicalRecordModel(String reportTitle, String categoryTag, String reportDate, String originInfo, boolean isVerified, String documentId) {
            this.reportTitle = reportTitle;
            this.categoryTag = categoryTag;
            this.reportDate = reportDate;
            this.originInfo = originInfo;
            this.isVerified = isVerified;
            this.documentId = documentId;
        }
    }

    public static class TimelineItem {
        public static final int VIEW_TYPE_HEADER = 0;
        public static final int VIEW_TYPE_RECORD = 1;

        public int type;
        public String headerTitle;
        public MedicalRecordModel recordModel;

        public TimelineItem(int type, String headerTitle, MedicalRecordModel recordModel) {
            this.type = type;
            this.headerTitle = headerTitle;
            this.recordModel = recordModel;
        }
    }

    // --- TIMELINE ADAPTER ---

    public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final ArrayList<TimelineItem> list;

        public TimelineAdapter(ArrayList<TimelineItem> list) {
            this.list = list;
        }

        @Override
        public int getItemViewType(int position) {
            return list.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TimelineItem.VIEW_TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_record_header, parent, false);
                return new HeaderViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medical_record, parent, false);
                return new RecordViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TimelineItem item = list.get(position);

            if (getItemViewType(position) == TimelineItem.VIEW_TYPE_HEADER) {
                HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
                headerHolder.monthHeaderText.setText(item.headerTitle);
            } else {
                RecordViewHolder recordHolder = (RecordViewHolder) holder;
                MedicalRecordModel model = item.recordModel;

                recordHolder.reportTitleText.setText(model.reportTitle);

                // Appropriate category emoji/text icons
                String displayTag = model.categoryTag;
                if ("Lab Report".equalsIgnoreCase(displayTag) || "Lab Reports".equalsIgnoreCase(displayTag)) {
                    displayTag = "📄 Lab Report";
                } else if ("X-Ray".equalsIgnoreCase(displayTag) || "X-Rays".equalsIgnoreCase(displayTag)) {
                    displayTag = "🩻 X-Ray";
                } else if ("Medical Report".equalsIgnoreCase(displayTag) || "Medical Reports".equalsIgnoreCase(displayTag)) {
                    displayTag = "📋 Medical Report";
                } else if ("Prescription".equalsIgnoreCase(displayTag) || "Prescriptions".equalsIgnoreCase(displayTag)) {
                    displayTag = "💊 Prescription";
                } else if ("Scan / Imaging".equalsIgnoreCase(displayTag) || "Imaging".equalsIgnoreCase(displayTag)) {
                    displayTag = "📸 Imaging";
                } else {
                    displayTag = "📁 " + displayTag;
                }

                recordHolder.categoryTagText.setText(displayTag);
                recordHolder.reportOriginText.setText(model.originInfo);
                recordHolder.reportDateText.setText(model.reportDate);

                // Show or hide verification badge
                if (model.isVerified) {
                    recordHolder.verifiedBadgeCard.setVisibility(View.VISIBLE);
                } else {
                    recordHolder.verifiedBadgeCard.setVisibility(View.GONE);
                }

                // Adjust timeline line top/bottom visibility depending on position
                recordHolder.timelineLine.setVisibility(View.VISIBLE);

                // Click navigates to RecordDetailsActivity with correct RECORD_ID and PATIENT_ID
                recordHolder.itemView.setOnClickListener(v -> {
                    Intent detailsIntent = new Intent(ViewRecordsActivity.this, RecordDetailsActivity.class);
                    detailsIntent.putExtra("RECORD_ID", model.documentId);
                    String pid = activePatientId;
                    if (pid == null || pid.trim().isEmpty()) {
                        if (auth.getCurrentUser() != null) {
                            pid = auth.getCurrentUser().getUid();
                        }
                    }
                    detailsIntent.putExtra("PATIENT_ID", pid);
                    detailsIntent.putExtra("patientId", pid);
                    startActivity(detailsIntent);
                });
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView monthHeaderText;

            public HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                monthHeaderText = itemView.findViewById(R.id.monthHeaderText);
            }
        }

        public class RecordViewHolder extends RecyclerView.ViewHolder {
            TextView reportTitleText, categoryTagText, reportOriginText, reportDateText;
            com.google.android.material.card.MaterialCardView verifiedBadgeCard;
            View timelineLine;

            public RecordViewHolder(@NonNull View itemView) {
                super(itemView);
                reportTitleText = itemView.findViewById(R.id.reportTitleText);
                categoryTagText = itemView.findViewById(R.id.categoryTagText);
                reportOriginText = itemView.findViewById(R.id.reportOriginText);
                reportDateText = itemView.findViewById(R.id.reportDateText);
                verifiedBadgeCard = itemView.findViewById(R.id.verifiedBadgeCard);
                timelineLine = itemView.findViewById(R.id.timelineLine);
            }
        }
    }
}
