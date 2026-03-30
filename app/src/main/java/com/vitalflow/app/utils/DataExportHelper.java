package com.vitalflow.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Vital;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataExportHelper {

    public static void exportData(Context context, AppDao appDao) {
        Toast.makeText(context, "Generating Excel Report...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // 1. Fetch All Data
                List<Medication> meds = appDao.getAllMedicationsSync().blockingGet();
                List<Vital> vitals = appDao.getAllVitalsSync().blockingGet();
                List<DailyHealth> daily = appDao.getAllDailyHealthSync().blockingGet();
                List<PeriodLog> cycles = appDao.getAllLogsSync().blockingGet();
                List<FoodLog> food = appDao.getAllFoodLogsSync().blockingGet();

                // 2. Create Excel Workbook
                Workbook workbook = new XSSFWorkbook();

                // --- SHEET 1: MEDICATIONS ---
                Sheet sheetMeds = workbook.createSheet("Medications");
                createHeader(sheetMeds,
                        "Name", "Dosage", "Type", "Time", "Frequency/Day",
                        "Scheduled Days", "Start Date", "End Date", "Doctor Name",
                        "Hospital Name", "Pills Left", "Times Taken Today", "Status",
                        "Last Action Date", "Prescription URI (Rx)");
                int rowNum = 1;
                for (Medication m : meds) {
                    Row row = sheetMeds.createRow(rowNum++);
                    row.createCell(0).setCellValue(m.getName());
                    row.createCell(1).setCellValue(m.getDosage());
                    row.createCell(2).setCellValue(m.getType() != null ? m.getType() : "Pill");
                    row.createCell(3).setCellValue(m.getTime());
                    row.createCell(4).setCellValue(m.getFrequencyPerDay());
                    row.createCell(5).setCellValue(m.getSelectedDays());
                    row.createCell(6).setCellValue(m.getStartDate());
                    row.createCell(7).setCellValue(m.getEndDate());
                    row.createCell(8).setCellValue(m.getDoctorName() != null ? m.getDoctorName() : "");
                    row.createCell(9).setCellValue(m.getHospitalName() != null ? m.getHospitalName() : "");
                    row.createCell(10).setCellValue(m.getPillsLeft());
                    row.createCell(11).setCellValue(m.getTimesTakenToday());
                    row.createCell(12).setCellValue(m.getDbStatus() != null ? m.getDbStatus() : "PENDING");
                    row.createCell(13).setCellValue(m.getLastActionDate() != null ? m.getLastActionDate() : "");
                    // Save the ACTUAL URI string, not just "Yes" or "No"
                    row.createCell(14).setCellValue(m.getPrescriptionUri() != null ? m.getPrescriptionUri() : "");
                }

                // --- SHEET 2: VITALS ---
                Sheet sheetVitals = workbook.createSheet("Vitals");
                createHeader(sheetVitals, "Date", "Type", "Value", "Unit", "Context/Notes");
                rowNum = 1;
                for (Vital v : vitals) {
                    Row row = sheetVitals.createRow(rowNum++);
                    row.createCell(0).setCellValue(v.getDate());
                    row.createCell(1).setCellValue(v.getType());
                    row.createCell(2).setCellValue(v.getValue1() + (v.getValue2() != null ? "/" + v.getValue2() : ""));
                    row.createCell(3).setCellValue(v.getUnit());
                    row.createCell(4).setCellValue(v.getContextTags() + " " + v.getSymptoms());
                }

                // --- SHEET 3: DAILY HEALTH ---
                Sheet sheetDaily = workbook.createSheet("Daily Stats");
                createHeader(sheetDaily, "Date", "Steps", "Water (ml)", "Sleep", "Mood (1-5)");
                rowNum = 1;
                for (DailyHealth d : daily) {
                    Row row = sheetDaily.createRow(rowNum++);
                    row.createCell(0).setCellValue(d.getDate());
                    row.createCell(1).setCellValue(d.getStepCount());
                    row.createCell(2).setCellValue(d.getWaterIntakeMl());
                    row.createCell(3).setCellValue(d.getSleepDuration());
                    row.createCell(4).setCellValue(getMoodString(d.getMoodRating()));
                }

                // --- SHEET 4: CYCLE LOGS ---
                Sheet sheetCycle = workbook.createSheet("Cycle History");
                createHeader(sheetCycle, "Date", "Flow?", "Intensity", "Symptoms");
                rowNum = 1;
                for (PeriodLog p : cycles) {
                    Row row = sheetCycle.createRow(rowNum++);
                    row.createCell(0).setCellValue(p.date);
                    row.createCell(1).setCellValue(p.isFlow ? "Yes" : "No");
                    row.createCell(2).setCellValue(getIntensityString(p.flowIntensity));
                    row.createCell(3).setCellValue(p.symptoms);
                }

                // --- SHEET 5: NUTRITION ---
                Sheet sheetFood = workbook.createSheet("Nutrition");
                createHeader(sheetFood, "Date", "Time", "Food", "Calories", "Macros");
                rowNum = 1;
                for (FoodLog f : food) {
                    Row row = sheetFood.createRow(rowNum++);
                    row.createCell(0).setCellValue(f.date);
                    row.createCell(1).setCellValue(f.time);
                    row.createCell(2).setCellValue(f.foodName);
                    row.createCell(3).setCellValue(f.calories);
                    row.createCell(4).setCellValue(f.macros);
                }

                // 3. Save File
                String fileName = "VitalFlow_Report_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".xlsx";
                File file = new File(context.getCacheDir(), fileName);
                FileOutputStream fileOut = new FileOutputStream(file);
                workbook.write(fileOut);
                fileOut.close();
                workbook.close();

                // 4. Share
                shareFile(context, file);

            } catch (Exception e) {
                Log.e("Export", "Error", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Export Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private static void createHeader(Sheet sheet, String... headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
            sheet.setColumnWidth(i, 15 * 256); // Set reasonable column width
        }
    }

    private static String getIntensityString(int intensity) {
        if (intensity == 1) return "Light";
        if (intensity == 2) return "Medium";
        if (intensity == 3) return "Heavy";
        return "-";
    }

    private static String getMoodString(int rating) {
        return switch (rating) {
            case 1 -> "Terrible 😭";
            case 2 -> "Bad 😞";
            case 3 -> "Neutral 😐";
            case 4 -> "Good 🙂";
            case 5 -> "Excellent 🤩";
            default -> "Not Logged";
        };
    }

    private static void shareFile(Context context, File file) {
        try {
            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);

            // MIME type for .xlsx
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            intent.putExtra(Intent.EXTRA_SUBJECT, "My Health Report (VitalFlow)");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Share Report");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

        } catch (Exception e) {
            Log.e("Export", "Share Error", e);
        }
    }
}