package com.example.medilinkchain;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
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
 * SupabaseDbHelper — Handles REST API database queries to Supabase tables
 * (such as health_risk_assessments) using OkHttp.
 */
public class SupabaseDbHelper {

    private static final String TAG = "SupabaseDbHelper";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface SaveCallback {
        void onSuccess(HealthRiskAssessmentModel savedModel);
        void onFailure(String errorMessage);
    }

    public interface FetchCallback {
        void onSuccess(List<HealthRiskAssessmentModel> list);
        void onFailure(String errorMessage);
    }

    /**
     * Saves a Health Risk Assessment record into Supabase database table `health_risk_assessments`.
     */
    public static void saveHealthRiskAssessment(HealthRiskAssessmentModel model, SaveCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (!SupabaseConfig.isConfigured()) {
                    postFailure(callback, "Supabase configuration missing.");
                    return;
                }

                if (model.getId() == null || model.getId().isEmpty()) {
                    model.setId(UUID.randomUUID().toString());
                }

                if (model.getCreatedAt() == null || model.getCreatedAt().isEmpty()) {
                    model.setCreatedAt(java.time.Instant.now().toString());
                }

                JSONObject json = new JSONObject();
                json.put("id", model.getId());
                json.put("user_id", model.getUserId());
                json.put("age", model.getAge());
                json.put("gender", model.getGender());
                json.put("height_cm", model.getHeightCm());
                json.put("weight_kg", model.getWeightKg());
                json.put("bmi", model.getBmi());
                json.put("systolic_bp", model.getSystolicBp());
                json.put("diastolic_bp", model.getDiastolicBp());
                json.put("fasting_glucose", model.getFastingGlucose());
                json.put("smoking", model.getSmoking());
                json.put("alcohol", model.getAlcohol());
                json.put("physical_activity", model.getPhysicalActivity());
                json.put("family_history", model.getFamilyHistory());
                json.put("risk_score", model.getRiskScore());
                json.put("risk_category", model.getRiskCategory());
                json.put("created_at", model.getCreatedAt());

                String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/health_risk_assessments";
                RequestBody body = RequestBody.create(
                        json.toString(),
                        MediaType.parse("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Health Risk Assessment saved successfully to Supabase.");
                        MAIN_HANDLER.post(() -> callback.onSuccess(model));
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "Failed to save assessment to Supabase: HTTP " + response.code() + " - " + responseBody);
                        postFailure(callback, "Save failed (HTTP " + response.code() + "). Please try again.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception saving health assessment to Supabase", e);
                postFailure(callback, "Error saving assessment: " + e.getMessage());
            }
        });
    }

    /**
     * Fetches historical Health Risk Assessments for a given patient from Supabase.
     */
    public static void fetchHealthRiskAssessments(String userId, FetchCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (!SupabaseConfig.isConfigured()) {
                    postFailure(callback, "Supabase configuration missing.");
                    return;
                }

                String url = SupabaseConfig.SUPABASE_URL
                        + "/rest/v1/health_risk_assessments?user_id=eq."
                        + userId
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
                        JSONArray jsonArray = new JSONArray(jsonString);

                        List<HealthRiskAssessmentModel> list = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject obj = jsonArray.getJSONObject(i);
                            HealthRiskAssessmentModel m = parseModel(obj);
                            list.add(m);
                        }

                        MAIN_HANDLER.post(() -> callback.onSuccess(list));
                    } else {
                        String err = "Failed to load history (HTTP " + response.code() + ")";
                        postFailure(callback, err);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception fetching health assessment history from Supabase", e);
                postFailure(callback, "Error loading history: " + e.getMessage());
            }
        });
    }

    private static HealthRiskAssessmentModel parseModel(JSONObject obj) {
        HealthRiskAssessmentModel m = new HealthRiskAssessmentModel();
        m.setId(obj.optString("id"));
        m.setUserId(obj.optString("user_id"));
        m.setAge(obj.optInt("age"));
        m.setGender(obj.optString("gender"));
        m.setHeightCm(obj.optDouble("height_cm"));
        m.setWeightKg(obj.optDouble("weight_kg"));
        m.setBmi(obj.optDouble("bmi"));
        m.setBmiCategory(HealthRiskCalculator.getBmiCategory(m.getBmi()));
        m.setSystolicBp(obj.optInt("systolic_bp"));
        m.setDiastolicBp(obj.optInt("diastolic_bp"));
        m.setBpStatus(HealthRiskCalculator.getBpStatus(m.getSystolicBp(), m.getDiastolicBp()));
        m.setFastingGlucose(obj.optInt("fasting_glucose"));
        m.setGlucoseStatus(HealthRiskCalculator.getGlucoseStatus(m.getFastingGlucose()));
        m.setSmoking(obj.optString("smoking"));
        m.setAlcohol(obj.optString("alcohol"));
        m.setPhysicalActivity(obj.optString("physical_activity"));
        m.setFamilyHistory(obj.optString("family_history"));
        m.setRiskScore(obj.optInt("risk_score"));
        m.setRiskCategory(obj.optString("risk_category"));
        m.setCreatedAt(obj.optString("created_at"));
        return m;
    }

    private static void postFailure(SaveCallback callback, String msg) {
        MAIN_HANDLER.post(() -> callback.onFailure(msg));
    }

    private static void postFailure(FetchCallback callback, String msg) {
        MAIN_HANDLER.post(() -> callback.onFailure(msg));
    }
}
