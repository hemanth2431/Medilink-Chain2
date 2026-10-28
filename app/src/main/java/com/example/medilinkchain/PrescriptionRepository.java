package com.example.medilinkchain;

import android.content.Context;
import android.net.Uri;
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
 * PrescriptionRepository — Handles Supabase Storage uploads/deletions and REST database CRUD for prescriptions,
 * with Firestore synchronization for offline resilience and immediate UI updates.
 */
public class PrescriptionRepository {

    private static final String TAG = "PrescriptionRepo";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");
    private static final FirebaseFirestore DB = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    public interface UploadProgressCallback {
        void onProgress(int percent);
        void onSuccess(PrescriptionModel savedModel);
        void onFailure(String error);
    }

    /**
     * Uploads prescription file to Supabase Storage and saves metadata to PostgreSQL table `prescriptions` and Firestore.
     */
    public static void uploadPrescription(
            Context context,
            Uri fileUri,
            String fileName,
            String mimeType,
            PrescriptionModel model,
            UploadProgressCallback callback) {

        if (model.getUserId() == null || model.getUserId().isEmpty()) {
            postFailure(callback, "Authentication required. Please log in.");
            return;
        }

        SupabaseStorageHelper.uploadFile(
                context,
                fileUri,
                fileName,
                "prescriptions",
                mimeType,
                model.getUserId(),
                new SupabaseStorageHelper.UploadCallback() {
                    @Override
                    public void onProgress(int percent) {
                        if (callback != null) {
                            MAIN_HANDLER.post(() -> callback.onProgress(percent));
                        }
                    }

                    @Override
                    public void onSuccess(String fileUrl, String storagePath) {
                        model.setFilePath(storagePath);
                        model.setFileUrl(fileUrl);
                        if (model.getId() == null || model.getId().isEmpty()) {
                            model.setId(UUID.randomUUID().toString());
                        }

                        // Save metadata to Supabase DB & Firestore
                        savePrescriptionMetadata(model, new Callback<PrescriptionModel>() {
                            @Override
                            public void onSuccess(PrescriptionModel result) {
                                if (callback != null) {
                                    MAIN_HANDLER.post(() -> callback.onSuccess(result));
                                }
                            }

                            @Override
                            public void onFailure(String error) {
                                if (callback != null) {
                                    MAIN_HANDLER.post(() -> callback.onFailure(error));
                                }
                            }
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.e(TAG, "Storage upload failed: " + errorMessage);
                        if (callback != null) {
                            MAIN_HANDLER.post(() -> callback.onFailure("Prescription upload failed. Please check internet connection and try again."));
                        }
                    }
                }
        );
    }

    /**
     * Saves prescription metadata to Supabase `prescriptions` table and Firestore.
     */
    public static void savePrescriptionMetadata(PrescriptionModel model, Callback<PrescriptionModel> callback) {
        if (model == null || model.getUserId() == null || model.getUserId().isEmpty()) {
            postFailure(callback, "Authentication required.");
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
            // 1. Sync to Firestore
            try {
                DB.collection("users")
                        .document(model.getUserId())
                        .collection("prescriptions")
                        .document(model.getId())
                        .set(model);
            } catch (Exception e) {
                Log.e(TAG, "Firestore prescription save error", e);
            }

            // 2. Post to Supabase REST
            if (SupabaseConfig.isConfigured()) {
                try {
                    JSONObject json = model.toJsonObject();
                    String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/prescriptions";

                    Request request = new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(json.toString(), JSON_MEDIA))
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=representation")
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        Log.d(TAG, "Supabase save prescription status: " + response.code());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase save prescription exception: " + e.getMessage());
                }
            }

            postSuccess(callback, model);
        });
    }

    /**
     * Fetches prescriptions for a user ordered by created_at desc.
     */
    public static void fetchPrescriptions(String userId, Callback<List<PrescriptionModel>> callback) {
        if (userId == null || userId.isEmpty()) {
            postFailure(callback, "Authentication required.");
            return;
        }

        EXECUTOR.execute(() -> {
            boolean fetchedFromSupabase = false;
            List<PrescriptionModel> list = new ArrayList<>();

            if (SupabaseConfig.isConfigured()) {
                try {
                    String url = SupabaseConfig.SUPABASE_URL
                            + "/rest/v1/prescriptions?user_id=eq." + userId
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
                                list.add(PrescriptionModel.fromJsonObject(obj));
                            }
                            fetchedFromSupabase = true;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Supabase fetch prescriptions exception: " + e.getMessage());
                }
            }

            if (fetchedFromSupabase && !list.isEmpty()) {
                postSuccess(callback, list);
                return;
            }

            // Fetch from Firestore
            DB.collection("users")
                    .document(userId)
                    .collection("prescriptions")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        List<PrescriptionModel> firestoreList = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            PrescriptionModel p = doc.toObject(PrescriptionModel.class);
                            if (p != null) {
                                if (p.getId() == null || p.getId().isEmpty()) p.setId(doc.getId());
                                firestoreList.add(p);
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
                        Log.e(TAG, "Firestore fetch prescriptions failure", e);
                        postSuccess(callback, list);
                    });
        });
    }

    /**
     * Deletes prescription: first deletes from Supabase Storage, then deletes metadata from PostgreSQL & Firestore.
     */
    public static void deletePrescription(PrescriptionModel model, Callback<Void> callback) {
        if (model == null) {
            postFailure(callback, "Invalid prescription");
            return;
        }

        EXECUTOR.execute(() -> {
            // 1. Delete from Supabase Storage
            SupabaseStorageHelper.deleteFile(model.getFilePath(), new SupabaseStorageHelper.DeleteCallback() {
                @Override
                public void onSuccess() {
                    // 2. Delete metadata from Firestore
                    try {
                        if (model.getUserId() != null && !model.getUserId().isEmpty()) {
                            DB.collection("users")
                                    .document(model.getUserId())
                                    .collection("prescriptions")
                                    .document(model.getId())
                                    .delete();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Firestore prescription delete error", e);
                    }

                    // 3. Delete metadata from PostgreSQL
                    deleteDatabaseRecord(model.getId(), callback);
                }

                @Override
                public void onFailure(String errorMessage) {
                    Log.e(TAG, "Storage deletion failed: " + errorMessage);
                    postFailure(callback, "Failed to delete prescription file: " + errorMessage);
                }
            });
        });
    }

    private static void deleteDatabaseRecord(String id, Callback<Void> callback) {
        EXECUTOR.execute(() -> {
            if (SupabaseConfig.isConfigured()) {
                try {
                    String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/prescriptions?id=eq." + id;
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
                    Log.w(TAG, "Supabase delete exception: " + e.getMessage());
                }
            }
            postSuccess(callback, null);
        });
    }

    private static <T> void postSuccess(Callback<T> callback, T result) {
        if (callback != null) MAIN_HANDLER.post(() -> callback.onSuccess(result));
    }

    private static <T> void postFailure(Callback<T> callback, String error) {
        if (callback != null) MAIN_HANDLER.post(() -> callback.onFailure(error));
    }

    private static void postFailure(UploadProgressCallback callback, String error) {
        if (callback != null) MAIN_HANDLER.post(() -> callback.onFailure(error));
    }
}
