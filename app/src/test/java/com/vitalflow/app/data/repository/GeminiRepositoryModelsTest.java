package com.vitalflow.app.data.repository;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Pins the JSON keys of the result types that {@link GeminiRepository} parses from the model's reply.
 * The prompts in GeminiRepository ask the model for exactly these keys.
 */
public class GeminiRepositoryModelsTest {

    private final Gson gson = new Gson();

    @Test
    public void foodAnalysisResult_fromJson_mapsSnakeCaseKeys() {
        GeminiRepository.FoodAnalysisResult result = gson.fromJson(
                "{\"food_name\":\"Dal rice\",\"calories\":420,\"macros\":\"P14 C70 F8\"}",
                GeminiRepository.FoodAnalysisResult.class);

        assertEquals("Dal rice", result.food_name);
        assertEquals(420, result.calories);
        assertEquals("P14 C70 F8", result.macros);
    }

    @Test
    public void prescriptionResult_fromJson_mapsEveryField() {
        GeminiRepository.PrescriptionResult result = gson.fromJson(
                "{\"name\":\"Paracetamol\",\"dosage\":\"500mg\",\"frequency\":3,\"type\":\"Tablet\"}",
                GeminiRepository.PrescriptionResult.class);

        assertEquals("Paracetamol", result.name);
        assertEquals("500mg", result.dosage);
        assertEquals(3, result.frequency);
        assertEquals("Tablet", result.type);
    }

    @Test
    public void voiceActions_fromJsonList_mapsWaterAndFoodActions() {
        Type listType = new TypeToken<List<GeminiRepository.VoiceAction>>() { }.getType();

        List<GeminiRepository.VoiceAction> actions = gson.fromJson(
                "[{\"action\":\"WATER\",\"amount_ml\":250},"
                        + "{\"action\":\"FOOD\",\"food_name\":\"Egg\",\"calories\":70,\"macros\":\"P6\"},"
                        + "{\"action\":\"MEDICATION\",\"med_id\":\"m1\",\"med_name\":\"Iron\"}]",
                listType);

        assertEquals(3, actions.size());
        assertEquals("WATER", actions.get(0).action);
        assertEquals(250, actions.get(0).amount_ml);
        assertEquals("Egg", actions.get(1).food_name);
        assertEquals(70, actions.get(1).calories);
        assertEquals("P6", actions.get(1).macros);
        assertEquals("m1", actions.get(2).med_id);
        assertEquals("Iron", actions.get(2).med_name);
    }
}
