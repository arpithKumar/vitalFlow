package com.vitalflow.app.utils;

import static org.junit.Assert.assertEquals;

import com.vitalflow.app.data.entity.CycleHistory;
import com.vitalflow.app.data.entity.PeriodLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Unit tests for {@link CycleHelper}: grouping flow days into cycles, the average cycle
 * length, and the ovulation / fertile-window maths. Pure JVM tests, no Android needed.
 */
public class CycleHelperTest {

    private Locale originalLocale;

    @Before
    public void pinLocale() {
        // CycleHelper parses "yyyy-MM-dd" with the default locale. Pin it so the tests do not
        // depend on the machine they run on.
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    // ---------------------------------------------------------------------------------------
    // analyzeCycles
    // ---------------------------------------------------------------------------------------

    @Test
    public void analyzeCycles_noLogs_returnsEmptyList() {
        assertEquals(0, CycleHelper.analyzeCycles(Collections.emptyList()).size());
    }

    @Test
    public void analyzeCycles_consecutiveFlowDays_formOneCycle() {
        List<CycleHistory> cycles = CycleHelper.analyzeCycles(
                flowDays("2026-01-12", "2026-01-13", "2026-01-14", "2026-01-15", "2026-01-16"));

        assertEquals(1, cycles.size());
        CycleHistory cycle = cycles.get(0);
        assertEquals(LocalDate.of(2026, 1, 12), utcDate(cycle.startDate));
        assertEquals(LocalDate.of(2026, 1, 16), utcDate(cycle.endDate));
        assertEquals(5, cycle.durationDays);
        assertEquals("the first cycle has no previous period to measure against",
                0, cycle.cycleLengthDays);
    }

    @Test
    public void analyzeCycles_gapOverThreshold_startsNewCycle_newestFirst() {
        List<CycleHistory> cycles = CycleHelper.analyzeCycles(flowDays(
                "2026-01-01", "2026-01-02", "2026-01-03",
                "2026-01-29", "2026-01-30", "2026-01-31"));

        assertEquals(2, cycles.size());

        CycleHistory newest = cycles.get(0);
        assertEquals(LocalDate.of(2026, 1, 29), utcDate(newest.startDate));
        assertEquals(3, newest.durationDays);
        assertEquals("cycle length is measured start to start (Jan 1 -> Jan 29)",
                28, newest.cycleLengthDays);

        CycleHistory oldest = cycles.get(1);
        assertEquals(LocalDate.of(2026, 1, 1), utcDate(oldest.startDate));
        assertEquals(0, oldest.cycleLengthDays);
    }

    @Test
    public void analyzeCycles_gapOfExactlyTenDays_staysInTheSameCycle() {
        List<CycleHistory> cycles = CycleHelper.analyzeCycles(flowDays("2026-01-01", "2026-01-11"));

        assertEquals("a 10 day gap is not larger than the threshold", 1, cycles.size());
        assertEquals(11, cycles.get(0).durationDays);
    }

    @Test
    public void analyzeCycles_gapOfElevenDays_startsNewCycle() {
        List<CycleHistory> cycles = CycleHelper.analyzeCycles(flowDays("2026-01-01", "2026-01-12"));

        assertEquals(2, cycles.size());
    }

    @Test
    public void analyzeCycles_ignoresDaysWithoutFlow() {
        List<PeriodLog> logs = new ArrayList<>();
        logs.add(new PeriodLog("2026-01-05", false, 0, "Cramps"));
        logs.add(new PeriodLog("2026-01-06", true, 2, ""));
        logs.add(new PeriodLog("2026-01-20", false, 0, "Headache"));

        List<CycleHistory> cycles = CycleHelper.analyzeCycles(logs);

        assertEquals(1, cycles.size());
        assertEquals(1, cycles.get(0).durationDays);
    }

    @Test
    public void analyzeCycles_unsortedInput_givesSameResultAsSortedInput() {
        List<CycleHistory> sorted = CycleHelper.analyzeCycles(
                flowDays("2026-01-01", "2026-01-02", "2026-01-29", "2026-01-30"));
        List<CycleHistory> reversed = CycleHelper.analyzeCycles(
                flowDays("2026-01-30", "2026-01-29", "2026-01-02", "2026-01-01"));

        assertEquals(sorted.size(), reversed.size());
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(sorted.get(i).startDate, reversed.get(i).startDate);
            assertEquals(sorted.get(i).durationDays, reversed.get(i).durationDays);
            assertEquals(sorted.get(i).cycleLengthDays, reversed.get(i).cycleLengthDays);
        }
    }

