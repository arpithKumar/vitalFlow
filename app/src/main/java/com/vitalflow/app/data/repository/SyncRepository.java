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
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
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
            int skipped = performFullSync(null);
            // A retry cannot fix a record that does not map to the entity, so this is not a failure.
            if (skipped > 0) Log.w("SyncRepo", skipped + " cloud records could not be restored");
            return true;
        } catch (Exception e) {
            Log.e("SyncRepo", "Sync Failed", e);
            return false;
        }
    }

    // --- 2. ASYNC SYNC (For Profile UI) ---

    // --- 3. DELETE PROPAGATION (called from ViewModels on local delete) ---
    // Local Room deletes never removed the matching Firestore doc, so the next
    // download-merge-upload sync would re-download and resurrect the deleted item.
    public void deleteFromCloud(String collectionName, String docId) {
        if (auth.getCurrentUser() == null || docId == null || docId.trim().isEmpty()) return;
        String uid = auth.getCurrentUser().getUid();
        firestore.collection("users").document(uid).collection(collectionName).document(docId)
                .delete()
                .addOnFailureListener(e -> Log.e("SyncRepo", "Cloud delete failed for " + collectionName + "/" + docId, e));
    }

    public void syncDataToCloud(OnSyncListener listener) {
        if (auth.getCurrentUser() == null) {
            notifyComplete(listener, false, "User not logged in");
            return;
        }
        Single.fromCallable(() -> performFullSync(listener))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        skipped -> {
                            // The upload worked, but some cloud records could not be turned back into
                            // local ones. Say so instead of reporting a clean sync.
                            if (skipped > 0) {
                                notifyComplete(listener, false,
                                        skipped + " records could not be restored from the cloud");
                            } else {
                                notifyComplete(listener, true, "Sync Complete");
                            }
                        },
                        e -> {
                            Log.e("SyncRepo", "Async Sync Error", e);
                            notifyComplete(listener, false, e.getMessage());
                        }
                );
    }

    // --- THE CORE LOGIC (Safe Order: Download -> Merge -> Upload) ---
    /**
     * Downloads, merges and uploads everything.
     *
     * @return how many cloud documents could not be turned back into local records (0 = a clean sync)
     */
    private int performFullSync(OnSyncListener listener) throws ExecutionException, InterruptedException {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        int skipped = 0;

        notifyProgress(listener, 1, 4); // Progress 25%
        skipped += restoreCollection(uid, "medications", Medication.class, appDao::insertMedication);
        skipped += restoreCollection(uid, "vitals", Vital.class, appDao::insertVital);

        notifyProgress(listener, 2, 4); // Progress 50%
        skipped += restoreCollection(uid, "daily_health", DailyHealth.class, appDao::insertDailyHealth);
        skipped += restoreCollection(uid, "period_logs", PeriodLog.class, appDao::insertPeriodLog);
        skipped += restoreCollection(uid, "food_logs", FoodLog.class, appDao::insertFoodLog);


        notifyProgress(listener, 3, 4); // Progress 75%
        uploadCollection(uid, "medications", appDao.getAllMedicationsSync().blockingGet());
        uploadCollection(uid, "vitals", appDao.getAllVitalsSync().blockingGet());
        uploadCollection(uid, "daily_health", appDao.getAllDailyHealthSync().blockingGet());

        notifyProgress(listener, 4, 4); // Progress 100%
        uploadCollection(uid, "period_logs", appDao.getAllLogsSync().blockingGet());
        uploadCollection(uid, "food_logs", appDao.getAllFoodLogsSync().blockingGet());
        return skipped;
    }

    // --- HELPER: UPLOAD ---
    private <T> void uploadCollection(String uid, String collectionName, List<T> items) {
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
    /** @return how many documents of this collection could not be restored */
    private <T> int restoreCollection(String uid, String collectionName, Class<T> type, InsertCallback<T> callback) throws ExecutionException, InterruptedException {
        CollectionReference ref = firestore.collection("users").document(uid).collection(collectionName);

        // Fetch all documents in the collection
        var snapshot = Tasks.await(ref.get());

        int skipped = 0;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            try {
                T item = doc.toObject(type);
                if (item != null) {
                    callback.insert(item).blockingAwait();
                }
            } catch (Exception e) {
                skipped++;
                Log.e("SyncRepo", "Could not restore " + collectionName + "/" + doc.getId(), e);
            }
        }
        return skipped;
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