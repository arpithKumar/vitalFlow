package com.vitalflow.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "vitals")
public class Vital {
    @PrimaryKey
    @NonNull
    private String id;
    private String type;         // "Heart Rate", "Blood Pressure", "Oxygen (SpO2)", "Weight"
    private String value1;       // Primary value (e.g. 72)
    private String value2;       // Secondary value (e.g. 80 for BP), can be null
    private String unit;         // "bpm", "mmHg", "%"
    private String date;         // Display date string
    private long timestamp;      // Sorting timestamp
    private String contextTags;  // "Missed Meds, Stressed"
    private String symptoms;     // "Dizziness"

    public Vital(String type, String value1, String value2, String unit, String date, long timestamp, String contextTags, String symptoms) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.value1 = value1;
        this.value2 = value2;
        this.unit = unit;
        this.date = date;
        this.timestamp = timestamp;
        this.contextTags = contextTags;
        this.symptoms = symptoms;
    }

    // Explicit setter for Room
    public void setId(@NonNull String id) {
        this.id = id;
    }

    // Getters
    @NonNull
    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getValue1() {
        return value1;
    }

    public String getValue2() {
        return value2;
    }

    public String getUnit() {
        return unit;
    }

    public String getDate() {
        return date;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getContextTags() {
        return contextTags;
    }

    public String getSymptoms() {
        return symptoms;
    }
}