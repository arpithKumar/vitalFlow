package com.vitalflow.app;

import android.app.Application;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class VitalFlowApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }
}
