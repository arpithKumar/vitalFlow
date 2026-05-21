package com.vitalflow.app.workers;

import android.content.Context;
import android.util.Log;

import com.vitalflow.app.BuildConfig;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.utils.HealthConnectManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import kotlin.Unit;

public class HealthSyncWorker extends Worker {

    public HealthSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (BuildConfig.DEBUG) Log.d("HealthSyncWorker", "Starting health sync...");

        HealthWorkerEntryPoint entryPoint = EntryPointAccessors.fromApplication(
                getApplicationContext(),
                HealthWorkerEntryPoint.class
        );
        AppDao appDao = entryPoint.getAppDao();

        HealthConnectManager hcManager = new HealthConnectManager(getApplicationContext());
        if (!hcManager.isAvailable()) return Result.success();

        String todayDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
        DailyHealth dailyHealth;

        try {
            dailyHealth = appDao.getDailyHealthSync(todayDateKey)
                    .blockingGet();
        } catch (Exception e) {
            Log.e("HealthSyncWorker", "Error fetching daily health data", e);
            dailyHealth = null;
        }

        if (dailyHealth == null) {
            dailyHealth = new DailyHealth(todayDateKey, 0, 2000, 0, "0h 0m", 0);
        }

        CountDownLatch latch = new CountDownLatch(3);
        final DailyHealth finalHealth = dailyHealth;

        hcManager.readTodaySteps(steps -> {
            if (steps > finalHealth.getStepCount()) {
                finalHealth.setStepCount(steps.intValue());
            }
            latch.countDown();
            return Unit.INSTANCE;
        });

        hcManager.readSleepAndCalories((calories, sleep) -> {
            if (!sleep.equals("0h 0m")) {
                finalHealth.setSleepDuration(sleep);
            }
            latch.countDown();
            return Unit.INSTANCE;
        });

        hcManager.readAvgHeartRate(bpm -> {
            if (bpm > 0) {
                Vital hrVital = new Vital("Heart Rate", String.valueOf(bpm), null, "bpm", todayDateKey, System.currentTimeMillis(), "Health Connect", null);
                appDao.insertVital(hrVital).subscribe();
            }
            latch.countDown();
            return Unit.INSTANCE;
        });

        try {
            latch.await(15, TimeUnit.SECONDS);
            appDao.insertDailyHealth(finalHealth).blockingAwait();
            if (BuildConfig.DEBUG) Log.d("HealthSyncWorker", "Health sync completed successfully");
            return Result.success();
        } catch (Exception e) {
            Log.e("HealthSyncWorker", "Error syncing health data", e);
            return Result.retry();
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface HealthWorkerEntryPoint {
        AppDao getAppDao();
    }
}
