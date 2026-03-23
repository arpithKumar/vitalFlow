package com.vitalflow.app.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.vitalflow.app.MainActivity;
import com.vitalflow.app.R;

import java.util.concurrent.TimeUnit;

public class NotificationHelper {

    private static final String CHANNEL_ID_MEDS = "med_inventory_channel";
    private static final String CHANNEL_ID_DOWNLOADS = "download_channel"; // New Channel
    public static final String CHANNEL_ID_WATER = "health_reminders";
    private static final int DOWNLOAD_NOTIF_ID = 999; // Unique ID for updating


    public static void scheduleDailyReminder(Context context, int intervalHours) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ReminderWorker.class, intervalHours, TimeUnit.HOURS).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "daily_health_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    public static void cancelReminders(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork("daily_health_reminder");
    }

    public static void createNotificationChannel(Context context) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) return;

        NotificationChannel medChannel = new NotificationChannel(
                CHANNEL_ID_MEDS, "Medication Alerts", NotificationManager.IMPORTANCE_HIGH);
        medChannel.setDescription("Reminders to take medications and refill stock");
        notificationManager.createNotificationChannel(medChannel);

        NotificationChannel downloadChannel = new NotificationChannel(
                CHANNEL_ID_DOWNLOADS, "Downloads & Reports", NotificationManager.IMPORTANCE_LOW);
        downloadChannel.setDescription("Progress status for PDF reports");
        notificationManager.createNotificationChannel(downloadChannel);

        NotificationChannel waterChannel = new NotificationChannel(
                CHANNEL_ID_WATER, "Daily Reminders", NotificationManager.IMPORTANCE_DEFAULT);
        waterChannel.setDescription("Hydration and health reminders");
        notificationManager.createNotificationChannel(waterChannel);
    }

    private static PendingIntent getOpenAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // --- PDF NOTIFICATIONS ---

    public static void showDownloadProgress(Context context) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
                    .setSmallIcon(android.R.drawable.stat_sys_download) // Standard Download Icon
                    .setContentTitle("Generating Health Report")
                    .setContentText("Gathering AI insights...")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true) // User cannot swipe it away while downloading
                    .setProgress(0, 0, true); // Indeterminate progress bar

            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIF_ID, builder.build());
        } catch (SecurityException e) {
            // Handle permission error
        }
    }

    public static void showDownloadComplete(Context context, Uri fileUri) {
        if (fileUri == null) return;

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 3. PendingIntent
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOADS)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done) // Success Icon
                    .setContentTitle("Report Downloaded")
                    .setContentText("Your PDF has been saved to Downloads.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH) // Make it pop up now
                    .setOngoing(false) // User can swipe away now
                    .setProgress(0, 0, false) // Remove progress bar
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            // Notify with SAME ID to update the existing notification
            NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIF_ID, builder.build());
        } catch (SecurityException e) {
            // Handle permission error
        }
    }

    public static void sendLowStockAlert(Context context, String medName, int count) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MEDS)
                    .setSmallIcon(R.drawable.ic_medicine_pro)
                    .setContentTitle("Refill Reminder: " + medName)
                    .setContentText("Only " + count + " pills remaining. Time to refill!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(getOpenAppIntent(context));

            NotificationManagerCompat.from(context).notify(medName.hashCode(), builder.build());
        } catch (SecurityException e) {
        }
    }

    public static void sendMedicationReminder(Context context, String medName, String time) {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MEDS)
                    .setSmallIcon(R.drawable.ic_heart) // Use your app logo or specific med icon
                    .setContentTitle("Time to take your Meds")
                    .setContentText("Take " + medName + " (" + time + ")")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(getOpenAppIntent(context));

            int uniqueId = (medName + time).hashCode();
            NotificationManagerCompat.from(context).notify(uniqueId, builder.build());
        } catch (SecurityException e) {
        }
    }

    public static void triggerImmediateMedicationCheck(Context context) {
        androidx.work.OneTimeWorkRequest checkRequest =
                new androidx.work.OneTimeWorkRequest.Builder(MedicationWorker.class)
                        .build();
        WorkManager.getInstance(context).enqueue(checkRequest);
    }

}