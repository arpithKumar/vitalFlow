package com.vitalflow.app.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Vital;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.reactivex.rxjava3.core.Completable;

public class SyncRepository {

    private final AppDao appDao;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnSyncListener {
        void onProgress(int current, int total);

        void onComplete(boolean success, String message);
    }

    @Inject
    public SyncRepository(AppDao appDao) {
        this.appDao = appDao;
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    // --- 1. BLOCKING SYNC (For WorkManager & Login Restore) ---
    public boolean syncDataBlocking() {
        if (auth.getCurrentUser() == null) return false;
        try {
            performFullSync(null);
            return true;
        } catch (Exception e) {
            Log.e("SyncRepo", "Sync Failed", e);
            return false;
        }
    }

    // --- 2. ASYNC SYNC (For Profile UI) ---

    public void syncDataToCloud(OnSyncListener listener) {
        if (auth.getCurrentUser() == null) {
            notifyComplete(listener, false, "User not logged in");
            return;
        }

        // Run the heavy sync logic on a background thread
        new Thread(() -> {
            try {
                performFullSync(listener);
                notifyComplete(listener, true, "Sync Complete");
            } catch (Exception e) {
                Log.e("SyncRepo", "Async Sync Error", e);
                notifyComplete(listener, false, e.getMessage());
            }
        }).start();
    }

    // --- THE CORE LOGIC (Safe Order: Download -> Merge -> Upload) ---
    private void performFullSync(OnSyncListener listener) throws ExecutionException, InterruptedException {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        notifyProgress(listener, 1, 4); // Progress 25%
        restoreCollection(uid, "medications", Medication.class, appDao::insertMedication);
        restoreCollection(uid, "vitals", Vital.class, appDao::insertVital);

        notifyProgress(listener, 2, 4); // Progress 50%
        restoreCollection(uid, "daily_health", DailyHealth.class, appDao::insertDailyHealth);
        restoreCollection(uid, "period_logs", PeriodLog.class, appDao::insertPeriodLog);
        restoreCollection(uid, "food_logs", FoodLog.class, appDao::insertFoodLog);


        notifyProgress(listener, 3, 4); // Progress 75%
        uploadCollection(uid, "medications", appDao.getAllMedicationsSync().blockingGet());
        uploadCollection(uid, "vitals", appDao.getAllVitalsSync().blockingGet());
        uploadCollection(uid, "daily_health", appDao.getAllDailyHealthSync().blockingGet());

        notifyProgress(listener, 4, 4); // Progress 100%
        uploadCollection(uid, "period_logs", appDao.getAllLogsSync().blockingGet());
        uploadCollection(uid, "food_logs", appDao.getAllFoodLogsSync().blockingGet());
    }

    // --- HELPER: UPLOAD ---
    private <T> void uploadCollection(String uid, String collectionName, List<T> items) throws ExecutionException, InterruptedException {
        if (items == null || items.isEmpty()) return;

        CollectionReference ref = firestore.collection("users").document(uid).collection(collectionName);
        List<WriteBatch> batches = new ArrayList<>();
        WriteBatch currentBatch = firestore.batch();
        int count = 0;

        for (T item : items) {
            if (item == null) continue;
            String docId = getDocId(item);
            if (docId == null || docId.trim().isEmpty()) {
                docId = "auto_" + System.currentTimeMillis() + "_" + item.hashCode();
            }

            currentBatch.set(ref.document(docId), item, SetOptions.merge());
            count++;

            if (count >= 400) {
                batches.add(currentBatch);
                currentBatch = firestore.batch();
                count = 0;
            }
        }

        if (count > 0) {
            batches.add(currentBatch);
        }

        batches.forEach(batch -> {
            try {
                Tasks.await(batch.commit());
            } catch (ExecutionException | InterruptedException e) {
                Log.e("SyncRepo", "Couldn't upload batch! ", e);
            }
        });
    }

    // --- HELPER: RESTORE ---
    private <T> void restoreCollection(String uid, String collectionName, Class<T> type, InsertCallback<T> callback) throws ExecutionException, InterruptedException {
        CollectionReference ref = firestore.collection("users").document(uid).collection(collectionName);

        // Fetch all documents in the collection
        var snapshot = Tasks.await(ref.get());

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            try {
                T item = doc.toObject(type);
                if (item != null) {
                    callback.insert(item).blockingAwait();
                }
            } catch (Exception e) {
                Log.e("SyncRepo", "Skipped corrupted document: " + doc.getId(), e);
            }
        }
    }

    // Extracts ID based on object type
    private String getDocId(Object item) {
        if (item instanceof DailyHealth) return ((DailyHealth) item).getDate();
        if (item instanceof PeriodLog) return ((PeriodLog) item).date;
        if (item instanceof Medication) return ((Medication) item).getId();
        if (item instanceof Vital) return ((Vital) item).getId();
        if (item instanceof FoodLog) return ((FoodLog) item).id;
        return String.valueOf(System.currentTimeMillis());
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

    interface InsertCallback<T> {
        Completable insert(T item);
    }
}