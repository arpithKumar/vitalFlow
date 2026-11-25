package com.vitalflow.app.di;

import android.content.Context;

import com.vitalflow.app.diagnostics.CrashReporting;
import com.vitalflow.app.diagnostics.FirebaseCrashBackend;
import com.vitalflow.app.diagnostics.SharedPrefsConsentStore;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DiagnosticsModule {

    @Provides
    @Singleton
    public CrashReporting provideCrashReporting(@ApplicationContext Context context) {
        return new CrashReporting(new SharedPrefsConsentStore(context), new FirebaseCrashBackend());
    }
}
