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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ReportViewModel extends ViewModel {

    private final AppDao appDao;
    private final GeminiRepository geminiRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        executor.execute(() -> {
            try {
                // 1. GATHER ALL DATA
                List<DailyHealth> last30Days = appDao.getLast30DaysSync();
                List<PeriodLog> recentCycles = appDao.getLast3CyclesSync();

                // Fetch Vitals (Sort by newest) & Limit to last 10 for concise AI context
                List<Vital> allVitals = appDao.getAllVitalsSync();
                List<Vital> recentVitals = allVitals.stream()
                        .sorted((v1, v2) -> Long.compare(v2.getTimestamp(), v1.getTimestamp()))
                        .limit(10)
                        .collect(Collectors.toList());

                List<Medication> medications = appDao.getAllMedicationsSync();

                // 2. BUILD AI PROMPT
                String prompt = buildDoctorPrompt(userName, last30Days, recentCycles, recentVitals, medications);

                // 3. ASK GEMINI
                geminiRepository.sendMessage(prompt, null, new GeminiRepository.GeminiCallback() {
                    @Override
                    public void onSuccess(String aiSummary) {
                        // 4. Create Final Report Object
                        Report data = new Report(userName, recentCycles, last30Days, recentVitals, medications, aiSummary);
                        data.regularityScore = calculateRegularity(recentCycles);

                        reportState.postValue(data);
                        isLoading.postValue(false);
                    }

                    @Override
                    public void onError(Throwable t) {
                        errorState.postValue("AI Generation Failed: " + t.getMessage());
                        isLoading.postValue(false);
                    }
                });
            } catch (Exception e) {
                errorState.postValue("Data Error: " + e.getMessage());
                isLoading.postValue(false);
            }
        });
    }

    private String buildDoctorPrompt(String name, List<DailyHealth> stats, List<PeriodLog> cycles, List<Vital> vitals, List<Medication> meds) {
        StringBuilder sb = new StringBuilder();
        sb.append("Act as a Clinical Health Assistant. Write a professional 1-paragraph summary for a PDF medical report.\n");
        sb.append("Patient: ").append(name).append("\n");

        // Stats Averages
        double avgSleep = stats.stream().mapToDouble(d -> parseSleep(d.getSleepDuration())).average().orElse(0);
        double avgWater = stats.stream().mapToInt(DailyHealth::getWaterIntakeMl).average().orElse(0);
        sb.append("30-Day Avg: Sleep ").append(String.format("%.1f", avgSleep)).append("hrs, Water ").append((int) avgWater).append("ml.\n");

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
            sb.append(m.getName()).append(" (").append(m.getDosage()).append("); ");
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
}