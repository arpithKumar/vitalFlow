package com.vitalflow.app.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.HealthPlan;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.gemini.GeminiRequest;
import com.vitalflow.app.data.entity.gemini.GeminiResponse;
import com.vitalflow.app.data.network.GeminiApiService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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

        executeCall(apiKey, getUserModel(), request, callback);
    }

    public void getDailyHealthPlan(List<PeriodLog> recentLogs,
                                   int cycleDay,
                                   List<FoodLog> recentFood,
                                   List<Medication> activeMeds,
                                   String sleepDuration,
                                   HealthPlanCallback callback) {
        String apiKey = getUserApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }

        // 1. Build Context String from History (Last ~3-5 days)
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Current Cycle Day: ").append(cycleDay).append(". \n");

        contextBuilder.append("Last Night's Sleep: ").append(sleepDuration != null ? sleepDuration : "Unknown").append(".\n");

        contextBuilder.append("Active Medications:\n");
        if (activeMeds != null && !activeMeds.isEmpty()) {
            for (Medication med : activeMeds) {
                contextBuilder.append("- ").append(med.getName())
                        .append(" (Dose: ").append(med.getDosage()).append(")\n");
            }
        } else {
            contextBuilder.append("No active medications.\n");
        }

        contextBuilder.append("Recent Nutrition (Last 24h):\n");
        if (recentFood != null && !recentFood.isEmpty()) {
            for (FoodLog food : recentFood) {
                contextBuilder.append("- ").append(food.foodName)
                        .append(" (").append(food.calories).append(" kcal, ").append(food.macros).append(")\n");
            }
        } else {
            contextBuilder.append("No food logged recently.\n");
        }

        contextBuilder.append("Period History:\n");

        if (recentLogs.isEmpty()) {
            contextBuilder.append("No specific symptoms logged recently. Assume standard symptoms for this cycle phase.\n");
        } else {
            for (PeriodLog log : recentLogs) {
                contextBuilder.append("- Date ").append(log.date)
                        .append(": Flow=").append(log.isFlow ? log.flowIntensity : "None")
                        .append(", Symptoms=").append(log.symptoms).append("\n");
            }
        }

        // 2. The Strict JSON Prompt
        String prompt = "You are an expert Gynecologist, Fitness Coach and Nutritionist. \n" +
                "Analyze this user's data (Cycle Day, Sleep, Nutrition, Symptoms):\n" +
                "[" + contextBuilder.toString() + "]\n\n" +
                "Generate a highly personalized JSON health plan.\n" +
                "REQUIREMENTS:\n" +
                "1. If on Birth Control, ignore standard ovulation predictions.\n" +
                "2. Check if nutrition conflicts with meds (e.g. Grapefruit + certain meds).\n" +
                "3. If taking pain meds, suggest complementary non-drug relief (heat, rest).\n" +
                "4. If sleep is low (<7h), suggest rest or energy-boosting foods.\n" +
                "5. Exercises: Suggest 3 specific exercises (Yoga/Stretching for period days, Cardio/Strength for others) suited for the Cycle Day and Sleep level.\n" +
                "6. Steps: Provide 3-4 short instruction steps for each exercise.\n" +
                "7. Nutrition: Suggest 3 - 4 specific foods to eat and 3 - 4 to avoid.\n" +
                "8. Relief: Suggest 3 - 4 gadgets or habits (e.g., 'Heating Pad', 'Magnesium').\n\n" +
                "OUTPUT FORMAT (Strict JSON, no markdown):\n" +
                "{\n" +
                "  \"daySummary\": \"Short 1-sentence summary connecting diet/sleep to cycle.\",\n" +
                "  \"workout\": [\n" +
                "    { \"name\": \"Exercise Name\", \"category\": \"Yoga/Cardio\", \"duration\": \"3 sets x 10 reps\", \"steps\": [\"Step 1\", \"Step 2\"] }\n" +
                "  ],\n" +
                "  \"nutrition\": { \"eat\": [\"Food A\", \"Food B\"], \"avoid\": [\"Food X\", \"Food Y\"] },\n" +
                "  \"reliefTips\": [\"Tip 1\", \"Tip 2\"]\n" +
                "}";


        apiService.generateContent(getUserModel(), apiKey, new GeminiRequest(prompt)).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String rawText = response.body().getText();
                        String json = rawText.replaceAll("```json", "")
                                .replaceAll("```", "")
                                .trim();

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

    public void sendMessage(String text, Uri imageUri, GeminiCallback callback) {

        String apiKey = getUserApiKey();

        // 1. Check if Key Exists
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }

        String finalPrompt = (text == null || text.trim().isEmpty())
                ? "Analyze this image and provide insights." : text;

        if (imageUri != null) {
            executor.execute(() -> {
                try {
                    ContentResolver resolver = context.getContentResolver();
                    String mimeType = resolver.getType(imageUri);
                    if (mimeType == null) mimeType = "image/jpeg";

                    InputStream is = resolver.openInputStream(imageUri);
                    Bitmap bitmap = BitmapFactory.decodeStream(is);

                    if (bitmap == null) {
                        callback.onError(new Exception("Failed to decode image"));
                        return;
                    }

                    GeminiRequest request = new GeminiRequest(
                            finalPrompt,
                            bitmapToBase64(bitmap, mimeType),
                            mimeType);
                    executeCall(apiKey, getUserModel(), request, callback);

                } catch (Exception e) {
                    callback.onError(e);
                }
            });
        } else {
            executeCall(apiKey, getUserModel(), new GeminiRequest(finalPrompt), callback);
        }
    }

    private void executeCall(String apiKey, String model, GeminiRequest request, GeminiCallback callback) {
        apiService.generateContent(apiKey, model, request).enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().getText());
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown Error";
                        Log.e("GeminiRepo", "API Error: " + errorBody);
                        callback.onError(new Exception("Error " + response.code() + ": " + errorBody));
                    } catch (Exception e) {
                        callback.onError(new Exception("Error " + response.code()));
                    }
                }
            }

            @Override
            public void onFailure(Call<GeminiResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap, String mimeType) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
        if (mimeType.contains("png")) format = Bitmap.CompressFormat.PNG;
        else if (mimeType.contains("webp")) format = Bitmap.CompressFormat.WEBP;

        bitmap.compress(format, 80, byteArrayOutputStream);
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

    public void analyzeFoodImage(Bitmap image, FoodAnalysisCallback callback) {
        String apiKey = getUserApiKey();
        if (apiKey == null) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }

        // Strict JSON Prompt
        String prompt = "Analyze this food image. Estimate calories and macros. " +
                "Return ONLY raw JSON (no markdown) in this format: " +
                "{ \"food_name\": \"Grilled Chicken Salad\", \"calories\": 450, \"macros\": \"30g Protein, 15g Fat, 10g Carbs\" }";

        executor.execute(() -> {
            try {
                String base64 = bitmapToBase64(image, "image/jpeg");
                GeminiRequest request = new GeminiRequest(prompt, base64, "image/jpeg");

                apiService.generateContent(getUserModel(), apiKey, request).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String json = response.body().getText()
                                        .replaceAll("```json", "")
                                        .replaceAll("```", "").trim();

                                FoodAnalysisResult result = gson.fromJson(json, FoodAnalysisResult.class);
                                callback.onSuccess(result);
                            } catch (Exception e) {
                                callback.onError(new Exception("Failed to parse food data: " + e.getMessage()));
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
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public interface FoodAnalysisCallback {
        void onSuccess(FoodAnalysisResult result);

        void onError(Throwable t);
    }

    // Helper Class for JSON Parsing
    public static class FoodAnalysisResult {
        public String food_name;
        public int calories;
        public String macros;
    }
}