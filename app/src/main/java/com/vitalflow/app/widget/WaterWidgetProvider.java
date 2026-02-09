package com.vitalflow.app.widget;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.vitalflow.app.MainActivity;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.PeriodLog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class WaterWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_ADD_WATER = "com.vitalflow.app.ACTION_ADD_WATER";
    public static final String ACTION_RESET_WATER = "com.vitalflow.app.ACTION_RESET_WATER";
    public static final String ACTION_LOG_MOOD = "com.vitalflow.app.ACTION_LOG_MOOD";
    public static final String EXTRA_MOOD_VALUE = "extra_mood_value";

    @Inject
    AppDao appDao;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @SuppressLint("CheckResult")
    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_water);
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent appPendingIntent = PendingIntent.getActivity(context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent);

        // Setup Visuals
        setupColors(views);
        setupActions(context, views);

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // 2. RX CHAIN: Fetch Health + Period Log
        Single.zip(
                        // Use .onErrorReturnItem to handle cases where data doesn't exist yet safely
                        appDao.getDailyHealthSync(today)
                                .defaultIfEmpty(new DailyHealth(today, 0, 2000, 0, "0h", 0)),
                        appDao.getLastPeriodLogSync()
                                .defaultIfEmpty(new PeriodLog("", false, 0, "")),
                        (health, period) -> new WidgetData(health, period)
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    // --- UPDATE UI ON MAIN THREAD ---

                    // A. Hydration Logic
                    int intake = data.health.getWaterIntakeMl();
                    int goal = 2000;

                    if (intake >= goal) {
                        views.setViewVisibility(R.id.tvWidgetIntake, View.GONE);
                        views.setViewVisibility(R.id.imgTrophy, View.VISIBLE);
                        views.setTextViewText(R.id.tvTitle, "Goal Reached! 🎉");
                    } else {
                        views.setViewVisibility(R.id.tvWidgetIntake, View.VISIBLE);
                        views.setViewVisibility(R.id.imgTrophy, View.GONE);
                        views.setTextViewText(R.id.tvWidgetIntake, intake + " ml");
                        views.setTextViewText(R.id.tvTitle, "VitalFlow");
                    }

                    // B. Period Logic
                    if (data.period.date != null && !data.period.date.isEmpty()) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            Date date = sdf.parse(data.period.date);
                            if (date != null) {
                                Calendar c = Calendar.getInstance();
                                c.setTime(date);
                                c.add(Calendar.DAY_OF_YEAR, 28);
                                String nextDate = new SimpleDateFormat("MMM dd", Locale.getDefault()).format(c.getTime());

                                views.setTextViewText(R.id.tvWidgetStatus, "Next Period: " + nextDate);
                                views.setViewVisibility(R.id.tvWidgetStatus, View.VISIBLE);
                                views.setViewVisibility(R.id.imgCycleIcon, View.VISIBLE);
                            }
                        } catch (Exception e) {
                            views.setViewVisibility(R.id.tvWidgetStatus, View.GONE);
                            views.setViewVisibility(R.id.imgCycleIcon, View.GONE);
                        }
                    } else {
                        views.setViewVisibility(R.id.tvWidgetStatus, View.GONE);
                        views.setViewVisibility(R.id.imgCycleIcon, View.GONE);
                    }

                    // Push Update
                    appWidgetManager.updateAppWidget(appWidgetId, views);

                }, Throwable::printStackTrace);
    }

    private void setupColors(RemoteViews views) {
        views.setInt(R.id.btnWidgetMood1, "setColorFilter", Color.parseColor("#EF9A9A"));
        views.setInt(R.id.btnWidgetMood2, "setColorFilter", Color.parseColor("#FFCC80"));
        views.setInt(R.id.btnWidgetMood3, "setColorFilter", Color.parseColor("#FFF59D"));
        views.setInt(R.id.btnWidgetMood4, "setColorFilter", Color.parseColor("#A5D6A7"));
        views.setInt(R.id.btnWidgetMood5, "setColorFilter", Color.parseColor("#66BB6A"));
        views.setInt(R.id.imgDrop, "setColorFilter", Color.parseColor("#00897B"));
    }

    private void setupActions(Context context, RemoteViews views) {
        views.setOnClickPendingIntent(R.id.btnWidgetAdd, createPendingIntent(context, ACTION_ADD_WATER, 0));
        views.setOnClickPendingIntent(R.id.btnWidgetReset, createPendingIntent(context, ACTION_RESET_WATER, 99));
        views.setOnClickPendingIntent(R.id.btnWidgetMood1, createMoodPI(context, 1));
        views.setOnClickPendingIntent(R.id.btnWidgetMood2, createMoodPI(context, 2));
        views.setOnClickPendingIntent(R.id.btnWidgetMood3, createMoodPI(context, 3));
        views.setOnClickPendingIntent(R.id.btnWidgetMood4, createMoodPI(context, 4));
        views.setOnClickPendingIntent(R.id.btnWidgetMood5, createMoodPI(context, 5));
    }

    // Helper methods remain the same...
    private PendingIntent createPendingIntent(Context context, String action, int reqCode) {
        Intent intent = new Intent(context, WaterWidgetProvider.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createMoodPI(Context context, int value) {
        Intent intent = new Intent(context, WaterWidgetProvider.class);
        intent.setAction(ACTION_LOG_MOOD);
        intent.putExtra(EXTRA_MOOD_VALUE, value);
        return PendingIntent.getBroadcast(context, value, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    @SuppressLint("CheckResult")
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (appDao == null) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (ACTION_ADD_WATER.equals(intent.getAction())) {
            // RX LOGIC: Get -> Update -> Insert
            appDao.getDailyHealthSync(today)
                    .defaultIfEmpty(new DailyHealth(today, 0, 2000, 0, "0h", 0))
                    .flatMapCompletable(data -> {
                        data.setWaterIntakeMl(data.getWaterIntakeMl() + 250);
                        return appDao.insertDailyHealth(data);
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> triggerUpdate(context), Throwable::printStackTrace);

            Toast.makeText(context, "+250ml Added 💧", Toast.LENGTH_SHORT).show();

        } else if (ACTION_RESET_WATER.equals(intent.getAction())) {
            appDao.getDailyHealthSync(today)
                    .flatMapCompletable(data -> {
                        data.setWaterIntakeMl(0);
                        return appDao.insertDailyHealth(data);
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> triggerUpdate(context), Throwable::printStackTrace);

            Toast.makeText(context, "Water Reset 🔄", Toast.LENGTH_SHORT).show();

        } else if (ACTION_LOG_MOOD.equals(intent.getAction())) {
            int moodValue = intent.getIntExtra(EXTRA_MOOD_VALUE, 3);
            appDao.getDailyHealthSync(today)
                    .defaultIfEmpty(new DailyHealth(today, 0, 2000, 0, "0h", moodValue))
                    .flatMapCompletable(data -> {
                        data.setMoodRating(moodValue);
                        return appDao.insertDailyHealth(data);
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> {
                    }, Throwable::printStackTrace); // No update needed for mood visuals on widget

            Toast.makeText(context, "Mood Logged!", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerUpdate(Context context) {
        AppWidgetManager man = AppWidgetManager.getInstance(context);
        int[] ids = man.getAppWidgetIds(new ComponentName(context, WaterWidgetProvider.class));
        onUpdate(context, man, ids);
    }

    private static class WidgetData {
        DailyHealth health;
        PeriodLog period;

        WidgetData(DailyHealth h, PeriodLog p) {
            this.health = h;
            this.period = p;
        }
    }
}