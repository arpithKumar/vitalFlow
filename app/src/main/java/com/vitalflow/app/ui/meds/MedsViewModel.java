package com.vitalflow.app.ui.meds;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.repository.GeminiRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public class MedsViewModel extends ViewModel {

    private final AppDao appDao;
    private final GeminiRepository geminiRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final LiveData<List<Medication>> allMedications;

    @Inject
    public MedsViewModel(AppDao appDao, GeminiRepository geminiRepository) {
        this.appDao = appDao;
        this.geminiRepository = geminiRepository;
        allMedications = appDao.getAllMedications();
    }

    public GeminiRepository getGeminiRepository() { return geminiRepository; }

    public AppDao getAppDao() { return appDao; }

    public LiveData<List<Medication>> getAllMedications() { return allMedications; }

    public Completable insertMedicationCompletable(Medication m) {
        return appDao.insertMedication(m);
    }

    public void insertMedication(Medication m) {
        disposables.add(appDao.insertMedication(m).subscribeOn(Schedulers.io()).subscribe());
    }

    public void deleteMedication(Medication m) {
        disposables.add(appDao.deleteMedication(m).subscribeOn(Schedulers.io()).subscribe());
    }

    public Completable deleteMedicationCompletable(Medication m) {
        return appDao.deleteMedication(m);
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}
