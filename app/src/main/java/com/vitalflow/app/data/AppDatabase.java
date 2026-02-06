package com.vitalflow.app.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Vital;

@Database(entities = {Vital.class, Medication.class, DailyHealth.class, PeriodLog.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AppDao appDao();
}