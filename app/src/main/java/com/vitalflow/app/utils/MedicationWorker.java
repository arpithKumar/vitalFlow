package com.vitalflow.app.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

public class MedicationWorker extends Worker {

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    interface WorkerEntryPoint {
        AppDao getAppDao();
    }

    public MedicationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        // 1. Manual Hilt Injection
        WorkerEntryPoint hiltEntryPoint = EntryPointAccessors.fromApplication(context, WorkerEntryPoint.class);
        AppDao appDao = hiltEntryPoint.getAppDao();

        // 2. Fetch Data (RxJava Fix)
        List<Medication> meds;
        try {
            // FIX: Use blockingGet() to unwrap the Single<List<Medication>>
            meds = appDao.getAllMedicationsSync().blockingGet();
        } catch (Exception e) {
            Log.e("MedWorker", "Failed to fetch meds", e);
            return Result.failure();
        }

        if (meds == null || meds.isEmpty()) return Result.success();

        // 3. Check Logic
        checkMedications(context, meds);

        return Result.success();
    }

    private void checkMedications(Context context, List<Medication> meds) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        long nowMillis = System.currentTimeMillis();

        for (Medication m : meds) {
            // A. Check Expiry
            if (m.getEndDate() != null && !m.getEndDate().equalsIgnoreCase("Forever") && m.getEndDate().compareTo(today) < 0) {
                continue;
            }

            // B. Check if already completed today
            if (m.getLastActionDate() != null && m.getLastActionDate().equals(today)) {
                if (m.getTimesTakenToday() >= m.getFrequencyPerDay()) continue;
            }

            // C. Calculate Time
            try {
                SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                Date startTime = sdfTime.parse(m.getTime());

                if (startTime != null) {
                    Calendar startCal = Calendar.getInstance();
                    startCal.setTime(startTime);

                    Calendar target = Calendar.getInstance();
                    target.setTimeInMillis(nowMillis); // Base on current day

                    target.set(Calendar.HOUR_OF_DAY, startCal.get(Calendar.HOUR_OF_DAY));
                    target.set(Calendar.MINUTE, startCal.get(Calendar.MINUTE));
                    target.set(Calendar.SECOND, 0);

                    // Add 6 hours for every dose already taken
                    int taken = (m.getLastActionDate() != null && m.getLastActionDate().equals(today)) ? m.getTimesTakenToday() : 0;
                    target.add(Calendar.HOUR_OF_DAY, taken * 6);

                    long diff = target.getTimeInMillis() - nowMillis;
                    long diffMinutes = diff / (1000 * 60);

                    // D. Trigger if due in next 20 mins OR slightly overdue (-5 mins)
                    if (diffMinutes >= -5 && diffMinutes <= 20) {
                        NotificationHelper.sendMedicationReminder(context, m.getName(), sdfTime.format(target.getTime()));
                    }
                }
            } catch (Exception e) {
                Log.e("MedWorker", "Date parsing error", e);
            }
        }
    }
}