    @Test
    public void analyzeCycles_malformedDate_isSkippedInsteadOfCrashing() {
        List<PeriodLog> logs = new ArrayList<>();
        logs.add(new PeriodLog("not-a-date", true, 2, ""));
        logs.add(new PeriodLog("2026-01-06", true, 2, ""));

        List<CycleHistory> cycles = CycleHelper.analyzeCycles(logs);

        assertEquals(1, cycles.size());
        assertEquals(1, cycles.get(0).durationDays);
    }

    // ---------------------------------------------------------------------------------------
    // calculateAverageCycleLength
    // ---------------------------------------------------------------------------------------

    @Test
    public void averageCycleLength_withFewerThanTwoCycles_defaultsTo28() {
        assertEquals(28, CycleHelper.calculateAverageCycleLength(Collections.emptyList()));
        assertEquals(28, CycleHelper.calculateAverageCycleLength(
                Collections.singletonList(cycleOfLength(31))));
    }

    @Test
    public void averageCycleLength_ignoresImplausibleLengths() {
        // 0 = first cycle, 10 and 60 = outliers (skipped months or spotting). Only 28 and 30 count.
        List<CycleHistory> history = Arrays.asList(
                cycleOfLength(0), cycleOfLength(28), cycleOfLength(30),
                cycleOfLength(10), cycleOfLength(60));

        assertEquals(29, CycleHelper.calculateAverageCycleLength(history));
    }

    @Test
    public void averageCycleLength_whenEveryCycleIsAnOutlier_defaultsTo28() {
        List<CycleHistory> history = Arrays.asList(
                cycleOfLength(0), cycleOfLength(10), cycleOfLength(60));

        assertEquals(28, CycleHelper.calculateAverageCycleLength(history));
    }

    @Test
    public void averageCycleLength_isTruncatedNotRounded() {
        List<CycleHistory> history = Arrays.asList(cycleOfLength(28), cycleOfLength(29));

        assertEquals("(28 + 29) / 2 = 28.5, integer division gives 28",
                28, CycleHelper.calculateAverageCycleLength(history));
    }

    // ---------------------------------------------------------------------------------------
    // calculateOvulationDate / calculateFertileWindow
    // ---------------------------------------------------------------------------------------

    @Test
    public void ovulationDate_isFourteenDaysBeforeTheNextPeriod() {
        Date nextPeriod = localDate(LocalDate.of(2026, 11, 24));

        Date ovulation = CycleHelper.calculateOvulationDate(nextPeriod);

        assertEquals(LocalDate.of(2026, 11, 10), systemDate(ovulation));
    }

    @Test
    public void fertileWindow_isFiveDaysBeforeOvulationPlusOvulationDay() {
        Date ovulation = localDate(LocalDate.of(2026, 11, 10));

        List<Date> window = CycleHelper.calculateFertileWindow(ovulation);

        assertEquals(6, window.size());
        assertEquals(LocalDate.of(2026, 11, 5), systemDate(window.get(0)));
        assertEquals(LocalDate.of(2026, 11, 10), systemDate(window.get(5)));
        for (int i = 1; i < window.size(); i++) {
            assertEquals("fertile days are consecutive",
                    systemDate(window.get(i - 1)).plusDays(1), systemDate(window.get(i)));
        }
    }

    // ---------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------

    private static List<PeriodLog> flowDays(String... isoDates) {
        List<PeriodLog> logs = new ArrayList<>();
        for (String isoDate : isoDates) {
            logs.add(new PeriodLog(isoDate, true, 2, ""));
        }
        return logs;
    }

    private static CycleHistory cycleOfLength(int cycleLengthDays) {
        return new CycleHistory(new Date(0), new Date(0), 5, cycleLengthDays);
    }

    /** CycleHelper parses period dates in UTC, so read them back in UTC. */
    private static LocalDate utcDate(Date date) {
        return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** Ovulation / fertile-window maths uses the device time zone (Calendar.getInstance()). */
    private static LocalDate systemDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Date localDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
