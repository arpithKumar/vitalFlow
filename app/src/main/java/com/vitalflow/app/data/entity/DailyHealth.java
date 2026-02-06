package com.vitalflow.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_health")
public class DailyHealth {
    @PrimaryKey
    @NonNull
    private String date; // Format: "yyyy-MM-dd"

    private int waterIntakeMl;
    private int waterGoalMl;
    private int stepCount;
    private String sleepDuration; // e.g. "7h 30m"
    private int moodRating; // 1-5

    public DailyHealth(@NonNull String date, int waterIntakeMl, int waterGoalMl, int stepCount, String sleepDuration, int moodRating) {
        this.date = date;
        this.waterIntakeMl = waterIntakeMl;
        this.waterGoalMl = waterGoalMl;
        this.stepCount = stepCount;
        this.sleepDuration = sleepDuration;
        this.moodRating = moodRating;
    }

    // Getters and Setters
    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    public int getWaterIntakeMl() {
        return waterIntakeMl;
    }

    public void setWaterIntakeMl(int waterIntakeMl) {
        this.waterIntakeMl = waterIntakeMl;
    }

    public int getWaterGoalMl() {
        return waterGoalMl;
    }

    public void setWaterGoalMl(int waterGoalMl) {
        this.waterGoalMl = waterGoalMl;
    }

    public int getStepCount() {
        return stepCount;
    }

    public void setStepCount(int stepCount) {
        this.stepCount = stepCount;
    }

    public String getSleepDuration() {
        return sleepDuration;
    }

    public void setSleepDuration(String sleepDuration) {
        this.sleepDuration = sleepDuration;
    }

    public int getMoodRating() {
        return moodRating;
    }

    public void setMoodRating(int moodRating) {
        this.moodRating = moodRating;
    }
}