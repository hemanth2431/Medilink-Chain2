package com.example.medilinkchain;

/**
 * SupabaseConfig — Central configuration for Supabase Storage integration.
 *
 * ══════════════════════════════════════════════════════
 * HOW TO SET YOUR SUPABASE ANON KEY (one-time setup):
 * ══════════════════════════════════════════════════════
 *
 * 1. Open:  local.properties   (in the project root — same folder as build.gradle.kts)
 * 2. Add this line at the bottom:
 *
 *        SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 *
 *    (replace with your actual key from Supabase Dashboard → Project Settings → API → anon/public)
 *
 * 3. Rebuild the project.  Gradle will inject the key into BuildConfig automatically.
 *
 * ══════════════════════════════════════════════════════
 * SECURITY RULES:
 * ══════════════════════════════════════════════════════
 * ✅  Use ONLY the anon / publishable key.
 * ❌  NEVER paste the service_role key here or anywhere in Android code.
 * ✅  local.properties is git-ignored by default — your key stays off Git.
 * ══════════════════════════════════════════════════════
 */
public final class SupabaseConfig {

    private SupabaseConfig() {}

    /** Supabase project REST endpoint. */
    public static final String SUPABASE_URL = "https://jpnnsyatozetlgkwchuy.supabase.co";

    /**
     * Supabase anon key — injected from local.properties via BuildConfig at compile time,
     * or fallback to explicit publishable key.
     */
    public static final String SUPABASE_ANON_KEY =
            (BuildConfig.SUPABASE_ANON_KEY != null && !BuildConfig.SUPABASE_ANON_KEY.isEmpty())
                    ? BuildConfig.SUPABASE_ANON_KEY
                    : "sb_publishable_rlpcxBn8tKCobSjYQJFrvw_p-U2ZzM8";

    /** Storage bucket name. */
    public static final String BUCKET_NAME = "medical-records";

    /** Maximum allowed upload size: 10 MB. */
    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    /** Returns true if the anon key has been configured. */
    public static boolean isConfigured() {
        return SUPABASE_ANON_KEY != null
                && !SUPABASE_ANON_KEY.isEmpty()
                && (SUPABASE_ANON_KEY.startsWith("eyJ") || SUPABASE_ANON_KEY.startsWith("sb_"));
    }
}
