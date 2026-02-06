package com.vitalflow.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Vital;

import java.util.List;

@Dao
public interface AppDao {

    // --- VITALS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertVital(Vital vital);

    @Query("SELECT * FROM vitals ORDER BY timestamp DESC")
    LiveData<List<Vital>> getAllVitals();

    // Get latest vital by type (for Dashboard Cards)
    @Query("SELECT * FROM vitals WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    LiveData<Vital> getLatestVital(String type);

    // --- MEDICATIONS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMedication(Medication medication);

    @Query("SELECT * FROM medications")
    LiveData<List<Medication>> getAllMedications();

    @Delete
    void deleteMedication(Medication medication);

    @Update
    void updateMedication(Medication medication);

    // --- DAILY HEALTH (Water/Steps) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDailyHealth(DailyHealth health);

    @Query("SELECT * FROM daily_health WHERE date = :date")
    LiveData<DailyHealth> getDailyHealth(String date);

    // --- RAW QUERIES FOR SYNC (Non-LiveData) ---
    @Query("SELECT * FROM medications")
    List<Medication> getAllMedicationsSync();

    @Query("SELECT * FROM vitals")
    List<Vital> getAllVitalsSync();

    @Query("SELECT * FROM daily_health")
    List<DailyHealth> getAllDailyHealthSync();

    @Query("SELECT * FROM daily_health WHERE date = :date LIMIT 1")
    DailyHealth getDailyHealthSync(String date);

    @Query("SELECT * FROM daily_health ORDER BY date DESC LIMIT 7")
    List<DailyHealth> getLastSevenDaysSync();

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertPeriodLog(PeriodLog log);

    @Query("SELECT * FROM period_logs WHERE isFlow = 1 ORDER BY date DESC")
    List<PeriodLog> getAllFlowDaysSync();

    @Query("SELECT * FROM period_logs WHERE isFlow = 1 ORDER BY date DESC LIMIT 1")
    PeriodLog getLastPeriodLogSync();

    @Query("SELECT * FROM period_logs ORDER BY date DESC")
    List<PeriodLog> getAllLogsSync();

    @Query("SELECT * FROM daily_health ORDER BY date DESC LIMIT 30")
    List<DailyHealth> getLast30DaysSync();

    // Fetch the last 3 periods for the Cycle History Table
    // We assume 'date' is the start date of the period
    @Query("SELECT * FROM period_logs ORDER BY date DESC LIMIT 3")
    List<PeriodLog> getLast3CyclesSync();
}