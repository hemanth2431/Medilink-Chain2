package com.example.medilinkchain;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * MedicineBootReceiver — Reschedules medicine alarms upon device reboot or package update.
 */
public class MedicineBootReceiver extends BroadcastReceiver {

    private static final String TAG = "MedicineBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "Device rebooted or package updated. Rescheduling medicine alarms...");

            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getUid() != null) {
                MedicineAlarmManager.rescheduleAllActiveMedications(context.getApplicationContext(), currentUser.getUid());
            }
        }
    }
}
