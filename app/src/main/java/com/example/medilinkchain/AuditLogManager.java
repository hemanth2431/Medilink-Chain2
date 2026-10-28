package com.example.medilinkchain;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.util.Date;
import java.util.HashMap;
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
 * AuditLogManager — Centralized append-only auditing logger for organization actions.
 */
public class AuditLogManager {

    private static final String TAG = "AuditLogManager";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface AuditCallback {
        void onSuccess();
        void onFailure(String error);
    }

    /**
     * Records an audit log event asynchronously.
     */
    public static void log(
            String organizationId,
            String actorId,
            String actorEmail,
            String actorRole,
            String action,
            String targetType,
            String targetId,
            String description,
            AuditCallback callback) {

        final String finalOrgId = (organizationId != null && !organizationId.isEmpty()) ? organizationId : "org_medilink_main";
        final String finalActorId = (actorId != null && !actorId.isEmpty()) ? actorId : "system";
        final String finalActorEmail = (actorEmail != null && !actorEmail.isEmpty()) ? actorEmail : "system@medilinkchain.org";
        final String finalActorRole = (actorRole != null && !actorRole.isEmpty()) ? actorRole : "ADMIN";
        final String logId = UUID.randomUUID().toString();
        final Date now = new Date();

        EXECUTOR.execute(() -> {
            // 1. Log to Firestore
            try {
                Map<String, Object> logData = new HashMap<>();
                logData.put("id", logId);
                logData.put("organizationId", finalOrgId);
                logData.put("actorId", finalActorId);
                logData.put("actorEmail", finalActorEmail);
                logData.put("actorRole", finalActorRole);
                logData.put("action", action);
                logData.put("targetType", targetType);
                logData.put("targetId", targetId);
                logData.put("description", description);
                logData.put("createdAt", now);

                FirebaseFirestore.getInstance().collection("audit_logs").document(logId).set(logData);
            } catch (Exception e) {
                Log.e(TAG, "Firestore audit log error: " + e.getMessage());
            }

            // 2. Log to Supabase REST if configured
            if (SupabaseConfig.isConfigured()) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("id", logId);
                    json.put("organization_id", finalOrgId);
                    json.put("actor_id", finalActorId);
                    json.put("actor_email", finalActorEmail);
                    json.put("actor_role", finalActorRole);
                    json.put("action", action);
                    json.put("target_type", targetType);
                    json.put("target_id", targetId);
                    json.put("description", description);
                    json.put("created_at", java.time.Instant.now().toString());

                    String url = SupabaseConfig.SUPABASE_URL + "/rest/v1/audit_logs";
                    RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8"));

                    Request request = new Request.Builder()
                            .url(url)
                            .post(body)
                            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "return=minimal")
                            .build();

                    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Supabase audit log response code: " + response.code());
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Supabase audit log error: " + e.getMessage());
                }
            }

            if (callback != null) {
                MAIN_HANDLER.post(callback::onSuccess);
            }
        });
    }

    /**
     * Convenience fire-and-forget log method.
     */
    public static void log(
            String organizationId,
            String actorId,
            String actorEmail,
            String actorRole,
            String action,
            String targetType,
            String targetId,
            String description) {
        log(organizationId, actorId, actorEmail, actorRole, action, targetType, targetId, description, null);
    }
}
