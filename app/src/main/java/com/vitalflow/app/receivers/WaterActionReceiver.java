package com.vitalflow.app.receivers;

import android.annotation.SuppressLint;
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

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class WaterActionReceiver extends BroadcastReceiver {

    @Inject
    AppDao appDao;

    @SuppressLint("CheckResult")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if ("ACTION_ADD_WATER".equals(intent.getAction())) {
            // 1. Get Extras
            int amountToAdd = intent.getIntExtra("amount", 250);
            int notificationId = intent.getIntExtra("notification_id", 1);
            String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            // 2. Go Async (Keeps Receiver alive for background work)
            final PendingResult pendingResult = goAsync();

            // 3. RX CHAIN: Get -> Modify -> Save -> Finish
            appDao.getDailyHealthSync(todayKey)
                    .defaultIfEmpty(new DailyHealth(todayKey, 0, 2000, 0, "0h 0m", 0))
                    .flatMapCompletable(currentData -> {
                        // Logic: Add water
                        currentData.setWaterIntakeMl(currentData.getWaterIntakeMl() + amountToAdd);
                        // Save to DB
                        return appDao.insertDailyHealth(currentData);
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        // SUCCESS
                        WidgetUpdateHelper.updateWidgets(context);
                        NotificationManagerCompat.from(context).cancel(notificationId);
                        Toast.makeText(context, "Added " + amountToAdd + "ml 💧", Toast.LENGTH_SHORT).show();
                        pendingResult.finish(); // CRITICAL: End async
                    }, throwable -> {
                        // ERROR
                        throwable.printStackTrace();
                        pendingResult.finish(); // CRITICAL: End async even on error
                    });
        }
    }
}