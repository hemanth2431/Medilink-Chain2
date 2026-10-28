import { createClient } from "@supabase/supabase-js";

// MediLinkChain Supabase Configuration
// Base URL must point to the project domain root, allowing auth/v1, rest/v1, and storage/v1 routing
const SUPABASE_URL = "https://jpnnsyatozetlgkwchuy.supabase.co";
const SUPABASE_ANON_KEY = "sb_publishable_rlpcxBn8tKCobSjYQJFrvw_p-U2ZzM8";

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: true,
  },
});

/**
 * Signs up a new patient directly with email and password without requiring email confirmation redirect.
 */
export async function signUpPatient(email, password) {
  const { data, error } = await supabase.auth.signUp({
    email,
    password,
    options: {
      data: {
        role: "PATIENT",
      },
    },
  });

  if (error) throw error;
  return data;
}

/**
 * Signs in using registered email address and password via Supabase Auth.
 * Returns the authenticated user session.
 */
export async function signIn(email, password) {
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password,
  });

  if (error) throw error;
  return data;
}

/**
 * Dispatches password recovery email using Supabase Auth.
 */
export async function requestPasswordReset(email, redirectTo = `${window.location.origin}/reset-password`) {
  const { data, error } = await supabase.auth.resetPasswordForEmail(email, {
    redirectTo,
  });

  if (error) throw error;
  return data;
}

/**
 * Resends email verification confirmation to the registered address.
 */
export async function resendEmailVerification(email) {
  const { data, error } = await supabase.auth.resend({
    type: "signup",
    email,
  });

  if (error) throw error;
  return data;
}

/**
 * Updates password during active password-recovery session.
 */
export async function updatePassword(newPassword) {
  const { data, error } = await supabase.auth.updateUser({
    password: newPassword,
  });

  if (error) throw error;
  return data;
}

/**
 * Fetches medical records scoped to the authenticated patient's ID.
 */
export async function fetchPatientMedicalRecords(patientId) {
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) throw new Error("Unauthenticated user session.");

  const { data, error } = await supabase
    .from("medical_records")
    .select("*")
    .eq("patient_id", patientId || user.id)
    .order("created_at", { ascending: false });

  if (error) throw error;
  return data;
}

/**
 * Safely terminates authenticated session and clears local cached state.
 */
export async function signOutPatient() {
  const { error } = await supabase.auth.signOut();
  if (error) throw error;
}

export default supabase;
