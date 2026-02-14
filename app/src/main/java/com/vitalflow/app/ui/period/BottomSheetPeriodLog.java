package com.vitalflow.app.ui.period;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.vitalflow.app.R;
import com.vitalflow.app.databinding.BottomSheetPeriodLogBinding;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BottomSheetPeriodLog extends BottomSheetDialogFragment {

    private BottomSheetPeriodLogBinding binding;
    private OnLogSavedListener listener;
    private List<String> dateKeys;

    public interface OnLogSavedListener {
        void onSaveLog(List<String> keys, boolean isFlow, int intensity, String symptoms);
    }

    public void setListener(OnLogSavedListener listener) {
        this.listener = listener;
    }

    public void setDateKeys(List<String> keys) {
        this.dateKeys = keys;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetPeriodLogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupHeader();
        binding.chipGroupFlow.check(R.id.chipMedium);

        binding.btnSaveLog.setOnClickListener(v -> saveLog());
    }

    private void setupHeader() {
        if (dateKeys == null || dateKeys.isEmpty()) return;

        if (dateKeys.size() == 1) {
            String prettyDate = formatDatePretty(dateKeys.get(0));
            binding.tvSheetTitle.setText("Log for " + prettyDate);
        } else {
            binding.tvSheetTitle.setText("Log for " + dateKeys.size() + " Days");
        }
    }

    private void saveLog() {

        StringBuilder symptomsBuilder = new StringBuilder();
        collectChips(binding.groupPhysical, symptomsBuilder);
        collectChips(binding.groupMood, symptomsBuilder);
        collectChips(binding.groupDigestive, symptomsBuilder);
        collectChips(binding.groupPCOS, symptomsBuilder);

        // 1. Get Flow
        int selectedFlowId = binding.chipGroupFlow.getCheckedChipId();

        // 2. Get Symptoms
        String symptoms = symptomsBuilder.toString();

        boolean hasSymptoms = symptomsBuilder.length() > 0;
        if (symptoms.isEmpty()) symptoms = "None";

        boolean isFlow;
        int intensity;

        if (selectedFlowId == View.NO_ID) {
            // CASE A: User selected NOTHING in the flow section.

            if (hasSymptoms) {
                // If they selected symptoms (e.g., Cramps) but NO flow,
                // assume it's just a symptom log (PMS).
                isFlow = false;
                intensity = 0;
            } else {
                // If they selected NOTHING (No symptoms, No flow chips),
                // assume they just want to mark the date as a PERIOD.
                // Default to Medium intensity.
                isFlow = true;
                intensity = 2;
            }
        } else if (selectedFlowId == R.id.chipNone) {
            // CASE B: User explicitly clicked "No Flow"
            isFlow = false;
            intensity = 0;
        } else {
            // CASE C: User selected a specific intensity
            isFlow = true;
            if (selectedFlowId == R.id.chipLight) intensity = 1;
            else if (selectedFlowId == R.id.chipMedium) intensity = 2;
            else intensity = 3; // Heavy
        }

        if (listener != null) {
            listener.onSaveLog(dateKeys, isFlow, intensity, symptoms);
        }
        dismiss();
    }

    private void collectChips(ChipGroup group, StringBuilder sb) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                if (((Chip) child).isChecked()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(((Chip) child).getText());
                }
            }
        }
    }

    private String formatDatePretty(String dateKey) {
        try {
            return new SimpleDateFormat("MMM dd", Locale.getDefault()).format(
                    new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
            );
        } catch (Exception e) {
            return dateKey;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}