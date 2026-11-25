package com.vitalflow.app.diagnostics;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

/** Switches Firebase Crashlytics collection on or off. */
public final class FirebaseCrashBackend implements CrashReporting.Backend {

    @Override
    public void setCollectionEnabled(boolean enabled) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled);
    }

    @Override
    public void deleteUnsentReports() {
        FirebaseCrashlytics.getInstance().deleteUnsentReports();
    }
}
