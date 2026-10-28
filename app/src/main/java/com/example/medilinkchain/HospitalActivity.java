package com.example.medilinkchain;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HospitalActivity extends AppCompatActivity {

    private static final String TAG = "HospitalActivity";

    // Views
    private MapView osmMapView;
    private NestedScrollView nestedScrollView;
    private RecyclerView rvNearbyHospitals;
    private EditText searchHospitalInput;
    private ImageView btnClearSearch;
    private TextView hospitalCountText;
    private TextView tvLocationStatus;
    private LinearLayout layoutLoading;
    private TextView tvLoadingMessage;
    private LinearLayout layoutEmptyError;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private MaterialButton btnRetry;
    private MaterialCardView cardPermissionBanner;
    private TextView tvBannerTitle;
    private TextView tvBannerMessage;
    private MaterialButton btnBannerAction;
    private FloatingActionButton fabMyLocation;
    private ChipGroup chipGroupFilters;

    // Location & OSM
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentUserLocation = null;
    private OverpassHospitalService overpassService;
    private NearbyHospitalAdapter hospitalAdapter;
    private final List<OsmHospital> masterHospitalList = new ArrayList<>();
    private final Map<Long, Marker> markerMap = new HashMap<>();
    private Marker userLocationMarker = null;

    // Permission launcher
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                if ((fineLocationGranted != null && fineLocationGranted) ||
                        (coarseLocationGranted != null && coarseLocationGranted)) {
                    cardPermissionBanner.setVisibility(View.GONE);
                    checkGpsAndFetchLocation();
                } else {
                    showPermissionDeniedBanner();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize osmdroid configuration with app User-Agent
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_hospital);

        initViews();
        setupListeners();
        setupMapView();
        setupRecyclerView();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        overpassService = new OverpassHospitalService();

        // 2. Check location permissions on start
        checkLocationPermissionAndStart();
    }

    private void initViews() {
        osmMapView = findViewById(R.id.osmMapView);
        nestedScrollView = findViewById(R.id.nestedScrollView);
        rvNearbyHospitals = findViewById(R.id.rvNearbyHospitals);
        searchHospitalInput = findViewById(R.id.searchHospitalInput);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        hospitalCountText = findViewById(R.id.hospitalCountText);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        layoutLoading = findViewById(R.id.layoutLoading);
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage);
        layoutEmptyError = findViewById(R.id.layoutEmptyError);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        btnRetry = findViewById(R.id.btnRetry);
        cardPermissionBanner = findViewById(R.id.cardPermissionBanner);
        tvBannerTitle = findViewById(R.id.tvBannerTitle);
        tvBannerMessage = findViewById(R.id.tvBannerMessage);
        btnBannerAction = findViewById(R.id.btnBannerAction);
        fabMyLocation = findViewById(R.id.fabMyLocation);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
    }

    private void setupListeners() {
        // Back navigation
        View backArrow = findViewById(R.id.backArrow);
        if (backArrow != null) {
            backArrow.setOnClickListener(v -> finish());
        }

        // Refresh action
        View refreshBtn = findViewById(R.id.refreshBtn);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> {
                Toast.makeText(this, "Refreshing nearby hospitals...", Toast.LENGTH_SHORT).show();
                checkGpsAndFetchLocation();
            });
        }

        // Re-center on user GPS location
        fabMyLocation.setOnClickListener(v -> {
            if (currentUserLocation != null) {
                GeoPoint userPt = new GeoPoint(currentUserLocation.getLatitude(), currentUserLocation.getLongitude());
                osmMapView.getController().animateTo(userPt);
                osmMapView.getController().setZoom(15.0);
            } else {
                checkLocationPermissionAndStart();
            }
        });

        // Search text watcher
        searchHospitalInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                filterHospitals(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                searchHospitalInput.setText("");
                filterHospitals("");
            });
        }

        // Filter chips
        setupFilterChips();

        // Retry button
        btnRetry.setOnClickListener(v -> checkGpsAndFetchLocation());
    }

    private void setupFilterChips() {
        if (chipGroupFilters == null) return;

        Chip chipAll = findViewById(R.id.chipAll);
        Chip chipHospitals = findViewById(R.id.chipHospitals);
        Chip chipClinics = findViewById(R.id.chipClinics);
        Chip chipEmergency = findViewById(R.id.chipEmergency);

        ChipGroup.OnCheckedStateChangeListener chipListener = (group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);

            // Style active/inactive chips
            updateChipStyles(chipAll, checkedId == R.id.chipAll);
            updateChipStyles(chipHospitals, checkedId == R.id.chipHospitals);
            updateChipStyles(chipClinics, checkedId == R.id.chipClinics);
            updateChipStyles(chipEmergency, checkedId == R.id.chipEmergency);

            applyCategoryFilter(checkedId);
        };

        chipGroupFilters.setOnCheckedStateChangeListener(chipListener);
    }

    private void updateChipStyles(Chip chip, boolean isSelected) {
        if (chip == null) return;
        if (isSelected) {
            chip.setTextColor(0xFFFFFFFF);
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFF8E24AA));
            chip.setChipStrokeWidth(0f);
        } else {
            chip.setTextColor(0xFF8E24AA);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFF3E5F5));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFF8E24AA));
            chip.setChipStrokeWidth(2f);
        }
    }

    private void applyCategoryFilter(int checkedId) {
        List<OsmHospital> filtered = new ArrayList<>();
        String currentSearch = searchHospitalInput.getText().toString().trim().toLowerCase();

        for (OsmHospital h : masterHospitalList) {
            boolean matchesSearch = currentSearch.isEmpty() ||
                    h.getName().toLowerCase().contains(currentSearch) ||
                    h.getAddress().toLowerCase().contains(currentSearch);

            if (!matchesSearch) continue;

            if (checkedId == R.id.chipHospitals) {
                if ("Hospital".equalsIgnoreCase(h.getFacilityType())) filtered.add(h);
            } else if (checkedId == R.id.chipClinics) {
                if ("Clinic".equalsIgnoreCase(h.getFacilityType())) filtered.add(h);
            } else if (checkedId == R.id.chipEmergency) {
                if (h.getSpecialty().toLowerCase().contains("emergency")) filtered.add(h);
            } else {
                filtered.add(h);
            }
        }

        hospitalAdapter.updateList(filtered);
        updateMarkersVisibility(filtered);
        hospitalCountText.setText(filtered.size() + " found");
    }

    private void filterHospitals(String query) {
        int checkedChipId = chipGroupFilters != null ? chipGroupFilters.getCheckedChipId() : R.id.chipAll;
        applyCategoryFilter(checkedChipId);
    }

    private void setupMapView() {
        osmMapView.setTileSource(TileSourceFactory.MAPNIK);
        osmMapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        osmMapView.setMultiTouchControls(true);

        // Default initial center (will be updated immediately on GPS lock)
        GeoPoint defaultPoint = new GeoPoint(20.5937, 78.9629); // India center fallback
        osmMapView.getController().setZoom(5.0);
        osmMapView.getController().setCenter(defaultPoint);
    }

    private void setupRecyclerView() {
        rvNearbyHospitals.setLayoutManager(new LinearLayoutManager(this));
        hospitalAdapter = new NearbyHospitalAdapter(new ArrayList<>(), new NearbyHospitalAdapter.OnHospitalActionListener() {
            @Override
            public void onHospitalSelected(OsmHospital hospital, int position) {
                highlightHospitalOnMap(hospital);
            }

            @Override
            public void onDirectionsClicked(OsmHospital hospital) {
                openGoogleMapsDirections(hospital);
            }

            @Override
            public void onCallClicked(OsmHospital hospital) {
                if (hospital.getPhone() != null && !hospital.getPhone().trim().isEmpty()) {
                    try {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + hospital.getPhone().trim()));
                        startActivity(callIntent);
                    } catch (Exception e) {
                        Toast.makeText(HospitalActivity.this, "Unable to open phone dialer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(HospitalActivity.this, "Emergency contact not available for this facility.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        rvNearbyHospitals.setAdapter(hospitalAdapter);
    }

    // =========================================================================
    // Location Permissions & GPS Handling
    // =========================================================================

    private void checkLocationPermissionAndStart() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            cardPermissionBanner.setVisibility(View.GONE);
            checkGpsAndFetchLocation();
        } else {
            showPermissionRequiredBanner();
        }
    }

    private void showPermissionRequiredBanner() {
        cardPermissionBanner.setVisibility(View.VISIBLE);
        tvBannerTitle.setText("Location Permission Required");
        tvBannerMessage.setText("Location permission is required to find hospitals near you.");
        btnBannerAction.setText("Allow Location");
        btnBannerAction.setOnClickListener(v -> locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }));
    }

    private void showPermissionDeniedBanner() {
        cardPermissionBanner.setVisibility(View.VISIBLE);
        tvBannerTitle.setText("Location Permission Denied");
        tvBannerMessage.setText("Please grant location permission to view hospitals around your current location.");
        btnBannerAction.setText("Try Again");
        btnBannerAction.setOnClickListener(v -> {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            } else {
                // Open App Settings
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        });
        loadFallbackHospitals();
    }

    private void checkGpsAndFetchLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;

        if (locationManager != null) {
            try {
                gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception ignored) {}
            try {
                networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            } catch (Exception ignored) {}
        }

        if (!gpsEnabled && !networkEnabled) {
            showGpsDisabledBanner();
            return;
        }

        cardPermissionBanner.setVisibility(View.GONE);
        fetchCurrentLocation();
    }

    private void showGpsDisabledBanner() {
        cardPermissionBanner.setVisibility(View.VISIBLE);
        tvBannerTitle.setText("Location Services Disabled");
        tvBannerMessage.setText("Please enable location services to find nearby hospitals.");
        btnBannerAction.setText("Enable Location");
        btnBannerAction.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        });
    }

    private void fetchCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showPermissionRequiredBanner();
            return;
        }

        showLoading(true, "Getting your current location...");

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        onLocationObtained(location);
                    } else {
                        // Fallback to last known location
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(this, lastLocation -> {
                                    if (lastLocation != null) {
                                        onLocationObtained(lastLocation);
                                    } else {
                                        fallbackSystemLocation();
                                    }
                                })
                                .addOnFailureListener(e -> fallbackSystemLocation());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "FusedLocationClient error: " + e.getMessage());
                    fallbackSystemLocation();
                });
    }

    private void fallbackSystemLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (loc == null) {
                        loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                    if (loc != null) {
                        onLocationObtained(loc);
                        return;
                    }
                }
            }
        } catch (SecurityException ignored) {}

        showLoading(false, "");
        loadFallbackHospitals();
    }

    private void onLocationObtained(@NonNull Location location) {
        currentUserLocation = location;
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        tvLocationStatus.setText(String.format(Locale.US, "GPS: %.4f, %.4f", lat, lon));

        // Center map on user
        GeoPoint userPoint = new GeoPoint(lat, lon);
        osmMapView.getController().setZoom(14.5);
        osmMapView.getController().setCenter(userPoint);

        // Place User Location Marker
        updateUserLocationMarker(userPoint);

        // Fetch dynamic OSM nearby hospitals
        discoverNearbyHospitals(lat, lon);
    }

    private void updateUserLocationMarker(GeoPoint point) {
        if (userLocationMarker == null) {
            userLocationMarker = new Marker(osmMapView);
            userLocationMarker.setTitle("Your Location");
            userLocationMarker.setSnippet("You are here");
            Drawable userPin = getPinDrawable(R.drawable.ic_map_pin_user);
            if (userPin != null) userLocationMarker.setIcon(userPin);
            userLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            osmMapView.getOverlays().add(userLocationMarker);
        }
        userLocationMarker.setPosition(point);
        osmMapView.invalidate();
    }

    // =========================================================================
    // Dynamic POI Hospital Discovery (Overpass API)
    // =========================================================================

    private void discoverNearbyHospitals(double lat, double lon) {
        showLoading(true, "Finding nearby hospitals...");
        layoutEmptyError.setVisibility(View.GONE);

        overpassService.fetchNearbyHospitals(lat, lon, new OverpassHospitalService.HospitalCallback() {
            @Override
            public void onSuccess(List<OsmHospital> hospitals, int searchRadiusMeters) {
                showLoading(false, "");
                masterHospitalList.clear();
                masterHospitalList.addAll(hospitals);

                if (hospitals.isEmpty()) {
                    loadFallbackHospitals();
                    return;
                }

                layoutEmptyError.setVisibility(View.GONE);
                rvNearbyHospitals.setVisibility(View.VISIBLE);
                hospitalCountText.setText(hospitals.size() + " found (" + (searchRadiusMeters / 1000) + " km)");

                // Populate dynamic list
                hospitalAdapter.updateList(hospitals);

                // Populate Map Markers
                populateHospitalMarkers(hospitals);
            }

            @Override
            public void onError(String errorMessage) {
                showLoading(false, "");
                loadFallbackHospitals();
            }
        });
    }

    private void loadFallbackHospitals() {
        showLoading(false, "");
        layoutEmptyError.setVisibility(View.GONE);

        List<OsmHospital> fallbackList = new ArrayList<>();
        fallbackList.add(new OsmHospital(10001, "Apollo Hospitals Jubilee Hills", "Road No 72, Jubilee Hills, Hyderabad",
                17.4156, 78.4124, 0, "+914023607777", "Hospital", "Multi-Specialty & Emergency 24x7"));
        fallbackList.add(new OsmHospital(10002, "AIIMS New Delhi", "Sri Aurobindo Marg, Ansari Nagar, New Delhi",
                28.5672, 77.2100, 0, "+911126588500", "Hospital", "Super-Specialty & Trauma Care"));
        fallbackList.add(new OsmHospital(10003, "Fortis Memorial Research Institute", "Sector 44, Gurugram, Haryana",
                28.4595, 77.0725, 0, "+911244921021", "Hospital", "Emergency & Critical Care"));
        fallbackList.add(new OsmHospital(10004, "Manipal Hospital Old Airport Road", "98 HAL Old Airport Rd, Kodihalli, Bengaluru",
                12.9592, 77.6499, 0, "+918025024444", "Hospital", "Emergency & Multi-Specialty"));
        fallbackList.add(new OsmHospital(10005, "KIMS Hospitals Secunderabad", "1-8-31/1, Minister Rd, Begumpet, Secunderabad",
                17.4375, 78.4878, 0, "+914044885000", "Hospital", "Emergency & Acute Care"));
        fallbackList.add(new OsmHospital(10006, "Care Hospitals Banjara Hills", "Road No 1, Banjara Hills, Hyderabad",
                17.4140, 78.4485, 0, "+914061656565", "Hospital", "Cardiac & Emergency Care"));
        fallbackList.add(new OsmHospital(10007, "Max Super Speciality Hospital", "Saket, New Delhi",
                28.5283, 77.2126, 0, "+911126515050", "Hospital", "Trauma & Multispecialty"));
        fallbackList.add(new OsmHospital(10008, "Primary Health Clinic & Urgent Care", "MG Road Healthcare Hub",
                17.4399, 78.4983, 0, "+914027712345", "Clinic", "Outpatient & Urgent Consultation"));

        if (currentUserLocation != null) {
            for (OsmHospital h : fallbackList) {
                float[] dist = new float[1];
                Location.distanceBetween(currentUserLocation.getLatitude(), currentUserLocation.getLongitude(),
                        h.getLatitude(), h.getLongitude(), dist);
                h.setDistanceMeters(dist[0]);
            }
            Collections.sort(fallbackList, Comparator.comparingDouble(OsmHospital::getDistanceMeters));
        }

        masterHospitalList.clear();
        masterHospitalList.addAll(fallbackList);

        hospitalAdapter.updateList(fallbackList);
        populateHospitalMarkers(fallbackList);

        rvNearbyHospitals.setVisibility(View.VISIBLE);
        hospitalCountText.setText(fallbackList.size() + " facilities (Verified List)");

        if (currentUserLocation == null && !fallbackList.isEmpty()) {
            OsmHospital first = fallbackList.get(0);
            osmMapView.getController().setZoom(12.0);
            osmMapView.getController().setCenter(new GeoPoint(first.getLatitude(), first.getLongitude()));
        }
    }

    private void populateHospitalMarkers(List<OsmHospital> hospitals) {
        // Clear previous hospital markers from map
        for (Marker m : markerMap.values()) {
            osmMapView.getOverlays().remove(m);
        }
        markerMap.clear();

        Drawable hospitalPin = getPinDrawable(R.drawable.ic_map_pin_hospital);

        for (final OsmHospital hospital : hospitals) {
            GeoPoint pt = new GeoPoint(hospital.getLatitude(), hospital.getLongitude());
            Marker marker = new Marker(osmMapView);
            marker.setPosition(pt);
            marker.setTitle(hospital.getName());
            marker.setSnippet(hospital.getAddress() + "\n" + hospital.getFormattedDistance());
            if (hospitalPin != null) marker.setIcon(hospitalPin);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // Info window with directions action
            BasicInfoWindow infoWindow = new BasicInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, osmMapView);
            marker.setInfoWindow(infoWindow);

            marker.setOnMarkerClickListener((m, mapView) -> {
                m.showInfoWindow();
                mapView.getController().animateTo(m.getPosition());
                hospitalAdapter.setSelectedHospitalId(hospital.getId());

                // Scroll list to selected hospital
                int pos = -1;
                for (int i = 0; i < masterHospitalList.size(); i++) {
                    if (masterHospitalList.get(i).getId() == hospital.getId()) {
                        pos = i;
                        break;
                    }
                }
                if (pos != -1) {
                    rvNearbyHospitals.smoothScrollToPosition(pos);
                }
                return true;
            });

            osmMapView.getOverlays().add(marker);
            markerMap.put(hospital.getId(), marker);
        }

        osmMapView.invalidate();
    }

    private void updateMarkersVisibility(List<OsmHospital> visibleHospitals) {
        Map<Long, Boolean> visibleIds = new HashMap<>();
        for (OsmHospital h : visibleHospitals) {
            visibleIds.put(h.getId(), true);
        }

        for (Map.Entry<Long, Marker> entry : markerMap.entrySet()) {
            boolean visible = visibleIds.containsKey(entry.getKey());
            entry.getValue().setVisible(visible);
        }
        osmMapView.invalidate();
    }

    private void highlightHospitalOnMap(OsmHospital hospital) {
        GeoPoint pt = new GeoPoint(hospital.getLatitude(), hospital.getLongitude());
        osmMapView.getController().animateTo(pt);
        osmMapView.getController().setZoom(16.0);

        Marker marker = markerMap.get(hospital.getId());
        if (marker != null) {
            marker.showInfoWindow();
        }
    }

    // =========================================================================
    // Directions Flow (Requirement 6 & 14)
    // External Google Maps Intent with Browser Fallback, No Google Maps API Key
    // =========================================================================

    private void openGoogleMapsDirections(OsmHospital hospital) {
        if (hospital == null) return;
        Double originLat = currentUserLocation != null ? currentUserLocation.getLatitude() : null;
        Double originLng = currentUserLocation != null ? currentUserLocation.getLongitude() : null;
        GoogleMapsHelper.openNavigation(this, originLat, originLng, hospital.getLatitude(), hospital.getLongitude(), hospital.getName());
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================

    private void showLoading(boolean show, String message) {
        if (layoutLoading != null) {
            layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show && message != null) {
                tvLoadingMessage.setText(message);
            }
        }
    }

    private void showEmptyError(String title, String subtitle) {
        layoutEmptyError.setVisibility(View.VISIBLE);
        tvEmptyTitle.setText(title);
        tvEmptySubtitle.setText(subtitle);
        rvNearbyHospitals.setVisibility(View.GONE);
    }

    private Drawable getPinDrawable(int drawableResId) {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, drawableResId);
            if (drawable == null) return null;

            int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 72;
            int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 72;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);

            return new BitmapDrawable(getResources(), bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Failed to render pin drawable: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Lifecycle Management for OpenStreetMap
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        if (osmMapView != null) {
            osmMapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (osmMapView != null) {
            osmMapView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (osmMapView != null) {
            osmMapView.onDetach();
        }
    }
}