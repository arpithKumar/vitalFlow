package com.vitalflow.app.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataImportHelper {

    public interface ImportCallback {
        void onSuccess(int itemsRestored);

        void onError(String error);
    }

    public static void restoreFromExcel(Context context, Uri excelUri, AppDao appDao, ImportCallback callback) {
        new Thread(() -> {
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(excelUri);
                if (inputStream == null) {
                    callback.onError("Could not open the file. It might be inaccessible or deleted.");
                    return;
                }
                Workbook workbook = new XSSFWorkbook(inputStream);

                List<Medication> restoredMeds = new ArrayList<>();
                List<PeriodLog> restoredPeriods = new ArrayList<>();

                // 1. Target the Medications Sheet
                Sheet sheetMeds = workbook.getSheet("Medications");
                if (sheetMeds != null) {
                    for (int i = 1; i <= sheetMeds.getLastRowNum(); i++) {
                        Row row = sheetMeds.getRow(i);
                        if (row == null) continue;

                        try {
                            // Column 0-3: Core Details
                            String name = getCellText(row.getCell(0));
                            if (name.isEmpty()) continue; // Name is mandatory

                            String dosage = getCellText(row.getCell(1));

                            String type = getCellText(row.getCell(2));
                            if (type.isEmpty()) type = "Pill";

                            String time = getCellText(row.getCell(3));

                            // Column 4-5: Schedule
                            String freqString = getCellText(row.getCell(4)).replaceAll("[^0-9]", "");
                            int frequency = freqString.isEmpty() ? 1 : Integer.parseInt(freqString);

                            String scheduledDays = getCellText(row.getCell(5));

                            // Column 6-7: Dates
                            String startDate = getCellText(row.getCell(6));
                            if (startDate.isEmpty()) {
                                startDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                            }

                            String endDate = getCellText(row.getCell(7));
                            if (endDate.isEmpty()) endDate = "Forever";

                            // Column 8-9: Medical Contacts
                            String doctor = getCellText(row.getCell(8));
                            String hospital = getCellText(row.getCell(9));

                            // Column 10: Inventory
                            String pillsString = getCellText(row.getCell(10)).replaceAll("[^0-9]", "");
                            int pillsLeft = pillsString.isEmpty() ? 30 : Integer.parseInt(pillsString);

                            // Column 11-13: Live Status Tracking
                            String takenString = getCellText(row.getCell(11)).replaceAll("[^0-9]", "");
                            int takenToday = takenString.isEmpty() ? 0 : Integer.parseInt(takenString);

                            String status = getCellText(row.getCell(12));
                            if (status.isEmpty()) status = "PENDING";

                            String lastActionDate = getCellText(row.getCell(13));

                            // Column 14: URI
                            String rxUri = getCellText(row.getCell(14));

                            // BUILD THE EXACT MIRROR OBJECT
                            Medication med = new Medication(
                                    name, dosage, type, time,
                                    doctor, hospital, startDate, endDate,
                                    pillsLeft, frequency, scheduledDays, rxUri
                            );

                            // Restore exact state counters
                            med.setTimesTakenToday(takenToday);
                            med.setDbStatus(status);
                            med.setLastActionDate(lastActionDate);

                            restoredMeds.add(med);
                        } catch (Exception e) {
                            Log.e("ImportHelper", "Skipped corrupted meds row: " + i);
                        }
                    }
                }

                Sheet sheetCycle = workbook.getSheet("Cycle History");
                if (sheetCycle != null) {
                    for (int i = 1; i <= sheetCycle.getLastRowNum(); i++) {
                        Row row = sheetCycle.getRow(i);
                        if (row == null) continue;

                        try {
                            String date = getCellText(row.getCell(0));
                            if (date.isEmpty()) continue;

                            // Convert "Yes"/"No" back to boolean
                            String flowStr = getCellText(row.getCell(1));
                            boolean isFlow = flowStr.equalsIgnoreCase("Yes");

                            // Convert "Light/Medium/Heavy" back to int
                            String intensityStr = getCellText(row.getCell(2));
                            int intensity = 0;
                            if (intensityStr.equalsIgnoreCase("Light")) intensity = 1;
                            else if (intensityStr.equalsIgnoreCase("Medium")) intensity = 2;
                            else if (intensityStr.equalsIgnoreCase("Heavy")) intensity = 3;

                            String symptoms = getCellText(row.getCell(3));

                            PeriodLog period = new PeriodLog(date, isFlow, intensity, symptoms);
                            restoredPeriods.add(period);
                        } catch (Exception e) {
                            Log.e("ImportHelper", "Skipped corrupted cycle row: " + i);
                        }
                    }
                }


                workbook.close();
                inputStream.close();

                int totalRestored = 0;

                // 4. Save to Room Database
                if (!restoredMeds.isEmpty()) {
                    for (Medication m : restoredMeds) {
                        appDao.insertMedication(m).blockingSubscribe();
                    }
                    totalRestored += restoredMeds.size();
                }

                if (!restoredPeriods.isEmpty()) {
                    for (PeriodLog p : restoredPeriods) {
                        appDao.insertPeriodLog(p).blockingSubscribe();
                    }
                    totalRestored += restoredPeriods.size();
                }

                if (totalRestored > 0) {
                    callback.onSuccess(totalRestored);
                } else {
                    callback.onError("No valid data found to restore. Check the Excel file format.");
                }

            } catch (Exception e) {
                callback.onError("Failed to read Excel file: " + e.getMessage());
            }
        }).start();
    }

    // Helper to safely extract text from Apache POI cells regardless of formatting
    private static String getCellText(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                    return sdf.format(cell.getDateCellValue());
                }
                // Otherwise treat as plain number (removing decimals if it's a whole number)
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
