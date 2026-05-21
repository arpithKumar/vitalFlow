package com.vitalflow.app.ui.meds;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.MedicationHistory;
import com.vitalflow.app.databinding.BottomSheetMedHistoryBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class MedHistoryBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_MED_ID = "med_id";
    private static final String ARG_MED_NAME = "med_name";

    @Inject
    AppDao appDao;

    private BottomSheetMedHistoryBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

    /**
     * Per-medication history.
     */
    public static MedHistoryBottomSheet newInstance(String medId, String medName) {
        MedHistoryBottomSheet sheet = new MedHistoryBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_MED_ID, medId);
        args.putString(ARG_MED_NAME, medName);
        sheet.setArguments(args);
        return sheet;
    }

    /**
     * Global history across all medications.
     */
    public static MedHistoryBottomSheet newInstanceAll() {
        return new MedHistoryBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetMedHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String medId = getArguments() != null ? getArguments().getString(ARG_MED_ID) : null;
        String medName = getArguments() != null ? getArguments().getString(ARG_MED_NAME) : null;

        binding.tvHistoryTitle.setText(medId != null ? medName + " — History" : "Dose History");
        binding.btnCloseHistory.setOnClickListener(v -> dismiss());

        binding.rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));

        io.reactivex.rxjava3.core.Single<java.util.List<MedicationHistory>> query = medId != null
                ? appDao.getHistoryForMed(medId)
                : appDao.getAllMedicationHistory();

        disposables.add(
                query.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::showHistory, Throwable::printStackTrace)
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void showHistory(List<MedicationHistory> entries) {
        if (entries.isEmpty()) {
            binding.tvHistoryEmpty.setVisibility(View.VISIBLE);
            binding.rvHistory.setVisibility(View.GONE);
            return;
        }
        binding.tvHistoryEmpty.setVisibility(View.GONE);
        binding.rvHistory.setVisibility(View.VISIBLE);
        binding.rvHistory.setAdapter(new HistoryAdapter(entries));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }

    // ---- Inner adapter ----

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        private final List<MedicationHistory> items;
        private final SimpleDateFormat todayFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat fullFmt = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());

        HistoryAdapter(List<MedicationHistory> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_med_history_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MedicationHistory entry = items.get(position);
            boolean taken = "TAKEN".equalsIgnoreCase(entry.action);

            h.tvAction.setText(taken ? "Taken" : "Skipped");
            h.tvTime.setText(formatTimestamp(entry.timestamp));

            int iconRes = taken ? R.drawable.ic_check_circle : R.drawable.ic_close;
            int iconTint = taken
                    ? ContextCompat.getColor(requireContext(), R.color.med_status_taken)
                    : ContextCompat.getColor(requireContext(), R.color.med_status_skipped);
            int cardTint = taken
                    ? ContextCompat.getColor(requireContext(), R.color.stat_icon_bg)
                    : ContextCompat.getColor(requireContext(), R.color.surface_variant);

            h.ivIcon.setImageResource(iconRes);
            h.ivIcon.setColorFilter(iconTint);
            h.cardIcon.setCardBackgroundColor(cardTint);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String formatTimestamp(long millis) {
            Calendar now = Calendar.getInstance();
            Calendar target = Calendar.getInstance();
            target.setTimeInMillis(millis);
            boolean isToday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                    && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);
            return isToday
                    ? "Today, " + todayFmt.format(new Date(millis))
                    : fullFmt.format(new Date(millis));
        }

        class VH extends RecyclerView.ViewHolder {
            final MaterialCardView cardIcon;
            final ImageView ivIcon;
            final TextView tvAction;
            final TextView tvTime;

            VH(View itemView) {
                super(itemView);
                cardIcon = (MaterialCardView) ((ViewGroup) itemView).getChildAt(0);
                ivIcon = cardIcon.findViewById(R.id.ivHistoryIcon);
                tvAction = itemView.findViewById(R.id.tvHistoryAction);
                tvTime = itemView.findViewById(R.id.tvHistoryTime);
            }
        }
    }
}
