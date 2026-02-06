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
import java.util.Date;
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
        // 1. Get Flow
        int selectedFlowId = binding.chipGroupFlow.getCheckedChipId();
        boolean isFlow = (selectedFlowId != R.id.chipNone);
        int intensity = 0;
        if (selectedFlowId == R.id.chipLight) intensity = 1;
        if (selectedFlowId == R.id.chipMedium) intensity = 2;
        if (selectedFlowId == R.id.chipHeavy) intensity = 3;

        // 2. Get Symptoms
        StringBuilder symptoms = new StringBuilder();
        collectChips(binding.groupPhysical, symptoms);
        collectChips(binding.groupMood, symptoms);
        collectChips(binding.groupDigestive, symptoms);
        collectChips(binding.groupPCOS, symptoms);

        if (symptoms.length() == 0) symptoms.append("None");

        if (listener != null) {
            listener.onSaveLog(dateKeys, isFlow, intensity, symptoms.toString());
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
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey);
            return new SimpleDateFormat("MMM dd", Locale.getDefault()).format(d);
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