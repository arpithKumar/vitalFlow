package com.vitalflow.app.ui.vitals;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.databinding.ItemVitalBinding;

import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VitalsAdapter extends RecyclerView.Adapter<VitalsAdapter.VitalViewHolder> {

    private List<Vital> list;

    public VitalsAdapter(List<Vital> list) {
        this.list = list;
    }

    public void updateData(List<Vital> newList) {
        List<Vital> oldList = this.list;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                Vital o = oldList.get(oldPos);
                Vital n = newList.get(newPos);
                return o.getTimestamp() == n.getTimestamp() && o.getType().equals(n.getType());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Vital o = oldList.get(oldPos);
                Vital n = newList.get(newPos);
                boolean value2Match = (o.getValue2() == null && n.getValue2() == null) ||
                        (o.getValue2() != null && o.getValue2().equals(n.getValue2()));
                return o.getValue1().equals(n.getValue1()) && value2Match;
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
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(item.getDate());
            holder.binding.tvDate.setText(new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(parsed));
        } catch (ParseException e) {
            holder.binding.tvDate.setText(item.getDate());
        }
        holder.binding.tvUnit.setText(item.getUnit());

        // Display Logic (e.g. "120/80")
        if (item.getValue2() != null && !item.getValue2().isEmpty()) {
            holder.binding.tvValue.setText(MessageFormat.format("{0}/{1}", item.getValue1(), item.getValue2()));
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