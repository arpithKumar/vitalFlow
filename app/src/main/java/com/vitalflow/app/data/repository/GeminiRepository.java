package com.vitalflow.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Base64;

import com.google.gson.Gson;
import com.vitalflow.app.data.entity.HealthPlan;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.gemini.GeminiRequest;
import com.vitalflow.app.data.entity.gemini.GeminiResponse;
import com.vitalflow.app.data.network.GeminiApiService;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class GeminiRepository {

    private final GeminiApiService apiService;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    @Inject
    public GeminiRepository(GeminiApiService apiService, @ApplicationContext Context context) {
        this.apiService = apiService;
        this.context = context;
    }

    private String getUserApiKey() {
        SharedPreferences prefs = context.getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_gemini_key", null);
    }

    private String getUserModel() {
        SharedPreferences prefs = context.getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_gemini_model", "gemini-3-flash-preview");
    }

    public void getDailyTip(String userStats, GeminiCallback callback) {

        String apiKey = getUserApiKey();
        String model = getUserModel();

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }
        // Construct a persona-based prompt
        String prompt = "Act as a friendly, motivational health coach. " +
                "Based on this user data today: [" + userStats + "], " +
                "give ONE single, concise, actionable health tip (max 15 words). " +
                "Do not greet. Just give the tip.";

        GeminiRequest request = new GeminiRequest(prompt);

        executeCall(apiKey, model, request, callback);
    }

    public void getDailyHealthPlan(List<PeriodLog> recentLogs, int cycleDay, HealthPlanCallback callback) {
        String apiKey = getUserApiKey();
        String model = getUserModel();

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }

        // 1. Build Context String from History (Last ~3-5 days)
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Current Cycle Day: ").append(cycleDay).append(". \n");
        contextBuilder.append("User History (Recent Logs):\n");

        if (recentLogs.isEmpty()) {
            contextBuilder.append("No recent symptoms logged.\n");
        } else {
            // We assume list is sorted newest first, so we reverse to tell a story
            for (int i = recentLogs.size() - 1; i >= 0; i--) {
                PeriodLog log = recentLogs.get(i);
                contextBuilder.append("- Date ").append(log.date)
                        .append(": Flow=").append(log.isFlow ? log.flowIntensity : "None")
                        .append(", Symptoms=").append(log.symptoms).append("\n");
            }
        }

        // 2. The Strict JSON Prompt
        String prompt = "You are an expert Gynecologist and Fitness Coach. \n" +
                "Analyze this user's menstrual history:\n" +
                "[" + contextBuilder.toString() + "]\n\n" +
                "Based strictly on the 'Current Cycle Day' and recent symptoms, generate a structured JSON health plan.\n" +
                "REQUIREMENTS:\n" +
                "1. Exercises: Suggest 3 specific exercises (Yoga/Stretching for period days, Cardio/Strength for others).\n" +
                "2. Steps: Provide 3-4 short instruction steps for each exercise.\n" +
                "3. Nutrition: Suggest 3 - 4 specific foods to eat and 3 - 4 to avoid.\n" +
                "4. Relief: Suggest 3 - 4 gadgets or habits (e.g., 'Heating Pad', 'Magnesium').\n\n" +
                "OUTPUT FORMAT (Strict JSON, no markdown):\n" +
                "{\n" +
                "  \"daySummary\": \"Short 1-sentence summary.\",\n" +
                "  \"workout\": [\n" +
                "    { \"name\": \"Exercise Name\", \"category\": \"Yoga/Cardio\", \"duration\": \"3 sets x 10 reps\", \"steps\": [\"Step 1\", \"Step 2\"] }\n" +
                "  ],\n" +
                "  \"nutrition\": { \"eat\": [\"Food A\", \"Food B\"], \"avoid\": [\"Food X\", \"Food Y\"] },\n" +
                "  \"reliefTips\": [\"Tip 1\", \"Tip 2\"]\n" +
                "}";

        GeminiRequest request = new GeminiRequest(prompt);

        apiService.generateContent(model, apiKey, request).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String rawText = response.body().getText();
                        // Cleanup Markdown (Gemini often wraps in ```json ... ```)
                        String json = rawText.replaceAll("```json", "").replaceAll("```", "").trim();

                        HealthPlan plan = gson.fromJson(json, HealthPlan.class);
                        callback.onSuccess(plan);
                    } catch (Exception e) {
                        callback.onError(new Exception("Failed to parse AI Plan: " + e.getMessage()));
                    }
                } else {
                    callback.onError(new Exception("AI Error: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void sendMessage(String prompt, Bitmap image, GeminiCallback callback) {

        String apiKey = getUserApiKey();
        String model = getUserModel();

        // 1. Check if Key Exists
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }

        if (image != null) {
            // Encode image in background to avoid UI freeze
            executor.execute(() -> {
                String base64Image = bitmapToBase64(image);
                GeminiRequest request = new GeminiRequest(prompt, base64Image);
                executeCall(apiKey, model, request, callback);
            });
        } else {
            // Text only
            GeminiRequest request = new GeminiRequest(prompt);
            executeCall(apiKey, model, request, callback);
        }
    }

    private void executeCall(String apiKey, String model, GeminiRequest request, GeminiCallback callback) {
        apiService.generateContent(apiKey, model, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().getText());
                } else {
                    callback.onError(new Exception("AI Error: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // Compress to JPEG to reduce size (Gemini has limits)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    public interface GeminiCallback {
        void onSuccess(String tip);

        void onError(Throwable t);
    }

    public interface HealthPlanCallback {
        void onSuccess(HealthPlan plan);

        void onError(Throwable t);
    }
}