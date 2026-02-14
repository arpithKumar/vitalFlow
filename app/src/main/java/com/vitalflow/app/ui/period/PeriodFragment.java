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
import androidx.recyclerview.widget.DiffUtil;
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
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.HealthPlan;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.PeriodLog;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.FragmentPeriodBinding;
import com.vitalflow.app.databinding.ItemCalendarDayBinding;
import com.vitalflow.app.databinding.ItemCycleHistoryBinding;
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
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class PeriodFragment extends Fragment {

    @Inject
    AppDao appDao;
    @Inject
    GeminiRepository geminiRepository;

    private int dragStartPosition = -1;

    private FragmentPeriodBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

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

    // HISTORY LIST ADAPTER
    private HistoryAdapter historyAdapter;

    private boolean isSelectionMode = false;
    private final Handler handler = new Handler();
    private Runnable longPressRunnable;

    private android.widget.PopupWindow currentTooltip;
    private boolean movedTooFar = false;
    private float initialTouchX = 0f;
    private float initialTouchY = 0f;
    private final Map<String, Integer> cyclePhaseMap = new HashMap<>();

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

        binding.btnJumpToday.setOnClickListener(v -> {
            currentMonth.setTime(new Date()); // Reset to Today
            updateCalendar();
        });

        loadFlowData();
    }

    // --- 1. TOUCH & DRAG LOGIC ---
    @SuppressLint("ClickableViewAccessibility")
    private void setupDragInteraction() {
        binding.recyclerCalendar.setLayoutManager(new GridLayoutManager(getContext(), 7));

        binding.recyclerCalendar.setOnTouchListener((v, event) -> {
            int action = event.getAction();

            View child = binding.recyclerCalendar.findChildViewUnder(event.getX(), event.getY());
            int pos = -1;
            if (child != null) pos = binding.recyclerCalendar.getChildAdapterPosition(child);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    // Dismiss existing tooltip
                    if (currentTooltip != null) {
                        currentTooltip.dismiss();
                        currentTooltip = null;
                    }

                    isSelectionMode = false;
                    movedTooFar = false;
                    tempSelectedDates.clear();
                    dragStartPosition = pos;

                    // Save start position
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();

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
                            updateSelectionRange(finalPos);
                        }
                    };
                    handler.postDelayed(longPressRunnable, 400);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    // Check if user moved finger more than 20 pixels (Sloppy Tap Check)
                    if (!movedTooFar) {
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);
                        if (diffX > 20 || diffY > 20) {
                            movedTooFar = true;
                            handler.removeCallbacks(longPressRunnable); // Cancel Long Press
                        }
                    }

                    if (isSelectionMode && pos != -1) {
                        boolean changed = updateSelectionRange(pos);
                        if (changed) {
                            try {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            } catch (Exception e) {
                            }
                        }
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPressRunnable);
                    v.getParent().requestDisallowInterceptTouchEvent(false);

                    if (isSelectionMode) {
                        // WAS SELECTION DRAG -> Open Sheet
                        if (!tempSelectedDates.isEmpty()) {
                            openBatchLogSheet(new ArrayList<>(tempSelectedDates));
                        }

                        tempSelectedDates.clear();

                        // Only refresh the items that were actually highlighted
                        for (int i = 0; i < daysInMonth.size(); i++) {
                            String key = getKey(daysInMonth.get(i));
                            if (tempSelectedDates.contains(key)) {
                                adapter.notifyItemChanged(i); // Turn off highlight
                            }
                        }

                        isSelectionMode = false;

                    } else if (!movedTooFar) {
                        // WAS A CLEAN TAP (And not a scroll) -> Show Tooltip
                        if (pos != -1 && pos < daysInMonth.size()) {
                            Date date = daysInMonth.get(pos);
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(date);
                            // Only show for current month days
                            if (cal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)) {
                                showDateTooltip(child, date);
                            }
                        }
                    }
                    dragStartPosition = -1;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPressRunnable);
                    isSelectionMode = false;
                    movedTooFar = true;
                    dragStartPosition = -1;
                    break;
            }
            return false;
        });
    }

    private void showDateTooltip(View anchor, Date date) {
        if (getContext() == null || anchor == null) return;

        // 1. Inflate Layout
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_calender_tooltip, null);

        TextView tvDate = view.findViewById(R.id.tvTooltipDate);
        TextView tvStatus = view.findViewById(R.id.tvTooltipStatus);
        TextView tvDetails = view.findViewById(R.id.tvTooltipDetails);
        View dot = view.findViewById(R.id.viewStatusDot);

        String key = getKey(date);
        PeriodLog log = logMap.get(key);

        // 2. Set Data
        tvDate.setText(new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(date));

        // Default State
        String status = "No Data";
        int color = Color.GRAY;
        String details = "";

        // Logic Hierarchy: Period > Ovulation > Fertile > Predicted > Empty
        if (log != null && log.isFlow) {
            status = "Period";
            if (log.flowIntensity == 1) {
                status += " (Light)";
                color = Color.parseColor("#F8BBD0");
            } else if (log.flowIntensity == 2) {
                status += " (Medium)";
                color = Color.parseColor("#F06292");
            } else {
                status += " (Heavy)";
                color = Color.parseColor("#C2185B");
            }

            if (log.symptoms != null && !log.symptoms.isEmpty() && !log.symptoms.equals("None")) {
                details = "Symptoms: " + log.symptoms;
            }
        } else if (key.equals(ovulationDateKey)) {
            status = "Ovulation Day 🥚";
            color = Color.parseColor("#AB47BC");
            details = "Peak Fertility. High chance of pregnancy.";
        } else if (fertileDateKeys.contains(key)) {
            status = "Fertile Window 🌿";
            color = Color.parseColor("#E1BEE7");
            details = "High chance of pregnancy.";
        } else if (predictedDateKeys.contains(key)) {
            status = "Predicted Period";
            color = Color.parseColor("#FFCDD2");
        } else {
            // Handle "Just Symptoms" logs (Spotting/Cramps without flow)
            if (log != null && log.symptoms != null && !log.symptoms.equals("None")) {
                status = "Symptoms Logged";
                color = Color.parseColor("#FFCC80"); // Orange
                details = log.symptoms;
            } else {
                status = "Low Chance of Pregnancy";
                color = Color.parseColor("#CFD8DC");
            }
        }

        tvStatus.setText(status);
        dot.setBackgroundTintList(ColorStateList.valueOf(color));

        if (!details.isEmpty()) {
            tvDetails.setText(details);
            tvDetails.setVisibility(View.VISIBLE);
        } else {
            tvDetails.setVisibility(View.GONE);
        }

        // 3. Create Popup
        currentTooltip = new android.widget.PopupWindow(
                view,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true // Focusable (clicking outside dismisses it)
        );

        currentTooltip.setElevation(20);
        // Important: Set background to null so touch events outside dismiss it properly
        currentTooltip.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        // 4. Show it centered above the anchor view
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = view.getMeasuredWidth();
        int popupHeight = view.getMeasuredHeight();

        int xOff = (anchor.getWidth() / 2) - (popupWidth / 2);
        int yOff = -anchor.getHeight() - popupHeight + 10; // 10px overlap fix for visual connection

        currentTooltip.showAsDropDown(anchor, xOff, yOff);
    }

    private boolean updateSelectionRange(int currentPos) {
        if (dragStartPosition == -1 || currentPos == -1) return false;

        // 1. Calculate min and max to handle dragging backwards
        int start = Math.min(dragStartPosition, currentPos);
        int end = Math.max(dragStartPosition, currentPos);

        // 2. Clear previous temp selection to avoid artifacts
        // (This fixes the "random selection" issue)
        Set<String> newSelection = new HashSet<>();

        int targetMonth = currentMonth.get(Calendar.MONTH);

        for (int i = start; i <= end; i++) {
            if (i < daysInMonth.size()) {
                Date date = daysInMonth.get(i);
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                if (cal.get(Calendar.MONTH) == targetMonth) {
                    newSelection.add(getKey(date));
                }
            }
        }

        // 3. Only notify changes if selection is different
        if (!newSelection.equals(tempSelectedDates)) {
            for (int i = 0; i < daysInMonth.size(); i++) {
                String key = getKey(daysInMonth.get(i));
                boolean wasSelected = tempSelectedDates.contains(key);
                boolean isNowSelected = newSelection.contains(key);
                if (wasSelected != isNowSelected) {
                    adapter.notifyItemChanged(i);
                }
            }

            // 3. Update the source of truth
            tempSelectedDates.clear();
            tempSelectedDates.addAll(newSelection);
            return true;
        }
        return false;
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
        List<Completable> tasks = new ArrayList<>();
        for (String key : keys) {
            tasks.add(appDao.insertPeriodLog(new PeriodLog(key, isFlow, intensity, symptoms)));
        }

        disposables.add(Completable.merge(tasks) // Run all inserts
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::loadFlowData, Throwable::printStackTrace)); // Refresh on complete
    }

    // --- 3. DATA & UI LOADING ---
    private void loadFlowData() {
        disposables.add(appDao.getAllLogsSync()
                .subscribeOn(Schedulers.io())
                .map(allLogs -> {
                    // --- BACKGROUND CALCULATIONS ---
                    // 1. Populate Maps
                    logMap.clear();
                    List<PeriodLog> flowLogs = new ArrayList<>();
                    if (allLogs != null) {
                        for (PeriodLog log : allLogs) {
                            logMap.put(log.date, log);
                            if (log.isFlow) flowLogs.add(log);
                        }
                    }

                    // 2. Analyze Cycles
                    List<CycleHistory> historyList = CycleHelper.analyzeCycles(flowLogs);

                    // 3. Calculate Predictions (Updates member variables safe for next step)
                    calculatePredictionInternal(historyList);

                    // Pass data bundle to Main Thread
                    return new DataBundle(allLogs, flowLogs, historyList);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(data -> {
                    // --- UI UPDATES ---
                    updateCalendar();
                    updateChart(data.flowLogs);
                    updateSymptomChart(data.allLogs);

                    // Update Prediction UI Cards
                    if (data.historyList.isEmpty()) {
                        binding.tvHistoryTitleCycle.setVisibility(View.GONE);
                        binding.recyclerHistory.setVisibility(View.GONE);
                        updatePredictionFallback(data.flowLogs);
                    } else {
                        binding.tvHistoryTitleCycle.setVisibility(View.VISIBLE);
                        binding.recyclerHistory.setVisibility(View.VISIBLE);
                        updatePredictionUI(data.historyList);
                    }

                    historyAdapter.setData(data.historyList);

                    // Trigger AI
                    askAiForInsights(data.allLogs != null ? data.allLogs : new ArrayList<>());

                }, Throwable::printStackTrace));
    }

    private static class DataBundle {
        List<PeriodLog> allLogs;
        List<PeriodLog> flowLogs;
        List<CycleHistory> historyList;

        DataBundle(List<PeriodLog> allLogs, List<PeriodLog> flowLogs, List<CycleHistory> historyList) {
            this.allLogs = allLogs;
            this.flowLogs = flowLogs;
            this.historyList = historyList;
        }
    }

    private void calculatePredictionInternal(List<CycleHistory> history) {
        predictedDateKeys.clear();
        fertileDateKeys.clear();
        cyclePhaseMap.clear();
        ovulationDateKey = "";

        if (history.isEmpty()) return;

        CycleHistory lastCycle = history.get(0);
        int avgCycle = CycleHelper.calculateAverageCycleLength(history);
        int avgPeriod = 5;

        Calendar c = Calendar.getInstance();
        c.setTime(lastCycle.startDate);
        c.add(Calendar.DAY_OF_YEAR, avgCycle);
        Date nextPeriodStart = c.getTime();

        Date ovulationDate = CycleHelper.calculateOvulationDate(nextPeriodStart);
        List<Date> fertileWindow = CycleHelper.calculateFertileWindow(ovulationDate);
        ovulationDateKey = this.sdf.format(ovulationDate);
        fertileWindow.forEach(fd -> fertileDateKeys.add(sdf.format(fd)));

        Calendar predCal = Calendar.getInstance();
        predCal.setTime(nextPeriodStart);
        for (int i = 0; i < 5; i++) {
            predictedDateKeys.add(sdf.format(predCal.getTime()));
            predCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Calendar phaseCal = Calendar.getInstance();
        phaseCal.setTime(lastCycle.startDate);

        for (int i = 0; i < 60; i++) {
            Date d = phaseCal.getTime();
            String key = sdf.format(d);

            // Determine Phase
            int phase = 0; // Default

            if (fertileDateKeys.contains(key)) {
                phase = 2; // Ovulatory / Fertile (Purple)
            } else if (d.after(lastCycle.startDate) && d.before(fertileWindow.get(0))) {
                long daysSinceStart = TimeUnit.DAYS.convert(d.getTime() - lastCycle.startDate.getTime(), TimeUnit.MILLISECONDS);
                if (daysSinceStart >= avgPeriod) {
                    phase = 1;
                }
            } else if (d.after(fertileWindow.get(fertileWindow.size() - 1)) && d.before(nextPeriodStart)) {
                phase = 3;
            }

            if (phase != 0) cyclePhaseMap.put(key, phase);

            phaseCal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void updatePredictionUI(List<CycleHistory> history) {
        if (history.isEmpty()) {
            binding.tvDayCount.setText("Day\n--");
            binding.tvCycleStatus.setText("Log your first period");
            binding.progressCycle.setProgress(0);
            return;
        }

        CycleHistory lastCycle = history.get(0);
        int avgCycle = CycleHelper.calculateAverageCycleLength(history);

        Calendar c = Calendar.getInstance();
        c.setTime(lastCycle.startDate);
        c.add(Calendar.DAY_OF_YEAR, avgCycle);

        long diff = new Date().getTime() - lastCycle.startDate.getTime();
        long currentDay = TimeUnit.MILLISECONDS.toDays(diff) + 1;
        int progressDay = Math.min((int) currentDay, avgCycle);

        String nextDateText = new SimpleDateFormat("MMM dd", Locale.getDefault()).format(c.getTime());
        binding.tvPrediction.setText("Next Period: ~" + nextDateText + " (Avg " + avgCycle + " days)");

        if (currentDay > avgCycle) {
            binding.tvCycleStatus.setText("Cycle Day " + currentDay + " (Late)");
        } else {
            binding.tvCycleStatus.setText("Period in " + (avgCycle - currentDay) + " days");
        }
        binding.tvDayCount.setText("Day\n" + currentDay);

        binding.progressCycle.setMax(avgCycle);
        ObjectAnimator animation = ObjectAnimator.ofInt(binding.progressCycle, "progress", 0, progressDay);
        animation.setDuration(1000);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.start();
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

        if (adapter != null) {
            if (binding.recyclerCalendar.getChildCount() > 0) {
                adapter.notifyItemRangeChanged(0, daysInMonth.size());
            } else {
                // Fallback for first load (when grid is empty)
                adapter.notifyDataSetChanged();
            }
        }
        Calendar today = Calendar.getInstance();
        boolean isCurrentMonth = (today.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)) &&
                (today.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR));

        if (binding.btnJumpToday != null) {
            if (isCurrentMonth) {
                binding.btnJumpToday.hide();
            } else {
                binding.btnJumpToday.show();
            }
        }

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
            // Find the newest flow date
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
                binding.tvAiText.setText("Analyzing cycle, diet & sleep... ✨");
                binding.tvAiText.setOnClickListener(null);
            });
        }

        // B. Calculate Cycle Day (Existing Logic)
        int calculatedCycleDay = 1;
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
                    calculatedCycleDay = (int) TimeUnit.MILLISECONDS.toDays(diff) + 1;
                    if (calculatedCycleDay < 1) calculatedCycleDay = 1;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        final int cycleDay = calculatedCycleDay;

        // C. Get Recent Context (Last 5 days) (Existing Logic)
        List<PeriodLog> recentLogs = new ArrayList<>();
        if (logs != null) {
            int limit = Math.min(logs.size(), 5);
            for (int i = 0; i < limit; i++) {
                recentLogs.add(logs.get(i));
            }
        }

        // --- NEW: FETCH FOOD & SLEEP DATA ---
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        disposables.add(
                io.reactivex.rxjava3.core.Single.zip(
                                appDao.getFoodLogsSync(todayKey),
                                appDao.getDailyHealthListSync(todayKey),
                                appDao.getAllMedicationsSync(),
                                (foodLogs, dailyHealthList, meds) -> {
                                    String sleep = "Unknown";
                                    if (!dailyHealthList.isEmpty()) {
                                        sleep = dailyHealthList.get(0).getSleepDuration();
                                    }
                                    return new ContextBundle(foodLogs, sleep, meds);
                                }
                        )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(bundle -> {
                            // D. Call Repo Method (WITH NEW FOOD & SLEEP DATA)
                            geminiRepository.getDailyHealthPlan(
                                    recentLogs,
                                    cycleDay,
                                    bundle.foodLogs,
                                    bundle.meds,
                                    bundle.sleep,
                                    new GeminiRepository.HealthPlanCallback() {
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
                                            handleAiError(t, logs);
                                        }
                                    });
                        }, throwable -> {
                            // Fallback: If DB fetch fails, call AI without food/sleep (Robustness)
                            geminiRepository.getDailyHealthPlan(
                                    recentLogs,
                                    cycleDay,
                                    new ArrayList<>(),
                                    new ArrayList<>(),
                                    "Unknown",
                                    new GeminiRepository.HealthPlanCallback() {
                                        @Override
                                        public void onSuccess(HealthPlan plan) {
                                            if (getActivity() != null) {
                                                getActivity().runOnUiThread(() -> {
                                                    binding.tvAiText.setText(plan.daySummary + " \n(Tap for full plan 🧘‍♀️)");
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
                                            handleAiError(t, logs);
                                        }
                                    });
                        })
        );
    }

    private static class ContextBundle {
        List<FoodLog> foodLogs;
        String sleep;
        List<Medication> meds;

        ContextBundle(List<FoodLog> foodLogs, String sleep, List<Medication> meds) {
            this.foodLogs = foodLogs;
            this.sleep = sleep;
            this.meds = meds;
        }
    }

    private void handleAiError(Throwable t, List<PeriodLog> logs) {
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
            ItemCalendarDayBinding binding = ItemCalendarDayBinding.inflate(
                    LayoutInflater.from(parent.getContext()),
                    parent,
                    false
            );
            return new Holder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Date date = daysInMonth.get(position);
            String key = getKey(date);
            PeriodLog log = logMap.get(key);

            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            holder.binding.tvDayNumber.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));

            // 1. Reset State
            holder.binding.viewBackground.setBackgroundResource(R.drawable.bg_heatmap_square);
            holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5F5F5"))); // Default Empty
            holder.binding.tvDayNumber.setTextColor(Color.parseColor("#546E7A"));
            holder.binding.viewSymptomDot.setVisibility(View.GONE);
            holder.binding.viewSelectionBorder.setVisibility(View.GONE);

            // Gray out non-current month days
            boolean isSameMonth = cal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH);
            if (!isSameMonth) {
                holder.binding.tvDayNumber.setTextColor(Color.parseColor("#CFD8DC"));
                holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FAFAFA"))); // Lighter
                return;
            }

            boolean isPeriodDay = (log != null && log.isFlow) || predictedDateKeys.contains(key);

            if (!isPeriodDay) {
                Integer phase = cyclePhaseMap.get(key);
                if (phase != null) {
                    if (phase == 1) { // Follicular (Energy)
                        holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E1F5FE"))); // Light Blue
                    } else if (phase == 2) { // Ovulatory (Libido)
                        // (Handled below by fertile keys usually, but backup here)
                        holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9"))); // Green
                    } else if (phase == 3) { // Luteal (PMS)
                        holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF3E0"))); // Light Orange
                    }
                }
            }

            // --- HEATMAP LOGIC ---

            if (log != null && log.isFlow) {
                // FLOW EXISTS -> Color based on intensity
                int color;
                if (log.flowIntensity == 1) color = Color.parseColor("#F8BBD0"); // Light Pink
                else if (log.flowIntensity == 2) color = Color.parseColor("#F06292"); // Med Pink
                else color = Color.parseColor("#C2185B"); // Heavy Pink

                holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(color));
                holder.binding.tvDayNumber.setTextColor(Color.WHITE);
            } else if (key.equals(ovulationDateKey)) {
                // OVULATION -> Purple
                holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#AB47BC")));
                holder.binding.tvDayNumber.setTextColor(Color.WHITE);
            } else if (fertileDateKeys.contains(key)) {
                // FERTILE -> Light Purple
                holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#C8E6C9")));
                holder.binding.tvDayNumber.setTextColor(Color.parseColor("#1B5E20"));
            } else if (predictedDateKeys.contains(key)) {
                // PREDICTED PERIOD -> Dashed/Light Red (Simulated with color here)
                holder.binding.viewBackground.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFCDD2")));
                holder.binding.tvDayNumber.setTextColor(Color.parseColor("#B71C1C"));
            }

            if (log != null && log.symptoms != null && !log.symptoms.isEmpty() && !log.symptoms.equalsIgnoreCase("None")) {
                holder.binding.viewSymptomDot.setVisibility(View.VISIBLE);
            }

            // --- SELECTION LOGIC ---
            if (tempSelectedDates.contains(key)) {
                holder.binding.viewSelectionBorder.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return daysInMonth.size();
        }

        public static class Holder extends RecyclerView.ViewHolder {
            ItemCalendarDayBinding binding;

            public Holder(ItemCalendarDayBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private List<CycleHistory> data = new ArrayList<>();

        public void setData(List<CycleHistory> newHistoryList) {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return data.size();
                }

                @Override
                public int getNewListSize() {
                    return newHistoryList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    // Compare unique IDs (Assuming CycleHistory has startDate as a unique identifier)
                    return data.get(oldItemPosition).startDate.equals(newHistoryList.get(newItemPosition).startDate);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    CycleHistory oldItem = data.get(oldItemPosition);
                    CycleHistory newItem = newHistoryList.get(newItemPosition);

                    // Compare content fields
                    return oldItem.endDate.equals(newItem.endDate) &&
                            oldItem.durationDays == newItem.durationDays &&
                            oldItem.cycleLengthDays == newItem.cycleLengthDays;
                }
            });

            // 2. Update Data
            this.data = new ArrayList<>(newHistoryList); // Create copy

            // 3. Dispatch specific updates
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemCycleHistoryBinding binding = ItemCycleHistoryBinding.inflate(
                    LayoutInflater.from(parent.getContext()),
                    parent,
                    false
            );
            return new Holder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CycleHistory item = data.get(position);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());

            // Bind Data
            holder.binding.tvDateRange.setText(new StringBuilder().append(sdf.format(item.startDate)).append(" - ")
                    .append(sdf.format(item.endDate)).toString());
            holder.binding.tvYear.setText(yearFormat.format(item.startDate));
            holder.binding.tvDuration.setText(String.valueOf(item.durationDays));

            if (item.cycleLengthDays > 0) {
                holder.binding.tvCycleLength.setText(String.valueOf(item.cycleLengthDays));
                holder.binding.tvCycleLength.setVisibility(View.VISIBLE);
            } else {
                holder.binding.tvCycleLength.setText("-");
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public static class Holder extends RecyclerView.ViewHolder {
            ItemCycleHistoryBinding binding;

            public Holder(ItemCycleHistoryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}