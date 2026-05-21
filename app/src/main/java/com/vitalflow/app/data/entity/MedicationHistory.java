package com.vitalflow.app.data.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Audit log entry for a single medication event (TAKEN or SKIPPED).
 * Populated every time the user marks a dose or the action is triggered from a notification.
 *
 * <p>DB version 10 — see {@link com.vitalflow.app.data.Migrations#MIGRATION_9_10}.
 */
@Entity(tableName = "medication_history")
public class MedicationHistory {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String medId;
    public String medName;
    /** "TAKEN" or "SKIPPED" */
    public String action;
    /** Unix epoch millis — use {@link java.text.SimpleDateFormat} for display. */
    public long timestamp;

    public MedicationHistory() {}

    @Ignore
    public MedicationHistory(String medId, String medName, String action) {
        this.medId     = medId;
        this.medName   = medName;
        this.action    = action;
        this.timestamp = System.currentTimeMillis();
    }
}
