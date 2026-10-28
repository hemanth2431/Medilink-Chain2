package com.example.medilinkchain;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * MedicineAlarmReceiver — Handles scheduled alarm triggers and notification quick action clicks (TAKEN / SKIPPED).
 */
public class MedicineAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "MedicineAlarmReceiver";

    public static final String ACTION_MEDICINE_REMINDER = "com.example.medilinkchain.ACTION_MEDICINE_REMINDER";
    public static final String ACTION_MEDICINE_TAKEN = "com.example.medilinkchain.ACTION_MEDICINE_TAKEN";
    public static final String ACTION_MEDICINE_SKIPPED = "com.example.medilinkchain.ACTION_MEDICINE_SKIPPED";

    public static final String EXTRA_MEDICATION_ID = "extra_medication_id";
    public static final String EXTRA_MEDICINE_NAME = "extra_medicine_name";
    public static final String EXTRA_DOSAGE = "extra_dosage";
    public static final String EXTRA_SCHEDULED_TIME = "extra_scheduled_time";
    public static final String EXTRA_DOSE_INDEX = "extra_dose_index";
    public static final String EXTRA_USER_ID = "extra_user_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        MedicineAlarmManager.initNotificationChannels(context);

        String action = intent.getAction();
        String medId = intent.getStringExtra(EXTRA_MEDICATION_ID);
        String medName = intent.getStringExtra(EXTRA_MEDICINE_NAME);
        String dosage = intent.getStringExtra(EXTRA_DOSAGE);
        String scheduledTime = intent.getStringExtra(EXTRA_SCHEDULED_TIME);
        int doseIndex = intent.getIntExtra(EXTRA_DOSE_INDEX, 0);
        String userId = intent.getStringExtra(EXTRA_USER_ID);

        if (userId == null || userId.isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) userId = user.getUid();
        }

        if (ACTION_MEDICINE_REMINDER.equals(action)) {
            showMedicineReminderNotification(context, medId, medName, dosage, scheduledTime, doseIndex, userId);
        } else if (ACTION_MEDICINE_TAKEN.equals(action)) {
            dismissNotification(context, medId, doseIndex);
            handleMedicineAction(context, medId, medName, dosage, scheduledTime, userId, "TAKEN");
        } else if (ACTION_MEDICINE_SKIPPED.equals(action)) {
            dismissNotification(context, medId, doseIndex);
            handleMedicineAction(context, medId, medName, dosage, scheduledTime, userId, "SKIPPED");
        }
    }

    private void showMedicineReminderNotification(
            Context context,
            String medId,
            String medName,
            String dosage,
            String scheduledTime,
            int doseIndex,
            String userId) {

        int notificationId = MedicineAlarmManager.generateRequestCode(medId, doseIndex);

        // Tap content opens MedicineDashboardActivity
        Intent openIntent = new Intent(context, MedicineDashboardActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action: TAKEN
        Intent takenIntent = new Intent(context, MedicineAlarmReceiver.class);
        takenIntent.setAction(ACTION_MEDICINE_TAKEN);
        takenIntent.putExtra(EXTRA_MEDICATION_ID, medId);
        takenIntent.putExtra(EXTRA_MEDICINE_NAME, medName);
        takenIntent.putExtra(EXTRA_DOSAGE, dosage);
        takenIntent.putExtra(EXTRA_SCHEDULED_TIME, scheduledTime);
        takenIntent.putExtra(EXTRA_DOSE_INDEX, doseIndex);
        takenIntent.putExtra(EXTRA_USER_ID, userId);
        PendingIntent takenPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 100000,
                takenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action: SKIPPED
        Intent skippedIntent = new Intent(context, MedicineAlarmReceiver.class);
        skippedIntent.setAction(ACTION_MEDICINE_SKIPPED);
        skippedIntent.putExtra(EXTRA_MEDICATION_ID, medId);
        skippedIntent.putExtra(EXTRA_MEDICINE_NAME, medName);
        skippedIntent.putExtra(EXTRA_DOSAGE, dosage);
        skippedIntent.putExtra(EXTRA_SCHEDULED_TIME, scheduledTime);
        skippedIntent.putExtra(EXTRA_DOSE_INDEX, doseIndex);
        skippedIntent.putExtra(EXTRA_USER_ID, userId);
        PendingIntent skippedPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 200000,
                skippedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Medicine Reminder";
        String contentText = "Time to take " + (medName != null ? medName : "your medicine")
                + (dosage != null && !dosage.isEmpty() ? " (" + dosage + ")" : "") + ".";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MedicineAlarmManager.CHANNEL_ID_REMINDERS)
                .setSmallIcon(R.drawable.ic_pill_custom)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_check_circle, "TAKEN", takenPendingIntent)
                .addAction(R.drawable.ic_cancel, "SKIPPED", skippedPendingIntent);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notificationId, builder.build());
        }
    }

    private void dismissNotification(Context context, String medId, int doseIndex) {
        int notificationId = MedicineAlarmManager.generateRequestCode(medId, doseIndex);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(notificationId);
        }
    }

    private void handleMedicineAction(
            Context context,
            String medId,
            String medName,
            String dosage,
            String scheduledTime,
            String userId,
            String action) {

        if (userId == null || userId.isEmpty() || medId == null || medId.isEmpty()) return;

        MedicationRepository.fetchMedications(userId, new MedicationRepository.Callback<java.util.List<MedicationModel>>() {
            @Override
            public void onSuccess(java.util.List<MedicationModel> list) {
                if (list == null) return;
                for (MedicationModel med : list) {
                    if (med.getId() != null && med.getId().equals(medId)) {
                        MedicationRepository.recordMedicationAction(med, scheduledTime, action, new MedicationRepository.Callback<MedicationModel>() {
                            @Override
                            public void onSuccess(MedicationModel updated) {
                                Log.d(TAG, "Successfully recorded " + action + " for " + updated.getMedicineName());
                                // If running low, show refill notification
                                if ("TAKEN".equalsIgnoreCase(action) && updated.isRefillNeeded()) {
                                    showRefillNotification(context, updated);
                                }
                            }

                            @Override
                            public void onFailure(String error) {
                                Log.e(TAG, "Failed to record " + action + ": " + error);
                            }
                        });
                        break;
                    }
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to fetch med for notification action: " + error);
            }
        });
    }

    private void showRefillNotification(Context context, MedicationModel med) {
        int notifId = (med.getId() != null ? med.getId().hashCode() : 1) + 999;
        Intent intent = new Intent(context, MedicineDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "⚠️ Medicine Refill Reminder";
        String message = "Your medicine " + med.getMedicineName() + " is running low (" + med.getRemainingTablets() + " remaining). Please refill soon.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MedicineAlarmManager.CHANNEL_ID_REFILLS)
                .setSmallIcon(R.drawable.ic_pill_custom)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notifId, builder.build());
        }
    }
}
