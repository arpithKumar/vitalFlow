package com.vitalflow.app.ui.period;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.HealthPlan;
import com.vitalflow.app.databinding.LayoutBottomSheetHealthPlanBinding;

public class HealthPlanBottomSheet extends BottomSheetDialogFragment {

    private LayoutBottomSheetHealthPlanBinding binding;
    private HealthPlan plan;

    public void setHealthPlan(HealthPlan plan) {
        this.plan = plan;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutBottomSheetHealthPlanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (plan == null) {
            dismiss();
            return;
        }

        // 1. Summary
        binding.tvDaySummary.setText(plan.daySummary);

        // 2. Exercises List
        ExerciseAdapter adapter = new ExerciseAdapter();
        binding.recyclerExercises.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerExercises.setAdapter(adapter);
        if (plan.workout != null) {
            adapter.setData(plan.workout);
        }

        // 3. Nutrition (Eat) — Green Theme
        if (plan.nutrition != null && plan.nutrition.eat != null) {
            for (String food : plan.nutrition.eat) {
                addChip(binding.groupEat, food, R.color.chip_bg_eat, R.color.text_chip_eat);
            }
        }

        // 4. Nutrition (Avoid) — Red Theme
        if (plan.nutrition != null && plan.nutrition.avoid != null) {
            for (String food : plan.nutrition.avoid) {
                addChip(binding.groupAvoid, food, R.color.chip_bg_avoid, R.color.text_chip_avoid);
            }
        }

        // 5. Relief Tips — Teal Theme
        if (plan.reliefTips != null) {
            for (String tip : plan.reliefTips) {
                addChip(binding.groupTips, tip, R.color.chip_bg_tips, R.color.text_chip_tips);
            }
        }
    }

    private void addChip(com.google.android.material.chip.ChipGroup group, String text, int bgColorRes, int textColorRes) {
        Chip chip = new Chip(getContext());
        chip.setText(text);
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), bgColorRes)));
        chip.setTextColor(ContextCompat.getColor(requireContext(), textColorRes));
        chip.setTextSize(14f);
        float radiusPx = 16f * getResources().getDisplayMetrics().density;
        ShapeAppearanceModel shape = chip.getShapeAppearanceModel()
                .toBuilder()
                .setAllCornerSizes(radiusPx)
                .build();
        chip.setShapeAppearanceModel(shape);
        chip.setCheckable(false);
        group.addView(chip);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}