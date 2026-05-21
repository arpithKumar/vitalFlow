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

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class WaterWidgetProvider extends AppWidgetProvider {

    // -----------------------------------------------------------------------
    // Hilt EntryPoint — AppWidgetProvider cannot use @AndroidEntryPoint;
    // use EntryPointAccessors instead (same pattern as MedWidgetProvider).
    // -----------------------------------------------------------------------
    @EntryPoint
    @InstallIn(SingletonComponent.class)
    interface WaterWidgetEntryPoint {
        AppDao getAppDao();
    }

    private static AppDao getAppDao(Context context) {
        return EntryPointAccessors.fromApplication(
                context.getApplicationContext(), WaterWidgetEntryPoint.class).getAppDao();
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------
    public static final String ACTION_ADD_WATER  = "com.vitalflow.app.ACTION_ADD_WATER";
    public static final String ACTION_RESET_WATER = "com.vitalflow.app.ACTION_RESET_WATER";
    public static final String ACTION_LOG_MOOD   = "com.vitalflow.app.ACTION_LOG_MOOD";
    public static final String EXTRA_MOOD_VALUE  = "extra_mood_value";

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @SuppressLint("CheckResult")
    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        AppDao appDao = getAppDao(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_water);
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent);

        setupColors(context, views);
        setupActions(context, views);

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Single.zip(
                        appDao.getDailyHealthSync(today)
                                .defaultIfEmpty(new DailyHealth(today, 0, 2000, 0, "0h", 0)),
                        appDao.getLastPeriodLogSync()
                                .defaultIfEmpty(new PeriodLog("", false, 0, "")),
                        WidgetData::new
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    // Hydration
                    int intake = data.health.getWaterIntakeMl();
                    if (intake >= 2000) {
                        views.setViewVisibility(R.id.tvWidgetIntake, View.GONE);
                        views.setViewVisibility(R.id.imgTrophy, View.VISIBLE);
                        views.setTextViewText(R.id.tvTitle, "Goal Reached! 🎉");
                    } else {
                        views.setViewVisibility(R.id.tvWidgetIntake, View.VISIBLE);
                        views.setViewVisibility(R.id.imgTrophy, View.GONE);
                        views.setTextViewText(R.id.tvWidgetIntake, intake + " ml");
                        views.setTextViewText(R.id.tvTitle, "VitalFlow");
                    }

                    // Period
                    if (data.period.date != null && !data.period.date.isEmpty()) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            Date date = sdf.parse(data.period.date);
                            if (date != null) {
                                Calendar c = Calendar.getInstance();
                                c.setTime(date);
                                c.add(Calendar.DAY_OF_YEAR, 28);
                                String nextDate = new SimpleDateFormat("MMM dd", Locale.getDefault())
                                        .format(c.getTime());
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

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }, Throwable::printStackTrace);
    }

    // -----------------------------------------------------------------------
    // Receive
    // -----------------------------------------------------------------------
    @Override
    @SuppressLint("CheckResult")
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        AppDao appDao = getAppDao(context);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (ACTION_ADD_WATER.equals(intent.getAction())) {
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
                    .subscribe(() -> { }, Throwable::printStackTrace);

            Toast.makeText(context, "Mood Logged!", Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private void setupColors(Context context, RemoteViews views) {
        int nightFlags = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = nightFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        if (isDark) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_dark);
            views.setTextColor(R.id.tvTitle, Color.WHITE);
            views.setTextColor(R.id.tvWidgetIntake, Color.parseColor("#B2DFDB"));
            views.setInt(R.id.btnWidgetMood1, "setColorFilter", Color.parseColor("#E57373"));
            views.setInt(R.id.btnWidgetMood5, "setColorFilter", Color.parseColor("#81C784"));
            views.setInt(R.id.imgDrop, "setColorFilter", Color.parseColor("#4DB6AC"));
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_light);
            views.setTextColor(R.id.tvTitle, Color.BLACK);
            views.setInt(R.id.btnWidgetMood1, "setColorFilter", Color.parseColor("#EF9A9A"));
            views.setInt(R.id.btnWidgetMood5, "setColorFilter", Color.parseColor("#66BB6A"));
            views.setInt(R.id.imgDrop, "setColorFilter", Color.parseColor("#00897B"));
        }
    }

    private void setupActions(Context context, RemoteViews views) {
        views.setOnClickPendingIntent(R.id.btnWidgetAdd,  createPI(context, ACTION_ADD_WATER, 0));
        views.setOnClickPendingIntent(R.id.btnWidgetReset, createPI(context, ACTION_RESET_WATER, 99));
        views.setOnClickPendingIntent(R.id.btnWidgetMood1, createMoodPI(context, 1));
        views.setOnClickPendingIntent(R.id.btnWidgetMood2, createMoodPI(context, 2));
        views.setOnClickPendingIntent(R.id.btnWidgetMood3, createMoodPI(context, 3));
        views.setOnClickPendingIntent(R.id.btnWidgetMood4, createMoodPI(context, 4));
        views.setOnClickPendingIntent(R.id.btnWidgetMood5, createMoodPI(context, 5));
    }

    private PendingIntent createPI(Context context, String action, int reqCode) {
        Intent intent = new Intent(context, WaterWidgetProvider.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createMoodPI(Context context, int value) {
        Intent intent = new Intent(context, WaterWidgetProvider.class);
        intent.setAction(ACTION_LOG_MOOD);
        intent.putExtra(EXTRA_MOOD_VALUE, value);
        return PendingIntent.getBroadcast(context, value, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void triggerUpdate(Context context) {
        AppWidgetManager man = AppWidgetManager.getInstance(context);
        int[] ids = man.getAppWidgetIds(new ComponentName(context, WaterWidgetProvider.class));
        onUpdate(context, man, ids);
    }

    private static class WidgetData {
        DailyHealth health;
        PeriodLog period;
        WidgetData(DailyHealth h, PeriodLog p) { this.health = h; this.period = p; }
    }
}
