package com.vitalflow.app.data.entity.gemini;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GeminiRequest {
    @SerializedName("contents")
    private final List<Content> contents;

    public GeminiRequest(String prompt) {
        this.contents = Collections.singletonList(new Content("user", prompt));
    }

    public GeminiRequest(String prompt, String base64Image) {
        this.contents = Collections.singletonList(new Content("user", prompt, base64Image));
    }

    private static class Content {
        @SerializedName("role")
        private final String role;

        @SerializedName("parts")
        private final List<Part> parts;

        // Text Only
        public Content(String role, String text) {
            this.role = role;
            this.parts = Collections.singletonList(new Part(text));
        }

        // Text + Image
        public Content(String role, String text, String base64Image) {
            this.role = role;
            this.parts = new ArrayList<>();
            // Gemini prefers text context first
            if (text != null && !text.isEmpty()) {
                this.parts.add(new Part(text));
            }

            // Add Image Data
            InlineData inlineData = new InlineData("image/jpeg", base64Image);
            this.parts.add(new Part(inlineData));
        }
    }

    private static class Part {
        @SerializedName("text")
        private String text;

        @SerializedName("inline_data")
        private InlineData inline_data;

        public Part(String text) {
            this.text = text;
        }

        public Part(InlineData inlineData) {
            this.inline_data = inlineData;
        }
    }

    private record InlineData(@SerializedName("mime_type") String mime_type,
                              @SerializedName("data") String data) {
    }
}
