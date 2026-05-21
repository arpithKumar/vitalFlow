package com.vitalflow.app.ui.report;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.Report;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.databinding.FragmentReportBinding;
import com.vitalflow.app.utils.DataExportHelper;
import com.vitalflow.app.utils.PdfReportManager;
import com.vitalflow.app.utils.UiUtils;

import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ReportFragment extends Fragment {

    private FragmentReportBinding binding;
    private ReportViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ReportViewModel.class);

        binding.btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        observeViewModel();
        viewModel.generateFullReport();
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (Boolean.TRUE.equals(loading)) {
                binding.layoutReportLoading.setVisibility(View.VISIBLE);
                binding.layoutReportContent.setVisibility(View.GONE);
            } else {
                binding.layoutReportLoading.setVisibility(View.GONE);
                binding.layoutReportContent.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getReportData().observe(getViewLifecycleOwner(), this::populateReport);

        viewModel.getErrorState().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                UiUtils.showCustomSnackbar(
                        binding.getRoot(), requireContext(), error,
                        R.color.secondary, R.color.text_on_primary,
                        R.drawable.ic_arrow_right, null, null);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void populateReport(Report report) {
        if (report == null) return;

        // Header date
        binding.tvReportDate.setText("Generated " + report.reportDate);

        // AI Summary
        binding.tvAiSummary.setText(report.aiDoctorSummary);

        // Share button — sends the AI summary text via any installed app
        binding.btnShareSummary.setOnClickListener(v -> {
            CharSequence summaryText = binding.tvAiSummary.getText();
            if (summaryText == null || summaryText.length() == 0) return;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My VitalFlow Health Report");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "VitalFlow Health Report — " + report.reportDate + "\n\n" + summaryText);
            startActivity(Intent.createChooser(shareIntent, "Share report via…"));
        });

        // 30-day stats averages
        populateStats(report.last30DaysStats, report.regularityScore);

        // Medication adherence
        populateMedAdherence(report.activeMedications);

        // Recent vitals rows
        populateVitalsRows(report.recentVitals);

        // Active meds rows
        populateMedRows(report.activeMedications);

        // Export buttons
        binding.btnExportPdf.setOnClickListener(v -> {
            PdfReportManager.createReport(requireContext(), report);
            UiUtils.showCustomSnackbar(
                    binding.getRoot(), requireContext(), "PDF saved to Downloads",
                    R.color.primary, R.color.text_on_primary,
                    R.drawable.ic_download, null, null);
        });

        binding.btnExportExcel.setOnClickListener(v -> {
            DataExportHelper.exportData(requireContext(), viewModel.getAppDao());
            UiUtils.showCustomSnackbar(
                    binding.getRoot(), requireContext(), "Excel saved to Downloads",
                    R.color.primary, R.color.text_on_primary,
                    R.drawable.ic_download, null, null);
        });
    }

    @SuppressLint("SetTextI18n")
    private void populateStats(List<DailyHealth> stats, String regularity) {
        if (stats == null || stats.isEmpty()) {
            binding.tvAvgWater.setText("— ml");
            binding.tvAvgSleep.setText("— hrs");
            binding.tvCycleRegularity.setText(regularity != null ? shortenRegularity(regularity) : "—");
            return;
        }

        double avgWater = stats.stream()
                .mapToInt(DailyHealth::getWaterIntakeMl)
                .average().orElse(0);

        double avgSleep = stats.stream()
                .mapToDouble(d -> parseSleep(d.getSleepDuration()))
                .average().orElse(0);

        binding.tvAvgWater.setText((int) avgWater + " ml");
        binding.tvAvgSleep.setText(String.format(Locale.getDefault(), "%.1f hrs", avgSleep));
        binding.tvCycleRegularity.setText(regularity != null ? shortenRegularity(regularity) : "—");
    }

    private String shortenRegularity(String full) {
        if (full.toLowerCase(Locale.ROOT).contains("regular") && !full.toLowerCase(Locale.ROOT).contains("irregular"))
            return "Regular";
        if (full.toLowerCase(Locale.ROOT).contains("mild")) return "Mild";
        if (full.toLowerCase(Locale.ROOT).contains("irregular")) return "Irregular";
        return full.length() > 10 ? full.substring(0, 10) + "…" : full;
    }

    @SuppressLint("SetTextI18n")
    private void populateMedAdherence(List<Medication> meds) {
        if (meds == null || meds.isEmpty()) {
            binding.progressMedAdherence.setProgress(0);
            binding.tvMedAdherencePercent.setText("0%");
            binding.tvMedAdherenceLabel.setText("No active medications");
            return;
        }

        int totalExpected = 0;
        int totalTaken = 0;
        for (Medication m : meds) {
            totalExpected += m.getFrequencyPerDay();
            totalTaken += m.getTimesTakenToday();
        }

        int percent = totalExpected > 0 ? (int) ((totalTaken * 100f) / totalExpected) : 0;
        binding.progressMedAdherence.setProgress(percent);
        binding.tvMedAdherencePercent.setText(percent + "%");
        binding.tvMedAdherenceLabel.setText(totalTaken + " of " + totalExpected + " doses taken today");
    }

    private void populateVitalsRows(List<Vital> vitals) {
        binding.containerVitals.removeAllViews();
        if (vitals == null || vitals.isEmpty()) {
            addInfoRow(binding.containerVitals, "No vitals recorded yet");
            return;
        }
        for (Vital v : vitals) {
            String value = v.getValue1();
            if (v.getValue2() != null && !v.getValue2().isEmpty())
                value += "/" + v.getValue2();
            addVitalRow(binding.containerVitals, v.getType(), value + " " + v.getUnit(), v.getDate());
        }
    }

    private void populateMedRows(List<Medication> meds) {
        binding.containerMeds.removeAllViews();
        if (meds == null || meds.isEmpty()) {
            addInfoRow(binding.containerMeds, "No active medications");
            return;
        }
        for (Medication m : meds) {
            addMedRow(binding.containerMeds, m.getName(), m.getDosage() + " · " + m.getTime());
        }
    }

    @SuppressLint("SetTextI18n")
    private void addVitalRow(LinearLayout container, String type, String value, String date) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_report_row, container, false);
        ((TextView) row.findViewById(R.id.tvRowLabel)).setText(type);
        ((TextView) row.findViewById(R.id.tvRowValue)).setText(value);
        ((TextView) row.findViewById(R.id.tvRowSub)).setText(date != null ? date : "");
        container.addView(row);
    }

    @SuppressLint("SetTextI18n")
    private void addMedRow(LinearLayout container, String name, String detail) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_report_row, container, false);
        ((TextView) row.findViewById(R.id.tvRowLabel)).setText(name);
        ((TextView) row.findViewById(R.id.tvRowValue)).setText("");
        ((TextView) row.findViewById(R.id.tvRowSub)).setText(detail);
        container.addView(row);
    }

    private void addInfoRow(LinearLayout container, String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(requireContext().getColor(R.color.text_secondary));
        container.addView(tv);
    }

    private double parseSleep(String sleepStr) {
        try {
            String[] parts = sleepStr.split("h");
            double hours = Double.parseDouble(parts[0].trim());
            double minutes = parts.length > 1
                    ? Double.parseDouble(parts[1].replace("m", "").trim()) : 0;
            return hours + (minutes / 60.0);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
