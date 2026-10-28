package com.example.medilinkchain;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SupabaseStorageHelper — Handles file upload, signed URL generation, and deletion
 * in Supabase Storage using the REST API and OkHttp.
 *
 * Folder structure rule:
 * every uploaded file path starts with user id: ${uid}/${featureName}/${uuid}.${extension}
 */
public class SupabaseStorageHelper {

    private static final String TAG = "SupabaseStorage";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    /** Callback interface for upload results. */
    public interface UploadCallback {
        /** Called on main thread when upload succeeds. Returns signed URL and storage path. */
        void onSuccess(String signedUrl, String storagePath);

        /** Called on main thread when upload fails. */
        void onFailure(String errorMessage);

        /** Called on main thread periodically with upload progress (0-100). */
        void onProgress(int percent);
    }

    /** Callback interface for deletion results. */
    public interface DeleteCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    /** Callback interface for signed URL creation. */
    public interface SignedUrlCallback {
        void onSuccess(String signedUrl);
        void onFailure(String errorMessage);
    }

    /**
     * Uploads a file to Supabase Storage asynchronously and returns a signed URL.
     *
     * @param context     Android context (used to read the file via ContentResolver)
     * @param fileUri     URI of the file selected by the user
     * @param fileName    Original display name of the file
     * @param featureName Feature namespace (e.g. "medical-records", "report-analyzer")
     * @param mimeType    MIME type of the file
     * @param uid         Firebase UID used to namespace the storage path
     * @param callback    Callback to receive result or error on the main thread
     */
    public static void uploadFile(
            Context context,
            Uri fileUri,
            String fileName,
            String featureName,
            String mimeType,
            String uid,
            UploadCallback callback) {

        EXECUTOR.execute(() -> {

            // ── Validate configuration ──────────────────────────────────────
            if (SupabaseConfig.SUPABASE_URL == null || SupabaseConfig.SUPABASE_URL.isEmpty()) {
                Log.e(TAG, "Supabase URL is not configured.");
                postFailure(callback, "Supabase URL is not configured.");
                return;
            }
            if (!SupabaseConfig.isConfigured()) {
                Log.e(TAG, "Supabase anon key is not configured.");
                postFailure(callback, "Supabase publishable key is not configured.");
                return;
            }

            // ── Read file bytes ──────────────────────────────────────────────
            byte[] fileBytes;
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                if (inputStream == null) {
                    postFailure(callback, "Unable to read the selected file.");
                    return;
                }
                fileBytes = readAllBytes(inputStream);
                inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException reading file URI: " + e.getMessage(), e);
                postFailure(callback, "Network error: unable to read file for upload.");
                return;
            }

            // ── Validate file size ───────────────────────────────────────────
            if (fileBytes.length > SupabaseConfig.MAX_FILE_SIZE_BYTES) {
                postFailure(callback, "File size exceeds 10 MB limit.");
                return;
            }

            // ── Build safe storage path ──────────────────────────────────────
            String extension = extractExtension(fileName, mimeType);
            String fileUuid = UUID.randomUUID().toString();
            String safeFeature = (featureName != null && !featureName.isEmpty())
                    ? featureName.replaceAll("[^a-zA-Z0-9_-]", "_")
                    : "general";

            // Structure: ${uid}/${featureName}/${uuid}.${extension}
            String storagePath = uid + "/" + safeFeature + "/" + fileUuid + "." + extension;

            // ── Build upload URL ─────────────────────────────────────────────
            String uploadUrl = SupabaseConfig.SUPABASE_URL
                    + "/storage/v1/object/"
                    + SupabaseConfig.BUCKET_NAME
                    + "/"
                    + storagePath;

            Log.d(TAG, "=== SUPABASE STORAGE UPLOAD START ===");
            Log.d(TAG, "Bucket: " + SupabaseConfig.BUCKET_NAME);
            Log.d(TAG, "Path: " + storagePath);
            Log.d(TAG, "MIME: " + mimeType);
            Log.d(TAG, "Size: " + fileBytes.length + " bytes");
            Log.d(TAG, "Upload URL: " + uploadUrl);

            postProgress(callback, 10);

            // ── Build OkHttp request ─────────────────────────────────────────
            MediaType mediaType = MediaType.parse(mimeType);
            RequestBody requestBody = RequestBody.create(fileBytes, mediaType);

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", mimeType)
                    .addHeader("x-upsert", "true")
                    .post(requestBody)
                    .build();

            postProgress(callback, 50);

            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                int httpCode = response.code();
                String responseBody = response.body() != null ? response.body().string() : "(empty)";

                Log.d(TAG, "HTTP Status: " + httpCode);
                Log.d(TAG, "Response Body: " + responseBody);

                if (response.isSuccessful()) {
                    postProgress(callback, 80);

                    // Generate a Signed URL since the bucket is private (7 days validity = 604800 seconds)
                    String signedUrl = fetchSignedUrlSync(storagePath, 604800);
                    if (signedUrl == null) {
                        // Fallback to REST URL if signed URL fails
                        signedUrl = SupabaseConfig.SUPABASE_URL
                                + "/storage/v1/object/"
                                + SupabaseConfig.BUCKET_NAME
                                + "/"
                                + storagePath;
                    }

                    Log.d(TAG, "Upload SUCCESS. Storage Path: " + storagePath);
                    Log.d(TAG, "Signed URL: " + signedUrl);
                    postProgress(callback, 100);

                    final String finalSignedUrl = signedUrl;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                            callback.onSuccess(finalSignedUrl, storagePath));

                } else {
                    Log.e(TAG, "Supabase upload failed. HTTP " + httpCode + " — " + responseBody);
                    String userMessage = buildErrorMessage(httpCode, responseBody);
                    postFailure(callback, userMessage);
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error during Supabase upload: " + e.getMessage(), e);
                postFailure(callback, "Network error. Please check your internet connection and try again.");
            }
        });
    }

    /** Overload for backwards compatibility without featureName */
    public static void uploadFile(
            Context context,
            Uri fileUri,
            String fileName,
            String mimeType,
            String uid,
            UploadCallback callback) {
        uploadFile(context, fileUri, fileName, "medical-records", mimeType, uid, callback);
    }

    /**
     * Creates a signed URL for a file in private Supabase storage asynchronously.
     */
    public static void createSignedUrl(String storagePath, int expiresInSeconds, SignedUrlCallback callback) {
        EXECUTOR.execute(() -> {
            String signedUrl = fetchSignedUrlSync(storagePath, expiresInSeconds);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (signedUrl != null) {
                    callback.onSuccess(signedUrl);
                } else {
                    callback.onFailure("Failed to generate signed URL for storage path: " + storagePath);
                }
            });
        });
    }

    /**
     * Synchronously fetches a signed URL from Supabase Storage.
     */
    private static String fetchSignedUrlSync(String storagePath, int expiresInSeconds) {
        if (storagePath == null || storagePath.isEmpty()) return null;

        try {
            String signApiUrl = SupabaseConfig.SUPABASE_URL
                    + "/storage/v1/object/sign/"
                    + SupabaseConfig.BUCKET_NAME
                    + "/"
                    + storagePath;

            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("expiresIn", expiresInSeconds);

            RequestBody body = RequestBody.create(
                    jsonPayload.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(signApiUrl)
                    .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .post(body)
                    .build();

            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonString = response.body().string();
                    JSONObject resObj = new JSONObject(jsonString);
                    String signedPath = resObj.optString("signedURL", null);
                    if (signedPath != null && !signedPath.isEmpty()) {
                        if (signedPath.startsWith("http")) {
                            return signedPath;
                        } else {
                            return SupabaseConfig.SUPABASE_URL + "/storage/v1" + signedPath;
                        }
                    }
                } else {
                    Log.e(TAG, "Signed URL request failed. Code: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching signed URL", e);
        }
        return null;
    }

    /**
     * Deletes a file from Supabase Storage asynchronously.
     */
    public static void deleteFile(String storagePath, DeleteCallback callback) {
        if (storagePath == null || storagePath.isEmpty()) {
            if (callback != null) callback.onSuccess();
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                String deleteUrl = SupabaseConfig.SUPABASE_URL
                        + "/storage/v1/object/"
                        + SupabaseConfig.BUCKET_NAME
                        + "/"
                        + storagePath;

                Request request = new Request.Builder()
                        .url(deleteUrl)
                        .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .delete()
                        .build();

                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    if (response.isSuccessful() || response.code() == 404) {
                        Log.d(TAG, "File successfully deleted from Supabase Storage: " + storagePath);
                        if (callback != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
                        }
                    } else {
                        String err = "Failed to delete file from Supabase Storage (HTTP " + response.code() + ")";
                        Log.e(TAG, err);
                        if (callback != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onFailure(err));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting file from Supabase Storage", e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onFailure(e.getMessage()));
                }
            }
        });
    }

    private static String extractExtension(String fileName, String mimeType) {
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (ext.length() >= 2 && ext.length() <= 5) return ext;
        }
        if (mimeType != null) {
            if (mimeType.contains("pdf")) return "pdf";
            if (mimeType.contains("png")) return "png";
            if (mimeType.contains("jpeg") || mimeType.contains("jpg")) return "jpg";
        }
        return "bin";
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private static String buildErrorMessage(int httpCode, String body) {
        if (body != null && (body.contains("row-level security") || body.contains("AccessDenied") || body.contains("Unauthorized"))) {
            return "Permission Denied: Supabase Storage Row-Level Security (RLS) policy error.\n\n"
                    + "Supabase is blocking the file upload because the 'medical-records' bucket policy requires an INSERT permission for the 'anon' role.\n\n"
                    + "Fix in Supabase Dashboard → Storage → medical-records → Policies:\n"
                    + "Add an INSERT policy for Target Roles: 'anon', with condition: bucket_id = 'medical-records'.";
        }
        switch (httpCode) {
            case 400:
                return "Supabase Storage upload failed: bad request. " + body;
            case 401:
                return "Authentication required. Please check your Supabase configuration.";
            case 403:
                return "Supabase Storage upload failed: permission denied. Please check your bucket policies for the 'anon' role.";
            case 404:
                return "Supabase Storage bucket 'medical-records' not found. Please verify bucket name in Supabase Dashboard.";
            case 413:
                return "File size exceeds 10 MB limit.";
            default:
                return String.format(Locale.US,
                        "Supabase Storage upload failed (HTTP %d). %s", httpCode, body);
        }
    }

    private static void postFailure(UploadCallback callback, String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                callback.onFailure(message));
    }

    private static void postProgress(UploadCallback callback, int percent) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                callback.onProgress(percent));
    }
}
