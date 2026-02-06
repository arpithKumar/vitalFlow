package com.vitalflow.app.ui.splash;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

public class SplashTimer implements DefaultLifecycleObserver {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runnable;
    private final long delayMs;
    private boolean isTimerRunning = false;

    public SplashTimer(long delayMs, Runnable runnable) {
        this.delayMs = delayMs;
        this.runnable = runnable;
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        // Start/Resume the timer when the app comes to the foreground
        if (!isTimerRunning) {
            handler.postDelayed(runnable, delayMs);
            isTimerRunning = true;
        }
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        // Stop the timer if the user minimizes the app or screen rotates
        handler.removeCallbacks(runnable);
        isTimerRunning = false;
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        // Cleanup to prevent leaks
        handler.removeCallbacks(runnable);
        owner.getLifecycle().removeObserver(this);
    }
}
