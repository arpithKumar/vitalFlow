package com.vitalflow.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.MedicationHistory;
import com.vitalflow.app.utils.NotificationHelper;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class MedicationActionReceiver extends BroadcastReceiver {

    @Inject
    AppDao appDao;

    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String medId = intent.getStringExtra("med_id");
        int notifId = intent.getIntExtra("notif_id", 0);

        // 1. Dismiss the notification immediately so the user knows the tap worked
        NotificationManagerCompat.from(context).cancel(notifId);

        // 2. Handle the action based on the intent's action
        if ("ACTION_MARK_TAKEN".equals(action) && medId != null) {
            // Update Database in Background. goAsync() keeps the process alive past
            // onReceive() returning — without it, the OS is free to kill the process
            // before the IO-thread write completes, silently dropping the "taken" mark.
            final PendingResult pendingResult = goAsync();
            disposables.add(
                appDao.getMedicationById(medId)
                    .subscribeOn(Schedulers.io())
                    .flatMapCompletable(med -> {
                        int current = med.getTimesTakenToday();
                        if (current >= med.getFrequencyPerDay()) {
                            return Completable.complete();
                        }
                        med.setTimesTakenToday(current + 1);
                        if (med.getPillsLeft() > 0) med.setPillsLeft(med.getPillsLeft() - 1);
                        return appDao.insertMedication(med)
                                .andThen(appDao.insertMedicationHistory(
                                        new MedicationHistory(med.getId(), med.getName(), "TAKEN")));
                    })
                    // finish() only after the writes actually complete — not just dispatched —
                    // so the process isn't killed mid-write.
                    .subscribe(pendingResult::finish, throwable -> {
                        throwable.printStackTrace();
                        pendingResult.finish();
                    })
            );
            Toast.makeText(context, "Medication marked as taken!", Toast.LENGTH_SHORT).show();

        } else if ("ACTION_SNOOZE".equals(action) && medId != null) {
            String medName = intent.getStringExtra("med_name");
            NotificationHelper.scheduleSnooze(context, medId, medName);
            Toast.makeText(context, "Snoozed for 1 hour.", Toast.LENGTH_SHORT).show();
        }
    }
}
