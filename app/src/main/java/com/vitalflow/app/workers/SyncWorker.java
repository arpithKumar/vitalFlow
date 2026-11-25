package com.vitalflow.app.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vitalflow.app.data.repository.SyncRepository;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SyncRepository repository = EntryPointAccessors.fromApplication(
                getApplicationContext(),
                SyncWorkerEntryPoint.class
        ).getSyncRepository();

        boolean success = repository.syncDataBlocking();
        // Return failure (not retry) so WorkManager does not loop on PERMISSION_DENIED
        // or other persistent Firestore errors. Transient network errors are handled
        // inside SyncRepository which catches all exceptions and returns false.
        return success ? Result.success() : Result.failure();
    }

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface SyncWorkerEntryPoint {
        SyncRepository getSyncRepository();
    }
}