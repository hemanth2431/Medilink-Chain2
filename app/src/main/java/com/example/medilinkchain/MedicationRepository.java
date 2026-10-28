package com.example.medilinkchain;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MedicationRepository — Handles database operations for medications and medication_history
 * with dual persistence (Supabase REST API and Firestore synchronization).
 */
public class MedicationRepository {

    private static final String TAG = "MedicationRepo";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    private static final FirebaseFirestore DB = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    /**
     * Saves a new Medication to Supabase `medications` table and Firestore.
     */
    public static void saveMedication(MedicationModel model, Callback<MedicationModel> callback) {
        if (model == null || model.getUserId() == null || model.getUserId().isEmpty()) {
            postFailure(callback, "Authentication required to save medicine.");
            return;
        }

        if (model.getId() == null || model.getId().isEmpty()) {
            model.setId(UUID.randomUUID().toString());
        }
        String now = Instant.now().toString();
        if (model.getCreatedAt() == null || model.getCreatedAt().isEmpty()) {
            model.setCreatedAt(now);
        }
        model.setUpdatedAt(now);

        EXECUTOR.execute(() -> {
            // 1. Sync with Firestore
            try {
                DB.collection("users")
                        .document(model.getUserId())
                        .collection("medications")
                        .document(model.getId())
                        .set(model);
            } catch (Exception e) {
                Log.e(TAG, "Firestore medication save error", e);
            }

            // 2. Post to Supabase REST if configured
            if (SupabaseConfig.isConfigured()) {
                try {
                    JSONObject json = model.toJsonObject();
                    String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/medications";

                    Request request = new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(json.toString(), JSON_MEDIA))
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=representation")
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Medication saved to Supabase: " + model.getMedicineName());
                        } else {
                            Log.w(TAG, "Supabase medication save returned HTTP " + response.code());
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase medication save exception: " + e.getMessage());
                }
            }

            postSuccess(callback, model);
        });
    }

    /**
     * Updates an existing Medication in Supabase and Firestore.
     */
    public static void updateMedication(MedicationModel model, Callback<MedicationModel> callback) {
        if (model == null || model.getUserId() == null || model.getUserId().isEmpty()) {
            postFailure(callback, "Authentication required.");
            return;
        }

        model.setUpdatedAt(Instant.now().toString());

        EXECUTOR.execute(() -> {
            // 1. Update Firestore
            try {
                DB.collection("users")
                        .document(model.getUserId())
                        .collection("medications")
                        .document(model.getId())
                        .set(model);
            } catch (Exception e) {
                Log.e(TAG, "Firestore medication update error", e);
            }

            // 2. Patch Supabase
            if (SupabaseConfig.isConfigured()) {
                try {
                    JSONObject json = model.toJsonObject();
                    String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/medications?id=eq." + model.getId();

                    Request request = new Request.Builder()
                            .url(url)
                            .patch(RequestBody.create(json.toString(), JSON_MEDIA))
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        Log.d(TAG, "Supabase update status: " + response.code());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase update error: " + e.getMessage());
                }
            }

            postSuccess(callback, model);
        });
    }

    /**
     * Deletes a Medication and its history.
     */
    public static void deleteMedication(String userId, String medicationId, Callback<Void> callback) {
        EXECUTOR.execute(() -> {
            // 1. Delete from Firestore
            try {
                if (userId != null && !userId.isEmpty()) {
                    DB.collection("users")
                            .document(userId)
                            .collection("medications")
                            .document(medicationId)
                            .delete();
                }
            } catch (Exception e) {
                Log.e(TAG, "Firestore medication delete error", e);
            }

            // 2. Delete from Supabase
            if (SupabaseConfig.isConfigured()) {
                try {
                    String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/medications?id=eq." + medicationId;
                    Request request = new Request.Builder()
                            .url(url)
                            .delete()
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        Log.d(TAG, "Supabase delete status: " + response.code());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase delete error: " + e.getMessage());
                }
            }

            postSuccess(callback, null);
        });
    }

    /**
     * Fetches all medications for a user ordered by created_at desc.
     */
    public static void fetchMedications(String userId, Callback<List<MedicationModel>> callback) {
        if (userId == null || userId.isEmpty()) {
            postFailure(callback, "Authentication required.");
            return;
        }

        EXECUTOR.execute(() -> {
            boolean fetchedFromSupabase = false;
            List<MedicationModel> list = new ArrayList<>();

            if (SupabaseConfig.isConfigured()) {
                try {
                    String url = SupabaseConfig.SUPABASE_URL
                            + "/rest/v1/medications?user_id=eq." + userId
                            + "&order=created_at.desc";

                    Request request = new Request.Builder()
                            .url(url)
                            .get()
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String jsonString = response.body().string();
                            JSONArray array = new JSONArray(jsonString);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = array.getJSONObject(i);
                                list.add(MedicationModel.fromJsonObject(obj));
                            }
                            fetchedFromSupabase = true;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase fetch medications exception: " + e.getMessage());
                }
            }

            if (fetchedFromSupabase && !list.isEmpty()) {
                postSuccess(callback, list);
                return;
            }

            // Fetch from Firestore
            DB.collection("users")
                    .document(userId)
                    .collection("medications")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<MedicationModel> firestoreList = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            MedicationModel m = doc.toObject(MedicationModel.class);
                            if (m != null) {
                                if (m.getId() == null || m.getId().isEmpty()) m.setId(doc.getId());
                                firestoreList.add(m);
                            }
                        }
                        postSuccess(callback, firestoreList);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firestore fetch medications failure", e);
                        postSuccess(callback, list);
                    });
        });
    }

    /**
     * Records an action (TAKEN or SKIPPED) on a medication.
     * TAKEN reduces remaining tablets by 1; SKIPPED does not reduce tablet count.
     */
    public static void recordMedicationAction(MedicationModel med, String scheduledTime, String action, Callback<MedicationModel> callback) {
        EXECUTOR.execute(() -> {
            try {
                int quantityUsed = 0;
                if ("TAKEN".equalsIgnoreCase(action)) {
                    quantityUsed = 1;
                    int remaining = med.getRemainingTablets() - 1;
                    if (remaining < 0) remaining = 0;
                    med.setRemainingTablets(remaining);
                }

                // 1. Log event to medication_history
                String histId = UUID.randomUUID().toString();
                String now = Instant.now().toString();
                MedicationHistoryModel history = new MedicationHistoryModel(
                        histId,
                        med.getId(),
                        med.getUserId(),
                        med.getMedicineName(),
                        med.getDosage(),
                        scheduledTime,
                        action.toUpperCase(),
                        quantityUsed,
                        now
                );

                // Save history to Firestore
                try {
                    DB.collection("users")
                            .document(med.getUserId())
                            .collection("medication_history")
                            .document(histId)
                            .set(history);
                } catch (Exception e) {
                    Log.e(TAG, "Firestore history log error", e);
                }

                // Save history to Supabase REST
                if (SupabaseConfig.isConfigured()) {
                    try {
                        String historyUrl = SupabaseConfig.SUPABASE_URL + "/rest/v1/medication_history";
                        Request histRequest = new Request.Builder()
                                .url(historyUrl)
                                .post(RequestBody.create(history.toJsonObject().toString(), JSON_MEDIA))
                                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                                .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                                .addHeader("Content-Type", "application/json")
                                .build();
                        try (Response r = HTTP_CLIENT.newCall(histRequest).execute()) {
                            Log.d(TAG, "Supabase history log status: " + r.code());
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Supabase history log error", e);
                    }
                }

                // 2. Update remaining tablets in medications table & Firestore
                updateMedication(med, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error recording medication action", e);
                postFailure(callback, "Error recording action: " + e.getMessage());
            }
        });
    }

    /**
     * Records a Refill event: adds addedQuantity to remaining tablets and logs to history.
     */
    public static void refillMedication(MedicationModel med, int addedQuantity, Callback<MedicationModel> callback) {
        EXECUTOR.execute(() -> {
            try {
                int newRemaining = med.getRemainingTablets() + addedQuantity;
                med.setRemainingTablets(newRemaining);
                med.setTabletQuantity(Math.max(med.getTabletQuantity(), newRemaining));

                // 1. Log REFILLED action in medication_history
                String histId = UUID.randomUUID().toString();
                String now = Instant.now().toString();
                MedicationHistoryModel history = new MedicationHistoryModel(
                        histId,
                        med.getId(),
                        med.getUserId(),
                        med.getMedicineName(),
                        med.getDosage(),
                        "Refill",
                        "REFILLED",
                        addedQuantity,
                        now
                );

                // Save history to Firestore
                try {
                    DB.collection("users")
                            .document(med.getUserId())
                            .collection("medication_history")
                            .document(histId)
                            .set(history);
                } catch (Exception e) {
                    Log.e(TAG, "Firestore refill log error", e);
                }

                // Save history to Supabase REST
                if (SupabaseConfig.isConfigured()) {
                    try {
                        String historyUrl = SupabaseConfig.SUPABASE_URL + "/rest/v1/medication_history";
                        Request histRequest = new Request.Builder()
                                .url(historyUrl)
                                .post(RequestBody.create(history.toJsonObject().toString(), JSON_MEDIA))
                                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                                .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                                .addHeader("Content-Type", "application/json")
                                .build();
                        try (Response r = HTTP_CLIENT.newCall(histRequest).execute()) {
                            Log.d(TAG, "Supabase refill log status: " + r.code());
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Supabase refill log error", e);
                    }
                }

                // 2. Update medication in table & Firestore
                updateMedication(med, callback);

            } catch (Exception e) {
                Log.e(TAG, "Error refilling medication", e);
                postFailure(callback, "Error refilling medicine: " + e.getMessage());
            }
        });
    }

    /**
     * Fetches historical medication logs with optional action filter ("ALL", "TAKEN", "SKIPPED", "REFILLED").
     */
    public static void fetchMedicationHistory(String userId, String filterAction, Callback<List<MedicationHistoryModel>> callback) {
        if (userId == null || userId.isEmpty()) {
            postFailure(callback, "Authentication required.");
            return;
        }

        EXECUTOR.execute(() -> {
            boolean fetchedFromSupabase = false;
            List<MedicationHistoryModel> list = new ArrayList<>();

            if (SupabaseConfig.isConfigured()) {
                try {
                    StringBuilder url = new StringBuilder(SupabaseConfig.SUPABASE_URL)
                            .append("/rest/v1/medication_history?user_id=eq.")
                            .append(userId);

                    if (filterAction != null && !filterAction.isEmpty() && !"ALL".equalsIgnoreCase(filterAction)) {
                        url.append("&action=eq.").append(filterAction.toUpperCase());
                    }
                    url.append("&order=created_at.desc");

                    Request request = new Request.Builder()
                            .url(url.toString())
                            .get()
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String jsonString = response.body().string();
                            JSONArray array = new JSONArray(jsonString);
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = array.getJSONObject(i);
                                list.add(MedicationHistoryModel.fromJsonObject(obj));
                            }
                            fetchedFromSupabase = true;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase history fetch exception: " + e.getMessage());
                }
            }

            if (fetchedFromSupabase && !list.isEmpty()) {
                postSuccess(callback, list);
                return;
            }

            // Fetch from Firestore
            com.google.firebase.firestore.Query query = DB.collection("users")
                    .document(userId)
                    .collection("medication_history");

            if (filterAction != null && !filterAction.isEmpty() && !"ALL".equalsIgnoreCase(filterAction)) {
                query = query.whereEqualTo("action", filterAction.toUpperCase());
            }

            query.get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<MedicationHistoryModel> firestoreList = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            MedicationHistoryModel m = doc.toObject(MedicationHistoryModel.class);
                            if (m != null) {
                                if (m.getId() == null || m.getId().isEmpty()) m.setId(doc.getId());
                                firestoreList.add(m);
                            }
                        }
                        // Sort reverse chronological
                        Collections.sort(firestoreList, (a, b) -> {
                            String c1 = a.getCreatedAt() != null ? a.getCreatedAt() : "";
                            String c2 = b.getCreatedAt() != null ? b.getCreatedAt() : "";
                            return c2.compareTo(c1);
                        });
                        postSuccess(callback, firestoreList);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Firestore history fetch failure", e);
                        postSuccess(callback, list);
                    });
        });
    }

    private static <T> void postSuccess(Callback<T> callback, T result) {
        if (callback != null) MAIN_HANDLER.post(() -> callback.onSuccess(result));
    }

    private static <T> void postFailure(Callback<T> callback, String error) {
        if (callback != null) MAIN_HANDLER.post(() -> callback.onFailure(error));
    }
}
