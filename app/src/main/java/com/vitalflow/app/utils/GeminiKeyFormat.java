package com.vitalflow.app.utils;

import java.util.regex.Pattern;

/**
 * Cheap sanity check for a pasted Gemini API key.
 *
 * <p>Google decides whether a key is valid. This only catches obvious paste mistakes (empty, spaces, a
 * URL, a truncated copy) before the key is saved. It deliberately does not insist on a prefix: keys
 * can start with {@code AIza} (older keys) or {@code AQ.} (newer keys), and a fixed prefix would reject
 * a working key.
 */
public final class GeminiKeyFormat {

    private static final int MIN_LENGTH = 20;
    private static final int MAX_LENGTH = 256;
    /** Letters, digits and the separators that appear in Google API keys. */
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._-]+");

    private GeminiKeyFormat() {
    }

    /** True if {@code key}, after trimming, has a plausible length and only key characters. */
    public static boolean looksValid(String key) {
        if (key == null) {
            return false;
        }
        String trimmed = key.trim();
        return trimmed.length() >= MIN_LENGTH
                && trimmed.length() <= MAX_LENGTH
                && ALLOWED.matcher(trimmed).matches();
    }
}
