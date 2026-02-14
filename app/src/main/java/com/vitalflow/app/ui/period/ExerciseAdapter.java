package com.vitalflow.app.ui.period;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.HealthPlan;
import com.vitalflow.app.databinding.ItemExerciseBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.Holder> {

    private List<HealthPlan.Exercise> exercises = new ArrayList<>();

    private final Set<Integer> expandedPositions = new HashSet<>();

    public void setData(List<HealthPlan.Exercise> newList) {
        DiffUtil.DiffResult diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return exercises.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                // Assuming exercise names are unique enough for identity
                return exercises.get(oldItemPosition).name.equals(newList.get(newItemPosition).name);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                HealthPlan.Exercise oldItem = exercises.get(oldItemPosition);
                HealthPlan.Exercise newItem = newList.get(newItemPosition);

                // Compare content (Name, Duration, Category)
                return oldItem.name.equals(newItem.name) &&
                        oldItem.duration.equals(newItem.duration) &&
                        oldItem.category.equals(newItem.category);
            }
        });

        // Update data
        this.exercises = new ArrayList<>(newList);

        // Dispatch specific updates (Better performance/animations than notifyDataSetChanged)
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExerciseBinding binding = ItemExerciseBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HealthPlan.Exercise item = exercises.get(position);
        Context context = holder.itemView.getContext();

        holder.binding.tvExName.setText(item.name);
        holder.binding.tvExDuration.setText(item.duration);
        holder.binding.chipCategory.setText(item.category);

        boolean isExpanded = expandedPositions.contains(position);
        holder.binding.layoutSteps.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.binding.imgExpand.setRotation(isExpanded ? 180 : 0);

        holder.binding.cardExercise.setOnClickListener(v -> {
            boolean currentExpanded = expandedPositions.contains(position);

            // Toggle state in our tracking set
            if (currentExpanded) {
                expandedPositions.remove(position);
            } else {
                expandedPositions.add(position);
            }

            // Animate
            TransitionManager.beginDelayedTransition(holder.binding.cardExercise, new AutoTransition());
            holder.binding.layoutSteps.setVisibility(currentExpanded ? View.GONE : View.VISIBLE);
            holder.binding.imgExpand.animate().rotation(currentExpanded ? 0 : 180).setDuration(200).start();
        });

        // Format Steps List into a numbered string
        holder.binding.containerSteps.removeAllViews();

        if (item.steps != null && !item.steps.isEmpty()) {
            for (int i = 0; i < item.steps.size(); i++) {
                addStepRow(context, holder.binding.containerSteps, i + 1, item.steps.get(i));
            }
        }
    }

    private void addStepRow(Context context, LinearLayout container, int stepNum, String stepText) {
        // Create Row
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dpToPx(context, 12));
        row.setLayoutParams(rowParams);

        // Badge (Circle Number)
        TextView badge = new TextView(context);
        badge.setText(String.valueOf(stepNum));
        badge.setTextSize(12);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(Color.parseColor("#00695C"));
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundResource(R.drawable.bg_step_circle);

        int badgeSize = dpToPx(context, 24);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        badgeParams.setMargins(0, 0, dpToPx(context, 12), 0);
        badge.setLayoutParams(badgeParams);

        // Text
        TextView text = new TextView(context);
        text.setText(stepText);
        text.setTextColor(Color.parseColor("#37474F"));
        text.setTextSize(14);
        text.setLineSpacing(0, 1.2f);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        text.setLayoutParams(textParams);

        row.addView(badge);
        row.addView(text);
        container.addView(row);
    }

    private int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @Override
    public int getItemCount() {
        return exercises == null ? 0 : exercises.size();
    }

    public static class Holder extends RecyclerView.ViewHolder {
        ItemExerciseBinding binding;

        public Holder(ItemExerciseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}