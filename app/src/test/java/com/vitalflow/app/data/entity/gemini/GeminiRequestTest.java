package com.vitalflow.app.data.entity.gemini;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.Test;

/**
 * Pins the JSON the app sends to Gemini. A renamed Java field must never change these keys, which is
 * exactly what an unprotected release build used to do ({@code contents} became {@code a}).
 */
public class GeminiRequestTest {

    private final Gson gson = new Gson();

    @Test
    public void toJson_textOnlyPrompt_matchesGeminiRestShape() {
        String json = gson.toJson(new GeminiRequest("hello"));

        assertEquals(
                JsonParser.parseString(
                        "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"hello\"}]}]}"),
                JsonParser.parseString(json));
    }

    @Test
    public void toJson_promptWithImage_addsInlineDataPart() {
        String json = gson.toJson(new GeminiRequest("what is this?", "QUJD", "image/jpeg"));

        assertEquals(
                JsonParser.parseString("{\"contents\":[{\"role\":\"user\",\"parts\":["
                        + "{\"text\":\"what is this?\"},"
                        + "{\"inline_data\":{\"mime_type\":\"image/jpeg\",\"data\":\"QUJD\"}}]}]}"),
                JsonParser.parseString(json));
    }

    @Test
    public void toJson_imageWithoutPrompt_sendsOnlyTheImagePart() {
        String json = gson.toJson(new GeminiRequest("", "QUJD", "image/png"));

        assertEquals(
                JsonParser.parseString("{\"contents\":[{\"role\":\"user\",\"parts\":["
                        + "{\"inline_data\":{\"mime_type\":\"image/png\",\"data\":\"QUJD\"}}]}]}"),
                JsonParser.parseString(json));
    }
}
