package com.vitalflow.app.ui.meds;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.databinding.ItemMedicationBinding;
import com.vitalflow.app.utils.NotificationHelper;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class MedsAdapter extends RecyclerView.Adapter<MedsAdapter.MedViewHolder> {

    private List<Medication> list = new ArrayList<>();
    private final OnMedClickListener listener;

    public interface OnMedClickListener {
        void onStatusChange(Medication med, String status);

        void onCardClick(Medication med);
    }

    public MedsAdapter(OnMedClickListener listener) {
        this.listener = listener;
    }

    // Database-Driven Update: Just replace the list
    public void updateList(List<Medication> newList) {
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
                // Assuming Name is unique. If you have an ID, use med.id instead.
                return list.get(oldItemPosition).getName().equals(newList.get(newItemPosition).getName());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Medication oldItem = list.get(oldItemPosition);
                Medication newItem = newList.get(newItemPosition);

                // Check fields that affect the UI
                return oldItem.getStatus().equals(newItem.getStatus()) &&
                        oldItem.getPillsLeft() == newItem.getPillsLeft() &&
                        oldItem.getTimesTakenToday() == newItem.getTimesTakenToday();
            }
        });

        this.list = new ArrayList<>(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    public Medication getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public MedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMedicationBinding binding = ItemMedicationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MedViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MedViewHolder holder, int position) {

        Medication med = list.get(position);

        holder.binding.tvMedName.setText(med.getName());
        holder.binding.tvDosage.setText(MessageFormat.format("{0} ({1})", med.getDosage(), med.getType()));
        holder.binding.tvTime.setText(med.getTime());

        // Extra Info Logic
        StringBuilder extra = new StringBuilder();
        if (med.getDoctorName() != null && !med.getDoctorName().isEmpty())
            extra.append(med.getDoctorName());

        String scheduledDays = med.getSelectedDays();
        if (scheduledDays != null && !scheduledDays.equals("Everyday")) {
            if (extra.length() > 0) extra.append(" • ");
            extra.append(scheduledDays);
        }

        // 2. Dates (Start - End)
        if (med.getStartDate() != null && !med.getStartDate().isEmpty()) {
            if (extra.length() > 0) extra.append(" • ");

            extra.append(med.getStartDate());

            // Only add End Date if it's NOT "Forever"
            if (med.getEndDate() != null && !med.getEndDate().isEmpty() && !med.getEndDate().equalsIgnoreCase("Forever")) {
                extra.append(" - ").append(med.getEndDate());
            }
        }
        holder.binding.tvExtraInfo.setText(extra.toString());

        // Refill Warning Logic
        String medType = med.getType() != null ? med.getType().toLowerCase(java.util.Locale.ROOT) : "pill";
        String unit;

        if (medType.equals("liquid")) {
            unit = "ml";
        } else if (medType.equals("injection")) {
            unit = "units";
        } else {
            unit = medType + "s";
        }
        holder.binding.tvRefill.setText(MessageFormat.format("{0} {1} left", med.getPillsLeft(), unit));
        holder.binding.tvRefill.setTextColor(med.getPillsLeft() <= 5
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.warning_red)
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.primary));

        if (med.getPrescriptionUri() != null && !med.getPrescriptionUri().isEmpty()) {
            holder.binding.btnViewPrescription.setVisibility(View.VISIBLE);

            holder.binding.btnViewPrescription.setOnClickListener(v -> {
                // Create a quick, full-screen dark mode viewer
                android.app.Dialog dialog = new android.app.Dialog(v.getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                android.widget.ImageView imageView = new android.widget.ImageView(v.getContext());

                imageView.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);

                try {
                    imageView.setImageURI(android.net.Uri.parse(med.getPrescriptionUri()));
                } catch (Exception e) {
                    android.widget.Toast.makeText(v.getContext(), "Could not load image", android.widget.Toast.LENGTH_SHORT).show();
                }

                // Tap the image to close the viewer
                imageView.setOnClickListener(imgView -> dialog.dismiss());

                dialog.setContentView(imageView);
                dialog.show();
            });
        } else {
            holder.binding.btnViewPrescription.setVisibility(View.GONE);
        }

        // Visual State Update
        String status = med.getStatus();
        int taken = med.getTimesTakenToday();
        int total = med.getFrequencyPerDay();

        updateVisuals(holder, status, taken, total);

        // Click Listeners (Edit)
        holder.binding.cardRoot.setOnClickListener(v -> listener.onCardClick(med));

        holder.binding.btnTake.setOnClickListener(v -> {
            listener.onStatusChange(med, "TAKEN");
            if (med.getPillsLeft() <= 5) {
                NotificationHelper.sendLowStockAlert(v.getContext(), med.getName(), med.getPillsLeft());
            }
        });

        holder.binding.btnSkip.setOnClickListener(v -> listener.onStatusChange(med, "SKIPPED"));

        holder.binding.tvStatus.setOnClickListener(v -> listener.onStatusChange(med, "PENDING"));
    }

    private void updateVisuals(MedViewHolder holder, String status, int taken, int total) {
        android.content.Context ctx = holder.itemView.getContext();
        if ("TAKEN".equals(status)) {
            // SHOW COMPLETED STATE
            holder.binding.layoutActions.setVisibility(View.GONE);
            holder.binding.tvStatus.setVisibility(View.VISIBLE);

            holder.binding.tvStatus.setText("ALL DONE");
            holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_success));
            holder.binding.tvStatus.setBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_success));

            holder.binding.cardRoot.animate().alpha(0.75f).setDuration(400).start();
            holder.binding.viewStatusStrip.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.med_status_taken));

            // Strike-through
            holder.binding.tvMedName.setPaintFlags(holder.binding.tvMedName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

        } else if ("SKIPPED".equals(status)) {
            // SHOW SKIPPED STATE
            holder.binding.layoutActions.setVisibility(View.GONE);
            holder.binding.tvStatus.setVisibility(View.VISIBLE);

            holder.binding.tvStatus.setText("SKIPPED");
            holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.icon_swipe_delete));
            holder.binding.tvStatus.setBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_swipe_delete));

            holder.binding.cardRoot.setAlpha(0.8f);

            // Strike-through
            holder.binding.tvMedName.setPaintFlags(holder.binding.tvMedName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.binding.viewStatusStrip.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.med_status_skipped));
        } else {
            // PENDING (NORMAL) STATE
            holder.binding.layoutActions.setVisibility(View.VISIBLE);

            holder.binding.cardRoot.setAlpha(1f);
            holder.binding.viewStatusStrip.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.med_status_pending));

            // Remove Strike-through
            holder.binding.tvMedName.setPaintFlags(holder.binding.tvMedName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));

            if (total > 1 && taken > 0) {
                holder.binding.tvStatus.setVisibility(View.VISIBLE);
                holder.binding.tvStatus.setText(MessageFormat.format("{0} / {1} Taken", taken, total));
                holder.binding.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.primary));
                holder.binding.tvStatus.setBackgroundColor(ContextCompat.getColor(ctx, R.color.chip_bg_selected));
            } else {
                holder.binding.tvStatus.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class MedViewHolder extends RecyclerView.ViewHolder {
        final ItemMedicationBinding binding;

        public MedViewHolder(ItemMedicationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}