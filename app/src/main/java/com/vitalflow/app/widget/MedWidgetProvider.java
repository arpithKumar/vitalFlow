package com.vitalflow.app.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.di.DatabaseModule;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;

public class MedWidgetProvider extends AppWidgetProvider {

    @EntryPoint
    @InstallIn(SingletonComponent.class)
    interface MedWidgetEntryPoint {
        AppDao getAppDao();
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        new Thread(() -> {
            try {
                MedWidgetEntryPoint ep = EntryPointAccessors.fromApplication(
                        context.getApplicationContext(), MedWidgetEntryPoint.class);
                AppDao dao = ep.getAppDao();
                List<Medication> meds = dao.getAllMedicationsSync().blockingGet();

                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String todayDow = new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date());
                int nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

                Medication next = null;
                for (Medication m : meds) {
                    if (m.getEndDate() != null && !m.getEndDate().equalsIgnoreCase("Forever")
                            && m.getEndDate().compareTo(today) < 0) continue;
                    String days = m.getSelectedDays();
                    if (days != null && !days.equals("Everyday") && !days.contains(todayDow)) continue;
                    if ("TAKEN".equalsIgnoreCase(m.getStatus())) continue;
                    String timeSlot = m.getTime() != null ? m.getTime().toLowerCase(Locale.ROOT) : "";
                    int slotHour = timeSlot.contains("morning") ? 8
                            : timeSlot.contains("afternoon") ? 13
                            : timeSlot.contains("evening") ? 18
                            : timeSlot.contains("night") ? 21 : 8;
                    if (slotHour >= nowHour) {
                        if (next == null) next = m;
                        else {
                            int prevHour = next.getTime() != null && next.getTime().toLowerCase(Locale.ROOT).contains("morning") ? 8
                                    : next.getTime() != null && next.getTime().toLowerCase(Locale.ROOT).contains("afternoon") ? 13
                                    : next.getTime() != null && next.getTime().toLowerCase(Locale.ROOT).contains("evening") ? 18 : 21;
                            if (slotHour < prevHour) next = m;
                        }
                    }
                }

                for (int id : ids) {
                    RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_med);
                    if (next != null) {
                        rv.setTextViewText(R.id.tvWidgetMedName, next.getName());
                        rv.setTextViewText(R.id.tvWidgetMedTime,
                                next.getTime() != null ? next.getTime() : "—");
                        rv.setTextViewText(R.id.tvWidgetMedDosage,
                                next.getDosage() != null ? next.getDosage() : "");
                    } else {
                        rv.setTextViewText(R.id.tvWidgetMedName, "All done for today ✅");
                        rv.setTextViewText(R.id.tvWidgetMedTime, "");
                        rv.setTextViewText(R.id.tvWidgetMedDosage, "");
                    }
                    manager.updateAppWidget(id, rv);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public static void requestUpdate(Context context) {
        Intent intent = new Intent(context, MedWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, MedWidgetProvider.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(intent);
    }
}
