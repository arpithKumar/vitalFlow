package com.vitalflow.app.ui.report;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Report;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.data.repository.GeminiRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltViewModel
public class ReportViewModel extends ViewModel {

    private final AppDao appDao;
    private final GeminiRepository geminiRepository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Report> reportState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorState = new MutableLiveData<>();

    @Inject
    public ReportViewModel(AppDao appDao, GeminiRepository geminiRepository) {
        this.appDao = appDao;
        this.geminiRepository = geminiRepository;
    }

    public LiveData<Report> getReportData() {
        return reportState;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorState() {
        return errorState;
    }

    public void generateFullReport() {
        isLoading.setValue(true);
        String userName = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getDisplayName()
                : "VitalFlow User";

        // 3. RX ZIP: Fetch 4 sources in Parallel
        Single<List<DailyHealth>> healthSource = appDao.getLast30DaysSync().subscribeOn(Schedulers.io());
        Single<List<PeriodLog>> cycleSource = appDao.getLast3CyclesSync().subscribeOn(Schedulers.io());
        Single<List<Medication>> medSource = appDao.getAllMedicationsSync().subscribeOn(Schedulers.io());

        // Fetch Vitals & Sort/Limit in background
        Single<List<Vital>> vitalSource = appDao.getAllVitalsSync()
                .subscribeOn(Schedulers.io())
                .map(list -> list.stream()
                        .sorted((v1, v2) -> Long.compare(v2.getTimestamp(), v1.getTimestamp()))
                        .limit(10)
                        .collect(Collectors.toList()));

        disposables.add(
                Single.zip(healthSource, cycleSource, medSource, vitalSource,
                                (health, cycles, meds, vitals) -> {
                                    // Create container object to pass to next step
                                    return new RawData(health, cycles, meds, vitals);
                                })
                        .flatMap(data -> {
                            // 4. Generate AI Prompt (Still on Background Thread)
                            String prompt = buildDoctorPrompt(userName, data.health, data.cycles, data.vitals, data.meds);

                            // 5. Wrap Gemini Callback in RxJava Single
                            return Single.<Report>create(emitter -> {
                                geminiRepository.sendMessage(prompt, null, new GeminiRepository.GeminiCallback() {
                                    @Override
                                    public void onSuccess(String aiSummary) {
                                        Report report = new Report(userName, data.cycles, data.health, data.vitals, data.meds, aiSummary);
                                        report.regularityScore = calculateRegularity(data.cycles);
                                        emitter.onSuccess(report);
                                    }

                                    @Override
                                    public void onError(Throwable t) {
                                        // Fallback report without AI summary if network fails
                                        Report report = new Report(userName, data.cycles, data.health, data.vitals, data.meds, "AI Insights unavailable (Offline).");
                                        report.regularityScore = calculateRegularity(data.cycles);
                                        emitter.onSuccess(report);
                                    }
                                });
                            });
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                report -> {
                                    reportState.setValue(report);
                                    isLoading.setValue(false);
                                },
                                throwable -> {
                                    errorState.setValue("Failed to generate report: " + throwable.getMessage());
                                    isLoading.setValue(false);
                                }
                        )
        );
    }

    private static class RawData {
        List<DailyHealth> health;
        List<PeriodLog> cycles;
        List<Medication> meds;
        List<Vital> vitals;

        RawData(List<DailyHealth> h, List<PeriodLog> c, List<Medication> m, List<Vital> v) {
            this.health = h;
            this.cycles = c;
            this.meds = m;
            this.vitals = v;
        }
    }

    private String buildDoctorPrompt(String name, List<DailyHealth> stats, List<PeriodLog> cycles, List<Vital> vitals, List<Medication> meds) {
        StringBuilder sb = new StringBuilder();
        sb.append("Act as a Clinical Health Assistant. Write a professional 1-paragraph summary for a PDF medical report.\n");
        sb.append("Patient: ").append(name).append("\n");

        // Stats Averages

        double avgSleep = (stats == null) ? 0d :
                stats.stream().mapToDouble(d -> parseSleep(d.getSleepDuration())).average().orElse(0);
        double avgWater = (stats == null) ? 0d :
                stats.stream().mapToInt(DailyHealth::getWaterIntakeMl).average().orElse(0);
        String sleepFmt = String.format(java.util.Locale.getDefault(), "%.1f", avgSleep);
        int waterRounded = (int) Math.round(avgWater);
        sb.append("30-Day Avg: Sleep ").append(sleepFmt).append(" hrs, Water ").append(waterRounded).append(" ml.\n");

        // Cycle Data
        sb.append("Menstrual History: ").append(cycles.isEmpty() ? "No logs" : cycles.toString()).append("\n");

        // Vitals Context
        sb.append("Recent Vitals: ");
        if (vitals.isEmpty()) sb.append("None recorded.");
        else {
            for (Vital v : vitals) {
                sb.append(v.getType()).append(": ").append(v.getValue1());
                if (v.getValue2() != null) sb.append("/").append(v.getValue2());
                sb.append(v.getUnit()).append("; ");
            }
        }
        sb.append("\n");

        // Meds Context
        sb.append("Current Medications: ");
        for (Medication m : meds)
            sb.append(m.getName()).append(" (").append(m.getDosage())
                    .append(", Scheduled: ").append(m.getSelectedDays())
                    .append("); ");
        sb.append("\n");

        sb.append("INSTRUCTIONS: Highlight any irregularities in vitals or cycle. Mention medication adherence if relevant. Be concise and professional. NO MARKDOWN.");
        return sb.toString();
    }

    private double parseSleep(String sleepStr) {
        try {
            // "7h 30m" -> 7.5
            String[] parts = sleepStr.split("h");
            double hours = Double.parseDouble(parts[0].trim());
            double minutes = 0;
            if (parts.length > 1) {
                minutes = Double.parseDouble(parts[1].replace("m", "").trim());
            }
            return hours + (minutes / 60.0);
        } catch (Exception e) {
            return 0;
        }
    }

    private String calculateRegularity(List<PeriodLog> logs) {
        if (logs == null || logs.size() < 2) return "Insufficient Data";

        // Safe sort
        try {
            logs.sort(Comparator.comparing(o -> o.date));
        } catch (Exception e) {
            return "Data Error";
        }

        List<Integer> lengths = new java.util.ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());

        for (int i = 0; i < logs.size() - 1; i++) {
            try {
                long diff = sdf.parse(logs.get(i + 1).date).getTime() - sdf.parse(logs.get(i).date).getTime();
                int days = (int) (diff / (1000 * 60 * 60 * 24));
                if (days > 15 && days < 45) lengths.add(days);
            } catch (Exception ignored) {
            }
        }

        if (lengths.isEmpty()) return "Insufficient Data";

        int variation = java.util.Collections.max(lengths) - java.util.Collections.min(lengths);
        int avg = (int) lengths.stream().mapToInt(Integer::intValue).average().orElse(28);

        if (variation <= 7) return "Regular (Avg " + avg + " days)";
        if (variation <= 10) return "Mildly Irregular";
        return "Irregular (Var " + variation + " days)";
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}