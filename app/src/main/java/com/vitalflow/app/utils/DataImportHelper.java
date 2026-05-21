package com.vitalflow.app.utils;

import android.content.Context;
import android.net.Uri;

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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class DataImportHelper {

    public interface ImportCallback {
        void onSuccess(int itemsRestored);
        void onError(String error);
    }

    /**
     * Parses {@code excelUri} on an IO thread and inserts the restored records into Room.
     * Delivers the result on the main thread via {@code callback}.
     * No raw threads — uses RxJava {@code Single.fromCallable}.
     */
    public static void restoreFromExcel(Context context, Uri excelUri,
                                        AppDao appDao, ImportCallback callback) {
        Single.fromCallable(() -> doImport(context, excelUri, appDao))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        callback::onSuccess,
                        error -> callback.onError(
                                error.getMessage() != null ? error.getMessage() : "Unknown import error")
                );
    }

    // -----------------------------------------------------------------------
    // Private — runs entirely on Schedulers.io()
    // -----------------------------------------------------------------------

    /** Returns the total number of records restored, or throws on failure. */
    private static int doImport(Context context, Uri excelUri, AppDao appDao) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(excelUri);
        if (inputStream == null) {
            throw new Exception("Could not open the file. It might be inaccessible or deleted.");
        }

        Workbook workbook = new XSSFWorkbook(inputStream);
        List<Medication> restoredMeds    = new ArrayList<>();
        List<PeriodLog>  restoredPeriods = new ArrayList<>();

        // ---- Medications sheet ----
        Sheet sheetMeds = workbook.getSheet("Medications");
        if (sheetMeds != null) {
            for (int i = 1; i <= sheetMeds.getLastRowNum(); i++) {
                Row row = sheetMeds.getRow(i);
                if (row == null) continue;
                try {
                    String name = getCellText(row.getCell(0));
                    if (name.isEmpty()) continue;

                    String dosage        = getCellText(row.getCell(1));
                    String type          = getCellText(row.getCell(2));
                    if (type.isEmpty()) type = "Pill";
                    String time          = getCellText(row.getCell(3));

                    String freqStr = getCellText(row.getCell(4)).replaceAll("[^0-9]", "");
                    int frequency  = freqStr.isEmpty() ? 1 : Integer.parseInt(freqStr);

                    String scheduledDays = getCellText(row.getCell(5));
                    String startDate     = getCellText(row.getCell(6));
                    if (startDate.isEmpty()) {
                        startDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(new Date());
                    }
                    String endDate = getCellText(row.getCell(7));
                    if (endDate.isEmpty()) endDate = "Forever";

                    String doctor   = getCellText(row.getCell(8));
                    String hospital = getCellText(row.getCell(9));

                    String pillsStr = getCellText(row.getCell(10)).replaceAll("[^0-9]", "");
                    int pillsLeft   = pillsStr.isEmpty() ? 30 : Integer.parseInt(pillsStr);

                    String takenStr = getCellText(row.getCell(11)).replaceAll("[^0-9]", "");
                    int takenToday  = takenStr.isEmpty() ? 0 : Integer.parseInt(takenStr);

                    String status        = getCellText(row.getCell(12));
                    if (status.isEmpty()) status = "PENDING";
                    String lastActionDate = getCellText(row.getCell(13));
                    String rxUri         = getCellText(row.getCell(14));

                    Medication med = new Medication(
                            name, dosage, type, time,
                            doctor, hospital, startDate, endDate,
                            pillsLeft, frequency, scheduledDays, rxUri);
                    med.setTimesTakenToday(takenToday);
                    med.setDbStatus(status);
                    med.setLastActionDate(lastActionDate);

                    restoredMeds.add(med);
                } catch (Exception ignored) {
                    // Skip corrupted rows silently
                }
            }
        }

        // ---- Cycle History sheet ----
        Sheet sheetCycle = workbook.getSheet("Cycle History");
        if (sheetCycle != null) {
            for (int i = 1; i <= sheetCycle.getLastRowNum(); i++) {
                Row row = sheetCycle.getRow(i);
                if (row == null) continue;
                try {
                    String date = getCellText(row.getCell(0));
                    if (date.isEmpty()) continue;

                    boolean isFlow = getCellText(row.getCell(1)).equalsIgnoreCase("Yes");

                    String intensityStr = getCellText(row.getCell(2));
                    int intensity = 0;
                    if (intensityStr.equalsIgnoreCase("Light"))  intensity = 1;
                    else if (intensityStr.equalsIgnoreCase("Medium")) intensity = 2;
                    else if (intensityStr.equalsIgnoreCase("Heavy"))  intensity = 3;

                    String symptoms = getCellText(row.getCell(3));
                    restoredPeriods.add(new PeriodLog(date, isFlow, intensity, symptoms));
                } catch (Exception ignored) {
                    // Skip corrupted rows silently
                }
            }
        }

        workbook.close();
        inputStream.close();

        // ---- Persist to Room (blocking is fine — we're on Schedulers.io()) ----
        int total = 0;
        for (Medication m : restoredMeds) {
            appDao.insertMedication(m).blockingSubscribe();
            total++;
        }
        for (PeriodLog p : restoredPeriods) {
            appDao.insertPeriodLog(p).blockingSubscribe();
            total++;
        }

        if (total == 0) {
            throw new Exception("No valid data found to restore. Check the Excel file format.");
        }
        return total;
    }

    // -----------------------------------------------------------------------
    // Cell parser
    // -----------------------------------------------------------------------
    private static String getCellText(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(cell.getDateCellValue());
                }
                double val = cell.getNumericCellValue();
                return val == (long) val ? String.valueOf((long) val) : String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
