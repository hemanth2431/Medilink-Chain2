package com.example.medilinkchain;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * HealthAssessmentRepository — Persists and fetches rule-based health assessments
 * across Supabase PostgreSQL REST and Firestore.
 */
public class HealthAssessmentRepository {

    private static final String TAG = "HealthAssessmentRepo";
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
     * Saves health assessment record to Supabase and Firestore.
     */
    public static void saveAssessment(HealthAssessmentModel model, Callback<HealthAssessmentModel> callback) {
        if (model == null || model.getUserId() == null || model.getUserId().isEmpty()) {
            postFailure(callback, "Authentication required to save assessment.");
            return;
        }

        if (model.getId() == null || model.getId().isEmpty()) {
            model.setId(UUID.randomUUID().toString());
        }
        String now = Instant.now().toString();
        model.setCreatedAt(now);

        EXECUTOR.execute(() -> {
            // 1. Sync to Firestore
            try {
                DB.collection("users")
                        .document(model.getUserId())
                        .collection("health_assessments")
                        .document(model.getId())
                        .set(model);
            } catch (Exception e) {
                Log.e(TAG, "Firestore assessment save error", e);
            }

            // 2. Post to Supabase REST
            if (SupabaseConfig.isConfigured()) {
                try {
                    JSONObject json = model.toJsonObject();
                    String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/health_assessments";

                    Request request = new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(json.toString(), JSON_MEDIA))
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=representation")
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        Log.d(TAG, "Supabase health assessment save response: " + response.code());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase health assessment save exception: " + e.getMessage());
                }
            }

            postSuccess(callback, model);
        });
    }

    /**
     * Fetches all health assessments for a given user.
     */
    public static void fetchAssessments(String userId, Callback<List<HealthAssessmentModel>> callback) {
        if (userId == null || userId.isEmpty()) {
            postFailure(callback, "Authentication required.");
            return;
        }

        EXECUTOR.execute(() -> {
            boolean fetchedFromSupabase = false;
            List<HealthAssessmentModel> list = new ArrayList<>();

            if (SupabaseConfig.isConfigured()) {
                try {
                    String url = SupabaseConfig.SUPABASE_URL
                            + "/rest/v1/health_assessments?user_id=eq." + userId
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
                                list.add(HealthAssessmentModel.fromJsonObject(obj));
                            }
                            fetchedFromSupabase = true;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase fetch assessments exception: " + e.getMessage());
                }
            }

            if (fetchedFromSupabase && !list.isEmpty()) {
                postSuccess(callback, list);
                return;
            }

            // Fetch from Firestore
            DB.collection("users")
                    .document(userId)
                    .collection("health_assessments")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<HealthAssessmentModel> firestoreList = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            HealthAssessmentModel m = doc.toObject(HealthAssessmentModel.class);
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
                        Log.e(TAG, "Firestore fetch assessments failure", e);
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
