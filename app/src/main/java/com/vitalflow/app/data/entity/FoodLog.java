package com.vitalflow.app.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "food_logs")
public class FoodLog {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String date; // Format: YYYY-MM-DD
    public String time; // Format: HH:MM
    public String foodName;
    public int calories;
    public String macros; // e.g., "P: 30g, C: 45g, F: 10g"

    public FoodLog(String date, String time, String foodName, int calories, String macros) {
        this.date = date;
        this.time = time;
        this.foodName = foodName;
        this.calories = calories;
        this.macros = macros;
    }
}