package com.vitalflow.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "period_logs")
public class PeriodLog {
    // FIX: Make date the Primary Key so we don't get duplicates
    @PrimaryKey
    @NonNull
    public String date;       // Format: YYYY-MM-DD

    public boolean isFlow;    // True if period flow exists
    public int flowIntensity; // 1=Light, 2=Medium, 3=Heavy
    public String symptoms;   // "Cramps,Headache,Bloating"

    public PeriodLog(@NonNull String date, boolean isFlow, int flowIntensity, String symptoms) {
        this.date = date;
        this.isFlow = isFlow;
        this.flowIntensity = flowIntensity;
        this.symptoms = symptoms;
    }
}