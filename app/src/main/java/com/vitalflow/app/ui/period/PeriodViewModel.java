package com.vitalflow.app.ui.period;

import androidx.lifecycle.ViewModel;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.repository.GeminiRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@HiltViewModel
public class PeriodViewModel extends ViewModel {

    private final AppDao appDao;
    private final GeminiRepository geminiRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    public PeriodViewModel(AppDao appDao, GeminiRepository geminiRepository) {
        this.appDao = appDao;
        this.geminiRepository = geminiRepository;
    }

    public GeminiRepository getGeminiRepository() { return geminiRepository; }

    public Single<List<PeriodLog>> getAllLogsSync() { return appDao.getAllLogsSync(); }
    public Single<List<FoodLog>> getFoodLogsSync(String date) { return appDao.getFoodLogsSync(date); }
    public Single<List<DailyHealth>> getDailyHealthListSync(String date) { return appDao.getDailyHealthListSync(date); }
    public Single<List<Medication>> getAllMedicationsSync() { return appDao.getAllMedicationsSync(); }

    public Completable insertPeriodLogCompletable(PeriodLog log) {
        return appDao.insertPeriodLog(log);
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}
