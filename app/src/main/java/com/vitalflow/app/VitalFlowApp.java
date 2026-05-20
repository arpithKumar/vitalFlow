package com.vitalflow.app;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class VitalFlowApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply theme before any Activity creates its window to prevent cold-start flash
        SharedPreferences prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(
                prefs.getBoolean("is_dark_mode", false)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
