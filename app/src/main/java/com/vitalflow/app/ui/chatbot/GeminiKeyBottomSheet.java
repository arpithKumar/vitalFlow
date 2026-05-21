package com.vitalflow.app.ui.chatbot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.vitalflow.app.utils.SecurePrefsHelper;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vitalflow.app.databinding.BottomSheetGeminiKeyBinding;

public class GeminiKeyBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetGeminiKeyBinding binding;
    private OnKeySavedListener listener;

    private final String[] MODELS = new String[]{
            // --- GEMINI 3 (2026 Cutting Edge) ---
            "gemini-3-pro-preview",      // Smartest: Best for PDF Reports & Complex logic
            "gemini-3-flash-preview",    // Fastest: Best for instant Chat

            // --- GEMINI 2.5 (2025 Stable) ---
            "gemini-2.5-pro",            // Balanced High Intelligence
            "gemini-2.5-flash",          // Balanced Speed
            "gemini-2.0-flash-exp",      // Experimental (Google's testing ground)

            // --- GEMINI 1.5 (Reliable & Free Tier Friendly) ---
            "gemini-1.5-pro",            // Great reasoning, lower rate limits
            "gemini-1.5-flash",          // The "Safe Default" (rarely hits 429 errors)
            "gemini-1.5-flash-8b",       // Extremely lightweight

            // --- LEGACY ---
            "gemini-1.0-pro"
    };

    public interface OnKeySavedListener {
        void onKeySaved();
    }

    public void setListener(OnKeySavedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGeminiKeyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, MODELS);
        binding.autoCompleteModel.setAdapter(adapter);

        String savedKey = SecurePrefsHelper.getGeminiKey(requireContext());
        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        String savedModel = prefs.getString("user_gemini_model", "gemini-3-flash-preview"); // Default

        binding.etApiKey.setText(savedKey != null ? savedKey : "");
        binding.autoCompleteModel.setText(savedModel, false);

        // 1. Link to Google AI Studio
        binding.btnGetKey.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"));
            startActivity(intent);
        });

        // 2. Save Key Logic
        binding.btnSaveKey.setOnClickListener(v -> {
            String key = binding.etApiKey.getText().toString().trim();
            String model = binding.autoCompleteModel.getText().toString().trim();

            if (key.length() > 20 && key.startsWith("AIza")) { // Basic validation for Google Keys
                saveKey(key, model);
                if (listener != null) listener.onKeySaved();
                dismiss();
                Toast.makeText(getContext(), "Gemini Connected! 🌟", Toast.LENGTH_SHORT).show();
            } else {
                binding.etApiKey.setError("Invalid API Key (should start with AIza)");
            }
        });
    }

    private void saveKey(String key, String model) {
        // API key stored encrypted; model is non-sensitive, stays in vital_prefs
        SecurePrefsHelper.setGeminiKey(requireContext(), key);
        requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE)
                .edit().putString("user_gemini_model", model).apply();
    }
}