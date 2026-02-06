package com.vitalflow.app.utils;

import com.vitalflow.app.data.entity.DailyHealth;

import java.util.List;

public class InsightEngine {
    public static String getInsight(DailyHealth today, List<DailyHealth> history) {
        if (today == null) return "Every small step counts towards a healthier heart!";

        // 1. Mood & Activity Correlation
        if (today.getMoodRating() >= 4 && today.getStepCount() > 6000) {
            return "You're active and feeling great today! Notice how movement boosts your mood?";
        }

        // 2. Hydration Logic
        if (today.getWaterIntakeMl() >= today.getWaterGoalMl()) {
            return "Goal reached! Hydration is the best fuel for your focus and energy.";
        }

        // 3. Activity Trend
        if (history != null && !history.isEmpty()) {
            double avgSteps = history.stream().mapToInt(DailyHealth::getStepCount).average().orElse(0);
            if (today.getStepCount() > avgSteps && today.getStepCount() > 0) {
                return "You're crushing it! You've already surpassed your weekly average steps.";
            }
        }

        return "Consistency is the secret. Small habits lead to life-changing results!";
    }
}