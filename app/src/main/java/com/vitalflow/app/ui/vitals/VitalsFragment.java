package com.vitalflow.app.ui.vitals;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
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
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class VitalsFragment extends Fragment {

    @Inject
    AppDao appDao;

    private FragmentVitalsBinding binding;
    private VitalsAdapter adapter;
    private List<Vital> vitalsList = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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

        binding.fabAddVital.setOnClickListener(v -> showAddVitalSheet());

        // 3. OBSERVE LIVE DATA FROM DATABASE
        appDao.getAllVitals().observe(getViewLifecycleOwner(), vitals -> {
            // Update local list
            this.vitalsList = vitals;

            // Update UI Adapter
            adapter = new VitalsAdapter(vitals);
            binding.rvVitals.setAdapter(adapter);

            // Refresh Analytics
            setupGraph();
            updateEmergencyCards();
            runInsightEngine();
        });
    }

    // --- VISUAL TRENDS ---
    // Inside VitalsFragment.java

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

        List<Entry> entries = new ArrayList<>();
        int index = 0;

        // Sort logic to ensure graph draws correctly left-to-right
        List<Vital> chronologicalList = new ArrayList<>(vitalsList);
        Collections.reverse(chronologicalList);

        for (Vital v : chronologicalList) {
            if (v.getType().equals("Heart Rate") && v.getTimestamp() >= cutoffTime) {
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

        LineDataSet dataSet = new LineDataSet(entries, "Heart Rate");

        // 1. THEME COLOR
        int themeColor = Color.parseColor("#00897B");

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
        dataSet.setCircleHoleColor(Color.WHITE); // Center is White
        dataSet.setDrawValues(false);      // Clean look

        // 5. GRADIENT FILL
        dataSet.setDrawFilled(true);
        Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.fade_teal);
        dataSet.setFillDrawable(drawable);

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
        xAxis.setTextColor(Color.parseColor("#90A4AE")); // Grey text

        // Y-Axis (Left)
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setGridColor(Color.parseColor("#EEEEEE")); // Very subtle grid
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisLeft().setTextColor(Color.parseColor("#90A4AE"));

        chart.getAxisRight().setEnabled(false); // Hide right axis

        // 7. ALERT LINE (Optional, keeps medical context)
        LimitLine ll = new LimitLine(100f, "");
        ll.setLineColor(Color.parseColor("#FFCDD2")); // Soft Red
        ll.setLineWidth(1f);
        ll.enableDashedLine(10f, 10f, 0f);
        chart.getAxisLeft().removeAllLimitLines();
        chart.getAxisLeft().addLimitLine(ll);

        // 8. ANIMATE
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
                        binding.cardInsight.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                    } else if (hr > 100) {
                        binding.tvInsightText.setText("Heart rate elevated. Try drinking 500ml water to lower it.");
                        binding.cardInsight.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                    } else {
                        binding.tvInsightText.setText("Did you know? Regular sleep lowers resting HR.");
                        binding.cardInsight.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
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
                        binding.cardInsight.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
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
        sheet.setListener(vital -> {
            disposables.add(appDao.insertVital(vital)
                    .subscribeOn(Schedulers.io())
                    .subscribe());
        });
        sheet.show(getParentFragmentManager(), "AddVitalSheet");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}