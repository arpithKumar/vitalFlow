package com.vitalflow.app.data.entity.gemini;

import java.util.List;

public class GeminiResponse {
    private List<Candidate> candidates;

    public String getText() {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0).content.parts.get(0).text;
        }
        return "Stay hydrated and healthy!";
    }

    private static class Candidate {
        private Content content;
    }

    private static class Content {
        private List<Part> parts;
    }

    private static class Part {
        private String text;
    }
}
