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
                notifyComplete(listener, false, e.getMessage());
            }
        }).start();
    }

    // --- THE CORE LOGIC (Safe Order: Download -> Merge -> Upload) ---
    private void performFullSync(OnSyncListener listener) throws ExecutionException, InterruptedException {
        String uid = auth.getCurrentUser().getUid();

        // ---------------------------------------------------------
        // PART A: DOWNLOAD CLOUD DATA FIRST (RESTORE/MERGE)
        // ---------------------------------------------------------
        // We pull the latest cloud data and insert it into Room.
        // Room's OnConflictStrategy.REPLACE ensures that if Cloud data is newer/different,
        // our local DB gets updated BEFORE we try to upload anything.

        notifyProgress(listener, 1, 4); // Progress 25%
        restoreCollection(uid, "medications", Medication.class, appDao::insertMedication);
        restoreCollection(uid, "vitals", Vital.class, appDao::insertVital);

        notifyProgress(listener, 2, 4); // Progress 50%
        restoreCollection(uid, "daily_health", DailyHealth.class, appDao::insertDailyHealth);
        restoreCollection(uid, "period_logs", PeriodLog.class, appDao::insertPeriodLog);
        restoreCollection(uid, "food_logs", FoodLog.class, appDao::insertFoodLog);

        // ---------------------------------------------------------
        // PART B: UPLOAD MERGED DATA TO CLOUD
        // ---------------------------------------------------------
        // Now that Local DB contains the "Merged Truth" (Local changes + Cloud updates),
        // we push this consistent state back to the Cloud.

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

        WriteBatch batch = firestore.batch();
        CollectionReference ref = firestore.collection("users").document(uid).collection(collectionName);

        // Firestore batches represent atomic operations
        // NOTE: Firestore has a limit of 500 ops per batch.
        // For production, you might need to split this if user has >500 items.
        // For this MVP, a single batch per collection is usually fine.

        for (T item : items) {
            String docId = getDocId(item);
            batch.set(ref.document(docId), item, SetOptions.merge());
        }
        Tasks.await(batch.commit()); // Block until upload finishes
    }

    // --- HELPER: RESTORE ---
    private <T> void restoreCollection(String uid, String collectionName, Class<T> type, InsertCallback<T> callback) throws ExecutionException, InterruptedException {
        CollectionReference ref = firestore.collection("users").document(uid).collection(collectionName);

        // Fetch all documents in the collection
        var snapshot = Tasks.await(ref.get());

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            T item = doc.toObject(type);
            if (item != null) {
                callback.insert(item).blockingAwait();
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