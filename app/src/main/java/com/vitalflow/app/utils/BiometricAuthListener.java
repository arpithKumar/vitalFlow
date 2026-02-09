package com.vitalflow.app.utils;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class BiometricAuthListener {

    private final FragmentActivity activity;
    private final OnAuthListener listener;

    public interface OnAuthListener {
        void onSuccess();

        void onError(String error);
    }

    public BiometricAuthListener(FragmentActivity activity, OnAuthListener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void startAuth() {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    listener.onError("Authentication Canceled");
                } else {
                    listener.onError(errString.toString());
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                listener.onSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("VitalFlow Security")
                .setSubtitle("Unlock to access your health data")
                .setNegativeButtonText("Exit App") // Or "Use PIN" if you implement that
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}