package com.vitalflow.app.data.entity.gemini;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response body of the Gemini {@code generateContent} REST call.
 *
 * <p>Gson fills this class by reflection, so the JSON names are pinned with
 * {@link SerializedName} and the whole package is kept by R8 (see {@code proguard-rules.pro}).
 */
public class GeminiResponse {
    @SerializedName("candidates")
    private List<Candidate> candidates;

    public String getText() {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0).content.parts.get(0).text;
        }
        return "Stay hydrated and healthy!";
    }

    private static class Candidate {
        @SerializedName("content")
        private Content content;
    }

    private static class Content {
        @SerializedName("parts")
        private List<Part> parts;
    }

    private static class Part {
        @SerializedName("text")
        private String text;
    }
}
