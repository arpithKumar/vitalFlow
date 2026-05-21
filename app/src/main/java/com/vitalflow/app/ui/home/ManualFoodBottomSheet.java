package com.vitalflow.app.ui.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.databinding.BottomSheetManualFoodBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Bottom sheet that lets the user log a meal without the Gemini camera flow.
 * Requires food name and calories; macros are optional.
 *
 * <p>Call {@link #setCallback(OnFoodLoggedCallback)} before showing the sheet,
 * then add it via {@code show(childFragmentManager, "ManualFood")}.
 */
public class ManualFoodBottomSheet extends BottomSheetDialogFragment {

    public interface OnFoodLoggedCallback {
        void onFoodLogged(FoodLog log);
    }

    private BottomSheetManualFoodBinding binding;
    private OnFoodLoggedCallback callback;

    public void setCallback(OnFoodLoggedCallback callback) {
        this.callback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetManualFoodBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSaveFood.setOnClickListener(v -> {
            String name = binding.etFoodName.getText() != null
                    ? binding.etFoodName.getText().toString().trim() : "";
            String calStr = binding.etCalories.getText() != null
                    ? binding.etCalories.getText().toString().trim() : "";
            String macros = binding.etMacros.getText() != null
                    ? binding.etMacros.getText().toString().trim() : "";

            // Validate
            if (TextUtils.isEmpty(name)) {
                binding.tilFoodName.setError("Please enter a food name");
                return;
            }
            binding.tilFoodName.setError(null);

            if (TextUtils.isEmpty(calStr)) {
                binding.tilCalories.setError("Please enter calories");
                return;
            }
            int calories;
            try {
                calories = Integer.parseInt(calStr);
                if (calories < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                binding.tilCalories.setError("Enter a valid number");
                return;
            }
            binding.tilCalories.setError(null);

            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            FoodLog log = new FoodLog(date, time, name, calories, macros.isEmpty() ? null : macros);

            if (callback != null) callback.onFoodLogged(log);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
