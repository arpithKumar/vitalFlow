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

    public GeminiRequest(String prompt, String base64Image, String mimeType) {
        this.contents = Collections.singletonList(new Content("user", prompt, base64Image, mimeType));
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
        public Content(String role, String text, String base64Image, String mimeType) {
            this.role = role;
            this.parts = new ArrayList<>();
            if (text != null && !text.isEmpty()) {
                this.parts.add(new Part(text));
            }

            this.parts.add(new Part(new InlineData(mimeType, base64Image)));
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

    private static class InlineData {
        @SerializedName("mime_type")
        private final String mime_type;
        @SerializedName("data")
        private final String data;

        public InlineData(String mime_type, String data) {
            this.mime_type = mime_type;
            this.data = data;
        }
    }
}
