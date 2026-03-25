package com.vitalflow.app.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.utils.NotificationHelper;

public class SnoozeWorker extends Worker {

    public SnoozeWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String medId = getInputData().getString("med_id");
        String medName = getInputData().getString("med_name");

        if (medId != null && medName != null) {
            // We create a temporary Medication object just to pass back to the NotificationHelper
            Medication snoozeMed = new Medication();
            snoozeMed.setId(medId);
            snoozeMed.setName(medName);
            snoozeMed.setDosage("Snoozed Reminder");
            snoozeMed.setTime("Now");

            NotificationHelper.sendMedicationReminder(getApplicationContext(), snoozeMed);
        }

        return Result.success();
    }
}
