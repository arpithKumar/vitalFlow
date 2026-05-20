package com.vitalflow.app.ui.vitals;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Vital;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public class VitalsViewModel extends ViewModel {

    private final AppDao appDao;
    private final LiveData<List<Vital>> allVitals;
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    public VitalsViewModel(AppDao appDao) {
        this.appDao = appDao;
        this.allVitals = appDao.getAllVitals();
    }

    public LiveData<List<Vital>> getAllVitals() {
        return allVitals;
    }

    public void insertVital(Vital vital) {
        disposables.add(
                appDao.insertVital(vital)
                        .subscribeOn(Schedulers.io())
                        .subscribe()
        );
    }

    @Override
    protected void onCleared() {
        disposables.clear();
    }
}