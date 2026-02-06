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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.HealthPlan;

import java.util.ArrayList;
import java.util.List;

public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.Holder> {

    private List<HealthPlan.Exercise> exercises = new ArrayList<>();

    public void setData(List<HealthPlan.Exercise> list) {
        this.exercises = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exercise, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HealthPlan.Exercise item = exercises.get(position);
        Context context = holder.itemView.getContext();

        holder.tvName.setText(item.name);
        holder.tvDuration.setText(item.duration); // e.g. "3 sets of 12 reps"
        holder.chipCategory.setText(item.category);

        // Format Steps List into a numbered string
        holder.containerSteps.removeAllViews();

        if (item.steps != null) {
            for (int i = 0; i < item.steps.size(); i++) {
                String stepText = item.steps.get(i);

                // Create Row layout
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                rowParams.setMargins(0, 0, 0, dpToPx(context, 12));
                row.setLayoutParams(rowParams);

                // Create Numbering badges
                TextView badge = new TextView(context);
                badge.setText(String.valueOf(i + 1));
                badge.setTextSize(12);
                badge.setTypeface(null, Typeface.BOLD);
                badge.setTextColor(Color.parseColor("#00695C")); // Teal Text
                badge.setGravity(Gravity.CENTER);

                // Circle Background (Programmatic)
                badge.setBackgroundResource(R.drawable.bg_step_circle);

                // Badge Layout Params (Fixed Size 24dp)
                int badgeSize = dpToPx(context, 24);
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(badgeSize, badgeSize);
                badgeParams.setMargins(0, 0, dpToPx(context, 12), 0); // Space between badge and text
                badge.setLayoutParams(badgeParams);

                // 3. Create Instruction Text
                TextView text = new TextView(context);
                text.setText(stepText);
                text.setTextColor(Color.parseColor("#37474F"));
                text.setTextSize(14);
                text.setLineSpacing(0, 1.2f); // Better readability

                // Allow text to take remaining width
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                );
                text.setLayoutParams(textParams);

                // 4. Assemble
                row.addView(badge);
                row.addView(text);

                holder.containerSteps.addView(row);
            }
        }

        // --- SMOOTH ANIMATION LOGIC ---
        holder.itemView.setOnClickListener(v -> {
            boolean isVisible = holder.layoutSteps.getVisibility() == View.VISIBLE;

            // 1. Tell the TransitionManager to animate changes on the Card
            TransitionManager.beginDelayedTransition(holder.cardView, new AutoTransition());

            // 2. Toggle Visibility
            holder.layoutSteps.setVisibility(isVisible ? View.GONE : View.VISIBLE);

            // 3. Rotate Arrow
            holder.imgExpand.animate().rotation(isVisible ? 0 : 180).setDuration(200).start();
        });
    }

    private int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @Override
    public int getItemCount() {
        return exercises == null ? 0 : exercises.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView tvName, tvDuration;
        Chip chipCategory;
        LinearLayout layoutSteps;
        LinearLayout containerSteps;
        ImageView imgExpand;

        public Holder(@NonNull View v) {
            super(v);
            cardView = v.findViewById(R.id.cardExercise);
            tvName = v.findViewById(R.id.tvExName);
            tvDuration = v.findViewById(R.id.tvExDuration);
            containerSteps = v.findViewById(R.id.containerSteps);
            chipCategory = v.findViewById(R.id.chipCategory);
            layoutSteps = v.findViewById(R.id.layoutSteps);
            imgExpand = v.findViewById(R.id.imgExpand);
        }
    }
}