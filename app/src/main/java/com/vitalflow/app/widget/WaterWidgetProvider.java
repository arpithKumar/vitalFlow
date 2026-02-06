package com.vitalflow.app.widget;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class WaterWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_ADD_WATER = "com.vitalflow.app.ACTION_ADD_WATER";
    public static final String ACTION_RESET_WATER = "com.vitalflow.app.ACTION_RESET_WATER";
    public static final String ACTION_LOG_MOOD = "com.vitalflow.app.ACTION_LOG_MOOD";
    public static final String EXTRA_MOOD_VALUE = "extra_mood_value";

    @Inject
    AppDao appDao;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        executor.execute(() -> {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_water);

            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            PendingIntent appPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent);

            // 1. SETUP MOOD COLORS
            views.setInt(R.id.btnWidgetMood1, "setColorFilter", Color.parseColor("#EF9A9A"));
            views.setInt(R.id.btnWidgetMood2, "setColorFilter", Color.parseColor("#FFCC80"));
            views.setInt(R.id.btnWidgetMood3, "setColorFilter", Color.parseColor("#FFF59D"));
            views.setInt(R.id.btnWidgetMood4, "setColorFilter", Color.parseColor("#A5D6A7"));
            views.setInt(R.id.btnWidgetMood5, "setColorFilter", Color.parseColor("#66BB6A"));
            views.setInt(R.id.imgDrop, "setColorFilter", Color.parseColor("#00897B"));

            // 2. SETUP ACTIONS
            views.setOnClickPendingIntent(R.id.btnWidgetAdd,
                    createPendingIntent(context, ACTION_ADD_WATER, 0));
            views.setOnClickPendingIntent(R.id.btnWidgetReset,
                    createPendingIntent(context, ACTION_RESET_WATER, 99));

            views.setOnClickPendingIntent(R.id.btnWidgetMood1, createMoodPI(context, 1));
            views.setOnClickPendingIntent(R.id.btnWidgetMood2, createMoodPI(context, 2));
            views.setOnClickPendingIntent(R.id.btnWidgetMood3, createMoodPI(context, 3));
            views.setOnClickPendingIntent(R.id.btnWidgetMood4, createMoodPI(context, 4));
            views.setOnClickPendingIntent(R.id.btnWidgetMood5, createMoodPI(context, 5));

            // 3. FETCH DATA
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            DailyHealth health = appDao.getDailyHealthSync(today);
            PeriodLog lastPeriod = appDao.getLastPeriodLogSync();

            int intake = (health != null) ? health.getWaterIntakeMl() : 0;
            int goal = 2000;

            // --- A. GOAL CHECK (Trophy Logic) ---
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

            // --- B. PERIOD PREDICTION (UPDATED) ---
            // Logic: If data exists -> Show Date. If NO data -> Hide everything.
            if (lastPeriod != null && lastPeriod.date != null) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date date = sdf.parse(lastPeriod.date);

                    if (date != null) {
                        Calendar c = Calendar.getInstance();
                        c.setTime(date);
                        c.add(Calendar.DAY_OF_YEAR, 28); // Predict next start date

                        String nextDate = new SimpleDateFormat("MMM dd", Locale.getDefault()).format(c.getTime());

                        // SHOW Info
                        views.setTextViewText(R.id.tvWidgetStatus, "Next Menstrual Expected : " + nextDate);
                        views.setViewVisibility(R.id.tvWidgetStatus, View.VISIBLE);
                        views.setViewVisibility(R.id.imgCycleIcon, View.VISIBLE);
                    }
                } catch (Exception e) {
                    // Hide if parsing fails
                    views.setViewVisibility(R.id.tvWidgetStatus, View.GONE);
                    views.setViewVisibility(R.id.imgCycleIcon, View.GONE);
                }
            } else {
                // HIDE if no data (This removes "Hydration Status")
                views.setViewVisibility(R.id.tvWidgetStatus, View.GONE);
                views.setViewVisibility(R.id.imgCycleIcon, View.GONE);
            }

            // Commit Update
            appWidgetManager.updateAppWidget(appWidgetId, views);
        });
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
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (appDao == null) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (ACTION_ADD_WATER.equals(intent.getAction())) {
            executor.execute(() -> {
                DailyHealth data = appDao.getDailyHealthSync(today);
                if (data == null) data = new DailyHealth(today, 250, 2000, 0, "0h", 0);
                else data.setWaterIntakeMl(data.getWaterIntakeMl() + 250);
                appDao.insertDailyHealth(data);
                triggerUpdate(context);
            });
            Toast.makeText(context, "+250ml Added 💧", Toast.LENGTH_SHORT).show();

        } else if (ACTION_RESET_WATER.equals(intent.getAction())) {
            executor.execute(() -> {
                DailyHealth data = appDao.getDailyHealthSync(today);
                if (data != null) {
                    data.setWaterIntakeMl(0); // Reset
                    appDao.insertDailyHealth(data);
                    triggerUpdate(context);
                }
            });
            Toast.makeText(context, "Water Reset 🔄", Toast.LENGTH_SHORT).show();

        } else if (ACTION_LOG_MOOD.equals(intent.getAction())) {
            int moodValue = intent.getIntExtra(EXTRA_MOOD_VALUE, 3);
            executor.execute(() -> {
                DailyHealth data = appDao.getDailyHealthSync(today);
                if (data == null) data = new DailyHealth(today, 0, 2000, 0, "0h", moodValue);
                else data.setMoodRating(moodValue);
                appDao.insertDailyHealth(data);
            });
            Toast.makeText(context, "Mood Logged!", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerUpdate(Context context) {
        AppWidgetManager man = AppWidgetManager.getInstance(context);
        int[] ids = man.getAppWidgetIds(new ComponentName(context, WaterWidgetProvider.class));
        onUpdate(context, man, ids);
    }
}