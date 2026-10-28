package com.example.medilinkchain;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OverpassHospitalService {

    private static final String TAG = "OverpassHospitalService";
    private static final String USER_AGENT = "MediLinkChain-Android-App/1.0 (healthcare-discovery; https://github.com/hemanth2431/MediLinkChain)";

    // Overpass API endpoints with automatic failover
    private static final String[] OVERPASS_ENDPOINTS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    };

    private static final int INITIAL_RADIUS_METERS = 5000;   // 5 km
    private static final int EXPANDED_RADIUS_METERS = 12000; // 12 km
    private static final int MAX_RADIUS_METERS = 25000;      // 25 km

    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    // Simple cache to prevent redundant requests (5-minute TTL)
    private static double cachedLat = 0;
    private static double cachedLon = 0;
    private static long cacheTimestamp = 0;
    private static List<OsmHospital> cachedHospitals = null;

    public interface HospitalCallback {
        void onSuccess(List<OsmHospital> hospitals, int searchRadiusMeters);
        void onError(String errorMessage);
    }

    public OverpassHospitalService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Fetch nearby hospitals around the user's GPS coordinates with automatic radius expansion.
     */
    public void fetchNearbyHospitals(final double userLat, final double userLon, final HospitalCallback callback) {
        // Check cache if user has not moved far (> 200m) and cache is fresh (< 2 mins)
        long now = System.currentTimeMillis();
        if (cachedHospitals != null && (now - cacheTimestamp < 120000)) {
            float[] dist = new float[1];
            Location.distanceBetween(userLat, userLon, cachedLat, cachedLon, dist);
            if (dist[0] < 200) {
                // Update distances relative to new exact position
                List<OsmHospital> updated = new ArrayList<>(cachedHospitals);
                for (OsmHospital h : updated) {
                    float[] d = new float[1];
                    Location.distanceBetween(userLat, userLon, h.getLatitude(), h.getLongitude(), d);
                    h.setDistanceMeters(d[0]);
                }
                Collections.sort(updated, Comparator.comparingDouble(OsmHospital::getDistanceMeters));
                callback.onSuccess(updated, INITIAL_RADIUS_METERS);
                return;
            }
        }

        // Start search at 5km radius
        searchWithRadius(userLat, userLon, INITIAL_RADIUS_METERS, 0, new HospitalCallback() {
            @Override
            public void onSuccess(List<OsmHospital> hospitals, int radius) {
                if (hospitals.isEmpty() && radius < EXPANDED_RADIUS_METERS) {
                    // Try expanding radius to 12 km
                    searchWithRadius(userLat, userLon, EXPANDED_RADIUS_METERS, 0, new HospitalCallback() {
                        @Override
                        public void onSuccess(List<OsmHospital> expandedHospitals, int r2) {
                            if (expandedHospitals.isEmpty() && r2 < MAX_RADIUS_METERS) {
                                // Try expanding to 25 km
                                searchWithRadius(userLat, userLon, MAX_RADIUS_METERS, 0, callback);
                            } else {
                                postSuccess(callback, expandedHospitals, r2, userLat, userLon);
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            postError(callback, errorMessage);
                        }
                    });
                } else {
                    postSuccess(callback, hospitals, radius, userLat, userLon);
                }
            }

            @Override
            public void onError(String errorMessage) {
                postError(callback, errorMessage);
            }
        });
    }

    private void postSuccess(HospitalCallback callback, List<OsmHospital> hospitals, int radius, double lat, double lon) {
        cachedLat = lat;
        cachedLon = lon;
        cacheTimestamp = System.currentTimeMillis();
        cachedHospitals = new ArrayList<>(hospitals);
        mainHandler.post(() -> callback.onSuccess(hospitals, radius));
    }

    private void postError(HospitalCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    private void searchWithRadius(final double lat, final double lon, final int radiusMeters, final int endpointIndex, final HospitalCallback callback) {
        if (endpointIndex >= OVERPASS_ENDPOINTS.length) {
            postError(callback, "Unable to reach hospital discovery service. Please check your connection.");
            return;
        }

        String endpoint = OVERPASS_ENDPOINTS[endpointIndex];

        // Overpass QL to query hospitals, clinics, and emergency healthcare facilities
        String overpassQuery = String.format(Locale.US,
                "[out:json][timeout:25];" +
                "(" +
                "  node[\"amenity\"=\"hospital\"](around:%d,%.6f,%.6f);" +
                "  way[\"amenity\"=\"hospital\"](around:%d,%.6f,%.6f);" +
                "  relation[\"amenity\"=\"hospital\"](around:%d,%.6f,%.6f);" +
                "  node[\"amenity\"=\"clinic\"](around:%d,%.6f,%.6f);" +
                "  way[\"amenity\"=\"clinic\"](around:%d,%.6f,%.6f);" +
                "  node[\"healthcare\"=\"hospital\"](around:%d,%.6f,%.6f);" +
                "  way[\"healthcare\"=\"hospital\"](around:%d,%.6f,%.6f);" +
                ");" +
                "out center tags;",
                radiusMeters, lat, lon,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon,
                radiusMeters, lat, lon
        );

        okhttp3.HttpUrl parsedUrl = okhttp3.HttpUrl.parse(endpoint);
        if (parsedUrl == null) {
            searchWithRadius(lat, lon, radiusMeters, endpointIndex + 1, callback);
            return;
        }

        okhttp3.HttpUrl url = parsedUrl.newBuilder()
                .addQueryParameter("data", overpassQuery)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Overpass endpoint " + endpoint + " failed: " + e.getMessage());
                // Failover to next mirror endpoint
                searchWithRadius(lat, lon, radiusMeters, endpointIndex + 1, callback);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "Overpass endpoint returned code " + response.code());
                    response.close();
                    searchWithRadius(lat, lon, radiusMeters, endpointIndex + 1, callback);
                    return;
                }

                try {
                    String jsonResponse = response.body().string();
                    List<OsmHospital> results = parseOverpassJson(jsonResponse, lat, lon);
                    callback.onSuccess(results, radiusMeters);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse Overpass response: " + e.getMessage(), e);
                    searchWithRadius(lat, lon, radiusMeters, endpointIndex + 1, callback);
                }
            }
        });
    }

    private List<OsmHospital> parseOverpassJson(String json, double userLat, double userLon) {
        List<OsmHospital> hospitalList = new ArrayList<>();
        Map<String, Boolean> seenPositions = new HashMap<>();

        try {
            JSONObject root = new JSONObject(json);
            if (!root.has("elements")) {
                return hospitalList;
            }

            JSONArray elements = root.getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject elem = elements.getJSONObject(i);
                long id = elem.optLong("id", i);

                double lat = 0;
                double lon = 0;

                if (elem.has("lat") && elem.has("lon")) {
                    lat = elem.getDouble("lat");
                    lon = elem.getDouble("lon");
                } else if (elem.has("center")) {
                    JSONObject center = elem.getJSONObject("center");
                    lat = center.getDouble("lat");
                    lon = center.getDouble("lon");
                } else {
                    continue;
                }

                // Prevent duplicate elements that share identical coordinate rounding
                String coordKey = String.format(Locale.US, "%.5f_%.5f", lat, lon);
                if (seenPositions.containsKey(coordKey)) {
                    continue;
                }
                seenPositions.put(coordKey, true);

                JSONObject tags = elem.optJSONObject("tags");
                String name = "";
                String street = "";
                String suburb = "";
                String city = "";
                String fullAddr = "";
                String phone = "";
                String amenity = "hospital";
                String healthcare = "";
                String emergency = "";

                if (tags != null) {
                    if (tags.has("name")) {
                        name = tags.getString("name");
                    } else if (tags.has("name:en")) {
                        name = tags.getString("name:en");
                    } else if (tags.has("official_name")) {
                        name = tags.getString("official_name");
                    } else if (tags.has("operator")) {
                        name = tags.getString("operator") + " Hospital";
                    }

                    if (tags.has("addr:full")) fullAddr = tags.getString("addr:full");
                    if (tags.has("addr:street")) street = tags.getString("addr:street");
                    if (tags.has("addr:suburb")) suburb = tags.getString("addr:suburb");
                    if (tags.has("addr:city")) city = tags.getString("addr:city");

                    if (tags.has("phone")) phone = tags.getString("phone");
                    else if (tags.has("contact:phone")) phone = tags.getString("contact:phone");
                    else if (tags.has("emergency:phone")) phone = tags.getString("emergency:phone");

                    if (tags.has("amenity")) amenity = tags.getString("amenity");
                    if (tags.has("healthcare")) healthcare = tags.getString("healthcare");
                    if (tags.has("emergency")) emergency = tags.getString("emergency");
                }

                // Build a friendly name if not present
                if (name == null || name.trim().isEmpty()) {
                    if ("clinic".equalsIgnoreCase(amenity)) {
                        name = "Local Medical Clinic";
                    } else {
                        name = "Medical Care Facility";
                    }
                }

                // Build formatted address
                StringBuilder addrBuilder = new StringBuilder();
                if (!fullAddr.isEmpty()) {
                    addrBuilder.append(fullAddr);
                } else {
                    if (!street.isEmpty()) addrBuilder.append(street);
                    if (!suburb.isEmpty()) {
                        if (addrBuilder.length() > 0) addrBuilder.append(", ");
                        addrBuilder.append(suburb);
                    }
                    if (!city.isEmpty()) {
                        if (addrBuilder.length() > 0) addrBuilder.append(", ");
                        addrBuilder.append(city);
                    }
                }

                String address = addrBuilder.toString();
                if (address.isEmpty()) {
                    address = String.format(Locale.US, "Lat: %.4f, Lon: %.4f", lat, lon);
                }

                // Calculate distance from user
                float[] distResults = new float[1];
                Location.distanceBetween(userLat, userLon, lat, lon, distResults);
                float distanceMeters = distResults[0];

                String facilityType = "hospital".equalsIgnoreCase(amenity) ? "Hospital" : "Clinic";
                String specialty = "yes".equalsIgnoreCase(emergency) ? "Emergency Care" : "General & Specialist";

                OsmHospital hospital = new OsmHospital(
                        id,
                        name,
                        address,
                        lat,
                        lon,
                        distanceMeters,
                        phone,
                        facilityType,
                        specialty
                );

                hospitalList.add(hospital);
            }

            // Sort nearest-first
            Collections.sort(hospitalList, Comparator.comparingDouble(OsmHospital::getDistanceMeters));

        } catch (Exception e) {
            Log.e(TAG, "Error while parsing Overpass response: " + e.getMessage());
        }

        return hospitalList;
    }
}
