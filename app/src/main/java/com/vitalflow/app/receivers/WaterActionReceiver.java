package com.vitalflow.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.utils.WidgetUpdateHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class WaterActionReceiver extends BroadcastReceiver {

    @Inject
    AppDao appDao;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if ("ACTION_ADD_WATER".equals(intent.getAction())) {
            // 1. Get the amount to add
            int amountToAdd = intent.getIntExtra("amount", 250);
            int notificationId = intent.getIntExtra("notification_id", 1);

            // 2. Go Async
            final PendingResult pendingResult = goAsync();
            ExecutorService executor = Executors.newSingleThreadExecutor();

            executor.execute(() -> {
                try {
                    // 3. Get Today's Data
                    String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    DailyHealth currentData = appDao.getDailyHealthSync(todayKey);

                    // 4. Create or Update
                    if (currentData == null) {
                        currentData = new DailyHealth(todayKey, amountToAdd, 2000, 0, "0h 0m", 0);
                    } else {
                        currentData.setWaterIntakeMl(currentData.getWaterIntakeMl() + amountToAdd);
                    }
                    appDao.insertDailyHealth(currentData);

                    // 5. CRITICAL: Force Widget Update
                    WidgetUpdateHelper.updateWidgets(context);

                } finally {
                    // 6. Dismiss Notification & Finish
                    NotificationManagerCompat.from(context).cancel(notificationId);
                    executor.shutdown();
                    pendingResult.finish();
                }
            });

            // 7. Show Feedback immediately
            Toast.makeText(context, "Added " + amountToAdd + "ml 💧", Toast.LENGTH_SHORT).show();
        }
    }
}