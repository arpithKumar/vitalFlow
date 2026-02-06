package com.vitalflow.app.data.entity;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HealthPlan {
    @SerializedName("daySummary")
    public String daySummary;

    @SerializedName("workout")
    public List<Exercise> workout;

    @SerializedName("nutrition")
    public Nutrition nutrition;

    @SerializedName("reliefTips")
    public List<String> reliefTips;

    public static class Exercise {
        @SerializedName("name")
        public String name;

        @SerializedName("category")
        public String category;   // "Yoga", "Cardio", "Strength"

        @SerializedName("duration")
        public String duration;   // "3 sets of 12 reps"

        @SerializedName("steps")
        public List<String> steps;
    }

    public static class Nutrition {
        @SerializedName("eat")
        public List<String> eat;

        @SerializedName("avoid")
        public List<String> avoid;
    }
}