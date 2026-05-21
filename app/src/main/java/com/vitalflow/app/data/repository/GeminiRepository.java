package com.vitalflow.app.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;

import com.vitalflow.app.BuildConfig;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.HealthPlan;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.entity.gemini.GeminiRequest;
import com.vitalflow.app.data.entity.gemini.GeminiResponse;
import com.vitalflow.app.data.network.GeminiApiService;
import com.vitalflow.app.utils.BitmapUtils;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
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

    private String getPersonaInstruction() {
        SharedPreferences prefs = context.getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        String persona = prefs.getString("ai_persona", "Standard Coach");

        if (persona.contains("Zen Master")) {
            return "Act as a highly spiritual, calming Zen Master. Use peaceful, mindful language. ";
        } else if (persona.contains("Cheerleader")) {
            return "Act as an overly enthusiastic, high-energy Cheerleader. Use very excited language and slang! ";
        } else if (persona.contains("Drill Sergeant")) {
            return "Act as a tough, loud, no-excuses military Drill Sergeant. Be demanding, strict, and use military terms. ";
        }

        return "Act as a friendly, motivational health coach. ";
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
        String prompt = getPersonaInstruction() +
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
                        .append(" (Dose: ").append(med.getDosage())
                        .append(", Scheduled: ").append(med.getSelectedDays())
                        .append(")\n");
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
        String contextBuilderString = contextBuilder.toString();
        String prompt = getPersonaInstruction() + "You are an expert Gynecologist, Fitness Coach and Nutritionist. \n" +
                "Analyze this user's data (Cycle Day, Sleep, Nutrition, Symptoms):\n" +
                "[" + contextBuilderString + "]\n\n" +
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
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String rawText = response.body().getText();
                        int startIndex = rawText.indexOf('{');
                        int endIndex = rawText.lastIndexOf('}');
                        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                            String json = rawText.substring(startIndex, endIndex + 1);
                            if (BuildConfig.DEBUG) Log.d("Gemini Periods Response", json);
                            HealthPlan plan = gson.fromJson(json, HealthPlan.class);
                            callback.onSuccess(plan);
                        } else {
                            callback.onError(new Exception("AI returned invalid format: " + rawText));
                        }
                    } catch (Exception e) {
                        callback.onError(new Exception("Failed to parse AI Plan: " + e.getMessage()));
                    }
                } else {
                    callback.onError(new Exception("AI Error: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                callback.onError(t);
            }
        });
    }

    public void sendMessage(String text, Uri imageUri, GeminiCallback callback) {

        String apiKey = getUserApiKey();

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

                    Bitmap bitmap = BitmapUtils.decodeSampledBitmap(resolver, imageUri, 800, 800);
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
        apiService.generateContent(model, apiKey, request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body().getText());
                } else {
                    try (okhttp3.ResponseBody errorBody = response.errorBody()) {
                        Log.e("GeminiRepo", "API Error: " + (null != errorBody ? errorBody.string() : "Unknown Error"));
                        callback.onError(new Exception("Error " + response.code() + ": " + errorBody));
                    } catch (Exception e) {
                        callback.onError(new Exception("Error " + response.code()));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                callback.onError(t);
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap, String mimeType) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
        if (mimeType.contains("png")) {
            format = Bitmap.CompressFormat.PNG;
        } else if (mimeType.contains("webp")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                format = Bitmap.CompressFormat.WEBP_LOSSY;
            } else {
                @SuppressWarnings("deprecation")
                Bitmap.CompressFormat legacy = Bitmap.CompressFormat.WEBP;
                format = legacy;
            }
        }

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
                    public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String rawText = response.body().getText();
                                int startIndex = rawText.indexOf('{');
                                int endIndex = rawText.lastIndexOf('}');
                                if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                                    String json = rawText.substring(startIndex, endIndex + 1);
                                    FoodAnalysisResult result = gson.fromJson(json, FoodAnalysisResult.class);
                                    callback.onSuccess(result);
                                } else {
                                    callback.onError(new Exception("AI returned invalid format: " + rawText));
                                }
                            } catch (Exception e) {
                                callback.onError(new Exception("Failed to parse food data: " + e.getMessage()));
                            }
                        } else {
                            callback.onError(new Exception("AI Error: " + response.code()));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
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

    public void analyzePrescription(Bitmap image, PrescriptionCallback callback) {
        String apiKey = getUserApiKey();
        if (apiKey == null) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }

        String prompt = "Analyze this medication label/prescription. Extract the data. " +
                "Return ONLY raw JSON (no markdown) in this exact format: " +
                "{ \"name\": \"Medication Name\", \"dosage\": \"e.g. 500mg\", \"frequency\": 2, \"type\": \"Pill\" }. " +
                "If type is unknown, use \"Pill\". If frequency is unknown, use 1.";

        executor.execute(() -> {
            try {
                String base64 = bitmapToBase64(image, "image/jpeg");
                GeminiRequest request = new GeminiRequest(prompt, base64, "image/jpeg");

                apiService.generateContent(getUserModel(), apiKey, request).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String rawText = response.body().getText();
                                int startIndex = rawText.indexOf('{');
                                int endIndex = rawText.lastIndexOf('}');
                                if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                                    String json = rawText.substring(startIndex, endIndex + 1);
                                    PrescriptionResult result = gson.fromJson(json, PrescriptionResult.class);
                                    callback.onSuccess(result);
                                } else {
                                    callback.onError(new Exception("Invalid format"));
                                }
                            } catch (Exception e) {
                                callback.onError(e);
                            }
                        } else {
                            callback.onError(new Exception("AI Error"));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                        callback.onError(t);
                    }
                });
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public interface PrescriptionCallback {
        void onSuccess(PrescriptionResult result);

        void onError(Throwable t);
    }

    public static class PrescriptionResult {
        public String name;
        public String dosage;
        public int frequency;
        public String type;
    }

    public void analyzeVoiceCommand(String spokenText, List<Medication> activeMeds, VoiceCommandCallback callback) {
        String apiKey = getUserApiKey();
        if (apiKey == null) {
            callback.onError(new SecurityException("API_KEY_MISSING"));
            return;
        }

        // Pass the user's actual database medications to Gemini so it can ID them
        StringBuilder medsString = new StringBuilder();
        if (activeMeds != null) {
            for (Medication m : activeMeds) {
                medsString.append("{id: '").append(m.getId()).append("', name: '").append(m.getName()).append("'}, ");
            }
        }

        String prompt = "You are a health tracking AI. The user just voice-logged this: \"" + spokenText + "\"\n" +
                "Extract the actions they want to log into a strict JSON array. ONLY return the JSON array, no markdown.\n" +
                "Format rules:\n" +
                "1. For Water: {\"action\": \"WATER\", \"amount_ml\": 250} (Extract amount or default to 250)\n" +
                "2. For Food: {\"action\": \"FOOD\", \"food_name\": \"apple\", \"calories\": 95, \"macros\": \"14g Carbs\"}\n" +
                "3. For Medication: {\"action\": \"MEDICATION\", \"med_id\": \"id_here\", \"med_name\": \"name\"}. Match from this exact list: [" + medsString + "]. If the med isn't in the list, skip it.\n" +
                "Example output: [{\"action\": \"WATER\", \"amount_ml\": 500}, {\"action\": \"FOOD\", \"food_name\": \"Pizza\", \"calories\": 280, \"macros\": \"30g Carbs, 10g Fat, 12g Protein\"}]";

        executor.execute(() -> {
            try {
                GeminiRequest request = new GeminiRequest(prompt);
                apiService.generateContent(getUserModel(), apiKey, request).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String rawText = response.body().getText();
                                int startIndex = rawText.indexOf('[');
                                int endIndex = rawText.lastIndexOf(']');
                                if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                                    String json = rawText.substring(startIndex, endIndex + 1);

                                    // Parse the array of actions
                                    Type listType = new TypeToken<List<VoiceAction>>() {
                                    }.getType();
                                    List<VoiceAction> actions = gson.fromJson(json, listType);
                                    callback.onSuccess(actions);
                                } else {
                                    callback.onError(new Exception("Invalid format"));
                                }
                            } catch (Exception e) {
                                callback.onError(e);
                            }
                        } else {
                            callback.onError(new Exception("AI Error"));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                        callback.onError(t);
                    }
                });
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public interface VoiceCommandCallback {
        void onSuccess(List<VoiceAction> actions);

        void onError(Throwable t);
    }

    public static class VoiceAction {
        public String action;    // "WATER", "FOOD", "MEDICATION"
        public int amount_ml;    // For water
        public String food_name; // For food
        public int calories;     // For food
        public String macros;    // For food
        public String med_id;    // For medication
        public String med_name;  // For medication
    }
}