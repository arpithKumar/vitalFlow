package com.vitalflow.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.UUID;

@Entity(tableName = "food_logs")
public class FoodLog {
    @PrimaryKey
    @NonNull
    public String id;

    public String date; // Format: YYYY-MM-DD
    public String time; // Format: HH:MM
    public String foodName;
    public int calories;
    public String macros; // e.g., "P: 30g, C: 45g, F: 10g"

    @Ignore
    public FoodLog() {
    }

    public FoodLog(String date, String time, String foodName, int calories, String macros) {
        this.id = UUID.randomUUID().toString();
        this.date = date;
        this.time = time;
        this.foodName = foodName;
        this.calories = calories;
        this.macros = macros;
    }
}