package com.vitalflow.app.data.repository;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Vital;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SyncRepository {

    private final FirebaseFirestore db;
    private final AppDao appDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public SyncRepository(AppDao appDao) {
        this.db = FirebaseFirestore.getInstance();
        this.appDao = appDao;
    }

    @SuppressLint("CheckResult")
    public void syncDataToCloud(OnSyncListener listener) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            notifyComplete(listener, false, "User not logged in");
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Single.zip(
                        appDao.getAllMedicationsSync(),
                        appDao.getAllVitalsSync(),
                        appDao.getAllDailyHealthSync(),
                        appDao.getAllLogsSync(), // Added Period Logs
                        (meds, vitals, daily, logs) -> new SyncData(meds, vitals, daily, logs)
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()) // Perform UI callbacks on Main
                .subscribe(data -> {

                    int totalItems = data.meds.size() + data.vitals.size() + data.daily.size() + data.logs.size();

                    if (totalItems == 0) {
                        notifyComplete(listener, true, "Nothing to sync");
                        return;
                    }

                    // Notify Start
                    notifyProgress(listener, 0, totalItems);
                    AtomicInteger syncedCount = new AtomicInteger(0);

                    // --- BATCH 1: Medications ---
                    WriteBatch batchMeds = db.batch();
                    for (Medication m : data.meds) {
                        batchMeds.set(db.collection("users").document(userId).collection("medications").document(m.getId()), m);
                    }
                    Task<Void> taskMeds = batchMeds.commit().addOnSuccessListener(v -> notifyProgress(listener, syncedCount.addAndGet(data.meds.size()), totalItems));

                    // --- BATCH 2: Vitals ---
                    WriteBatch batchVitals = db.batch();
                    for (Vital v : data.vitals) {
                        batchVitals.set(db.collection("users").document(userId).collection("vitals").document(v.getId()), v);
                    }
                    Task<Void> taskVitals = batchVitals.commit().addOnSuccessListener(v -> notifyProgress(listener, syncedCount.addAndGet(data.vitals.size()), totalItems));

                    // --- BATCH 3: Daily Health ---
                    WriteBatch batchDaily = db.batch();
                    for (DailyHealth d : data.daily) {
                        batchDaily.set(db.collection("users").document(userId).collection("daily_health").document(d.getDate()), d);
                    }
                    Task<Void> taskDaily = batchDaily.commit().addOnSuccessListener(v -> notifyProgress(listener, syncedCount.addAndGet(data.daily.size()), totalItems));

                    // --- BATCH 4: Period Logs ---
                    WriteBatch batchLogs = db.batch();
                    for (PeriodLog p : data.logs) {
                        batchLogs.set(db.collection("users").document(userId).collection("period_logs").document(p.date), p);
                    }
                    Task<Void> taskLogs = batchLogs.commit().addOnSuccessListener(v -> notifyProgress(listener, syncedCount.addAndGet(data.logs.size()), totalItems));

                    // Wait for ALL batches to finish
                    Tasks.whenAll(taskMeds, taskVitals, taskDaily, taskLogs)
                            .addOnSuccessListener(aVoid -> notifyComplete(listener, true, "Synced " + totalItems + " items"))
                            .addOnFailureListener(e -> notifyComplete(listener, false, "Sync failed: " + e.getMessage()));

                }, error -> notifyComplete(listener, false, "Local DB Error: " + error.getMessage()));
    }

    /**
     * BLOCKING version for WorkManager.
     */
    public boolean syncDataBlocking() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return false;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        try {
            // 1. Fetch Data Synchronously (on background thread)
            SyncData data = Single.zip(
                    appDao.getAllMedicationsSync(),
                    appDao.getAllVitalsSync(),
                    appDao.getAllDailyHealthSync(),
                    appDao.getAllLogsSync(),
                    SyncData::new
            ).blockingGet(); // Blocks until DB reads are done

            if (data == null) return false;

            // 2. Prepare Batches
            WriteBatch batch = db.batch(); // Combine into one big batch for background sync

            for (Medication m : data.meds)
                batch.set(db.collection("users").document(userId).collection("medications").document(m.getId()), m);

            for (Vital v : data.vitals)
                batch.set(db.collection("users").document(userId).collection("vitals").document(v.getId()), v);

            for (DailyHealth d : data.daily)
                batch.set(db.collection("users").document(userId).collection("daily_health").document(d.getDate()), d);

            for (PeriodLog p : data.logs)
                batch.set(db.collection("users").document(userId).collection("period_logs").document(p.date), p);

            // 3. Execute Blocking Upload
            Tasks.await(batch.commit());
            return true;

        } catch (Exception e) {
            Log.e("SyncRepository", "Background sync failed", e);
            return false;
        }
    }

    private static class SyncData {
        List<Medication> meds;
        List<Vital> vitals;
        List<DailyHealth> daily;
        List<PeriodLog> logs;

        SyncData(List<Medication> meds, List<Vital> vitals, List<DailyHealth> daily, List<PeriodLog> logs) {
            this.meds = meds;
            this.vitals = vitals;
            this.daily = daily;
            this.logs = logs;
        }
    }

    private void notifyProgress(OnSyncListener listener, int current, int total) {
        if (listener != null) {
            mainHandler.post(() -> listener.onProgress(current, total));
        }
    }

    private void notifyComplete(OnSyncListener listener, boolean success, String message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onComplete(success, message));
        }
    }

    public interface OnSyncListener {
        void onProgress(int current, int total);

        void onComplete(boolean success, String message);
    }
}