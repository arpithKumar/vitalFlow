package com.vitalflow.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
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
    private String date;         // ISO date string "yyyy-MM-dd HH:mm:ss"
    private long timestamp;      // Sorting timestamp
    private String contextTags;  // "Missed Meds, Stressed"
    private String symptoms;     // "Dizziness"

    /** Used by Firestore when it restores a record; the values then arrive through the setters. */
    @Ignore
    public Vital() {
        this.id = UUID.randomUUID().toString();
    }

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

    // Setters. Firestore restores a record through them: a property without a setter is silently left empty.
    public void setId(@NonNull String id) {
        this.id = id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setValue1(String value1) {
        this.value1 = value1;
    }

    public void setValue2(String value2) {
        this.value2 = value2;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setContextTags(String contextTags) {
        this.contextTags = contextTags;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
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