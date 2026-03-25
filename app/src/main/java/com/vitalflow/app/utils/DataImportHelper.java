package com.vitalflow.app.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DataImportHelper {

    public interface ImportCallback {
        void onSuccess(int itemsRestored);

        void onError(String error);
    }

    public static void restoreFromExcel(Context context, Uri excelUri, AppDao appDao, ImportCallback callback) {
        new Thread(() -> {
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(excelUri);
                Workbook workbook = new XSSFWorkbook(inputStream);

                // 1. Target the Medications Sheet
                Sheet sheetMeds = workbook.getSheet("Medications");
                if (sheetMeds == null) {
                    callback.onError("Could not find 'Medications' sheet in this file.");
                    return;
                }

                List<Medication> restoredMeds = new ArrayList<>();

                // 2. Loop through rows (Skip row 0 because it's the Header)
                for (int i = 1; i <= sheetMeds.getLastRowNum(); i++) {
                    Row row = sheetMeds.getRow(i);
                    if (row == null) continue;

                    try {
                        String name = getCellText(row.getCell(0));
                        if (name.isEmpty()) continue; // Skip empty rows

                        String dosage = getCellText(row.getCell(1));

                        // Parse "2x Daily" back into an integer: 2
                        String freqString = getCellText(row.getCell(2)).replaceAll("[^0-9]", "");
                        int frequency = freqString.isEmpty() ? 1 : Integer.parseInt(freqString);

                        String scheduledDays = getCellText(row.getCell(3));
                        String time = getCellText(row.getCell(4));

                        // Parse Pills Left
                        String pillsString = getCellText(row.getCell(6)).replaceAll("[^0-9]", "");
                        int pillsLeft = pillsString.isEmpty() ? 30 : Integer.parseInt(pillsString);

                        // 3. Rebuild the Medication Object
                        // Note: We leave doctor, hospital, dates empty for now, and set prescriptionUri to null.
                        Medication med = new Medication(
                                name, dosage, "Pill", time,
                                "", "", "Today", "Forever",
                                pillsLeft, frequency, scheduledDays, null
                        );

                        restoredMeds.add(med);
                    } catch (Exception e) {
                        Log.e("ImportHelper", "Skipped corrupted row: " + i);
                    }
                }

                workbook.close();
                inputStream.close();

                // 4. Save to Room Database
                if (!restoredMeds.isEmpty()) {
                    for (Medication m : restoredMeds) {
                        appDao.insertMedication(m).blockingSubscribe();
                    }
                    callback.onSuccess(restoredMeds.size());
                } else {
                    callback.onError("No valid medications found to restore.");
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
                return String.valueOf((int) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
