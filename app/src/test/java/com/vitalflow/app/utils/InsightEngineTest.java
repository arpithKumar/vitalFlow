package com.vitalflow.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vitalflow.app.data.entity.DailyHealth;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link InsightEngine}: which health message is shown for which day. The rules
 * are checked in priority order (mood + activity, then hydration, then step trend, then the
 * default), so each test isolates one rule and one test pins the priority.
 */
public class InsightEngineTest {

    private static final int GOAL_ML = 2000;

    @Test
    public void noDataForToday_returnsGenericEncouragement() {
        assertTrue(InsightEngine.getInsight(null, Collections.emptyList()).contains("small step"));
    }

    @Test
    public void goodMoodAndManySteps_returnsActivityMessage() {
        DailyHealth today = day(500, 6001, 4);

        assertTrue(InsightEngine.getInsight(today, Collections.emptyList())
                .contains("active and feeling great"));
    }

    @Test
    public void goodMoodButExactly6000Steps_doesNotCountAsActive() {
        DailyHealth today = day(500, 6000, 5);

        assertTrue(InsightEngine.getInsight(today, Collections.emptyList())
                .contains("Consistency is the secret"));
    }

    @Test
    public void lowMoodWithManySteps_doesNotCountAsActive() {
        DailyHealth today = day(500, 9000, 3);

        assertFalse(InsightEngine.getInsight(today, Collections.emptyList())
                .contains("active and feeling great"));
    }

    @Test
    public void hydrationGoalReached_returnsHydrationMessage() {
        DailyHealth today = day(GOAL_ML, 1000, 0);

        assertTrue(InsightEngine.getInsight(today, Collections.emptyList())
                .contains("Hydration is the best fuel"));
    }

    @Test
    public void moodAndActivityMessage_winsOverHydrationMessage() {
        DailyHealth today = day(GOAL_ML + 500, 8000, 5);

        assertTrue(InsightEngine.getInsight(today, Collections.emptyList())
                .contains("active and feeling great"));
    }

    @Test
    public void stepsAboveWeeklyAverage_returnsTrendMessage() {
        List<DailyHealth> history = Arrays.asList(
                day(0, 4000, 0), day(0, 5000, 0), day(0, 6000, 0)); // average 5000
        DailyHealth today = day(500, 7000, 0);

        assertTrue(InsightEngine.getInsight(today, history).contains("surpassed your weekly average"));
    }

    @Test
    public void stepsEqualToWeeklyAverage_fallsBackToDefaultMessage() {
        List<DailyHealth> history = Arrays.asList(day(0, 4000, 0), day(0, 6000, 0)); // average 5000
        DailyHealth today = day(500, 5000, 0);

        assertTrue(InsightEngine.getInsight(today, history).contains("Consistency is the secret"));
    }

    @Test
    public void zeroStepsToday_neverCountsAsAboveAverage() {
        List<DailyHealth> history = Arrays.asList(day(0, 0, 0), day(0, 0, 0)); // average 0
        DailyHealth today = day(500, 0, 0);

        assertTrue(InsightEngine.getInsight(today, history).contains("Consistency is the secret"));
    }

    @Test
    public void missingOrEmptyHistory_fallsBackToDefaultMessage() {
        DailyHealth today = day(500, 7000, 0);

        assertTrue(InsightEngine.getInsight(today, null).contains("Consistency is the secret"));
        assertTrue(InsightEngine.getInsight(today, Collections.emptyList())
                .contains("Consistency is the secret"));
    }

    private static DailyHealth day(int waterMl, int steps, int mood) {
        return new DailyHealth("2026-11-24", waterMl, GOAL_ML, steps, "7h 0m", mood);
    }
}
