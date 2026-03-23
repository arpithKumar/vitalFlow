package com.vitalflow.app.utils;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.Report;
import com.vitalflow.app.data.entity.Vital;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PdfReportManager {

    // A4 Dimensions (72 DPI)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 50;

    // Colors
    private static final int COLOR_PRIMARY = Color.parseColor("#00695C");
    private static final int COLOR_TEXT_DARK = Color.BLACK;
    private static final int COLOR_TEXT_LIGHT = Color.DKGRAY;
    private static final int COLOR_WARNING = Color.parseColor("#C62828"); // Red for alerts

    public static Uri createReport(Context context, Report data) {
        PdfDocument document = new PdfDocument();
        Paint paint = new Paint();
        Paint titlePaint = new Paint();

        // ---------------------------------------------------------
        // PAGE 1: DASHBOARD, GRAPH & AI SUMMARY
        // ---------------------------------------------------------
        PdfDocument.PageInfo pageInfo1 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page1 = document.startPage(pageInfo1);
        Canvas canvas = page1.getCanvas();

        int yPosition = MARGIN;

        // 1. Header
        Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_heart);
        if (logo != null) {
            Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, 40, 40, false);
            canvas.drawBitmap(scaledLogo, MARGIN, yPosition, paint);
        }

        titlePaint.setTextSize(24);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setColor(COLOR_PRIMARY);
        canvas.drawText("VitalFlow Clinical Report", MARGIN + 50, yPosition + 30, titlePaint);

        paint.setTextSize(10);
        paint.setColor(Color.GRAY);
        canvas.drawText("Generated: " + data.reportDate, PAGE_WIDTH - 180, yPosition + 30, paint);

        yPosition += 70;

        // 2. Patient Info
        drawSectionHeader(canvas, "Patient Profile", yPosition);
        yPosition += 25;
        paint.setTextSize(12);
        paint.setColor(COLOR_TEXT_DARK);
        canvas.drawText("Name: " + data.userName, MARGIN, yPosition, paint);
        canvas.drawText("Cycle Status: " + data.regularityScore, MARGIN, yPosition + 15, paint);
        yPosition += 40;

        // 3. AI Clinical Summary
        drawSectionHeader(canvas, "Clinical Summary (AI Analysis)", yPosition);
        yPosition += 25;

        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(12);
        textPaint.setColor(COLOR_TEXT_LIGHT);
        textPaint.setAntiAlias(true);

        StaticLayout aiLayout = StaticLayout.Builder.obtain(data.aiDoctorSummary, 0, data.aiDoctorSummary.length(), textPaint, PAGE_WIDTH - (2 * MARGIN))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1.0f, 1.1f)
                .setIncludePad(false)
                .build();

        canvas.save();
        canvas.translate(MARGIN, yPosition);
        aiLayout.draw(canvas);
        canvas.restore();

        yPosition += aiLayout.getHeight() + 35;

        // 4. Vitals Trend Graph (Heart Rate)
        if (data.recentVitals != null && !data.recentVitals.isEmpty()) {
            drawSectionHeader(canvas, "Heart Rate Trend (Last 10 Readings)", yPosition);
            yPosition += 30;
            drawSimpleGraph(canvas, data.recentVitals, MARGIN, yPosition, PAGE_WIDTH - (2 * MARGIN), 100);
            yPosition += 120; // Height of graph + padding
        }

        // 5. 30-Day Snapshot
        drawSectionHeader(canvas, "30-Day Averages", yPosition);
        yPosition += 35;
        double avgSteps = data.last30DaysStats.stream().mapToInt(DailyHealth::getStepCount).average().orElse(0);
        double avgWater = data.last30DaysStats.stream().mapToInt(DailyHealth::getWaterIntakeMl).average().orElse(0);

        drawStatBox(canvas, MARGIN, yPosition, "Avg Steps", String.format(Locale.US, "%.0f", avgSteps));
        drawStatBox(canvas, MARGIN + 130, yPosition, "Avg Water", String.format(Locale.US, "%.0f ml", avgWater));

        // 6. Doctor Signature Block (Uses Absolute Position, so yPosition doesn't matter here)
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 80, MARGIN + 200, PAGE_HEIGHT - 80, paint);
        canvas.drawLine(PAGE_WIDTH - 250, PAGE_HEIGHT - 80, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 80, paint);

        paint.setTextSize(10);
        paint.setColor(Color.DKGRAY);
        canvas.drawText("Physician Signature", MARGIN, PAGE_HEIGHT - 65, paint);
        canvas.drawText("Date", PAGE_WIDTH - 250, PAGE_HEIGHT - 65, paint);

        document.finishPage(page1);

        // ---------------------------------------------------------
        // PAGE 2: MEDICATIONS (With Red Flags)
        // ---------------------------------------------------------
        if (data.activeMedications != null && !data.activeMedications.isEmpty()) {
            PdfDocument.PageInfo pageInfo2 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create();
            PdfDocument.Page page2 = document.startPage(pageInfo2);
            Canvas canvas2 = page2.getCanvas();

            yPosition = MARGIN;
            drawSectionHeader(canvas2, "Current Medication Schedule", yPosition);
            yPosition += 30;

            // Table Header
            paint.setColor(Color.LTGRAY);
            paint.setStyle(Paint.Style.FILL);
            canvas2.drawRect(MARGIN, yPosition - 10, PAGE_WIDTH - MARGIN, yPosition + 15, paint);

            paint.setColor(COLOR_TEXT_DARK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(12);
            canvas2.drawText("Medication", MARGIN + 5, yPosition + 10, paint);
            canvas2.drawText("Dosage", MARGIN + 140, yPosition + 10, paint);
            canvas2.drawText("Frequency", MARGIN + 230, yPosition + 10, paint);
            canvas2.drawText("Adherence (Today)", MARGIN + 400, yPosition + 10, paint);

            paint.setTypeface(Typeface.DEFAULT);
            yPosition += 40;

            for (Medication med : data.activeMedications) {
                canvas2.drawText(med.getName(), MARGIN + 5, yPosition, paint);
                canvas2.drawText(med.getDosage(), MARGIN + 140, yPosition, paint);

                String freq = med.getFrequencyPerDay() + "x Daily";
                canvas2.drawText(freq, MARGIN + 230, yPosition, paint);
                paint.setTextSize(10);
                canvas2.drawText("@ " + med.getTime(), MARGIN + 230, yPosition + 12, paint);
                String days = med.getSelectedDays();
                if (days != null && !days.equals("Everyday")) {
                    canvas2.drawText(days, MARGIN + 230, yPosition + 24, paint);
                }

                paint.setTextSize(12);

                String statusText;
                int statusColor = COLOR_TEXT_DARK;

                if ("TAKEN".equals(med.getStatus())) {
                    statusText = "Completed";
                    statusColor = Color.parseColor("#2E7D32");
                } else if ("SKIPPED".equals(med.getStatus())) {
                    statusText = "Skipped";
                    statusColor = COLOR_WARNING;
                } else {
                    statusText = med.getTimesTakenToday() + "/" + med.getFrequencyPerDay();
                }

                paint.setColor(statusColor);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas2.drawText(statusText, MARGIN + 400, yPosition, paint);

                paint.setColor(COLOR_TEXT_DARK); // Reset
                paint.setTypeface(Typeface.DEFAULT);

                // Separator line
                paint.setColor(Color.LTGRAY);
                paint.setStrokeWidth(0.5f);
                canvas2.drawLine(MARGIN, yPosition + 30, PAGE_WIDTH - MARGIN, yPosition + 30, paint);

                yPosition += 45;
            }
            document.finishPage(page2);
        }

        // ---------------------------------------------------------
        // PAGE 3: VITALS & CYCLE (With Red Flags)
        // ---------------------------------------------------------
        if ((data.recentVitals != null && !data.recentVitals.isEmpty()) || (data.recentCycles != null && !data.recentCycles.isEmpty())) {
            PdfDocument.PageInfo pageInfo3 = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 3).create();
            PdfDocument.Page page3 = document.startPage(pageInfo3);
            Canvas canvas3 = page3.getCanvas();
            yPosition = MARGIN;

            // VITALS TABLE
            if (data.recentVitals != null && !data.recentVitals.isEmpty()) {
                drawSectionHeader(canvas3, "Recent Vitals History", yPosition);
                yPosition += 30;

                // Header
                paint.setColor(Color.LTGRAY);
                paint.setStyle(Paint.Style.FILL);
                canvas3.drawRect(MARGIN, yPosition - 10, PAGE_WIDTH - MARGIN, yPosition + 15, paint);
                paint.setColor(COLOR_TEXT_DARK);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas3.drawText("Date", MARGIN + 5, yPosition + 10, paint);
                canvas3.drawText("Type", MARGIN + 120, yPosition + 10, paint);
                canvas3.drawText("Value", MARGIN + 250, yPosition + 10, paint);
                canvas3.drawText("Context", MARGIN + 350, yPosition + 10, paint);
                paint.setTypeface(Typeface.DEFAULT);
                yPosition += 40;

                for (Vital v : data.recentVitals) {
                    canvas3.drawText(v.getDate(), MARGIN + 5, yPosition, paint);
                    canvas3.drawText(v.getType(), MARGIN + 120, yPosition, paint);

                    // [Red Flag] for Vitals
                    String val = v.getValue1();
                    if (v.getValue2() != null) val += "/" + v.getValue2();
                    val += " " + v.getUnit();

                    boolean isAbnormal = false;
                    try {
                        if (v.getType().equals("Heart Rate") && Integer.parseInt(v.getValue1()) > 100)
                            isAbnormal = true;
                        if (v.getType().contains("Oxygen") && Integer.parseInt(v.getValue1()) < 95)
                            isAbnormal = true;
                    } catch (Exception ignored) {
                    }

                    if (isAbnormal) paint.setColor(COLOR_WARNING); // RED
                    canvas3.drawText(val, MARGIN + 250, yPosition, paint);
                    paint.setColor(COLOR_TEXT_DARK); // Reset

                    String ctx = (v.getContextTags() == null || v.getContextTags().isEmpty()) ? "-" : v.getContextTags();
                    canvas3.drawText(ctx, MARGIN + 350, yPosition, paint);

                    yPosition += 25;
                }
                yPosition += 40;
            }

            // CYCLE TABLE
            if (data.recentCycles != null && !data.recentCycles.isEmpty()) {
                drawSectionHeader(canvas3, "Menstrual Cycle History", yPosition);
                yPosition += 30;

                paint.setColor(Color.LTGRAY);
                paint.setStyle(Paint.Style.FILL);
                canvas3.drawRect(MARGIN, yPosition - 10, PAGE_WIDTH - MARGIN, yPosition + 15, paint);
                paint.setColor(COLOR_TEXT_DARK);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas3.drawText("Date", MARGIN + 5, yPosition + 10, paint);
                canvas3.drawText("Flow", MARGIN + 150, yPosition + 10, paint);
                canvas3.drawText("Symptoms", MARGIN + 300, yPosition + 10, paint);
                paint.setTypeface(Typeface.DEFAULT);
                yPosition += 40;

                for (PeriodLog log : data.recentCycles) {
                    canvas3.drawText(log.date, MARGIN + 5, yPosition, paint);
                    String flow = log.isFlow ? (log.flowIntensity == 3 ? "Heavy" : (log.flowIntensity == 2 ? "Medium" : "Light")) : "None";
                    canvas3.drawText(flow, MARGIN + 150, yPosition, paint);
                    String sym = log.symptoms != null ? log.symptoms : "-";
                    if (sym.length() > 30) sym = sym.substring(0, 27) + "...";
                    canvas3.drawText(sym, MARGIN + 300, yPosition, paint);
                    yPosition += 25;
                }
            }

            document.finishPage(page3);
        }

        Uri savedUri = savePdf(context, document);
        document.close();
        return savedUri;
    }

    // --- CUSTOM GRAPH DRAWER (Vector based) ---
    private static void drawSimpleGraph(Canvas canvas, List<Vital> vitals, int x, int y, int width, int height) {
        // Filter only Heart Rate & Sort Chronologically
        List<Vital> heartRates = vitals.stream()
                .filter(v -> v.getType().equals("Heart Rate"))
                .sorted(Comparator.comparingLong(Vital::getTimestamp)) // Java 8 Comparator
                .collect(Collectors.toList());

        if (heartRates.isEmpty()) {
            Paint p = new Paint();
            p.setColor(Color.GRAY);
            p.setTextSize(12);
            canvas.drawText("No recent heart rate data to graph.", x, y + height / 2.0f, p);
            return;
        }

        Paint linePaint = new Paint();
        linePaint.setColor(COLOR_PRIMARY);
        linePaint.setStrokeWidth(2);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        Paint dotPaint = new Paint();
        dotPaint.setColor(COLOR_PRIMARY);
        dotPaint.setStyle(Paint.Style.FILL);

        Paint axisPaint = new Paint();
        axisPaint.setColor(Color.LTGRAY);
        axisPaint.setStrokeWidth(1);

        // Draw Axis Box
        canvas.drawLine(x, y, x, y + height, axisPaint); // Y-axis
        canvas.drawLine(x, y + height, x + width, y + height, axisPaint); // X-axis

        // Determine Min/Max
        float min = 40, max = 140; // Default range
        try {
            float realMin = Float.MAX_VALUE, realMax = Float.MIN_VALUE;
            for (Vital v : heartRates) {
                float val = Float.parseFloat(v.getValue1());
                if (val < realMin) realMin = val;
                if (val > realMax) realMax = val;
            }
            // Add padding
            min = Math.max(0, realMin - 10);
            max = realMax + 10;
        } catch (Exception e) {
        }

        float range = max - min;
        float stepX = (float) width / (Math.max(1, heartRates.size() - 1));

        Path path = new Path();
        for (int i = 0; i < heartRates.size(); i++) {
            float val = Float.parseFloat(heartRates.get(i).getValue1());

            // Normalize values to coordinates
            float px = x + (i * stepX);
            float py = y + height - ((val - min) / range * height);

            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);

            canvas.drawCircle(px, py, 3, dotPaint);
        }
        canvas.drawPath(path, linePaint);
    }

    private static void drawSectionHeader(Canvas canvas, String text, int y) {
        Paint paint = new Paint();
        paint.setColor(COLOR_PRIMARY);
        paint.setTextSize(16);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText(text, MARGIN, y, paint);

        paint.setStrokeWidth(1);
        paint.setColor(Color.LTGRAY);
        canvas.drawLine(MARGIN, y + 8, PAGE_WIDTH - MARGIN, y + 8, paint);
    }

    private static void drawStatBox(Canvas canvas, int x, int y, String label, String value) {
        Paint boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(Color.GRAY);
        boxPaint.setStrokeWidth(1);
        canvas.drawRect(x, y, x + 110, y + 60, boxPaint);

        Paint textPaint = new Paint();
        textPaint.setTextSize(10);
        textPaint.setColor(Color.GRAY);
        canvas.drawText(label, x + 10, y + 20, textPaint);

        textPaint.setTextSize(16);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(COLOR_TEXT_DARK);
        canvas.drawText(value, x + 10, y + 45, textPaint);
    }

    private static Uri savePdf(Context context, PdfDocument doc) {
        String fileName = "VitalFlow_Report_" + System.currentTimeMillis() + ".pdf";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream fos = context.getContentResolver().openOutputStream(uri)) {
                        doc.writeTo(fos);
                    }
                    Toast.makeText(context, "Full Report Saved to Downloads!", Toast.LENGTH_LONG).show();
                    return uri; // Return MediaStore URI
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    doc.writeTo(fos);
                }

                Toast.makeText(context, "Full Report Saved to Downloads!", Toast.LENGTH_LONG).show();
                // Return URI via FileProvider
                return FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        file
                );
            }
        } catch (Exception e) {
            Toast.makeText(context, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}