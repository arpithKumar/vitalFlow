package com.vitalflow.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.FoodLog;

public class FoodLogAdapter extends ListAdapter<FoodLog, FoodLogAdapter.Holder> {

    public FoodLogAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull FoodLog oldItem, @NonNull FoodLog newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull FoodLog oldItem, @NonNull FoodLog newItem) {
                return oldItem.foodName.equals(newItem.foodName) && oldItem.calories == newItem.calories;
            }
        });
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food_log, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FoodLog item = getItem(position);
        holder.tvTime.setText(item.time);
        holder.tvFoodName.setText(item.foodName);
        holder.tvMacros.setText(item.macros);
        holder.tvCalories.setText(item.calories + " kcal");
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvTime, tvFoodName, tvMacros, tvCalories;

        public Holder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvFoodName = itemView.findViewById(R.id.tvFoodName);
            tvMacros = itemView.findViewById(R.id.tvMacros);
            tvCalories = itemView.findViewById(R.id.tvCalories);
        }
    }
}