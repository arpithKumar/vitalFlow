package com.vitalflow.app.utils;

import com.vitalflow.app.data.entity.CycleHistory;
import com.vitalflow.app.data.entity.PeriodLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CycleHelper {

    private static final long GAP_THRESHOLD_DAYS = 10; // New cycle if gap > 10 days

    public static List<CycleHistory> analyzeCycles(List<PeriodLog> logs) {
        List<CycleHistory> historyList = new ArrayList<>();
        List<Date> flowDates = getSortedFlowDates(logs);

        if (flowDates.isEmpty()) return historyList;

        // 1. Group dates into "Sessions" (Consecutive flow days)
        List<List<Date>> sessions = new ArrayList<>();
        List<Date> currentSession = new ArrayList<>();
        currentSession.add(flowDates.get(0));

        for (int i = 1; i < flowDates.size(); i++) {
            Date prev = flowDates.get(i - 1);
            Date curr = flowDates.get(i);

            long diffDays = getDaysDifference(prev, curr);

            if (diffDays > GAP_THRESHOLD_DAYS) {
                sessions.add(new ArrayList<>(currentSession));
                currentSession.clear();
            }
            currentSession.add(curr);
        }
        sessions.add(currentSession);

        // 2. Convert Sessions to CycleHistory objects (Newest first)
        for (int i = sessions.size() - 1; i >= 0; i--) {
            List<Date> session = sessions.get(i);
            Date start = session.get(0);
            Date end = session.get(session.size() - 1);
            int duration = (int) getDaysDifference(start, end) + 1;

            int cycleLength = 0;
            if (i > 0) {
                // Gap calculated from previous session's start
                Date prevStart = sessions.get(i - 1).get(0);
                cycleLength = (int) getDaysDifference(prevStart, start);
            }

            historyList.add(new CycleHistory(start, end, duration, cycleLength));
        }
        return historyList;
    }

    public static int calculateAverageCycleLength(List<CycleHistory> history) {
        if (history.size() < 2) return 28; // Default fallback
        long total = 0;
        int count = 0;
        for (CycleHistory item : history) {
            // Filter outliers (e.g. skipped months)
            if (item.cycleLengthDays > 20 && item.cycleLengthDays < 45) {
                total += item.cycleLengthDays;
                count++;
            }
        }
        return count == 0 ? 28 : (int) (total / count);
    }

    private static long getDaysDifference(Date d1, Date d2) {
        long diff = d2.getTime() - d1.getTime();
        return TimeUnit.MILLISECONDS.toDays(diff);
    }

    private static List<Date> getSortedFlowDates(List<PeriodLog> logs) {
        List<Date> dates = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

        for (PeriodLog log : logs) {
            if (log.isFlow) {
                try {
                    dates.add(sdf.parse(log.date));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        Collections.sort(dates);
        return dates;
    }

    // Ovulation Period calculator
    public static Date calculateOvulationDate(Date predictedNextPeriodStart) {
        Calendar c = Calendar.getInstance();
        c.setTime(predictedNextPeriodStart);
        c.add(Calendar.DAY_OF_YEAR, -14); // 14 days before period
        return c.getTime();
    }

    // Fertility window
    public static List<Date> calculateFertileWindow(Date ovulationDate) {
        List<Date> fertileDays = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        c.setTime(ovulationDate);

        // Add the 5 days leading up to ovulation
        c.add(Calendar.DAY_OF_YEAR, -5);
        for (int i = 0; i < 6; i++) { // 5 days before + ovulation day
            fertileDays.add(c.getTime());
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return fertileDays;
    }
}