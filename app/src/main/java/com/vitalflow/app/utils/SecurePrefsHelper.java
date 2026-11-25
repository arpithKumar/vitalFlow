package com.vitalflow.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Helper for storing sensitive values (currently: Gemini API key) in
 * {@link EncryptedSharedPreferences} backed by AES-256-GCM.
 *
 * <p>On first read, performs a one-time silent migration: if the key exists in plaintext
 * {@code vital_prefs} it is copied to the encrypted store and removed from plaintext.
 *
 * <p>All methods fall back to plaintext {@code vital_prefs} if the KeyStore is unavailable
 * (rare, but possible on some heavily-modified OEM builds), so the app never crashes.
 */
public class SecurePrefsHelper {

    private static final String TAG = "SecurePrefsHelper";
    private static final String SECURE_PREFS_FILE  = "secure_vital_prefs";
    private static final String KEY_GEMINI_API_KEY = "user_gemini_key";
    /** Legacy plaintext file — used only for migration and fallback. */
    private static final String LEGACY_PREFS_FILE  = "vital_prefs";

    /** Cached instance — EncryptedSharedPreferences is expensive to construct. */
    private static SharedPreferences cachedSecurePrefs = null;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Reads the Gemini API key from encrypted storage.
     * Transparently migrates a plaintext key on first call.
     *
     * @return The API key string, or {@code null} if not set.
     */
    public static String getGeminiKey(Context context) {
        try {
            SharedPreferences secure = getSecurePrefs(context);
            // One-time migration from plaintext vital_prefs
            if (!secure.contains(KEY_GEMINI_API_KEY)) {
                SharedPreferences plain = context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE);
                String plainKey = plain.getString(KEY_GEMINI_API_KEY, null);
                if (plainKey != null && !plainKey.isEmpty()) {
                    secure.edit().putString(KEY_GEMINI_API_KEY, plainKey).apply();
                    plain.edit().remove(KEY_GEMINI_API_KEY).apply();
                }
            }
            return secure.getString(KEY_GEMINI_API_KEY, null);
        } catch (Exception e) {
            Log.e(TAG, "KeyStore unavailable — falling back to plaintext for read", e);
            cachedSecurePrefs = null; // force re-init on next attempt
            return context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
                    .getString(KEY_GEMINI_API_KEY, null);
        }
    }

    /**
     * Saves the Gemini API key to encrypted storage and removes any plaintext copy.
     *
     * @param key The API key, or {@code null} / empty string to clear it.
     */
    public static void setGeminiKey(Context context, String key) {
        try {
            SharedPreferences secure = getSecurePrefs(context);
            if (key == null || key.isEmpty()) {
                secure.edit().remove(KEY_GEMINI_API_KEY).apply();
            } else {
                secure.edit().putString(KEY_GEMINI_API_KEY, key).apply();
            }
            // Always scrub plaintext copy regardless of outcome above
            context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
                    .edit().remove(KEY_GEMINI_API_KEY).apply();
        } catch (Exception e) {
            Log.e(TAG, "KeyStore unavailable — falling back to plaintext for write", e);
            cachedSecurePrefs = null; // force re-init on next attempt
            context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
                    .edit().putString(KEY_GEMINI_API_KEY, key).apply();
        }
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    // security-crypto 1.1.0 stable deprecated MasterKey/EncryptedSharedPreferences in favor of
    // rolling your own Tink-based storage — a much larger migration than an incidental fix here.
    // Suppressing until that migration is deliberately scoped.
    @SuppressWarnings("deprecation")
    private static SharedPreferences getSecurePrefs(Context context)
            throws GeneralSecurityException, IOException {
        if (cachedSecurePrefs != null) return cachedSecurePrefs;
        MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        cachedSecurePrefs = EncryptedSharedPreferences.create(
                context.getApplicationContext(),
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
        return cachedSecurePrefs;
    }
}
