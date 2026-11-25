package com.vitalflow.app.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for {@link GeminiKeyFormat}. The sample keys are made up. */
public class GeminiKeyFormatTest {

    @Test
    public void looksValid_classicAIzaKey_isAccepted() {
        assertTrue(GeminiKeyFormat.looksValid("AIzaSyExampleExampleExampleExample01234"));
    }

    @Test
    public void looksValid_newerKeyWithAQPrefix_isAccepted() {
        // The app used to insist on "AIza" and rejected working keys like this one.
        assertTrue(GeminiKeyFormat.looksValid("AQ.ExampleExampleExampleExampleExampleExample0123"));
    }

    @Test
    public void looksValid_surroundingWhitespace_isIgnored() {
        assertTrue(GeminiKeyFormat.looksValid("  AIzaSyExampleExampleExampleExample01234\n"));
    }

    @Test
    public void looksValid_nullOrBlank_isRejected() {
        assertFalse(GeminiKeyFormat.looksValid(null));
        assertFalse(GeminiKeyFormat.looksValid(""));
        assertFalse(GeminiKeyFormat.looksValid("      "));
    }

    @Test
    public void looksValid_tooShort_isRejected() {
        assertFalse(GeminiKeyFormat.looksValid("AIzaShort"));
    }

    @Test
    public void looksValid_urlPastedByMistake_isRejected() {
        assertFalse(GeminiKeyFormat.looksValid("https://aistudio.google.com/app/apikey"));
    }

    @Test
    public void looksValid_spaceInsideTheKey_isRejected() {
        assertFalse(GeminiKeyFormat.looksValid("AIzaSyExample ExampleExampleExample0123"));
    }

    @Test
    public void looksValid_absurdlyLong_isRejected() {
        assertFalse(GeminiKeyFormat.looksValid("A".repeat(300)));
    }
}
