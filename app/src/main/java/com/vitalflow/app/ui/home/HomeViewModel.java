package com.vitalflow.app.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.data.repository.GeminiRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public class HomeViewModel extends ViewModel {

    private final AppDao appDao;
    private final GeminiRepository geminiRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final LiveData<List<Medication>> allMedications;
    private final LiveData<Vital> latestHeartRate;

    private final MutableLiveData<String> currentDate = new MutableLiveData<>();
    private final LiveData<DailyHealth> dailyHealth;
    private final LiveData<List<FoodLog>> foodLogs;
    private final LiveData<Integer> totalCalories;

    @Inject
    public HomeViewModel(AppDao appDao, GeminiRepository geminiRepository) {
        this.appDao = appDao;
        this.geminiRepository = geminiRepository;
        allMedications = appDao.getAllMedications();
        latestHeartRate = appDao.getLatestVital("Heart Rate");
        dailyHealth = Transformations.switchMap(currentDate, appDao::getDailyHealth);
        foodLogs = Transformations.switchMap(currentDate, appDao::getFoodLogsForDate);
        totalCalories = Transformations.switchMap(currentDate, appDao::getTotalCaloriesForDate);
    }

    public void setDate(String dateKey) {
        currentDate.setValue(dateKey);
    }

    /** Re-triggers all date-dependent LiveData queries (used by pull-to-refresh). */
    public void refresh() {
        String date = currentDate.getValue();
        if (date != null) currentDate.setValue(date);
    }

    public GeminiRepository getGeminiRepository() {
        return geminiRepository;
    }

    public AppDao getAppDao() {
        return appDao;
    }

    public LiveData<List<Medication>> getAllMedications() {
        return allMedications;
    }

    public LiveData<Vital> getLatestHeartRate() {
        return latestHeartRate;
    }

    public LiveData<DailyHealth> getDailyHealth() {
        return dailyHealth;
    }

    public LiveData<List<FoodLog>> getFoodLogs() {
        return foodLogs;
    }

    public LiveData<Integer> getTotalCalories() {
        return totalCalories;
    }

    public Single<List<DailyHealth>> getLastSevenDaysSync() {
        return appDao.getLastSevenDaysSync();
    }

    public Single<List<Medication>> getAllMedicationsSync() {
        return appDao.getAllMedicationsSync();
    }

    public void insertDailyHealth(DailyHealth dh) {
        disposables.add(appDao.insertDailyHealth(dh).subscribeOn(Schedulers.io()).subscribe());
    }

    public void insertFoodLog(FoodLog log) {
        disposables.add(appDao.insertFoodLog(log).subscribeOn(Schedulers.io()).subscribe());
    }

    public Completable insertDailyHealthCompletable(DailyHealth dh) {
        return appDao.insertDailyHealth(dh);
    }

    public Completable deleteFoodLogCompletable(FoodLog log) {
        return appDao.deleteFoodLog(log);
    }

    public void insertMedication(Medication m) {
        disposables.add(appDao.insertMedication(m).subscribeOn(Schedulers.io()).subscribe());
    }

    public void insertVital(Vital v) {
        disposables.add(appDao.insertVital(v).subscribeOn(Schedulers.io()).subscribe());
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}
