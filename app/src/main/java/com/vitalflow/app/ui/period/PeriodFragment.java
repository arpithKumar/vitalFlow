package com.vitalflow.app.ui.period;

import static android.view.HapticFeedbackConstants.LONG_PRESS;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.chip.Chip;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.CycleHistory;
import com.vitalflow.app.data.entity.HealthPlan;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.FragmentPeriodBinding;
import com.vitalflow.app.ui.chatbot.GeminiKeyBottomSheet;
import com.vitalflow.app.utils.CycleHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PeriodFragment extends Fragment {

    @Inject
    AppDao appDao;
    @Inject
    GeminiRepository geminiRepository;

    private FragmentPeriodBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Calendar currentMonth = Calendar.getInstance();
    private final List<Date> daysInMonth = new ArrayList<>();
    private CalendarAdapter adapter;

    // PREDICTION STATE
    private final Set<String> predictedDateKeys = new HashSet<>();
    private final Set<String> fertileDateKeys = new HashSet<>();
    private String ovulationDateKey = "";

    // Data Map: DateKey -> Log
    private final Map<String, PeriodLog> logMap = new HashMap<>();

    // --- DRAG SELECTION STATE ---
    // Dates selected during the current drag/click gesture
    private final Set<String> tempSelectedDates = new HashSet<>();
    private boolean isDragging = false;

    // HISTORY LIST ADAPTER
    private HistoryAdapter historyAdapter;

    private boolean isSelectionMode = false;
    private final Handler handler = new Handler();
    private Runnable longPressRunnable;

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPeriodBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        setupCalendarNavigation();
        adapter = new CalendarAdapter();
        setupChartStyle();

        binding.recyclerCalendar.setAdapter(adapter);
        setupDragInteraction();

        historyAdapter = new HistoryAdapter();
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerHistory.setAdapter(historyAdapter);

        loadFlowData();
    }

    // --- 1. TOUCH & DRAG LOGIC ---
    @SuppressLint("ClickableViewAccessibility")
    private void setupDragInteraction() {
        binding.recyclerCalendar.setLayoutManager(new GridLayoutManager(getContext(), 7));

        binding.recyclerCalendar.setOnTouchListener((v, event) -> {
            int action = event.getAction();

            // Find cell under finger
            View child = binding.recyclerCalendar.findChildViewUnder(event.getX(), event.getY());
            int pos = -1;
            if (child != null) pos = binding.recyclerCalendar.getChildAdapterPosition(child);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    isSelectionMode = false;
                    tempSelectedDates.clear();

                    // Long press
                    final int finalPos = pos;
                    longPressRunnable = () -> {
                        isSelectionMode = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        if (finalPos != -1) {
                            try {
                                v.performHapticFeedback(LONG_PRESS);
                            } catch (Exception e) {
                            }
                            selectDateAtPosition(finalPos);
                        }
                    };
                    handler.postDelayed(longPressRunnable, 500);
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (isSelectionMode && pos != -1) {
                        selectDateAtPosition(pos);
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    v.getParent().requestDisallowInterceptTouchEvent(false);

                    // If we were selecting, open the sheet now
                    if (isSelectionMode && !tempSelectedDates.isEmpty()) {
                        openBatchLogSheet(new ArrayList<>(tempSelectedDates));
                        isSelectionMode = false;
                    }

                    // Cleanup visual selection
                    tempSelectedDates.clear();
                    adapter.notifyDataSetChanged();
                    break;
            }
            return false;
        });
    }

    private void selectDateAtPosition(int pos) {
        if (pos >= 0 && pos < daysInMonth.size()) {
            Date date = daysInMonth.get(pos);
            String key = getKey(date);

            if (!tempSelectedDates.contains(key)) {
                tempSelectedDates.add(key);
                adapter.notifyItemChanged(pos);
            }
        }
    }

    // --- 2. BATCH BOTTOM SHEET ---
    private void openBatchLogSheet(List<String> dateKeys) {
        BottomSheetPeriodLog sheet = new BottomSheetPeriodLog();
        sheet.setDateKeys(dateKeys);
        // Save logic remains the same
        sheet.setListener(this::saveBatchLogs);
        sheet.show(getParentFragmentManager(), "PeriodLogSheet");
    }

    private void saveBatchLogs(List<String> keys, boolean isFlow, int intensity, String symptoms) {
        executor.execute(() -> {
            keys.forEach(key -> appDao.insertPeriodLog(new PeriodLog(key, isFlow, intensity, symptoms)));
            loadFlowData(); // Refresh UI
        });
    }

    // --- 3. DATA & UI LOADING ---
    private void loadFlowData() {
        executor.execute(() -> {
            List<PeriodLog> allLogs = appDao.getAllLogsSync();
            logMap.clear();
            predictedDateKeys.clear();
            fertileDateKeys.clear();
            ovulationDateKey = "";
            List<PeriodLog> flowLogs = new ArrayList<>();

            if (allLogs != null) {
                for (PeriodLog log : allLogs) {
                    logMap.put(log.date, log);
                    if (log.isFlow) flowLogs.add(log);
                }
            }

            List<CycleHistory> historyList = CycleHelper.analyzeCycles(flowLogs != null ? flowLogs : new ArrayList<>());

            // C. Calculate Prediction (NEW)
            if (!historyList.isEmpty()) {
                calculatePrediction(historyList);
            } else {
                // Initial State
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.tvDayCount.setText("Day\n--");
                        binding.tvCycleStatus.setText("Log your first period");
                        binding.progressCycle.setProgress(0);
                    });
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    updateCalendar();
                    updateChart(flowLogs);
                    updateSymptomChart(allLogs);

                    // Note: updatePrediction is now handled inside calculatePrediction/UI update
                    // but we keep the old one as fallback for text if history is empty
                    if (historyList.isEmpty()) {
                        binding.tvHistoryTitleCycle.setVisibility(View.GONE); // Make sure ID matches XML
                        binding.recyclerHistory.setVisibility(View.GONE);
                        updatePredictionFallback(flowLogs);
                    } else {
                        binding.tvHistoryTitleCycle.setVisibility(View.VISIBLE); // Make sure ID matches XML
                        binding.recyclerHistory.setVisibility(View.VISIBLE);
                    }

                    // Force AI call if logs exist, or send empty list prompt
                    askAiForInsights(allLogs != null ? allLogs : new ArrayList<>());
                    historyAdapter.setData(historyList);
                });
            }
        });
    }

    private void updateSymptomChart(List<PeriodLog> logs) {

        binding.chipGroupSymptoms.removeAllViews();

        if (logs == null || logs.isEmpty()) return;

        // A. Count Frequencies
        Map<String, Integer> counts = new HashMap<>();
        for (PeriodLog log : logs) {
            if (log.symptoms != null && !log.symptoms.isEmpty() && !log.symptoms.equals("None")) {
                String[] items = log.symptoms.split(",");
                for (String item : items) {
                    String cleanItem = item.trim();
                    if (!cleanItem.isEmpty()) {
                        counts.put(cleanItem, counts.getOrDefault(cleanItem, 0) + 1);
                    }
                }
            }
        }

        if (counts.isEmpty()) return;

        // B. Sort by Count (Top 5)
        List<Map.Entry<String, Integer>> sortedList = new ArrayList<>(counts.entrySet());
        // Sort descending
        sortedList.sort((e1, e2) -> e2.getValue()
                .compareTo(e1.getValue()));

        int limit = Math.min(sortedList.size(), 8);

        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = sortedList.get(i);
            String text = entry.getKey();
            int count = entry.getValue();

            Chip chip = new Chip(requireContext());
            chip.setText(text + " (" + count + ")");

            // Visual Weight: High Frequency = Darker Color
            if (count >= 5) {
                // Tier 1: Very High Frequency -> Deep Purple & Bold
                chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#D1C4E9"))); // Deep Purple 100
                chip.setTextColor(Color.parseColor("#311B92")); // Deep Purple 900
                chip.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (count >= 3) {
                // Tier 2: High Frequency -> Medium Purple
                chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E1BEE7"))); // Purple 100
                chip.setTextColor(Color.parseColor("#4A148C")); // Purple 900
            } else if (count == 2) {
                // Tier 3: Moderate Frequency -> Light Purple
                chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F3E5F5"))); // Purple 50
                chip.setTextColor(Color.parseColor("#7B1FA2")); // Purple 700
            } else {
                // Tier 4: Low Frequency (1) -> Subtle Pinkish Grey
                chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#FCE4EC"))); // Pink 50
                chip.setTextColor(Color.parseColor("#C2185B")); // Pink 700
            }

            chip.setClickable(false);
            chip.setCheckable(false);
            binding.chipGroupSymptoms.addView(chip);
        }
    }

    private void calculatePrediction(List<CycleHistory> history) {
        CycleHistory lastCycle = history.get(0);
        int avgCycle = CycleHelper.calculateAverageCycleLength(history);

        Calendar c = Calendar.getInstance();
        c.setTime(lastCycle.startDate);
        c.add(Calendar.DAY_OF_YEAR, avgCycle);
        Date nextPeriodStart = c.getTime();

        Date ovulationDate = CycleHelper.calculateOvulationDate(nextPeriodStart);
        List<Date> fertileWindow = CycleHelper.calculateFertileWindow(ovulationDate);
        ovulationDateKey = this.sdf.format(ovulationDate);

        fertileDateKeys.clear();
        fertileWindow.forEach(fd -> fertileDateKeys.add(sdf.format(fd)));

        predictedDateKeys.clear();
        Calendar predCal = Calendar.getInstance();
        predCal.setTime(nextPeriodStart);
        for (int i = 0; i < 5; i++) {
            predictedDateKeys.add(sdf.format(predCal.getTime()));
            predCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long diff = new Date().getTime() - lastCycle.startDate.getTime();
        long currentDay = TimeUnit.MILLISECONDS.toDays(diff) + 1; // +1 because start date is Day 1

        int progressDay = Math.min((int) currentDay, avgCycle);

        // 1. Update Text UI
        String nextDateText = new SimpleDateFormat("MMM dd", Locale.getDefault()).format(c.getTime());
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                binding.tvPrediction.setText(new StringBuilder().append("Next Period: ~")
                        .append(nextDateText)
                        .append(" (Avg ").append(avgCycle)
                        .append(" days)").toString());
                if (currentDay > avgCycle) {
                    binding.tvCycleStatus.setText(new StringBuilder().append("Cycle Day ").append(currentDay).append(" (Late)").toString());
                } else {
                    binding.tvCycleStatus.setText(new StringBuilder().append("Period in ").append(avgCycle - currentDay).append(" days").toString());
                }
                binding.tvDayCount.setText(new StringBuilder().append("Day\n").append(currentDay).toString());

                binding.progressCycle.setMax(avgCycle);
                ObjectAnimator animation = ObjectAnimator.ofInt(
                        binding.progressCycle,
                        "progress",
                        0,
                        progressDay
                );
                animation.setDuration(1000);
                animation.setInterpolator(new DecelerateInterpolator());
                animation.start();
            });
        }
    }

    private void updateCalendar() {
        daysInMonth.clear();
        Calendar calendar = (Calendar) currentMonth.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int offset = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        calendar.add(Calendar.DAY_OF_MONTH, -offset);

        for (int i = 0; i < 42; i++) {
            daysInMonth.add(calendar.getTime());
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (adapter != null) adapter.notifyDataSetChanged();

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        binding.tvMonthName.setText(sdf.format(currentMonth.getTime()));
    }

    private void setupCalendarNavigation() {
        binding.btnPrevMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateCalendar();
        });
        binding.btnNextMonth.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateCalendar();
        });
    }

    // --- 4. CHART & AI ---
    private void setupChartStyle() {
        LineChart chart = binding.chartCycle;

        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.parseColor("#90A4AE"));

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setEnabled(true); // Must be enabled to show labels
        yAxis.setDrawGridLines(true); // Optional: Keep grid lines for readability
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(4f);
        yAxis.setLabelCount(5, true); // Force 0, 1, 2, 3, 4
        yAxis.setGranularity(1f); // Interval is 1

        yAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                if (value == 1f) return "Light";
                if (value == 2f) return "Medium";
                if (value == 3f) return "Heavy";
                return "";
            }
        });

        chart.getAxisRight().setEnabled(false);

        chart.setNoDataText("Log flow to see intensity wave");
        chart.setTouchEnabled(false);
    }

    private void updateChart(List<PeriodLog> logs) {
        if (logs == null || logs.isEmpty()) return;

        logs.sort(Comparator.comparing(o -> o.date));

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        int index = 0;

        int start = Math.max(0, logs.size() - 7);

        // Get last 7 flow days
        for (int i = start; i < logs.size(); i++) {
            PeriodLog log = logs.get(i);
            if (!log.isFlow) continue;

            // Use Entry (for Line) instead of BarEntry
            entries.add(new Entry(index, log.flowIntensity));

            try {
                Date d = this.sdf.parse(log.date);
                labels.add(new SimpleDateFormat("dd/MM", Locale.getDefault()).format(d));
            } catch (Exception e) {
                labels.add("?");
            }
            index++;
        }

        if (entries.isEmpty()) return;

        LineDataSet set = new LineDataSet(entries, "Intensity");
        set.setColor(Color.parseColor("#C2185B"));
        set.setLineWidth(3f);
        set.setCircleColor(Color.parseColor("#C2185B"));
        set.setCircleRadius(5f);
        set.setDrawCircleHole(true);
        set.setCircleHoleColor(Color.WHITE);
        set.setDrawValues(false); // Clean look

        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        set.setDrawFilled(true);
        set.setFillColor(Color.parseColor("#C2185B"));
        set.setFillAlpha(40); // Transparent pink fill

        LineData data = new LineData(set);
        binding.chartCycle.setData(data);
        binding.chartCycle.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.chartCycle.getXAxis().setLabelCount(labels.size());
        binding.chartCycle.animateY(1200, com.github.mikephil.charting.animation.Easing.EaseInOutCubic);
    }

    private void updatePredictionFallback(List<PeriodLog> logs) {
        if (logs == null || logs.isEmpty()) {
            binding.tvPrediction.setText("Next Period: Log data to calculate");
            return;
        }
        try {
            // Find newest flow date
            Date lastDate = null;
            for (PeriodLog log : logs) {
                if (log.isFlow) {
                    lastDate = this.sdf.parse(log.date);
                    break;
                }
            }
            if (lastDate != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(lastDate);
                c.add(Calendar.DAY_OF_YEAR, 28);
                String nextDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(c.getTime());
                binding.tvPrediction.setText(new StringBuilder().append("Next Period: ~").append(nextDate).toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void askAiForInsights(List<PeriodLog> logs) {
        // A. Show Loading
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                binding.tvAiText.setText("Analyzing cycle trends... ✨");
                binding.tvAiText.setOnClickListener(null);
            });
        }

        // B. Calculate Cycle Day
        int cycleDay = 1;
        if (logs != null && !logs.isEmpty()) {
            try {
                Date latestFlow = null;
                // Assuming logs sorted date DESC
                for (PeriodLog log : logs) {
                    if (log.isFlow) {
                        latestFlow = sdf.parse(log.date);
                        break;
                    }
                }
                if (latestFlow != null) {
                    long diff = new Date().getTime() - latestFlow.getTime();
                    cycleDay = (int) TimeUnit.MILLISECONDS.toDays(diff) + 1;
                    if (cycleDay < 1) cycleDay = 1;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // C. Get Recent Context (Last 5 days)
        List<PeriodLog> recentLogs = new ArrayList<>();
        if (logs != null) {
            int limit = Math.min(logs.size(), 5);
            for (int i = 0; i < limit; i++) {
                recentLogs.add(logs.get(i));
            }
        }

        // D. Call New Repo Method
        geminiRepository.getDailyHealthPlan(recentLogs, cycleDay, new GeminiRepository.HealthPlanCallback() {
            @Override
            public void onSuccess(HealthPlan plan) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Display Summary
                        binding.tvAiText.setText(plan.daySummary + " \n(Tap for full plan 🧘‍♀️)");

                        // Add Click Listener -> Open Sheet
                        binding.tvAiText.setOnClickListener(v -> {
                            HealthPlanBottomSheet sheet = new HealthPlanBottomSheet();
                            sheet.setHealthPlan(plan);
                            sheet.show(getParentFragmentManager(), "HealthPlan");
                        });
                    });
                }
            }

            @Override
            public void onError(Throwable t) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (t.getMessage() != null && t.getMessage().contains("API_KEY_MISSING")) {
                        binding.tvAiText.setText("Tap to activate AI Coach ✨");
                        binding.tvAiText.setOnClickListener(v -> {
                            GeminiKeyBottomSheet sheet = new GeminiKeyBottomSheet();
                            sheet.setListener(() -> askAiForInsights(logs));
                            sheet.show(getParentFragmentManager(), "GeminiKey");
                        });
                    } else {
                        binding.tvAiText.setText("Plan currently unavailable.");
                        binding.tvAiText.setOnClickListener(null);
                    }
                });
            }
        });
    }

    // --- UTILS ---
    private String getKey(Date date) {
        return this.sdf.format(date);
    }

    // --- ADAPTER ---
    // Replace the entire CalendarAdapter class inside PeriodFragment.java

    class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Date date = daysInMonth.get(position);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            holder.tvDay.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));

            // 1. Reset State
            holder.viewBackground.setBackgroundResource(R.drawable.bg_heatmap_square);
            holder.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5F5F5"))); // Default Empty
            holder.viewBorder.setVisibility(View.GONE);
            holder.tvDay.setTextColor(Color.parseColor("#546E7A"));

            // Grey out non-current month days
            boolean isSameMonth = cal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH);
            if (!isSameMonth) {
                holder.tvDay.setTextColor(Color.parseColor("#CFD8DC"));
                holder.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FAFAFA"))); // Lighter
            }

            String key = getKey(date);
            PeriodLog log = logMap.get(key);

            // --- HEATMAP LOGIC ---

            if (log != null && log.isFlow) {
                // FLOW EXISTS -> Color based on intensity
                int color;
                if (log.flowIntensity == 1) color = Color.parseColor("#F8BBD0"); // Light Pink
                else if (log.flowIntensity == 2) color = Color.parseColor("#F06292"); // Med Pink
                else color = Color.parseColor("#C2185B"); // Heavy Pink

                holder.viewBackground.setBackgroundTintList(ColorStateList.valueOf(color));
                holder.tvDay.setTextColor(Color.WHITE);
            } else if (key.equals(ovulationDateKey)) {
                // OVULATION -> Purple
                holder.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#AB47BC")));
                holder.tvDay.setTextColor(Color.WHITE);
            } else if (fertileDateKeys.contains(key)) {
                // FERTILE -> Light Purple
                holder.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E1BEE7")));
                holder.tvDay.setTextColor(Color.parseColor("#4A148C"));
            } else if (predictedDateKeys.contains(key)) {
                // PREDICTED PERIOD -> Dashed/Light Red (Simulated with color here)
                holder.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFCDD2")));
                holder.tvDay.setTextColor(Color.parseColor("#B71C1C"));
            }

            // --- SELECTION LOGIC ---
            if (tempSelectedDates.contains(key)) {
                holder.viewBorder.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return daysInMonth.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView tvDay;
            View viewBackground;
            View viewBorder;

            public Holder(@NonNull View v) {
                super(v);
                tvDay = v.findViewById(R.id.tvDayNumber);
                viewBackground = v.findViewById(R.id.viewBackground);
                viewBorder = v.findViewById(R.id.viewSelectionBorder);
            }
        }
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private List<CycleHistory> data = new ArrayList<>();

        public void setData(List<CycleHistory> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cycle_history, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CycleHistory item = data.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

            // Bind Data
            holder.tvDateRange.setText(new StringBuilder().append(sdf.format(item.startDate)).append(" - ")
                    .append(sdf.format(item.endDate)).toString());
            holder.tvYear.setText(yearFormat.format(item.startDate));
            holder.tvDuration.setText(String.valueOf(item.durationDays));

            if (item.cycleLengthDays > 0) {
                holder.tvCycleLength.setText(String.valueOf(item.cycleLengthDays));
                holder.tvCycleLength.setVisibility(View.VISIBLE);
            } else {
                holder.tvCycleLength.setText("-");
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView tvDateRange, tvYear, tvDuration, tvCycleLength;

            public Holder(@NonNull View itemView) {
                super(itemView);
                tvDateRange = itemView.findViewById(R.id.tvDateRange);
                tvYear = itemView.findViewById(R.id.tvYear);
                tvDuration = itemView.findViewById(R.id.tvDuration);
                tvCycleLength = itemView.findViewById(R.id.tvCycleLength);
            }
        }
    }
}