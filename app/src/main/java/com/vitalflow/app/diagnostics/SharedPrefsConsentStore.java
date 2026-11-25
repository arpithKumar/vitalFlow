package com.vitalflow.app.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the crash-reporting choice in SharedPreferences. The default is "not granted". */
public final class SharedPrefsConsentStore implements CrashReporting.ConsentStore {

    static final String PREFS_NAME = "privacy_prefs";
    static final String KEY_CRASH_REPORTS = "crash_reports_enabled";

    private final SharedPreferences prefs;

    public SharedPrefsConsentStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public boolean isGranted() {
        return prefs.getBoolean(KEY_CRASH_REPORTS, false);
    }

    @Override
    public void setGranted(boolean granted) {
        prefs.edit().putBoolean(KEY_CRASH_REPORTS, granted).apply();
    }
}
