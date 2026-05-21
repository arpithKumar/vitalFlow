package com.vitalflow.app.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.MedicationHistory;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Vital;

@Database(entities = {Vital.class, Medication.class, DailyHealth.class, PeriodLog.class, FoodLog.class, MedicationHistory.class}, version = 10)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AppDao appDao();
}