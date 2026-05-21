package com.vitalflow.app.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.MedicationHistory;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Vital;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

@Dao
public interface AppDao {

    // --- VITALS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertVital(Vital vital);

    @Query("SELECT * FROM vitals ORDER BY timestamp DESC")
    LiveData<List<Vital>> getAllVitals();

    // Get latest vital by type (for Dashboard Cards)
    @Query("SELECT * FROM vitals WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    LiveData<Vital> getLatestVital(String type);

    // --- MEDICATIONS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertMedication(Medication medication);

    @Query("SELECT * FROM medications")
    LiveData<List<Medication>> getAllMedications();

    @Delete
    Completable deleteMedication(Medication medication);

    // --- DAILY HEALTH (Water/Steps) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertDailyHealth(DailyHealth health);

    @Query("SELECT * FROM daily_health WHERE date = :date")
    LiveData<DailyHealth> getDailyHealth(String date);

    // --- RAW QUERIES FOR SYNC (Non-LiveData) ---
    @Query("SELECT * FROM medications")
    Single<List<Medication>> getAllMedicationsSync();

    @Query("SELECT * FROM vitals")
    Single<List<Vital>> getAllVitalsSync();

    @Query("SELECT * FROM daily_health")
    Single<List<DailyHealth>> getAllDailyHealthSync();

    @Query("SELECT * FROM daily_health WHERE date = :date LIMIT 1")
    Maybe<DailyHealth> getDailyHealthSync(String date);

    @Query("SELECT * FROM daily_health ORDER BY date DESC LIMIT 7")
    Single<List<DailyHealth>> getLastSevenDaysSync();

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    Completable insertPeriodLog(PeriodLog log);

    @Query("SELECT * FROM period_logs WHERE isFlow = 1 ORDER BY date DESC LIMIT 1")
    Maybe<PeriodLog> getLastPeriodLogSync();

    @Query("SELECT * FROM period_logs ORDER BY date DESC")
    Single<List<PeriodLog>> getAllLogsSync();

    @Query("SELECT * FROM daily_health ORDER BY date DESC LIMIT 30")
    Single<List<DailyHealth>> getLast30DaysSync();

    // Fetch the last 3 periods for the Cycle History Table
    // We assume 'date' is the start date of the period
    @Query("SELECT * FROM period_logs ORDER BY date DESC LIMIT 3")
    Single<List<PeriodLog>> getLast3CyclesSync();

    @Insert
    Completable insertFoodLog(FoodLog log);

    @Delete
    Completable deleteFoodLog(FoodLog log);

    @Query("SELECT * FROM food_logs WHERE date = :date ORDER BY time DESC")
    LiveData<List<FoodLog>> getFoodLogsForDate(String date);

    @Query("SELECT SUM(calories) FROM food_logs WHERE date = :date")
    LiveData<Integer> getTotalCaloriesForDate(String date);

    @Query("SELECT * FROM food_logs WHERE date = :date")
    Single<List<FoodLog>> getFoodLogsSync(String date);

    @Query("SELECT * FROM daily_health WHERE date = :date")
    Single<List<DailyHealth>> getDailyHealthListSync(String date);

    // --- FOR SYNC ---
    @Query("SELECT * FROM food_logs")
    Single<List<FoodLog>> getAllFoodLogsSync();

    @Query("SELECT * FROM food_logs WHERE date >= :startDate ORDER BY date ASC")
    Single<List<FoodLog>> getFoodLogsFromDate(String startDate);

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    Single<Medication> getMedicationById(String id);

    @Query("SELECT * FROM daily_health WHERE date LIKE :monthPrefix || '%'")
    Single<List<DailyHealth>> getDailyHealthForMonthSync(String monthPrefix);

    @Query("SELECT * FROM period_logs WHERE date LIKE :monthPrefix || '%'")
    Single<List<PeriodLog>> getPeriodLogsForMonthSync(String monthPrefix);

    // --- MEDICATION HISTORY ---
    @Insert
    Completable insertMedicationHistory(MedicationHistory entry);

    @Query("SELECT * FROM medication_history WHERE medId = :medId ORDER BY timestamp DESC LIMIT 50")
    Single<List<MedicationHistory>> getHistoryForMed(String medId);

    @Query("SELECT * FROM medication_history ORDER BY timestamp DESC LIMIT 100")
    Single<List<MedicationHistory>> getAllMedicationHistory();
}