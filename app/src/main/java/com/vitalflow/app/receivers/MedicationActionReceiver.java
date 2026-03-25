package com.vitalflow.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.utils.NotificationHelper;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class MedicationActionReceiver extends BroadcastReceiver {

    @Inject
    AppDao appDao;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String medId = intent.getStringExtra("med_id");
        int notifId = intent.getIntExtra("notif_id", 0);

        // 1. Dismiss the notification immediately so the user knows the tap worked
        NotificationManagerCompat.from(context).cancel(notifId);

        // 2. Handle the action based on the intent's action
        if ("ACTION_MARK_TAKEN".equals(action) && medId != null) {
            // Update Database in Background
            Schedulers.io().scheduleDirect(() -> {
                // NOTE: Make sure your AppDao has a synchronous or single getter for a specific ID!
                appDao.getMedicationById(medId).subscribe(med -> {
                    if (med != null) {
                        int current = med.getTimesTakenToday();
                        if (current < med.getFrequencyPerDay()) {
                            med.setTimesTakenToday(current + 1);
                            if (med.getPillsLeft() > 0) med.setPillsLeft(med.getPillsLeft() - 1);
                            appDao.insertMedication(med).subscribeOn(Schedulers.io()).subscribe();
                        }
                    }
                }, Throwable::printStackTrace);
            });
            Toast.makeText(context, "Medication marked as taken!", Toast.LENGTH_SHORT).show();

        } else if ("ACTION_SNOOZE".equals(action) && medId != null) {
            String medName = intent.getStringExtra("med_name");
            NotificationHelper.scheduleSnooze(context, medId, medName);
            Toast.makeText(context, "Snoozed for 1 hour.", Toast.LENGTH_SHORT).show();
        }
    }
}
