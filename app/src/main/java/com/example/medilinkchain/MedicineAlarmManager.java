package com.example.medilinkchain;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * MedicineAlarmManager — Schedules, reschedules, and cancels native AlarmManager exact reminders
 * in the device's local timezone.
 */
public class MedicineAlarmManager {

    private static final String TAG = "MedicineAlarmManager";
    public static final String CHANNEL_ID_REMINDERS = "medication_reminders_channel";
    public static final String CHANNEL_ID_REFILLS = "medication_refills_channel";

    /**
     * Initializes notification channels for reminders and refills.
     */
    public static void initNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                // 1. Medicine Reminders Channel (High Importance)
                NotificationChannel remChannel = new NotificationChannel(
                        CHANNEL_ID_REMINDERS,
                        "Medicine Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                remChannel.setDescription("Actionable alerts for scheduled medicine doses");
                remChannel.enableVibration(true);
                nm.createNotificationChannel(remChannel);

                // 2. Refill Reminders Channel (Default Importance)
                NotificationChannel refChannel = new NotificationChannel(
                        CHANNEL_ID_REFILLS,
                        "Refill Reminders",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                refChannel.setDescription("Low stock and medicine refill alerts");
                nm.createNotificationChannel(refChannel);
            }
        }
    }

    /**
     * Schedules native exact alarms for all configured dose times for an active medication.
     */
    public static void scheduleMedicationAlarms(Context context, MedicationModel med) {
        if (context == null || med == null) return;
        initNotificationChannels(context);

        // Cancel previous alarms first to prevent duplicates
        cancelMedicationAlarms(context, med);

        // If inactive or past end date, do not schedule
        if (!"ACTIVE".equalsIgnoreCase(med.getStatus()) || med.isCompletedByDate()) {
            Log.d(TAG, "Not scheduling alarms for " + med.getMedicineName() + " (status: " + med.getStatus() + ", completed: " + med.isCompletedByDate() + ")");
            return;
        }

        List<String> times = med.getTimes();
        if (times == null || times.isEmpty()) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        timeFormat.setTimeZone(TimeZone.getDefault());

        for (int i = 0; i < times.size(); i++) {
            String timeStr = times.get(i).trim();
            try {
                Date parsedTime = timeFormat.parse(timeStr);
                if (parsedTime == null) continue;

                Calendar doseCal = Calendar.getInstance(TimeZone.getDefault());
                Calendar timeCal = Calendar.getInstance(TimeZone.getDefault());
                timeCal.setTime(parsedTime);

                doseCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                doseCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                doseCal.set(Calendar.SECOND, 0);
                doseCal.set(Calendar.MILLISECOND, 0);

                // If dose time already passed today, schedule for tomorrow
                Calendar now = Calendar.getInstance(TimeZone.getDefault());
                if (doseCal.before(now)) {
                    doseCal.add(Calendar.DAY_OF_MONTH, 1);
                }

                // If scheduled trigger time is past the course end date, do not schedule
                if (med.getEndDate() != null && !med.getEndDate().isEmpty()) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy", Locale.US);
                    try {
                        Date endDate = dateFormat.parse(med.getEndDate().trim());
                        if (endDate != null) {
                            Calendar endCal = Calendar.getInstance(TimeZone.getDefault());
                            endCal.setTime(endDate);
                            endCal.set(Calendar.HOUR_OF_DAY, 23);
                            endCal.set(Calendar.MINUTE, 59);
                            endCal.set(Calendar.SECOND, 59);
                            if (doseCal.after(endCal)) {
                                continue;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                int requestCode = generateRequestCode(med.getId(), i);

                Intent intent = new Intent(context, MedicineAlarmReceiver.class);
                intent.setAction(MedicineAlarmReceiver.ACTION_MEDICINE_REMINDER);
                intent.putExtra(MedicineAlarmReceiver.EXTRA_MEDICATION_ID, med.getId());
                intent.putExtra(MedicineAlarmReceiver.EXTRA_MEDICINE_NAME, med.getMedicineName());
                intent.putExtra(MedicineAlarmReceiver.EXTRA_DOSAGE, med.getDosage());
                intent.putExtra(MedicineAlarmReceiver.EXTRA_SCHEDULED_TIME, timeStr);
                intent.putExtra(MedicineAlarmReceiver.EXTRA_DOSE_INDEX, i);
                intent.putExtra(MedicineAlarmReceiver.EXTRA_USER_ID, med.getUserId());

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                scheduleExactOrFallback(alarmManager, doseCal.getTimeInMillis(), pendingIntent);
                Log.d(TAG, "Scheduled alarm for " + med.getMedicineName() + " at " + doseCal.getTime().toString());

            } catch (ParseException e) {
                Log.e(TAG, "Failed to parse time string: " + timeStr, e);
            }
        }
    }

    /**
     * Cancels all scheduled reminder alarms for a medication.
     */
    public static void cancelMedicationAlarms(Context context, MedicationModel med) {
        if (context == null || med == null) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        int maxTimes = 10;
        for (int i = 0; i < maxTimes; i++) {
            int requestCode = generateRequestCode(med.getId(), i);
            Intent intent = new Intent(context, MedicineAlarmReceiver.class);
            intent.setAction(MedicineAlarmReceiver.ACTION_MEDICINE_REMINDER);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
    }

    /**
     * Safe exact alarm scheduler that gracefully handles Android 12+ permission policies without throwing.
     */
    private static void scheduleExactOrFallback(AlarmManager alarmManager, long triggerAtMillis, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm permission not granted, falling back to setAndAllowWhileIdle: " + e.getMessage());
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Fallback alarm scheduling error: " + ex.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Alarm scheduling error: " + e.getMessage());
        }
    }

    public static void rescheduleAllActiveMedications(Context context, String userId) {
        if (context == null || userId == null || userId.isEmpty()) return;
        MedicationRepository.fetchMedications(userId, new MedicationRepository.Callback<List<MedicationModel>>() {
            @Override
            public void onSuccess(List<MedicationModel> list) {
                if (list != null) {
                    for (MedicationModel m : list) {
                        if ("ACTIVE".equalsIgnoreCase(m.getStatus()) && !m.isCompletedByDate()) {
                            scheduleMedicationAlarms(context, m);
                        }
                    }
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to reschedule on boot: " + error);
            }
        });
    }

    public static int generateRequestCode(String medId, int doseIndex) {
        int hash = (medId != null ? medId.hashCode() : 0);
        return Math.abs((hash * 31) + doseIndex);
    }
}
