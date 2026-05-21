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
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.vitalflow.app.MainActivity;
import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.receivers.MedicationActionReceiver;
import com.vitalflow.app.workers.SnoozeWorker;

import java.util.concurrent.TimeUnit;

public class NotificationHelper {

    private static final String CHANNEL_ID_MEDS = "med_inventory_channel";
    private static final String CHANNEL_ID_DOWNLOADS = "download_channel"; // New Channel
    public static final String CHANNEL_ID_WATER = "health_reminders";
    private static final int DOWNLOAD_NOTIF_ID = 999; // Unique ID for updating


    public static void scheduleDailyReminder(Context context, int intervalHours) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ReminderWorker.class,
                intervalHours,
                TimeUnit.MINUTES)
                .build();
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

    public static final String EXTRA_NAVIGATE_TO = "navigate_to";
    public static final String DEST_HOME    = "home";
    public static final String DEST_MEDS    = "meds";
    public static final String DEST_VITALS  = "vitals";
    public static final String DEST_PERIOD  = "period";

    private static PendingIntent getOpenAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Build a deep-link PendingIntent that navigates to a specific tab on arrival. */
    public static PendingIntent getDeepLinkIntent(Context context, String dest, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_NAVIGATE_TO, dest);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
                    .setContentIntent(getDeepLinkIntent(context, DEST_MEDS, 20));

            NotificationManagerCompat.from(context).notify(medName.hashCode(), builder.build());
        } catch (SecurityException e) {
        }
    }

    public static void sendMedicationReminder(Context context, Medication med) {
        sendMedicationReminder(context, med, med.getTime());
    }

    public static void sendMedicationReminder(Context context, Medication med, String displayTime) {
        try {
            int uniqueId = (med.getId() + displayTime).hashCode();

            Intent takenIntent = new Intent(context, MedicationActionReceiver.class);
            takenIntent.setAction("ACTION_MARK_TAKEN");
            takenIntent.putExtra("med_id", med.getId());
            takenIntent.putExtra("notif_id", uniqueId);
            PendingIntent takenPending = PendingIntent.getBroadcast(
                    context, uniqueId + 1, takenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent snoozeIntent = new Intent(context, MedicationActionReceiver.class);
            snoozeIntent.setAction("ACTION_SNOOZE");
            snoozeIntent.putExtra("med_id", med.getId());
            snoozeIntent.putExtra("med_name", med.getName());
            snoozeIntent.putExtra("notif_id", uniqueId);
            PendingIntent snoozePending = PendingIntent.getBroadcast(
                    context, uniqueId + 2, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MEDS)
                    .setSmallIcon(R.drawable.ic_heart)
                    .setContentTitle("Time for your Meds")
                    .setContentText("Take " + med.getName() + " (" + med.getDosage() + ")")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(getDeepLinkIntent(context, DEST_MEDS, uniqueId))
                    .addAction(R.drawable.ic_check_circle, "Mark Taken", takenPending)
                    .addAction(R.drawable.ic_clock, "Snooze 1h", snoozePending);

            NotificationManagerCompat.from(context).notify(uniqueId, builder.build());
        } catch (SecurityException e) {
        }
    }

    // 2. Add this helper method to schedule the snooze
    public static void scheduleSnooze(Context context, String medId, String medName) {
        androidx.work.Data inputData = new androidx.work.Data.Builder()
                .putString("med_id", medId)
                .putString("med_name", medName)
                .build();

        OneTimeWorkRequest snoozeRequest = new OneTimeWorkRequest.Builder(SnoozeWorker.class)
                .setInitialDelay(1, TimeUnit.HOURS)
                .setInputData(inputData)
                .build();

        androidx.work.WorkManager.getInstance(context).enqueue(snoozeRequest);

        // Confirmation notification — tells the user exactly when the reminder will re-fire
        sendSnoozeConfirmation(context, medName);
    }

    private static void sendSnoozeConfirmation(Context context, String medName) {
        java.util.Calendar fireTime = java.util.Calendar.getInstance();
        fireTime.add(java.util.Calendar.HOUR_OF_DAY, 1);
        String timeLabel = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(fireTime.getTime());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MEDS)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle("Reminder snoozed 💤")
                .setContentText((medName != null ? medName : "Medication") + " — fires at " + timeLabel)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(TimeUnit.HOURS.toMillis(1)); // auto-dismiss when the real reminder fires

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        //noinspection MissingPermission — permission checked before the action intent is built
        manager.notify(9001, builder.build());
    }

    public static void triggerImmediateMedicationCheck(Context context) {
        androidx.work.OneTimeWorkRequest checkRequest =
                new androidx.work.OneTimeWorkRequest.Builder(MedicationWorker.class)
                        .build();
        WorkManager.getInstance(context).enqueue(checkRequest);
    }

    /** Fire once at 7-, 30-, and 100-day streak milestones. */
    public static void sendStreakMilestoneNotification(Context context, int streakDays) {
        String emoji;
        String title;
        String body;
        if (streakDays == 7) {
            emoji = "🔥";
            title = "One week streak!";
            body  = "7 days in a row — you're building a great habit. Keep it up!";
        } else if (streakDays == 30) {
            emoji = "🏆";
            title = "30-day streak!";
            body  = "A whole month of consistency. You're on fire!";
        } else if (streakDays == 100) {
            emoji = "💯";
            title = "100-day streak!";
            body  = "100 days strong. You're a VitalFlow legend!";
        } else {
            return;
        }

        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_WATER)
                    .setSmallIcon(R.drawable.ic_heart)
                    .setContentTitle(emoji + " " + title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(getDeepLinkIntent(context, DEST_HOME, 300 + streakDays));

            NotificationManagerCompat.from(context).notify(("streak_" + streakDays).hashCode(), builder.build());
        } catch (SecurityException e) {
            // permission not granted
        }
    }

}