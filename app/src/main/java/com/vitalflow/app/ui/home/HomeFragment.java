package com.vitalflow.app.ui.home;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.FragmentHomeBinding;
import com.vitalflow.app.ui.report.ReportViewModel;
import com.vitalflow.app.utils.HealthConnectManager;
import com.vitalflow.app.utils.InsightEngine;
import com.vitalflow.app.utils.NotificationHelper;
import com.vitalflow.app.utils.PdfReportManager;
import com.vitalflow.app.utils.WidgetUpdateHelper;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeFragment extends Fragment {

    @Inject
    AppDao appDao;

    @Inject
    GeminiRepository geminiRepository;

    ReportViewModel reportViewModel;

    private FragmentHomeBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Data State
    private DailyHealth currentDayData;
    private String todayDateKey;
    private boolean goalReachedTriggered = false;

    // Animation State
    private int displayedWaterAmount = 0; // Tracks the current visual number for smooth counting

    // Health Connect
    private HealthConnectManager healthConnectManager;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationHelper.createNotificationChannel(requireContext());
        healthConnectManager = new HealthConnectManager(requireContext());

        // FIX: Used standard RequestMultiplePermissions to avoid "Contract" errors
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) fetchHealthConnectData(); // Fetch Steps AND Heart Rate
                    else
                        Toast.makeText(getContext(), "Permissions required for auto-sync", Toast.LENGTH_SHORT).show();
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        reportViewModel = new androidx.lifecycle.ViewModelProvider(this).get(ReportViewModel.class);
        loadUserData();
        updateGreeting();

        // 1. Calculate Today's Date Key (YYYY-MM-DD)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        todayDateKey = sdf.format(new Date());

        // 2. CHECK & REQUEST HEALTH PERMISSIONS (Auto-Sync Steps & HR)
        checkHealthConnectPermissions();

        binding.btnExportPdf.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Gathering data & AI Insights...", Toast.LENGTH_SHORT).show();
            NotificationHelper.showDownloadProgress(requireContext());
            reportViewModel.generateFullReport();
        });

        reportViewModel.getReportData().observe(getViewLifecycleOwner(), reportData -> {
            if (reportData != null) {
                PdfReportManager.createReport(requireContext(), reportData);
                NotificationHelper.showDownloadComplete(requireContext());
            }
        });

        reportViewModel.getErrorState().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null) {
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        setupAdditionalView(view);
    }

    private void setupAdditionalView(View view) {

        appDao.getDailyHealth(todayDateKey).observe(getViewLifecycleOwner(), dailyHealth -> {
            if (dailyHealth == null) {
                DailyHealth newEntry = new DailyHealth(todayDateKey, 0, 2000, 0, "0h 0m", 0);
                executor.execute(() -> appDao.insertDailyHealth(newEntry));
                return;
            }
            currentDayData = dailyHealth;
            boolean shouldAnimate = displayedWaterAmount != 0;
            if (displayedWaterAmount == 0 && dailyHealth.getWaterIntakeMl() == 0)
                shouldAnimate = true;
            updateHydrationUI(shouldAnimate);
            updateStatsUI();
            updateMoodUI();
            loadWeeklyData();
            generateGeminiTip();

            executor.execute(() -> {
                List<DailyHealth> history = appDao.getLastSevenDaysSync();
                String tip = InsightEngine.getInsight(currentDayData, history);
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null) binding.tvTipText.setText(tip);
                    });
                }
            });
        });

        appDao.getLatestVital("Heart Rate").observe(getViewLifecycleOwner(), vital -> {
            if (binding == null) return;
            ViewGroup cardContent = (ViewGroup) binding.cardVitals.getChildAt(0);
            TextView tvRate = (TextView) cardContent.getChildAt(2);
            if (vital != null)
                tvRate.setText(MessageFormat.format("{0} {1}", vital.getValue1(), vital.getUnit()));
            else tvRate.setText("-- bpm");
        });

        appDao.getAllMedications().observe(getViewLifecycleOwner(), this::calculateNextMedicationSmart);

        setupHydrationChips();
        binding.chipGoal.setOnClickListener(v -> showSetGoalDialog());
        binding.btnResetHydration.setOnClickListener(v -> resetHydration());

        binding.cardMeds.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_medsFragment));
        binding.cardVitals.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_vitalsFragment));
        binding.imgProfile.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.action_home_to_profile));

        setupWeeklyGraph();
        setupMoodTracker();
        startHeartPulseAnimation();
    }

    private void calculateNextMedicationSmart(List<Medication> meds) {
        if (meds == null || meds.isEmpty()) {
            binding.tvNextMedName.setText("No Meds");
            binding.tvNextMedTime.setText("Add Prescription");
            return;
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Medication nextMed = null;
        long minDiffMillis = Long.MAX_VALUE;
        boolean hasActiveMeds = false;

        for (Medication m : meds) {
            // 1. Filter Expired (EndDate < Today)
            if (m.getEndDate() != null && !m.getEndDate().equalsIgnoreCase("Forever") && m.getEndDate().compareTo(today) < 0) {
                continue;
            }
            hasActiveMeds = true;

            // 2. Logic Check: Has this med been reset in DB today?
            // If the date in DB isn't today, it means the user hasn't opened MedsFragment yet.
            // For display purposes, we assume 0 taken today.
            int takenCount = 0;
            if (m.getLastActionDate() != null && m.getLastActionDate().equals(today)) {
                takenCount = m.getTimesTakenToday();
            }

            // 3. Check Completion
            if (takenCount >= m.getFrequencyPerDay()) {
                continue; // Done for today
            }

            // 4. Calculate Target Time based on Frequency
            // Logic: StartTime + (TakenCount * 6 hours)
            try {
                SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                Date startTime = sdfTime.parse(m.getTime());

                if (startTime != null) {
                    Calendar doseTime = Calendar.getInstance();
                    doseTime.setTime(startTime);

                    // Reset to today's date + parsed time
                    Calendar target = Calendar.getInstance();
                    target.set(Calendar.HOUR_OF_DAY, doseTime.get(Calendar.HOUR_OF_DAY));
                    target.set(Calendar.MINUTE, doseTime.get(Calendar.MINUTE));
                    target.set(Calendar.SECOND, 0);

                    // Add Interval (Standard 6 hours spacing for next dose)
                    // If user took 1 dose, the next one is 6 hours after Start Time.
                    int intervalHours = 6;
                    target.add(Calendar.HOUR_OF_DAY, takenCount * intervalHours);

                    // We prioritize the *earliest time today* that isn't done.
                    // Even if it's in the past (overdue), it's the next thing to do.
                    long timeOfDayMillis = target.getTimeInMillis();

                    if (timeOfDayMillis < minDiffMillis) {
                        minDiffMillis = timeOfDayMillis;
                        nextMed = m;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (nextMed != null) {
            binding.tvNextMedName.setText(nextMed.getName());

            // Re-calculate the display time for the UI
            try {
                int taken = 0;
                if (nextMed.getLastActionDate() != null && nextMed.getLastActionDate().equals(today)) {
                    taken = nextMed.getTimesTakenToday();
                }

                SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                Date baseTime = sdfTime.parse(nextMed.getTime());
                Calendar c = Calendar.getInstance();
                c.setTime(baseTime);
                c.add(Calendar.HOUR_OF_DAY, taken * 6); // Add spacing for display

                String displayTime = sdfTime.format(c.getTime());
                binding.tvNextMedTime.setText(displayTime);

            } catch (Exception e) {
                binding.tvNextMedTime.setText(nextMed.getTime());
            }

        } else if (hasActiveMeds) {
            // Active meds exist, but all taken
            binding.tvNextMedName.setText("All Done");
            binding.tvNextMedTime.setText("See you tomorrow");
        } else {
            // No active meds (all expired)
            binding.tvNextMedName.setText("No Active");
            binding.tvNextMedTime.setText("Prescriptions");
        }
    }

    // --- HEALTH CONNECT LOGIC ---

    private void checkHealthConnectPermissions() {
        if (healthConnectManager.isAvailable()) {
            Set<String> perms = healthConnectManager.getRequiredPermissions();

            // Convert Set to Array for the launcher
            String[] permsArray = perms.toArray(new String[0]);

            healthConnectManager.checkPermissions(perms).thenAccept(granted -> {
                if (granted.containsAll(perms)) {
                    fetchHealthConnectData();
                } else {
                    // Request missing permissions
                    permissionLauncher.launch(permsArray);
                }
            });
        }
    }

    private void fetchHealthConnectData() {
        if (!healthConnectManager.isAvailable()) return;

        // 1. Fetch Steps
        healthConnectManager.readTodaySteps(new HealthConnectManager.OnStepsReadListener() {
            @Override
            public void onStepsRead(long steps) {
                if (isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (currentDayData != null && steps > 0) {
                            currentDayData.setStepCount((int) steps);
                            executor.execute(() -> appDao.insertDailyHealth(currentDayData));
                        }
                    });
                }
            }

            @Override
            public void onError(Throwable t) {
            }
        });

        // 2. Fetch Heart Rate
        healthConnectManager.readAvgHeartRate(new HealthConnectManager.OnHeartRateReadListener() {
            @Override
            public void onHeartRateRead(long avgBpm) {
                if (avgBpm > 0 && isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                        Vital hrVital = new Vital("Heart Rate", String.valueOf(avgBpm), null, "bpm", date, System.currentTimeMillis(), "Health Connect", null);
                        executor.execute(() -> appDao.insertVital(hrVital));
                    });
                }
            }

            @Override
            public void onError(Throwable t) {
            }
        });
    }

    private void loadUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String name = user.getDisplayName();
            if (name != null && !name.isEmpty()) {
                binding.tvUserName.setText(name.split(" ")[0]);
            }
            if (user.getPhotoUrl() != null) {
                Glide.with(this)
                        .load(user.getPhotoUrl())
                        .placeholder(R.drawable.ic_user_dummy)
                        .error(R.drawable.ic_user_dummy)
                        .into(binding.imgProfile);
            }
        }
    }

    private void startHeartPulseAnimation() {
        ObjectAnimator scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                binding.imgHeartPulse,
                PropertyValuesHolder.ofFloat("scaleX", 1.15f),
                PropertyValuesHolder.ofFloat("scaleY", 1.15f));
        scaleDown.setDuration(600); // Speed of heartbeat
        scaleDown.setRepeatCount(ObjectAnimator.INFINITE);
        scaleDown.setRepeatMode(ObjectAnimator.REVERSE);
        scaleDown.start();
    }

    private void updateGreeting() {
        int timeOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        binding.tvGreeting.setText(
                timeOfDay < 12 ?
                        "Good Morning," :
                        timeOfDay < 16 ?
                                "Good Afternoon," :
                                "Good Evening,");
    }

    private void addWater(int amount) {
        if (currentDayData == null) return;

        int newTotal = currentDayData.getWaterIntakeMl() + amount;

        if (newTotal > currentDayData.getWaterGoalMl() + 2000) {
            triggerShakeError();
            Toast.makeText(getContext(), "Today's goal reached!", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDayData.setWaterIntakeMl(newTotal);
        saveToDb();
    }

    // UPDATED: Smooth animation logic for Ring, Drop, and Text
    private void updateHydrationUI(boolean animate) {
        if (currentDayData == null) return;

        int current = currentDayData.getWaterIntakeMl();
        int goal = currentDayData.getWaterGoalMl();

        // 1. ANIMATE TEXT (Smooth counting)
        if (animate && displayedWaterAmount != current) {
            animateTextCounter(displayedWaterAmount, current);
        } else {
            binding.tvIntakeAmount.setText(String.valueOf(current));
            displayedWaterAmount = current;
        }

        // 2. ANIMATE RING
        binding.progressHydration.setMax(goal);
        if (animate) {
            ObjectAnimator animation = ObjectAnimator.ofInt(binding.progressHydration, "progress", binding.progressHydration.getProgress(), Math.min(current, goal));
            animation.setDuration(1000); // 1 Second
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();
        } else {
            binding.progressHydration.setProgress(Math.min(current, goal));
        }

        // 3. ANIMATE WATER DROP (Added 'animate' parameter)
        binding.waterDropView.setProgress((float) current, (float) goal, animate);

        binding.btnResetHydration.setVisibility(View.VISIBLE);
        // 4. Goal Logic
        if (current >= goal) {
            int extra = current - goal;
            String goalText = (extra > 0) ? "Goal Reached! (+" + extra + "ml)" : "Goal Reached!";
            binding.chipGoal.setText(goalText);
            binding.chipGoal.setTextColor(Color.parseColor("#00897B"));
            binding.chipGoal.setChipIconResource(R.drawable.ic_check_circle);
            binding.chipGoal.setChipIconTint(ColorStateList.valueOf(Color.parseColor("#00897B")));
            binding.chipGoal.setClickable(false);

            if (!goalReachedTriggered && animate) {
                playCelebration();
                goalReachedTriggered = true;
            }
        } else {
            binding.chipGoal.setText("Goal: " + goal + "ml");
            binding.chipGoal.setTextColor(Color.parseColor("#546E7A"));
            binding.chipGoal.setChipIconResource(R.drawable.ic_edit);
            binding.chipGoal.setChipIconTint(ColorStateList.valueOf(Color.parseColor("#90A4AE")));
            binding.chipGoal.setClickable(true);
        }
    }

    // NEW: Helper method to count numbers smoothly
    private void animateTextCounter(int start, int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(1000); // Syncs with the water drop animation duration
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            binding.tvIntakeAmount.setText(String.valueOf(val));
        });
        animator.start();
        displayedWaterAmount = end; // Update tracked value
    }

    private void setupMoodTracker() {
        ImageView[] moods = {binding.mood1, binding.mood2, binding.mood3, binding.mood4, binding.mood5};
        for (int i = 0; i < moods.length; i++) {
            int finalI = i;
            moods[i].setOnClickListener(v -> {
                if (currentDayData != null) {
                    currentDayData.setMoodRating(finalI + 1);
                    saveToDb();
                }
            });
        }
    }

    private void updateMoodUI() {
        if (currentDayData == null) return;
        ImageView[] moods = {binding.mood1, binding.mood2, binding.mood3, binding.mood4, binding.mood5};
        int[] colors = {0xFFEF9A9A, 0xFFFFCC80, 0xFFFFF59D, 0xFFA5D6A7, 0xFF66BB6A};

        int selectedIndex = currentDayData.getMoodRating() - 1;

        for (int i = 0; i < moods.length; i++) {
            ImageView m = moods[i];
            if (i == selectedIndex) {
                m.setColorFilter(colors[i]);
                m.animate().scaleX(1.3f).scaleY(1.3f).setDuration(200).start();
            } else {
                m.setColorFilter(Color.parseColor("#CFD8DC"));
                m.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
            }
        }
    }

    private void setupWeeklyGraph() {
        BarChart chart = binding.chartWeekly;

        // 1. General Styling
        chart.setDrawBarShadow(false);
        chart.setDrawValueAboveBar(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false); // Clean look
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setScaleEnabled(false);
        chart.setExtraBottomOffset(10f);

        // 2. X-Axis (Days)
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(Color.parseColor("#90A4AE"));
        xAxis.setTextSize(12f);
        xAxis.setGranularity(1f);

        // 3. Y-Axis (Left - Amounts)
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setEnabled(false); // Hide Y-Axis for minimal look
        chart.getAxisLeft().setAxisMinimum(0f);

        // 4. Empty State
        chart.setNoDataText("Loading history...");
        chart.setNoDataTextColor(Color.parseColor("#00897B"));
    }

    private void loadWeeklyData() {
        executor.execute(() -> {
            List<DailyHealth> history = appDao.getLastSevenDaysSync();

            // Reverse list so oldest day is first (Left to Right)
            if (history != null) Collections.reverse(history);

            ArrayList<BarEntry> entries = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();

            int index = 0;
            if (history != null && !history.isEmpty()) {
                for (DailyHealth day : history) {
                    // Y-Value: Water Intake
                    entries.add(new BarEntry(index, day.getWaterIntakeMl()));

                    // X-Label: Day Name (e.g., "Mon")
                    try {
                        SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        SimpleDateFormat outFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                        Date date = inFormat.parse(day.getDate());
                        if (date != null) {
                            labels.add(outFormat.format(date));
                        }
                    } catch (Exception e) {
                        labels.add("?");
                    }
                    index++;
                }
            }

            // Update UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> updateChartUI(entries, labels));
            }
        });
    }

    private void updateChartUI(ArrayList<BarEntry> entries, ArrayList<String> labels) {
        if (binding == null) return;

        BarDataSet set;
        if (binding.chartWeekly.getData() != null &&
                binding.chartWeekly.getData().getDataSetCount() > 0) {
            set = (BarDataSet) binding.chartWeekly.getData().getDataSetByIndex(0);
            set.setValues(entries);
            binding.chartWeekly.getData().notifyDataChanged();
            binding.chartWeekly.notifyDataSetChanged();
        } else {
            set = new BarDataSet(entries, "Hydration");
            set.setColor(Color.parseColor("#00897B")); // Brand Teal
            set.setDrawValues(false); // Hide numbers on bars
            set.setHighLightAlpha(0); // Disable tap highlight

            BarData data = new BarData(set);
            data.setBarWidth(0.5f); // Slimmer bars
            binding.chartWeekly.setData(data);
        }

        // Set X-Axis Labels
        binding.chartWeekly.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));

        // Refresh & Animate
        binding.chartWeekly.invalidate();
        binding.chartWeekly.animateY(1000);
    }

    private void resetHydration() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reset_confirmation, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        dialogView.findViewById(R.id.btnCancelReset).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirmReset).setOnClickListener(v -> {
            currentDayData.setWaterIntakeMl(0);
            goalReachedTriggered = false;
            saveToDb();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void saveToDb() {
        if (currentDayData != null) {
            executor.execute(() -> {
                appDao.insertDailyHealth(currentDayData);
                if (getContext() != null) {
                    WidgetUpdateHelper.updateWidgets(getContext());
                }
            });
        }
    }

    private void updateStatsUI() {
        if (currentDayData == null) return;
        ViewGroup stepCard = (ViewGroup) binding.cardSteps.getChildAt(0);
        TextView tvSteps = (TextView) stepCard.getChildAt(2);
        tvSteps.setText(String.format(Locale.US, "%,d", currentDayData.getStepCount()));

        ViewGroup sleepCard = (ViewGroup) binding.cardSleep.getChildAt(0);
        TextView tvSleep = (TextView) sleepCard.getChildAt(2);
        tvSleep.setText(currentDayData.getSleepDuration());
    }

    private void showSetGoalDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_set_goal, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etGoal = dialogView.findViewById(R.id.etGoalAmount);
        etGoal.setText(String.valueOf(currentDayData.getWaterGoalMl()));

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSaveGoal).setOnClickListener(v -> {
            String valStr = etGoal.getText().toString().trim();
            if (!valStr.isEmpty()) {
                int newGoal = Integer.parseInt(valStr);
                if (newGoal >= 500 && newGoal <= 7000) {
                    currentDayData.setWaterGoalMl(newGoal);
                    saveToDb();
                    dialog.dismiss();
                } else etGoal.setError("500-7000ml");
            }
        });
        dialog.show();
    }

    private void setupHydrationChips() {
        binding.chip250.setOnClickListener(v -> addWater(250));
        binding.chip500.setOnClickListener(v -> addWater(500));
        binding.chip750.setOnClickListener(v -> addWater(750));
        binding.chipCustom.setOnClickListener(v -> showCustomWaterDialog());
    }

    private void showCustomWaterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_water, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText etAmount = dialogView.findViewById(R.id.etCustomAmount);
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnAdd).setOnClickListener(v -> {
            String valueStr = etAmount.getText().toString().trim();
            if (!valueStr.isEmpty()) {
                int amount = Integer.parseInt(valueStr);
                addWater(amount);
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void playCelebration() {
        binding.lottieCelebration.setVisibility(View.VISIBLE);
        binding.lottieCelebration.playAnimation();
        binding.lottieCelebration.addAnimatorListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator a) {
            }

            @Override
            public void onAnimationEnd(@NonNull Animator a) {
                binding.lottieCelebration.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(@NonNull Animator a) {
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator a) {
            }
        });
    }

    private void triggerShakeError() {
        TranslateAnimation shake = new TranslateAnimation(0, 10, 0, 0);
        shake.setDuration(500);
        shake.setInterpolator(new CycleInterpolator(5));
        binding.cardHydration.startAnimation(shake);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void generateGeminiTip() {
        if (currentDayData == null) return;

        // 1. Show Loading State
        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        String todayDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String savedDate = prefs.getString("daily_tip_date", "");
        String savedTip = prefs.getString("daily_tip_content", "");

        if (savedDate.equals(todayDateKey) && !savedTip.isEmpty()) {
            // Use cached tip (Instant, no API call)
            binding.tvTipText.setText(savedTip);
            return;
        }

        binding.tvTipText.setText("Asking AI Coach...");

        // 2. Prepare Data String
        String stats = String.format(Locale.US,
                "Water: %dml/%dml, Steps: %d, Sleep: %s, Mood: %d/5",
                currentDayData.getWaterIntakeMl(), currentDayData.getWaterGoalMl(),
                currentDayData.getStepCount(), currentDayData.getSleepDuration(),
                currentDayData.getMoodRating()
        );

        // 3. Call Repository
        geminiRepository.getDailyTip(stats, new GeminiRepository.GeminiCallback() {
            @Override
            public void onSuccess(String tip) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        prefs.edit()
                                .putString("daily_tip_date", todayDateKey)
                                .putString("daily_tip_content", tip)
                                .apply();
                        binding.tvTipText.setText(tip);
                    });
                }
            }

            @Override
            public void onError(Throwable t) {
                // Fallback to local static insight on error
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String fallback = InsightEngine.getInsight(currentDayData, null);
                        binding.tvTipText.setText(fallback);
                    });
                }
            }
        });
    }
}