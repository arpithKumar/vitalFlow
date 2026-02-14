package com.vitalflow.app.ui.vitals;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.databinding.ItemVitalBinding;

import java.util.ArrayList;
import java.util.List;

public class VitalsAdapter extends RecyclerView.Adapter<VitalsAdapter.VitalViewHolder> {

    private List<Vital> list;

    public VitalsAdapter(List<Vital> list) {
        this.list = list;
    }

    public void updateData(List<Vital> newList) {
        this.list = newList;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return list.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                // Assuming timestamp + type creates a unique ID for Vitals
                Vital oldItem = list.get(oldItemPosition);
                Vital newItem = newList.get(newItemPosition);
                return oldItem.getTimestamp() == newItem.getTimestamp() &&
                        oldItem.getType().equals(newItem.getType());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Vital oldItem = list.get(oldItemPosition);
                Vital newItem = newList.get(newItemPosition);

                // Compare values
                boolean value2Match = (oldItem.getValue2() == null && newItem.getValue2() == null) ||
                        (oldItem.getValue2() != null && oldItem.getValue2().equals(newItem.getValue2()));

                return oldItem.getValue1().equals(newItem.getValue1()) && value2Match;
            }
        });

        this.list = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public VitalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVitalBinding binding = ItemVitalBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VitalViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VitalViewHolder holder, int position) {
        Vital item = list.get(position);

        holder.binding.tvTitle.setText(item.getType().toUpperCase());
        holder.binding.tvDate.setText(item.getDate());
        holder.binding.tvUnit.setText(item.getUnit());

        // Display Logic (e.g. "120/80")
        if (item.getValue2() != null && !item.getValue2().isEmpty()) {
            holder.binding.tvValue.setText(item.getValue1() + "/" + item.getValue2());
        } else {
            holder.binding.tvValue.setText(item.getValue1());
        }

        if (item.getType().contains("Heart")) {
            holder.binding.imgIcon.setImageResource(R.drawable.ic_heart);
        } else if (item.getType().contains("Pressure")) {
            holder.binding.imgIcon.setImageResource(R.drawable.ic_water_drop);
        } else if (item.getType().contains("Oxygen")) {
            holder.binding.imgIcon.setImageResource(R.drawable.ic_water_drop); // Or specific SpO2 icon
        } else {
            holder.binding.imgIcon.setImageResource(R.drawable.ic_edit);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class VitalViewHolder extends RecyclerView.ViewHolder {
        final ItemVitalBinding binding;

        public VitalViewHolder(ItemVitalBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}