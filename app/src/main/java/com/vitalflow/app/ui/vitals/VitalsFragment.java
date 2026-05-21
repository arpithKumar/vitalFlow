package com.vitalflow.app.ui.vitals;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.databinding.FragmentVitalsBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@AndroidEntryPoint
public class VitalsFragment extends Fragment {

    @Inject
    AppDao appDao;

    private FragmentVitalsBinding binding;
    private VitalsAdapter adapter;
    private List<Vital> vitalsList = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private VitalsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(VitalsViewModel.class);
        binding = FragmentVitalsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 2. SETUP ADAPTER (Empty initially)
        adapter = new VitalsAdapter(new ArrayList<>());
        binding.rvVitals.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvVitals.setAdapter(adapter);
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());

        binding.fabAddVital.setOnClickListener(v ->
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                                .withEndAction(() -> showAddVitalSheet()).start()).start());

        startVitalsShimmer();

        viewModel.getAllVitals().observe(getViewLifecycleOwner(), vitals -> {
            stopVitalsShimmer();
            this.vitalsList = vitals;
            adapter.updateData(vitals);
            setupGraph();
            updateEmergencyCards();
            runInsightEngine();
            if (vitals.isEmpty()) {
                binding.layoutEmptyVitals.setVisibility(View.VISIBLE);
                binding.rvVitals.setVisibility(View.GONE);
            } else {
                binding.layoutEmptyVitals.setVisibility(View.GONE);
                binding.rvVitals.setVisibility(View.VISIBLE);
            }
        });

        binding.chipGroupTimeFrame.setOnCheckedStateChangeListener((group, checkedIds) -> setupGraph());
        binding.chipGroupVitalType.setOnCheckedStateChangeListener((group, checkedIds) -> setupGraph());

        setupPullToRefresh();
    }

    private void setupPullToRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary));
        binding.swipeRefresh.setOnRefreshListener(() -> {
            androidx.work.OneTimeWorkRequest syncWork =
                    new androidx.work.OneTimeWorkRequest.Builder(
                            com.vitalflow.app.workers.SyncWorker.class).build();
            androidx.work.WorkManager.getInstance(requireContext()).enqueue(syncWork);
            // LiveData from Room auto-updates; just stop spinner
            binding.swipeRefresh.postDelayed(
                    () -> { if (binding != null) binding.swipeRefresh.setRefreshing(false); },
                    1500);
        });
    }

    private long calculateCutoff(int id, long now) {
        if (id == R.id.chip1W) return now - (7L * 24 * 60 * 60 * 1000);
        if (id == R.id.chip1M) return now - (30L * 24 * 60 * 60 * 1000);
        if (id == R.id.chip3M) return now - (90L * 24 * 60 * 60 * 1000);
        return 0L;
    }

    private void setupGraph() {
        if (vitalsList == null || vitalsList.isEmpty()) {
            binding.chartVitals.clear();
            return;
        }

        long now = System.currentTimeMillis();
        int selectedId = binding.chipGroupTimeFrame.getCheckedChipId();
        long cutoffTime = calculateCutoff(selectedId, now);

        // Resolve selected vital type
        int typeChipId = binding.chipGroupVitalType.getCheckedChipId();
        String selectedType;
        String unitLabel;
        if (typeChipId == R.id.chipVitalBP) {
            selectedType = "Blood Pressure";
            unitLabel = "mmHg (systolic)";
        } else if (typeChipId == R.id.chipVitalSpO2) {
            selectedType = "Oxygen (SpO2)";
            unitLabel = "%";
        } else if (typeChipId == R.id.chipVitalWeight) {
            selectedType = "Weight";
            unitLabel = "kg";
        } else {
            selectedType = "Heart Rate";
            unitLabel = "bpm";
        }

        List<Entry> entries = new ArrayList<>();
        int index = 0;

        // Sort logic to ensure graph draws correctly left-to-right
        List<Vital> chronologicalList = new ArrayList<>(vitalsList);
        Collections.reverse(chronologicalList);

        for (Vital v : chronologicalList) {
            if (v.getType().equals(selectedType) && v.getTimestamp() >= cutoffTime) {
                try {
                    entries.add(new Entry(index++, Float.parseFloat(v.getValue1())));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (entries.isEmpty()) {
            binding.chartVitals.clear();
            binding.chartVitals.setNoDataText("No data for this time range");
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, selectedType);

        // 1. THEME COLOR
        int themeColor = ContextCompat.getColor(requireContext(), R.color.primary);

        // 2. SMOOTH CURVES (Like the reference image)
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        // 3. THICK LINE
        dataSet.setColor(themeColor);
        dataSet.setLineWidth(3f);

        // 4. DONUT MARKERS (White fill, Colored Border)
        dataSet.setDrawCircles(true);
        dataSet.setCircleRadius(5f);       // Visible size
        dataSet.setCircleHoleRadius(3f);   // Large white hole
        dataSet.setCircleColor(themeColor);// Border is Teal
        dataSet.setCircleHoleColor(ContextCompat.getColor(requireContext(), R.color.chart_circle_hole));
        dataSet.setDrawValues(false);      // Clean look

        // 5. GRADIENT FILL
        dataSet.setDrawFilled(true);
        Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.fade_teal);
        dataSet.setFillDrawable(drawable);

        // Summary chips
        if (!entries.isEmpty()) {
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, sum = 0;
            for (Entry e : entries) {
                sum += e.getY();
                if (e.getY() < min) min = e.getY();
                if (e.getY() > max) max = e.getY();
            }
            float avg = sum / entries.size();
            binding.tvChartSummary.setText(
                    "Avg " + (int) avg + "  ·  Min " + (int) min + "  ·  Max " + (int) max + " " + unitLabel);
            binding.tvChartSummary.setVisibility(View.VISIBLE);
        } else {
            binding.tvChartSummary.setVisibility(View.GONE);
        }

        // 6. CHART CONFIGURATION
        LineChart chart = binding.chartVitals;
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setPinchZoom(false);
        chart.setScaleEnabled(false);

        // X-Axis (Bottom)
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.chart_axis_text));

        // Y-Axis (Left)
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.chart_grid_line));
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), R.color.chart_axis_text));

        chart.getAxisRight().setEnabled(false); // Hide right axis

        // 7. REFERENCE BANDS (normal HR range 60–100)
        chart.getAxisLeft().removeAllLimitLines();

        // HR-specific reference bands (60–100 bpm normal range)
        if ("Heart Rate".equals(selectedType)) {
            LimitLine llMin = new LimitLine(60f, "Min");
            llMin.setLineColor(ContextCompat.getColor(requireContext(), R.color.trend_up));
            llMin.setLineWidth(1f);
            llMin.setTextColor(ContextCompat.getColor(requireContext(), R.color.trend_up));
            llMin.setTextSize(9f);
            llMin.enableDashedLine(8f, 4f, 0f);
            llMin.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
            chart.getAxisLeft().addLimitLine(llMin);

            LimitLine llMax = new LimitLine(100f, "Max");
            llMax.setLineColor(ContextCompat.getColor(requireContext(), R.color.med_status_pending));
            llMax.setLineWidth(1f);
            llMax.setTextColor(ContextCompat.getColor(requireContext(), R.color.med_status_pending));
            llMax.setTextSize(9f);
            llMax.enableDashedLine(8f, 4f, 0f);
            llMax.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
            chart.getAxisLeft().addLimitLine(llMax);
        } else if ("Oxygen (SpO2)".equals(selectedType)) {
            // SpO2 reference: 95% threshold
            LimitLine llSpO2 = new LimitLine(95f, "95%");
            llSpO2.setLineColor(ContextCompat.getColor(requireContext(), R.color.med_status_pending));
            llSpO2.setLineWidth(1f);
            llSpO2.setTextColor(ContextCompat.getColor(requireContext(), R.color.med_status_pending));
            llSpO2.setTextSize(9f);
            llSpO2.enableDashedLine(8f, 4f, 0f);
            llSpO2.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
            chart.getAxisLeft().addLimitLine(llSpO2);
        } else if ("Blood Pressure".equals(selectedType)) {
            // BP systolic normal range 90–120
            LimitLine llBPLow = new LimitLine(90f, "Low");
            llBPLow.setLineColor(ContextCompat.getColor(requireContext(), R.color.trend_up));
            llBPLow.setLineWidth(1f);
            llBPLow.setTextColor(ContextCompat.getColor(requireContext(), R.color.trend_up));
            llBPLow.setTextSize(9f);
            llBPLow.enableDashedLine(8f, 4f, 0f);
            llBPLow.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
            chart.getAxisLeft().addLimitLine(llBPLow);

            LimitLine llBPHigh = new LimitLine(120f, "Normal");
            llBPHigh.setLineColor(ContextCompat.getColor(requireContext(), R.color.med_status_pending));
            llBPHigh.setLineWidth(1f);
            llBPHigh.setTextColor(ContextCompat.getColor(requireContext(), R.color.med_status_pending));
            llBPHigh.setTextSize(9f);
            llBPHigh.enableDashedLine(8f, 4f, 0f);
            llBPHigh.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
            chart.getAxisLeft().addLimitLine(llBPHigh);
        }

        chart.getAxisLeft().setDrawLimitLinesBehindData(true);

        // TAP-TO-DETAIL: build an index map of chart entries → source vitals
        final List<Vital> chartVitals = new ArrayList<>();
        final String tapUnitLabel = unitLabel;
        for (Vital v : chronologicalList) {
            if (v.getType().equals(selectedType) && v.getTimestamp() >= cutoffTime) {
                try {
                    Float.parseFloat(v.getValue1());
                    chartVitals.add(v);
                } catch (NumberFormatException ignored) {}
            }
        }

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int idx = (int) e.getX();
                if (idx < 0 || idx >= chartVitals.size()) return;
                Vital tapped = chartVitals.get(idx);

                String displayDate = tapped.getDate();
                try {
                    java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    java.text.SimpleDateFormat display = new java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", java.util.Locale.getDefault());
                    displayDate = display.format(parser.parse(tapped.getDate()));
                } catch (Exception ignored) {}

                String value = tapped.getValue2() != null && !tapped.getValue2().isEmpty()
                        ? tapped.getValue1() + "/" + tapped.getValue2()
                        : tapped.getValue1();
                String msg = "📊  " + value + " " + tapUnitLabel + "\n📅  " + displayDate;
                if (tapped.getContextTags() != null && !tapped.getContextTags().isEmpty()) {
                    msg += "\n🏷️  " + tapped.getContextTags().replace(", ", " · ");
                }

                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(tapped.getType() + " Reading")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onNothingSelected() {}
        });

        // 9. ANIMATE
        chart.setData(new LineData(dataSet));
        chart.animateXY(1500, 1500, com.github.mikephil.charting.animation.Easing.EaseOutQuart, com.github.mikephil.charting.animation.Easing.EaseOutQuart);
        chart.invalidate();
    }

    // --- INSIGHT ENGINE ---
    private void runInsightEngine() {
        if (vitalsList == null || vitalsList.isEmpty()) return;
        Vital latest = vitalsList.get(0);
        binding.cardInsight.setVisibility(View.VISIBLE);

        boolean missedMeds = latest.getContextTags() != null && latest.getContextTags().contains("Missed Meds");

        switch (latest.getType()) {
            case "Heart Rate" -> {
                try {
                    int hr = Integer.parseInt(latest.getValue1());
                    if (hr > 100 && missedMeds) {
                        binding.tvInsightText.setText("Alert: High HR (" + hr + " bpm) likely due to missed medication.");
                        binding.cardInsight.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_insight_alert));
                    } else if (hr > 100) {
                        binding.tvInsightText.setText("Heart rate elevated. Try drinking 500ml water to lower it.");
                        binding.cardInsight.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_insight_info));
                    } else {
                        binding.tvInsightText.setText("Did you know? Regular sleep lowers resting HR.");
                        binding.cardInsight.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_insight_positive));
                    }
                } catch (Exception e) {
                    binding.cardInsight.setVisibility(View.GONE);
                }
            }
            case "Oxygen (SpO2)" -> {
                try {
                    int spo2 = Integer.parseInt(latest.getValue1());
                    if (spo2 < 95) {
                        binding.tvInsightText.setText("Warning: Low Oxygen (" + spo2 + "%). Seek fresh air or help.");
                        binding.cardInsight.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_insight_alert));
                    } else {
                        binding.cardInsight.setVisibility(View.GONE);
                    }
                } catch (Exception e) {
                    binding.cardInsight.setVisibility(View.GONE);
                }
            }
            default -> binding.cardInsight.setVisibility(View.GONE);
        }
    }

    private void updateEmergencyCards() {
        if (vitalsList == null || vitalsList.isEmpty()) return;

        vitalsList.stream().filter(v -> v.getType().equals("Heart Rate")).findFirst()
                .ifPresent(v -> binding.tvLatestHR.setText(v.getValue1()));
        vitalsList.stream().filter(v -> v.getType().equals("Blood Pressure")).findFirst()
                .ifPresent(v -> binding.tvLatestBP.setText(v.getValue1() + "/" + v.getValue2()));
        vitalsList.stream().filter(v -> v.getType().contains("Oxygen")).findFirst()
                .ifPresent(v -> {
                    if (binding.tvLatestSpO2 != null) {
                        binding.tvLatestSpO2.setText(v.getValue1() + "%");
                    }
                });
    }

    private void showAddVitalSheet() {
        AddVitalBottomSheet sheet = new AddVitalBottomSheet();
        sheet.setListener(vital -> viewModel.insertVital(vital));
        sheet.show(getParentFragmentManager(), "AddVitalSheet");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void startVitalsShimmer() {
        if (binding == null) return;
        binding.shimmerVitalsContainer.setVisibility(View.VISIBLE);
        for (int i = 0; i < binding.shimmerVitalsContainer.getChildCount(); i++) {
            View child = binding.shimmerVitalsContainer.getChildAt(i);
            if (child instanceof com.facebook.shimmer.ShimmerFrameLayout) {
                ((com.facebook.shimmer.ShimmerFrameLayout) child).startShimmer();
            }
        }
    }

    private void stopVitalsShimmer() {
        if (binding == null) return;
        for (int i = 0; i < binding.shimmerVitalsContainer.getChildCount(); i++) {
            View child = binding.shimmerVitalsContainer.getChildAt(i);
            if (child instanceof com.facebook.shimmer.ShimmerFrameLayout) {
                ((com.facebook.shimmer.ShimmerFrameLayout) child).stopShimmer();
            }
        }
        binding.shimmerVitalsContainer.setVisibility(View.GONE);
    }
}