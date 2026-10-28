package com.example.medilinkchain;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SupabaseAuthHelper — Manages authentications against Supabase Auth (GoTrue REST API)
 * with direct sign-up, session checking, and direct sign-in with email & password.
 */
public class SupabaseAuthHelper {

    private static final String TAG = "SupabaseAuthHelper";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static class AuthResult {
        public final boolean sessionAvailable;
        public final String accessToken;
        public final String refreshToken;
        public final String userId;
        public final String email;
        public final String role;
        public final JSONObject rawResponse;

        public AuthResult(boolean sessionAvailable, String accessToken, String refreshToken,
                          String userId, String email, String role, JSONObject rawResponse) {
            this.sessionAvailable = sessionAvailable;
            this.accessToken = accessToken != null ? accessToken : "";
            this.refreshToken = refreshToken != null ? refreshToken : "";
            this.userId = userId != null ? userId : "";
            this.email = email != null ? email : "";
            this.role = role != null ? role : "PATIENT";
            this.rawResponse = rawResponse;
        }
    }

    public interface AuthCallback {
        void onSuccess(@NonNull AuthResult result);
        void onFailure(@NonNull String errorMessage, boolean isDuplicateEmail);
    }

    /**
     * Signs up a new account in Supabase with email, password and user role metadata.
     * Checks if a session (access_token) is immediately available upon registration.
     */
    public static void signUp(String email, String password, String role, AuthCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (!SupabaseConfig.isConfigured()) {
                    postFailure(callback, "Supabase configuration is not active.", false);
                    return;
                }

                JSONObject userMetadata = new JSONObject();
                userMetadata.put("role", role != null ? role.toUpperCase() : "PATIENT");

                JSONObject bodyJson = new JSONObject();
                bodyJson.put("email", email.trim());
                bodyJson.put("password", password);
                bodyJson.put("data", userMetadata);

                String endpoint = SupabaseConfig.SUPABASE_URL + "/auth/v1/signup";
                RequestBody requestBody = RequestBody.create(bodyJson.toString(), JSON);

                Request request = new Request.Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    String resBody = response.body() != null ? response.body().string() : "";
                    int code = response.code();
                    Log.d(TAG, "Supabase signUp HTTP " + code + ": " + resBody);

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(resBody);

                        String accessToken = json.optString("access_token", "");
                        String refreshToken = json.optString("refresh_token", "");

                        // Also inspect possible nested session object
                        JSONObject sessionObj = json.optJSONObject("session");
                        if (sessionObj != null) {
                            if (accessToken.isEmpty()) accessToken = sessionObj.optString("access_token", "");
                            if (refreshToken.isEmpty()) refreshToken = sessionObj.optString("refresh_token", "");
                        }

                        // Inspect user object
                        JSONObject userObj = json.optJSONObject("user");
                        if (userObj == null && json.has("id")) {
                            userObj = json;
                        }

                        String userId = userObj != null ? userObj.optString("id", "") : "";
                        String userEmail = userObj != null ? userObj.optString("email", email) : email;

                        boolean sessionAvailable = !accessToken.isEmpty();

                        AuthResult result = new AuthResult(sessionAvailable, accessToken, refreshToken, userId, userEmail, role, json);
                        MAIN_HANDLER.post(() -> callback.onSuccess(result));
                    } else {
                        handleErrorResponse(resBody, code, callback);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error during Supabase signUp", e);
                postFailure(callback, "Network error connecting to authentication server. Please check your internet connection.", false);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during Supabase signUp", e);
                postFailure(callback, "Registration error: " + e.getMessage(), false);
            }
        });
    }

    /**
     * Signs in an existing account directly using registered email and password via Supabase Auth.
     */
    public static void signIn(String email, String password, AuthCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                if (!SupabaseConfig.isConfigured()) {
                    postFailure(callback, "Supabase configuration is not active.", false);
                    return;
                }

                JSONObject bodyJson = new JSONObject();
                bodyJson.put("email", email.trim());
                bodyJson.put("password", password);

                String endpoint = SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password";
                RequestBody requestBody = RequestBody.create(bodyJson.toString(), JSON);

                Request request = new Request.Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                    String resBody = response.body() != null ? response.body().string() : "";
                    int code = response.code();
                    Log.d(TAG, "Supabase signIn HTTP " + code + ": " + resBody);

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(resBody);
                        String accessToken = json.optString("access_token", "");
                        String refreshToken = json.optString("refresh_token", "");

                        JSONObject userObj = json.optJSONObject("user");
                        String userId = userObj != null ? userObj.optString("id", "") : "";
                        String userEmail = userObj != null ? userObj.optString("email", email) : email;

                        String role = "PATIENT";
                        if (userObj != null) {
                            JSONObject meta = userObj.optJSONObject("user_metadata");
                            if (meta != null && meta.has("role")) {
                                role = meta.optString("role", "PATIENT");
                            }
                        }

                        boolean sessionAvailable = !accessToken.isEmpty();
                        AuthResult result = new AuthResult(sessionAvailable, accessToken, refreshToken, userId, userEmail, role, json);
                        MAIN_HANDLER.post(() -> callback.onSuccess(result));
                    } else {
                        handleErrorResponse(resBody, code, callback);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error during Supabase signIn", e);
                postFailure(callback, "Network error connecting to authentication server. Please check your internet connection.", false);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during Supabase signIn", e);
                postFailure(callback, "Authentication failed: " + e.getMessage(), false);
            }
        });
    }

    private static void handleErrorResponse(String resBody, int statusCode, AuthCallback callback) {
        String message = "Authentication failed.";
        boolean isDuplicate = false;

        try {
            if (resBody != null && !resBody.isEmpty()) {
                JSONObject errJson = new JSONObject(resBody);
                if (errJson.has("msg")) {
                    message = errJson.getString("msg");
                } else if (errJson.has("message")) {
                    message = errJson.getString("message");
                } else if (errJson.has("error_description")) {
                    message = errJson.getString("error_description");
                } else if (errJson.has("error")) {
                    message = errJson.getString("error");
                }
            }
        } catch (Exception ignored) {}

        String lower = message.toLowerCase();
        if (lower.contains("already registered") || lower.contains("user already exists") || lower.contains("duplicate")) {
            message = "An account with this email address already exists. Please sign in instead.";
            isDuplicate = true;
        } else if (lower.contains("invalid login credentials") || lower.contains("invalid grant") || lower.contains("invalid credentials")) {
            message = "Invalid email or password. Please check your credentials and try again.";
        } else if (lower.contains("email not confirmed")) {
            message = "Email confirmation is pending for this account. You may sign in directly or verify credentials.";
        }

        final String finalMsg = message;
        final boolean finalDup = isDuplicate;
        postFailure(callback, finalMsg, finalDup);
    }

    private static void postFailure(AuthCallback callback, String errorMessage, boolean isDuplicate) {
        MAIN_HANDLER.post(() -> callback.onFailure(errorMessage, isDuplicate));
    }
}

