package com.vitalflow.app.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vitalflow.app.R;
import com.vitalflow.app.receivers.WaterActionReceiver;

public class ReminderWorker extends Worker {

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        sendNotification();
        return Result.success();
    }

    private void sendNotification() {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "health_reminders";
        int notificationId = 1;

        // 1. Create Channel (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Daily Reminders", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }

        // 2. Create the "Drink 250ml" Action Intent
        Intent actionIntent = new Intent(context, WaterActionReceiver.class);
        actionIntent.setAction("ACTION_ADD_WATER");
        actionIntent.putExtra("amount", 250);
        actionIntent.putExtra("notification_id", notificationId);

        PendingIntent actionPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 3. Build Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_logo_splash)
                .setContentTitle("Stay Hydrated! \uD83D\uDCA7")
                .setContentText("You haven't logged water in a while.")
                .setColor(context.getResources().getColor(R.color.primary, null))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                // --- THE ACTION BUTTON ---
                .addAction(R.drawable.ic_add, "Drink +250ml", actionPendingIntent);

        manager.notify(notificationId, builder.build());
    }
}