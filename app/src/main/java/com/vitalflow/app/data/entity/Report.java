package com.vitalflow.app.data.entity;

import java.util.List;

public class Report {
    public String userName;
    public String reportDate;

    // Cycle Data
    public List<PeriodLog> recentCycles;
    public String regularityScore; // e.g., "High", "Irregular"

    // Health Stats
    public List<DailyHealth> last30DaysStats;
    public List<Vital> recentVitals;
    public List<Medication> activeMedications;

    // The Golden Nugget
    public String aiDoctorSummary;

    public Report(String userName,
                  List<PeriodLog> recentCycles,
                  List<DailyHealth> stats,
                  List<Vital> vitals,
                  List<Medication> meds,
                  String aiSummary) {
        this.userName = userName;
        this.recentCycles = recentCycles;
        this.last30DaysStats = stats;
        this.recentVitals = vitals;
        this.activeMedications = meds;
        this.aiDoctorSummary = aiSummary;
        this.reportDate = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(new java.util.Date());
    }
}