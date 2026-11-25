package com.vitalflow.app.data.entity.gemini;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.Test;

/**
 * Pins the JSON contract of the Gemini {@code generateContent} reply. The keys below are what the
 * REST API sends, so they must not depend on Java field names (R8 renames those in release builds).
 */
public class GeminiResponseTest {

    private static final String FALLBACK = "Stay hydrated and healthy!";
    private final Gson gson = new Gson();

    @Test
    public void getText_realApiReply_returnsFirstPartOfFirstCandidate() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Drink more water.\"}],"
                + "\"role\":\"model\"},\"finishReason\":\"STOP\",\"index\":0}],"
                + "\"usageMetadata\":{\"promptTokenCount\":5,\"totalTokenCount\":9}}";

        GeminiResponse response = gson.fromJson(json, GeminiResponse.class);

        assertEquals("Drink more water.", response.getText());
    }

    @Test
    public void getText_emptyCandidates_returnsFallback() {
        GeminiResponse response = gson.fromJson("{\"candidates\":[]}", GeminiResponse.class);

        assertEquals(FALLBACK, response.getText());
    }

    @Test
    public void getText_noCandidatesKey_returnsFallback() {
        GeminiResponse response = gson.fromJson("{}", GeminiResponse.class);

        assertEquals(FALLBACK, response.getText());
    }
}
