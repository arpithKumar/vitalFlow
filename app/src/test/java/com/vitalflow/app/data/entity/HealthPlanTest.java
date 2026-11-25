package com.vitalflow.app.data.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;

/** Pins the JSON keys the AI is asked to return for the daily health plan. */
public class HealthPlanTest {

    private final Gson gson = new Gson();

    @Test
    public void fromJson_fullPlan_mapsEveryField() {
        String json = "{\"daySummary\":\"Cycle day 3\","
                + "\"workout\":[{\"name\":\"Cat-cow\",\"category\":\"Yoga\","
                + "\"duration\":\"3 sets of 12 reps\",\"steps\":[\"Inhale\",\"Exhale\"]}],"
                + "\"nutrition\":{\"eat\":[\"Leafy greens\"],\"avoid\":[\"Caffeine\"]},"
                + "\"reliefTips\":[\"Use a heat pad\"]}";

        HealthPlan plan = gson.fromJson(json, HealthPlan.class);

        assertEquals("Cycle day 3", plan.daySummary);
        assertEquals(1, plan.workout.size());
        assertEquals("Cat-cow", plan.workout.get(0).name);
        assertEquals("Yoga", plan.workout.get(0).category);
        assertEquals("3 sets of 12 reps", plan.workout.get(0).duration);
        assertEquals(2, plan.workout.get(0).steps.size());
        assertEquals("Leafy greens", plan.nutrition.eat.get(0));
        assertEquals("Caffeine", plan.nutrition.avoid.get(0));
        assertEquals("Use a heat pad", plan.reliefTips.get(0));
    }

    @Test
    public void fromJson_missingSections_leavesThemNull() {
        HealthPlan plan = gson.fromJson("{\"daySummary\":\"Rest day\"}", HealthPlan.class);

        assertNotNull(plan);
        assertEquals("Rest day", plan.daySummary);
        assertNull(plan.workout);
        assertNull(plan.nutrition);
    }
}
