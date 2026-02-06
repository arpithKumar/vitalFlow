package com.vitalflow.app.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.Vital;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

public class SyncRepository {

    private final FirebaseFirestore db;
    private final AppDao appDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public SyncRepository(AppDao appDao) {
        this.db = FirebaseFirestore.getInstance();
        this.appDao = appDao;
    }

    public void syncDataToCloud(OnSyncListener listener) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            postComplete(listener, false, "User not logged in");
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        executor.execute(() -> {
            try {
                // 1. Fetch all local data
                List<Medication> meds = appDao.getAllMedicationsSync();
                List<Vital> vitals = appDao.getAllVitalsSync();
                List<DailyHealth> dailyHealth = appDao.getAllDailyHealthSync();

                int totalItems = meds.size() + vitals.size() + dailyHealth.size();

                if (totalItems == 0) {
                    postComplete(listener, true, "Nothing to sync");
                    return;
                }

                // Notify Total Size
                postProgress(listener, 0, totalItems);

                AtomicInteger syncedCount = new AtomicInteger(0);

                // --- Meds ---
                WriteBatch batchMeds = db.batch();
                for (Medication m : meds) {
                    batchMeds.set(db.collection("users").document(userId).collection("medications").document(String.valueOf(m.getId())), m);
                }
                Task<Void> taskMeds = batchMeds.commit().addOnSuccessListener(v -> {
                    int current = syncedCount.addAndGet(meds.size());
                    postProgress(listener, current, totalItems);
                });

                // --- Vitals ---
                WriteBatch batchVitals = db.batch();
                for (Vital v : vitals) {
                    batchVitals.set(db.collection("users").document(userId).collection("vitals").document(v.getId()), v);
                }
                Task<Void> taskVitals = batchVitals.commit().addOnSuccessListener(v -> {
                    int current = syncedCount.addAndGet(vitals.size());
                    postProgress(listener, current, totalItems);
                });

                // --- Daily ---
                WriteBatch batchDaily = db.batch();
                for (DailyHealth d : dailyHealth) {
                    batchDaily.set(db.collection("users").document(userId).collection("daily_health").document(d.getDate()), d);
                }
                Task<Void> taskDaily = batchDaily.commit().addOnSuccessListener(v -> {
                    int current = syncedCount.addAndGet(dailyHealth.size());
                    postProgress(listener, current, totalItems);
                });

                // Wait for all
                Tasks.whenAll(taskMeds, taskVitals, taskDaily)
                        .addOnSuccessListener(aVoid -> postComplete(listener, true, "Synced " + totalItems + " items"))
                        .addOnFailureListener(e -> postComplete(listener, false, "Sync failed: " + e.getMessage()));

            } catch (Exception e) {
                postComplete(listener, false, "Local DB Error: " + e.getMessage());
            }
        });
    }

    /**
     * BLOCKING version for WorkManager.
     */
    public boolean syncDataBlocking() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return false;

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        syncDataToCloud(new OnSyncListener() {
            @Override
            public void onProgress(int current, int total) {
            }

            @Override
            public void onComplete(boolean success, String message) {
                result[0] = success;
                latch.countDown();
            }
        });

        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
        return result[0];
    }

    private void postProgress(OnSyncListener listener, int current, int total) {
        if (listener != null) mainHandler.post(() -> listener.onProgress(current, total));
    }

    private void postComplete(OnSyncListener listener, boolean success, String message) {
        if (listener != null) mainHandler.post(() -> listener.onComplete(success, message));
    }

    public interface OnSyncListener {
        void onProgress(int current, int total);

        void onComplete(boolean success, String message);
    }
}