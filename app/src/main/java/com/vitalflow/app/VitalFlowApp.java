package com.vitalflow.app;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.vitalflow.app.diagnostics.CrashReporting;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class VitalFlowApp extends Application {

    @Inject
    CrashReporting crashReporting;

    @Override
    public void onCreate() {
        super.onCreate();
        // Match the crash-reporting service to the user's saved opt-in (off by default).
        crashReporting.applySavedChoice();

        // Apply theme before any Activity creates its window to prevent cold-start flash
        SharedPreferences prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(
                prefs.getBoolean("is_dark_mode", false)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);

        // OLED true-black: set window background to black before Activity inflates
        if (prefs.getBoolean("oled_mode", false) && prefs.getBoolean("is_dark_mode", false)) {
            setTheme(R.style.Theme_VitalFlow_OLED);
        }
    }
}
