package com.vitalflow.app.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@Entity(tableName = "medications")
public class Medication {

    @PrimaryKey
    @NonNull
    private String id;
    private String name;
    private String dosage;
    private String type;
    private String time;
    private int frequencyPerDay;
    private int timesTakenToday;
    private String startDate;
    private String endDate;

    private String selectedDays;
    private String prescriptionUri;

    // Extra details
    private String doctorName;
    private String hospitalName;
    private int pillsLeft;

    @ColumnInfo(name = "status")
    private String dbStatus;
    private String lastActionDate;

    @Ignore
    public Medication() {
    }

    @Ignore
    public Medication(String name, String dosage, String type, String time,
                      String doctorName, String hospitalName, String startDate, String endDate,
                      int pillsLeft, int frequencyPerDay, String selectedDays, String prescriptionUri) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.dosage = dosage;
        this.type = type;
        this.time = time;
        this.doctorName = doctorName;
        this.hospitalName = hospitalName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.pillsLeft = pillsLeft;
        this.frequencyPerDay = frequencyPerDay;
        this.selectedDays = selectedDays != null ? selectedDays : "Everyday";
        this.timesTakenToday = 0;
        this.dbStatus = "PENDING";
        this.lastActionDate = "";
        this.prescriptionUri = prescriptionUri;
    }

    // Helper Constructor for Room DB
    public Medication(@NonNull String id, String name, String dosage, String type, String time,
                      String doctorName, String hospitalName, String startDate, String endDate,
                      int pillsLeft, String dbStatus, String lastActionDate,
                      int frequencyPerDay, int timesTakenToday, String selectedDays, String prescriptionUri) {
        this.id = id;
        this.name = name;
        this.dosage = dosage;
        this.type = type;
        this.time = time;
        this.doctorName = doctorName;
        this.hospitalName = hospitalName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.pillsLeft = pillsLeft;
        this.dbStatus = dbStatus;
        this.lastActionDate = lastActionDate;
        this.frequencyPerDay = frequencyPerDay;
        this.selectedDays = selectedDays != null ? selectedDays : "Everyday";
        this.timesTakenToday = timesTakenToday;
        this.prescriptionUri = prescriptionUri;
    }

    // --- LOGIC METHODS ---

    // 1. Reset Logic: Resets counter if it's a new day
    public void checkDailyReset() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (!today.equals(lastActionDate)) {
            timesTakenToday = 0;
            dbStatus = "PENDING";
            lastActionDate = today;
        }
    }

    // 2. Status Logic
    public String getStatus() {
        checkDailyReset(); // Ensure fresh state
        if (timesTakenToday >= frequencyPerDay && frequencyPerDay > 0) return "TAKEN"; // Fully Done
        if ("SKIPPED".equals(dbStatus)) return "SKIPPED";
        return "PENDING";
    }

    // Helper Logic: Categorize time for the UI
    public String getTimeCategory() {
        if (time == null) return "Night";
        String t = time.toUpperCase();
        if (t.contains("AM")) return "Morning";
        if (t.contains("PM")) {
            try {
                int hour = Integer.parseInt(t.split(":")[0].trim());
                if (hour == 12 || (hour >= 1 && hour <= 4)) return "Afternoon";
            } catch (Exception ignored) {
            }
        }
        return "Night";
    }

    // --- GETTERS & SETTERS ---
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public int getPillsLeft() {
        return pillsLeft;
    }

    public void setPillsLeft(int pillsLeft) {
        this.pillsLeft = pillsLeft;
    }

    // DB Accessors
    public String getDbStatus() {
        return dbStatus;
    }

    public void setDbStatus(String dbStatus) {
        this.dbStatus = dbStatus;
    }

    public String getLastActionDate() {
        return lastActionDate;
    }

    public void setLastActionDate(String lastActionDate) {
        this.lastActionDate = lastActionDate;
    }

    // New Fields Accessors
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public int getFrequencyPerDay() {
        return frequencyPerDay;
    }

    public void setFrequencyPerDay(int frequencyPerDay) {
        this.frequencyPerDay = frequencyPerDay;
    }

    public int getTimesTakenToday() {
        return timesTakenToday;
    }

    public void setTimesTakenToday(int timesTakenToday) {
        this.timesTakenToday = timesTakenToday;
    }

    public String getSelectedDays() {
        return selectedDays;
    }

    public void setSelectedDays(String selectedDays) {
        this.selectedDays = selectedDays;
    }

    public String getPrescriptionUri() {
        return prescriptionUri;
    }

    public void setPrescriptionUri(String prescriptionUri) {
        this.prescriptionUri = prescriptionUri;
    }
}