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
        return success ? Result.success() : Result.retry();
    }

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    public interface SyncWorkerEntryPoint {
        SyncRepository getSyncRepository();
    }
}