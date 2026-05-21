package com.vitalflow.app.ui.home;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.speech.RecognizerIntent;
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
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

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
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.FragmentHomeBinding;
import com.vitalflow.app.ui.report.ReportViewModel;
import com.vitalflow.app.ui.story.WrappedDialogFragment;
import com.vitalflow.app.utils.BitmapUtils;
import com.vitalflow.app.utils.DataExportHelper;
import com.vitalflow.app.utils.DataImportHelper;
import com.vitalflow.app.utils.HealthConnectManager;
import com.vitalflow.app.utils.InsightEngine;
import com.vitalflow.app.utils.NotificationHelper;
import com.vitalflow.app.utils.PdfReportManager;
import com.vitalflow.app.utils.UiUtils;
import com.vitalflow.app.utils.WidgetUpdateHelper;
import com.vitalflow.app.workers.HealthSyncWorker;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class HomeFragment extends Fragment implements SensorEventListener {

    private HomeViewModel viewModel;
    private GeminiRepository geminiRepository;

    ReportViewModel reportViewModel;

    private FragmentHomeBinding binding;

    private final CompositeDisposable disposables = new CompositeDisposable();

    // Data State
    private DailyHealth currentDayData;
    private String todayDateKey;
    private boolean goalReachedTriggered = false;

    // Animation State
    private int displayedWaterAmount = 0; // Tracks the current visual number for smooth counting

    // Health Connect
    private HealthConnectManager healthConnectManager;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private boolean isSensorPresent = false;
    private int sensorStepsToday = 0;

    // Food Lens
    private FoodLogAdapter foodAdapter;
    private Uri currentPhotoUri; // To store the camera photo URI temporarily
    private AlertDialog loadingDialog;

    // Launcher for Gallery Picker
    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    // Launcher for Camera
    private ActivityResultLauncher<Uri> takePictureLauncher;
    // Launcher for Camera Permission
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;

    private ActivityResultLauncher<String> requestStepPermissionLauncher;
    private ActivityResultLauncher<String[]> requestHealthConnectLauncher;

    private ActivityResultLauncher<Intent> speechLauncher;
    private List<Medication> activeMedsList = new ArrayList<>();

    private ActivityResultLauncher<String[]> documentPickerLauncher;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getContext() != null) {
            NotificationHelper.createNotificationChannel(requireContext());
            healthConnectManager = new HealthConnectManager(requireContext());

            sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                isSensorPresent = (stepSensor != null);
            }
        }
        initLaunchers();
    }

    private void initLaunchers() {
        // Media Picker
        pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) analyzeFood(uri);
        });

        // Camera Launcher
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && currentPhotoUri != null) analyzeFood(currentPhotoUri);
        });

        // --- SPECIFIC CAMERA PERMISSION LAUNCHER ---
        requestCameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                launchCamera();
            } else {
                Toast.makeText(getContext(), "Camera permission needed to scan food", Toast.LENGTH_SHORT).show();
            }
        });

        // --- SPECIFIC STEP PERMISSION LAUNCHER ---
        requestStepPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
            }
        });

        // Health Connect Permissions
        requestHealthConnectLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) fetchHealthConnectData();
                    else
                        Toast.makeText(getContext(), "Permissions needed to sync watch data", Toast.LENGTH_SHORT).show();
                });

        // Speech-to-Text Launcher
        speechLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0).toLowerCase(java.util.Locale.ROOT);
                            processVoiceCommand(spokenText);
                        }
                    }
                });

        // Excel Document Picker Launcher
        documentPickerLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                showLoadingDialog();
                DataImportHelper.restoreFromExcel(requireContext(), uri, viewModel.getAppDao(), new DataImportHelper.ImportCallback() {
                    @Override
                    public void onSuccess(int itemsRestored) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            dismissLoadingDialog();
                            Toast.makeText(getContext(), "Restored " + itemsRestored + " medications! 🎉", Toast.LENGTH_LONG).show();
                            WidgetUpdateHelper.updateWidgets(getContext()); // Refresh widgets
                        });
                    }

                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            dismissLoadingDialog();
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Restore Failed")
                                    .setMessage(error)
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
                    }
                });
            }
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
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        geminiRepository = viewModel.getGeminiRepository();
        reportViewModel = new ViewModelProvider(this).get(ReportViewModel.class);
        loadUserData();
        updateGreeting();

        // 1. Calculate Today's Date Key (YYYY-MM-DD)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        todayDateKey = sdf.format(new Date());
        viewModel.setDate(todayDateKey);

        displayStreak();

        // 2. CHECK & REQUEST HEALTH PERMISSIONS (Auto-Sync Steps & HR)
        checkHealthConnectPermissions();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStepPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }

        setupButtons();
        checkAndShowMenuHint();
        setupAdditionalView(view);

        Calendar cal = Calendar.getInstance();
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        if (dayOfMonth <= 3) {
            binding.cardWrappedTrigger.setVisibility(View.VISIBLE);
            binding.cardWrappedTrigger.setOnClickListener(v -> {
                new WrappedDialogFragment().show(getParentFragmentManager(), "Wrapped");
            });
        }
    }

    private void checkAndShowMenuHint() {
        SharedPreferences prefs = requireActivity().getPreferences(android.content.Context.MODE_PRIVATE);
        boolean hintShown = prefs.getBoolean("is_first_time_menu_hint_shown", false);
        if (!hintShown) {
            startProfileHintAnimation();
            prefs.edit().putBoolean("is_first_time_menu_hint_shown", true).apply();
        }
    }

    private void generateAndShareCaregiverReport() {
        showLoadingDialog(); // Reuse your existing loading dialog!

        // 1. Fetch the last 7 days of health data
        disposables.add(viewModel.getLastSevenDaysSync()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(history -> {

                    // 2. Fetch their active medications
                    disposables.add(viewModel.getAllMedicationsSync()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(meds -> {
                                dismissLoadingDialog();
                                formatAndShareReport(history, meds);
                            }, e -> {
                                dismissLoadingDialog();
                                Toast.makeText(getContext(), "Error gathering meds", Toast.LENGTH_SHORT).show();
                            }));
                }, e -> {
                    dismissLoadingDialog();
                    Toast.makeText(getContext(), "Error gathering history", Toast.LENGTH_SHORT).show();
                }));
    }

    private void formatAndShareReport(List<DailyHealth> history, List<Medication> meds) {
        StringBuilder report = new StringBuilder();
        String dateStr = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());

        report.append("🩺 VitalFlow Health Summary\n");
        report.append("Generated on: ").append(dateStr).append("\n\n");

        // Section 1: Medications
        report.append("💊 ACTIVE MEDICATIONS:\n");
        if (meds == null || meds.isEmpty()) {
            report.append("- None logged.\n");
        } else {
            for (Medication m : meds) {
                report.append("- ").append(m.getName())
                        .append(" (").append(m.getDosage()).append(") ")
                        .append(" | ").append(m.getFrequencyPerDay()).append("x Daily\n");
            }
        }
        report.append("\n");

        // Section 2: Weekly Averages
        report.append("📊 7-DAY TRENDS:\n");
        if (history == null || history.isEmpty()) {
            report.append("- Not enough data logged yet.\n");
        } else {
            int totalWater = 0, totalSteps = 0;
            for (DailyHealth dh : history) {
                totalWater += dh.getWaterIntakeMl();
                totalSteps += dh.getStepCount();
            }
            int avgWater = totalWater / history.size();
            int avgSteps = totalSteps / history.size();

            report.append("- Avg Hydration: ").append(avgWater).append(" ml/day\n");
            report.append("- Avg Steps: ").append(avgSteps).append(" steps/day\n");

            // Just grab the most recent night's sleep
            if (currentDayData != null && currentDayData.getSleepDuration() != null) {
                report.append("- Recent Sleep: ").append(currentDayData.getSleepDuration()).append("\n");
            }
        }

        report.append("\nShared securely via the VitalFlow App.");

        // 3. Trigger Android's Native Share Sheet
        android.content.Intent sendIntent = new android.content.Intent();
        sendIntent.setAction(android.content.Intent.ACTION_SEND);
        sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, report.toString());
        sendIntent.setType("text/plain");

        android.content.Intent shareIntent = android.content.Intent.createChooser(sendIntent, "Share Medical Summary with...");
        startActivity(shareIntent);
    }

    private void startProfileHintAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(binding.imgProfile, "scaleX", 1.0f, 1.06f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(binding.imgProfile, "scaleY", 1.0f, 1.06f, 1.0f);

        scaleX.setDuration(2000);
        scaleY.setDuration(2000);

        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setRepeatMode(ObjectAnimator.RESTART);
        scaleY.setRepeatMode(ObjectAnimator.RESTART);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.setStartDelay(500);
        animatorSet.start();
    }

    private void setupButtons() {

        binding.btnMoreMenu.setOnClickListener(v -> {
            HomeMenuBottomSheet bottomSheet = new HomeMenuBottomSheet();
            bottomSheet.setMenuClickListener(new HomeMenuBottomSheet.MenuClickListener() {
                @Override
                public void onExportPdfClicked() {
                    Toast.makeText(getContext(), "Gathering data for PDF...", Toast.LENGTH_SHORT).show();
                    NotificationHelper.showDownloadProgress(requireContext());
                    reportViewModel.generateFullReport();
                }

                @Override
                public void onExportExcelClicked() {
                    SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
                    boolean isPro = prefs.getBoolean("is_pro_member", false);
                    if (isPro) {
                        Toast.makeText(getContext(), "Generating Excel Backup...", Toast.LENGTH_SHORT).show();
                        DataExportHelper.exportData(requireContext(), viewModel.getAppDao());
                    } else {

                        showUpgradeDialog();
                    }
                }

                @Override
                public void onShareClicked() {
                    generateAndShareCaregiverReport();
                }

                @Override
                public void onRestoreClicked() {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Restore Data")
                            .setMessage("Select a previously exported VitalFlow Excel (.xlsx) file to restore your medications.")
                            .setPositiveButton("Select File", (dialog, which) -> {
                                documentPickerLauncher.launch(new String[]{
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel"
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });

            bottomSheet.show(getChildFragmentManager(), "HomeMenuSheet");
        });

        reportViewModel.getReportData().observe(getViewLifecycleOwner(), reportData -> {
            if (reportData != null) {
                Uri pdfUri = PdfReportManager.createReport(requireContext(), reportData);
                if (pdfUri != null) {
                    NotificationHelper.showDownloadComplete(requireContext(), pdfUri);
                } else {
                    Toast.makeText(getContext(), "Failed to save report.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        reportViewModel.getErrorState().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null) {
                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showUpgradeDialog() {
        String[] features = {
                "📊  Export Data to Excel (Doctor Ready)",
                "☁️  Secure Cloud Backup & Sync",
                "🤖  Advanced AI Health Insights",
                "📈  Unlimited History Trends",
                "🚫  Ad-Free Experience"
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unlock VitalFlow Premium 👑")
                .setItems(features, null)
                .setPositiveButton("Subscribe - $4.99/mo", (dialog, which) -> {
                    // MOCK PURCHASE SUCCESS
                    SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putBoolean("is_pro_member", true).apply();

                    playCelebration(); // Pop the confetti on the Home Screen!
                    Toast.makeText(getContext(), "Welcome to Pro! You can now export to Excel.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Maybe Later", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isSensorPresent && sensorManager != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isSensorPresent && sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float totalStepsSinceReboot = event.values[0];

            SharedPreferences prefs = requireContext().getSharedPreferences("step_prefs", Context.MODE_PRIVATE);
            String savedDate = prefs.getString("date", "");
            float stepsAtMidnight = prefs.getFloat("steps_at_midnight", -1);

            if (!savedDate.equals(todayDateKey)) {
                stepsAtMidnight = totalStepsSinceReboot;
                prefs.edit().putString("date", todayDateKey).putFloat("steps_at_midnight", stepsAtMidnight).apply();
            } else if (stepsAtMidnight == -1) {
                stepsAtMidnight = totalStepsSinceReboot;
                prefs.edit().putFloat("steps_at_midnight", stepsAtMidnight).apply();
            }

            if (totalStepsSinceReboot < stepsAtMidnight) {
                stepsAtMidnight = 0;
                prefs.edit().putFloat("steps_at_midnight", 0).apply();
            }

            int nativeSteps = (int) (totalStepsSinceReboot - stepsAtMidnight);
            if (nativeSteps < 0) nativeSteps = 0;

            sensorStepsToday = nativeSteps;
            updateStepsIfHigher(nativeSteps);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateStepsIfHigher(int newStepCount) {
        if (currentDayData != null) {
            if (newStepCount > currentDayData.getStepCount()) {
                currentDayData.setStepCount(newStepCount);
                updateStatsUI();
                saveToDb();
            } else if (Math.abs(newStepCount - currentDayData.getStepCount()) < 5) {
                currentDayData.setStepCount(newStepCount);
                updateStatsUI();
            }
        }
    }

    private void updateHealthPetUI() {
        if (currentDayData == null || binding == null) return;

        int water = currentDayData.getWaterIntakeMl();
        int waterGoal = currentDayData.getWaterGoalMl();
        int steps = currentDayData.getStepCount();

        // Calculate progress percentage safely
        float waterPercent = waterGoal > 0 ? (float) water / waterGoal : 0;

        String emoji;
        String status;

        if (waterPercent >= 1.0f || steps >= 8000) {
            emoji = "🌟"; // Thriving!
            status = "Thriving! You're crushing it today!";

            // Add a fun little continuous bounce animation
            binding.tvPetEmoji.animate()
                    .translationY(-15f)
                    .setDuration(400)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (binding != null) {
                            binding.tvPetEmoji.animate().translationY(0f).setDuration(400).start();
                        }
                    }).start();

        } else if (waterPercent >= 0.4f || steps >= 3000) {
            emoji = "🌱"; // Growing
            status = "Growing steady! Keep up the good work.";
            binding.tvPetEmoji.setTranslationY(0f); // Reset animation
        } else {
            emoji = "🥀"; // Needs attention
            status = "Feeling sluggish. Feed me water or steps!";
            binding.tvPetEmoji.setTranslationY(0f); // Reset animation
        }

        binding.tvPetEmoji.setText(emoji);
        binding.tvPetStatus.setText(status);
    }

    private void setupAdditionalView(View view) {
        startHomeStatsShimmer();

        viewModel.getDailyHealth().observe(getViewLifecycleOwner(), dailyHealth -> {
            stopHomeStatsShimmer();
            if (dailyHealth == null) {
                DailyHealth newEntry = new DailyHealth(todayDateKey, 0, 2000, 0, "0h 0m", 0);
                viewModel.insertDailyHealth(newEntry);
                return;
            }
            currentDayData = dailyHealth;

            if (sensorStepsToday > currentDayData.getStepCount()) {
                currentDayData.setStepCount(sensorStepsToday);
                saveToDb();
            }

            boolean shouldAnimate = (displayedWaterAmount == 0 && dailyHealth.getWaterIntakeMl() > 0);
            updateHydrationUI(shouldAnimate);
            updateStatsUI();
            updateMoodUI();
            updateHealthPetUI();

            loadWeeklyData();
            generateGeminiTip();

            disposables.add(viewModel.getLastSevenDaysSync()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(history -> {
                        if (binding == null) return;
                        String tip = InsightEngine.getInsight(currentDayData, history);
                        if (isAdded()) binding.tvTipText.setText(tip);
                    }, Throwable::printStackTrace));
        });

        viewModel.getLatestHeartRate().observe(getViewLifecycleOwner(), vital -> {
            if (binding == null) return;
            if (vital != null)
                binding.tvHeartRateValue.setText(MessageFormat.format("{0} {1}", vital.getValue1(), vital.getUnit()));
            else binding.tvHeartRateValue.setText("-- bpm");
        });

        viewModel.getAllMedications().observe(getViewLifecycleOwner(), meds -> {
            this.activeMedsList = meds;
            calculateNextMedicationSmart(meds);
            updateAdherenceCard(meds);
        });

        binding.btnVoiceLog.setOnClickListener(v -> launchSpeechRecognizer());

        setupHydrationChips();
        setupWaterReminder();
        binding.chipGoal.setOnClickListener(v -> showSetGoalDialog());
        binding.btnResetHydration.setOnClickListener(v -> resetHydration());

        binding.cardMeds.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_medsFragment));
        binding.cardVitals.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_vitalsFragment));
        binding.imgProfile.setOnClickListener(v -> Navigation.findNavController(view).navigate(R.id.action_home_to_profile));

        setupWeeklyGraph();
        setupMoodTracker();
        startHeartPulseAnimation();
        setupNutritionSection();
    }

    private void launchSpeechRecognizer() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you just do? (e.g., 'I drank water and took my vitamins')");
            speechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void processVoiceCommand(String spokenText) {
        showLoadingDialog();

        geminiRepository.analyzeVoiceCommand(spokenText, activeMedsList, new GeminiRepository.VoiceCommandCallback() {
            @Override
            public void onSuccess(List<GeminiRepository.VoiceAction> actions) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    dismissLoadingDialog();
                    int actionsLogged = 0;
                    StringBuilder summary = new StringBuilder("Logged: ");

                    for (GeminiRepository.VoiceAction a : actions) {
                        if ("WATER".equals(a.action)) {
                            addWater(a.amount_ml);
                            summary.append(a.amount_ml).append("ml Water, ");
                            actionsLogged++;
                        } else if ("FOOD".equals(a.action)) {
                            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                            FoodLog log = new FoodLog(todayDateKey, time, a.food_name, a.calories, a.macros);
                            viewModel.insertFoodLog(log);
                            summary.append(a.food_name).append(", ");
                            actionsLogged++;
                        } else if ("MEDICATION".equals(a.action)) {
                            // Find the med and update it
                            for (Medication m : activeMedsList) {
                                if (m.getId().equals(a.med_id)) {
                                    m.setTimesTakenToday(m.getTimesTakenToday() + 1);
                                    if (m.getPillsLeft() > 0) m.setPillsLeft(m.getPillsLeft() - 1);
                                    viewModel.insertMedication(m);
                                    summary.append(m.getName()).append(", ");
                                    actionsLogged++;
                                    break;
                                }
                            }
                        }
                    }

                    if (actionsLogged > 0) {
                        // Clean up the trailing comma
                        String finalSummary = summary.substring(0, summary.length() - 2);
                        UiUtils.showCustomSnackbar(binding.getRoot(), requireContext(), finalSummary,
                                ContextCompat.getColor(requireContext(), R.color.primary),
                                ContextCompat.getColor(requireContext(), R.color.text_on_primary_soft),
                                R.drawable.ic_check_circle,
                                binding.btnScanFood, v -> {
                                });
                        playCelebration();
                    } else {
                        Toast.makeText(getContext(), "AI couldn't find any health actions.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        dismissLoadingDialog();
                        Toast.makeText(getContext(), "Voice logging failed.", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void setupNutritionSection() {
        foodAdapter = new FoodLogAdapter();
        binding.rvFoodLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFoodLogs.setAdapter(foodAdapter);

        // 1. Load Logs
        viewModel.getFoodLogs().observe(getViewLifecycleOwner(), logs -> {
            if (binding == null) return;
            foodAdapter.submitList(logs);
            binding.rvFoodLogs.setVisibility(logs.isEmpty() ? View.GONE : View.VISIBLE);
        });

        // 2. Load Total Calories
        viewModel.getTotalCalories().observe(getViewLifecycleOwner(), total -> {
            if (binding == null) return;
            int cal = total != null ? total : 0;
            binding.tvCaloriesTotal.setText(MessageFormat.format("{0} kcal", cal));
        });

        // 3. Scan Button Action
        binding.btnScanFood.setOnClickListener(v -> showImageSourceDialog());

        setupSwipeToDelete();
    }

    private void showImageSourceDialog() {
        String[] options = {"Take Photo 📸", "Choose from Gallery 🖼️"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Log Nutrition")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkCameraPermissionAndLaunch();
                    } else {
                        pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    }
                })
                .show();
    }

    private void displayStreak() {
        if (binding == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        int currentStreak = prefs.getInt("streak_count", 0);
        String lastStreakDate = prefs.getString("last_streak_date", "");

        // Check if they missed yesterday. If so, visually reset the streak to 0.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        if (!lastStreakDate.equals(todayDateKey) && !lastStreakDate.equals(yesterday) && !lastStreakDate.isEmpty()) {
            currentStreak = 0;
        }

        binding.tvStreakCount.setText("🔥 " + currentStreak + " Days");
    }

    private void updateStreakCount() {
        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        String lastStreakDate = prefs.getString("last_streak_date", "");
        int currentStreak = prefs.getInt("streak_count", 0);

        // If they haven't already earned it today
        if (!lastStreakDate.equals(todayDateKey)) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

            if (lastStreakDate.equals(yesterday)) {
                currentStreak++;
            } else {
                currentStreak = 1;
            }

            prefs.edit()
                    .putString("last_streak_date", todayDateKey)
                    .putInt("streak_count", currentStreak)
                    .apply();

            displayStreak();
        }
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            // Create a temporary file
            File photoFile = File.createTempFile("food_scan_", ".jpg", requireContext().getExternalCacheDir());

            // Get URI using FileProvider
            currentPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    photoFile
            );

            takePictureLauncher.launch(currentPhotoUri);
        } catch (IOException e) {
            Toast.makeText(getContext(), "Error creating image file", Toast.LENGTH_SHORT).show();
        }
    }

    private void analyzeFood(Uri imageUri) {
        showLoadingDialog();

        disposables.add(
                Single.fromCallable(() -> {
                            Bitmap bitmap = BitmapUtils.decodeSampledBitmap(
                                    requireContext().getContentResolver(), imageUri, 800, 800);
                            if (bitmap == null) throw new IOException("Image decode failed");
                            return bitmap;
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                bitmap -> geminiRepository.analyzeFoodImage(bitmap, new GeminiRepository.FoodAnalysisCallback() {
                                    @Override
                                    public void onSuccess(GeminiRepository.FoodAnalysisResult result) {
                                        if (getActivity() != null)
                                            getActivity().runOnUiThread(() -> {
                                                dismissLoadingDialog();
                                                showConfirmFoodDialog(result);
                                            });
                                    }

                                    @Override
                                    public void onError(Throwable t) {
                                        if (getActivity() != null)
                                            getActivity().runOnUiThread(() -> {
                                                dismissLoadingDialog();
                                                Toast.makeText(getContext(), "AI Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                            });
                                    }
                                }),
                                error -> {
                                    dismissLoadingDialog();
                                    Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                                }
                        )
        );
    }

    private void showLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        builder.setView(view);
        builder.setCancelable(false);

        loadingDialog = builder.create();
        if (loadingDialog.getWindow() != null) {
            loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void showConfirmFoodDialog(GeminiRepository.FoodAnalysisResult result) {
        String summary = "Calories: " + result.calories + "\n" + result.macros;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(result.food_name)
                .setMessage("Is this correct?\n\n" + summary)
                .setIcon(R.drawable.ic_check_circle) // Ensure you have this icon
                .setPositiveButton("Add to Log", (dialog, which) -> {

                    String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

                    FoodLog log = new FoodLog(
                            todayDateKey,
                            time,
                            result.food_name,
                            result.calories,
                            result.macros
                    );

                    viewModel.insertFoodLog(log);
                    Toast.makeText(getContext(), "Food Logged! ✅", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Retake", (dialog, which) -> {
                    showImageSourceDialog();
                })
                .show();
    }

    private void setupWaterReminder() {
        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);

        binding.btnWaterReminder.setOnClickListener(v -> {
            String[] options = {"Off (No Reminders)", "Every 30 Mins", "Every 1 Hour", "Every 2 Hours", "Custom..."};
            int[] predefinedIntervals = {0, 30, 60, 120};

            int currentInterval = prefs.getInt("water_reminder_interval", 0);
            int checkedItem = 4;
            for (int i = 0; i < predefinedIntervals.length; i++) {
                if (predefinedIntervals[i] == currentInterval) {
                    checkedItem = i;
                    break;
                }
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Water Reminders")
                    .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                        dialog.dismiss();

                        if (which < 4) {
                            int selectedInterval = predefinedIntervals[which];
                            prefs.edit().putInt("water_reminder_interval", selectedInterval).apply();

                            if (selectedInterval == 0) {
                                NotificationHelper.cancelReminders(requireContext());
                                Toast.makeText(getContext(), "Water reminders turned off", Toast.LENGTH_SHORT).show();
                            } else {
                                NotificationHelper.scheduleDailyReminder(requireContext(), selectedInterval);
                                String timeLabel = selectedInterval >= 60 ? (selectedInterval / 60) + " hours" : selectedInterval + " mins";
                                Toast.makeText(getContext(), "Reminding every " + timeLabel, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            showCustomReminderDialog(prefs);
                        }
                    })
                    .show();
        });
    }

    private void showCustomReminderDialog(SharedPreferences prefs) {
        // 1. Create a Horizontal Layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER);

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        int textColorSecondary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary);

        // 2. Build the "Hours" Scroll Wheel
        android.widget.NumberPicker hourPicker = new android.widget.NumberPicker(requireContext());
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(24);
        // This magic line prevents the keyboard from popping up if they tap the numbers!
        hourPicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        TextView tvHours = new TextView(requireContext());
        tvHours.setText(" hrs   ");
        tvHours.setTextSize(16f);
        tvHours.setTextColor(textColorSecondary);

        // 3. Build the "Minutes" Scroll Wheel
        android.widget.NumberPicker minPicker = new android.widget.NumberPicker(requireContext());
        minPicker.setMinValue(0);
        minPicker.setMaxValue(59);
        minPicker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        TextView tvMins = new TextView(requireContext());
        tvMins.setText(" mins");
        tvMins.setTextSize(16f);
        tvMins.setTextColor(textColorSecondary);

        // 4. Add them to the layout
        layout.addView(hourPicker);
        layout.addView(tvHours);
        layout.addView(minPicker);
        layout.addView(tvMins);

        // 5. Pre-fill with saved data
        int currentTotalMins = prefs.getInt("water_reminder_interval", 0);
        if (currentTotalMins > 0) {
            hourPicker.setValue(currentTotalMins / 60);
            minPicker.setValue(currentTotalMins % 60);
        } else {
            // Default to 1 hr 0 mins if they haven't set anything yet
            hourPicker.setValue(1);
            minPicker.setValue(0);
        }

        // 6. Show the Dialog
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Custom Interval")
                .setMessage("Remind me to drink water every:")
                .setView(layout)
                .setPositiveButton("Save", (dialog, whichButton) -> {

                    int hours = hourPicker.getValue();
                    int minutes = minPicker.getValue();
                    int totalMinutes = (hours * 60) + minutes;

                    // Ensure they pick at least 15 minutes to prevent notification spam
                    if (totalMinutes >= 15 && totalMinutes <= 1440) {
                        prefs.edit().putInt("water_reminder_interval", totalMinutes).apply();
                        NotificationHelper.scheduleDailyReminder(requireContext(), totalMinutes);

                        String timeLabel = (hours > 0 ? hours + " hrs " : "") + (minutes > 0 ? minutes + " mins" : "");
                        Toast.makeText(getContext(), "Reminding every " + timeLabel.trim(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Please pick a time of at least 15 mins.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void calculateNextMedicationSmart(List<Medication> meds) {
        if (binding == null) return;
        if (meds == null || meds.isEmpty()) {
            binding.tvNextMedName.setText("No Meds");
            binding.tvNextMedTime.setText("Add Prescription");
            return;
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String currentDayOfWeek = new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date());

        Medication nextMed = null;
        long minDiffMillis = Long.MAX_VALUE;
        boolean hasActiveMeds = false;

        for (Medication m : meds) {
            if (m.getEndDate() != null && !m.getEndDate().equalsIgnoreCase("Forever") && m.getEndDate().compareTo(today) < 0)
                continue;

            String scheduledDays = m.getSelectedDays();
            if (scheduledDays != null && !scheduledDays.equals("Everyday") && !scheduledDays.contains(currentDayOfWeek)) {
                continue;
            }

            hasActiveMeds = true;

            int takenCount = 0;
            if (m.getLastActionDate() != null && m.getLastActionDate().equals(today))
                takenCount = m.getTimesTakenToday();

            if (takenCount >= m.getFrequencyPerDay()) continue;

            try {
                SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                Date startTime = sdfTime.parse(m.getTime());

                if (startTime != null) {
                    Calendar doseTime = Calendar.getInstance();
                    doseTime.setTime(startTime);
                    Calendar target = Calendar.getInstance();
                    target.set(Calendar.HOUR_OF_DAY, doseTime.get(Calendar.HOUR_OF_DAY));
                    target.set(Calendar.MINUTE, doseTime.get(Calendar.MINUTE));
                    target.set(Calendar.SECOND, 0);

                    int intervalHours = 6;
                    target.add(Calendar.HOUR_OF_DAY, takenCount * intervalHours);

                    long now = System.currentTimeMillis();
                    long diff = target.getTimeInMillis() - now;

                    if (diff >= 0 && diff < minDiffMillis) {
                        minDiffMillis = diff;
                        nextMed = m;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (nextMed != null) {
            binding.tvNextMedName.setText(nextMed.getName());
            try {
                int taken = 0;
                if (nextMed.getLastActionDate() != null && nextMed.getLastActionDate().equals(today)) {
                    taken = nextMed.getTimesTakenToday();
                }

                SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                Date baseTime = sdfTime.parse(nextMed.getTime());
                Calendar c = Calendar.getInstance();
                c.setTime(baseTime);
                c.add(Calendar.HOUR_OF_DAY, taken * 6);

                String displayTime = sdfTime.format(c.getTime());
                binding.tvNextMedTime.setText(displayTime);

            } catch (Exception e) {
                binding.tvNextMedTime.setText(nextMed.getTime());
            }

        } else if (hasActiveMeds) {
            binding.tvNextMedName.setText("All Done");
            binding.tvNextMedTime.setText("See you tomorrow");
        } else {
            binding.tvNextMedName.setText("No Active");
            binding.tvNextMedTime.setText("Prescriptions");
        }
    }

    private void checkHealthConnectPermissions() {
        if (healthConnectManager.isAvailable()) {
            Set<String> perms = healthConnectManager.getRequiredPermissions();

            // Convert Set to Array for the launcher
            String[] permsArray = perms.toArray(new String[0]);

            healthConnectManager.checkPermissions(granted -> {
                if (granted.containsAll(perms)) {
                    fetchHealthConnectData();
                } else {
                    // Request missing permissions
                    requestHealthConnectLauncher.launch(permsArray);
                }
                return null;
            });
        }
    }

    private void fetchHealthConnectData() {
        if (!healthConnectManager.isAvailable()) return;

        // 1. Fetch Steps
        healthConnectManager.readTodaySteps(steps -> {
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> updateStepsIfHigher(steps.intValue()));
            }
            return null; // Required for Kotlin Interop
        });

        // 2. Fetch Heart Rate
        healthConnectManager.readAvgHeartRate(avgBpm -> {
            if (avgBpm > 0 && isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    // Check if we already have this reading to avoid duplicates (optional logic)
                    Vital hrVital = new Vital("Heart Rate", String.valueOf(avgBpm), null, "bpm", date, System.currentTimeMillis(), "Health Connect", null);
                    viewModel.insertVital(hrVital);
                });
            }
            return null;
        });

        healthConnectManager.readSleepAndCalories((calories, sleepDuration) -> {
            if (getActivity() != null && currentDayData != null) {
                getActivity().runOnUiThread(() -> {
                    // Only update if data is valid
                    if (!sleepDuration.equals("0h 0m")) {
                        currentDayData.setSleepDuration(sleepDuration);
                    }
                    saveToDb();
                    updateStatsUI();
                });
            }
            return null;
        });

        PeriodicWorkRequest healthSyncWork = new PeriodicWorkRequest.Builder(
                HealthSyncWorker.class, 4, TimeUnit.HOURS
        ).build();

        WorkManager.getInstance(requireContext())
                .enqueueUniquePeriodicWork(
                        "HealthConnectAutoSync",
                        ExistingPeriodicWorkPolicy.KEEP,
                        healthSyncWork
                );
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

    private void updateAdherenceCard(List<Medication> meds) {
        if (binding == null || meds == null || meds.isEmpty()) {
            if (binding != null) binding.cardAdherence.setVisibility(View.GONE);
            return;
        }

        int totalExpected = 0;
        int totalTaken = 0;
        for (Medication m : meds) {
            if (m.getFrequencyPerDay() > 0) {
                totalExpected += m.getFrequencyPerDay();
                totalTaken += Math.min(m.getTimesTakenToday(), m.getFrequencyPerDay());
            }
        }

        if (totalExpected == 0) {
            binding.cardAdherence.setVisibility(View.GONE);
            return;
        }

        int percent = (int) ((totalTaken / (float) totalExpected) * 100);
        binding.cardAdherence.setVisibility(View.VISIBLE);
        binding.tvAdherencePercent.setText(percent + "%");

        android.animation.ObjectAnimator progressAnim = android.animation.ObjectAnimator
                .ofInt(binding.progressAdherence, "progress", 0, percent);
        progressAnim.setDuration(800);
        progressAnim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        progressAnim.start();

        String label;
        if (percent == 100) {
            label = "Perfect today! 🎉";
            playCelebration();
        } else if (percent >= 75) {
            label = "Great job, almost there!";
        } else if (percent >= 50) {
            label = totalExpected - totalTaken + " dose(s) remaining today";
        } else {
            label = "Don't forget your medications";
        }
        binding.tvAdherenceLabel.setText(label);
    }

    private void updateGreeting() {
        int timeOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        int heroBgColor;
        int lottieRes;
        if (timeOfDay < 12) {
            greeting = "Good Morning,";
            heroBgColor = ContextCompat.getColor(requireContext(), R.color.bg_streak_card);
            lottieRes = R.raw.anim_greeting_day;
        } else if (timeOfDay < 17) {
            greeting = "Good Afternoon,";
            heroBgColor = ContextCompat.getColor(requireContext(), R.color.bg_insight_positive);
            lottieRes = R.raw.anim_greeting_day;
        } else {
            greeting = "Good Evening,";
            heroBgColor = ContextCompat.getColor(requireContext(), R.color.bg_date_start);
            lottieRes = R.raw.anim_greeting_night;
        }
        binding.tvGreeting.setText(greeting);
        binding.cardHealthPet.setCardBackgroundColor(heroBgColor);
        binding.lottieGreeting.setAnimation(lottieRes);
        binding.lottieGreeting.playAnimation();
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
        playWaterSplash();
        binding.getRoot().performHapticFeedback(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void springChip(View v, Runnable action) {
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(140)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                        .withEndAction(action).start()).start();
    }

    private void playWaterSplash() {
        if (binding == null) return;
        binding.lottieWaterSplash.setVisibility(View.VISIBLE);
        binding.lottieWaterSplash.playAnimation();
        binding.lottieWaterSplash.addAnimatorListener(new android.animation.Animator.AnimatorListener() {
            @Override public void onAnimationStart(android.animation.Animator a) {}
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (binding != null) binding.lottieWaterSplash.setVisibility(View.GONE);
                binding.lottieWaterSplash.removeAllAnimatorListeners();
            }
            @Override public void onAnimationCancel(android.animation.Animator a) {}
            @Override public void onAnimationRepeat(android.animation.Animator a) {}
        });
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
            binding.chipGoal.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
            binding.chipGoal.setChipIconResource(R.drawable.ic_check_circle);
            binding.chipGoal.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary)));
            binding.chipGoal.setClickable(false);

            if (!goalReachedTriggered && animate) {
                playCelebration();
                updateStreakCount();
                goalReachedTriggered = true;
            }
        } else {
            binding.chipGoal.setText(MessageFormat.format("Goal: {0}ml", goal));
            binding.chipGoal.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_day_normal));
            binding.chipGoal.setChipIconResource(R.drawable.ic_edit);
            binding.chipGoal.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.chart_axis_text)));
            binding.chipGoal.setClickable(true);
        }
    }

    // NEW: Helper method to count numbers smoothly
    private void animateTextCounter(int start, int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(1000); // Syncs with the water drop animation duration
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            if (binding != null) {
                int val = (int) animation.getAnimatedValue();
                binding.tvIntakeAmount.setText(String.valueOf(val));
            }
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
                m.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_day_other_month));
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
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.chart_axis_text));
        xAxis.setTextSize(12f);
        xAxis.setGranularity(1f);

        // 3. Y-Axis (Left - Amounts)
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);

        // 4. Empty State
        chart.setNoDataText("Loading history...");
        chart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
    }

    private void loadWeeklyData() {
        disposables.add(viewModel.getLastSevenDaysSync()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(history -> {
                    Collections.reverse(history);
                    ArrayList<BarEntry> entries = new ArrayList<>();
                    ArrayList<String> labels = new ArrayList<>();
                    int index = 0;
                    for (DailyHealth day : history) {
                        entries.add(new BarEntry(index, day.getWaterIntakeMl()));
                        try {
                            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            SimpleDateFormat outFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                            Date date = inFormat.parse(day.getDate());
                            if (date != null) labels.add(outFormat.format(date));
                        } catch (Exception e) {
                            labels.add("?");
                        }
                        index++;
                    }
                    updateChartUI(entries, labels);
                }, Throwable::printStackTrace));
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
            set.setColor(ContextCompat.getColor(requireContext(), R.color.primary));
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
            disposables.add(viewModel.insertDailyHealthCompletable(currentDayData)
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> {
                        if (getContext() != null) {
                            WidgetUpdateHelper.updateWidgets(getContext());
                        }
                    }, Throwable::printStackTrace));
        }
    }

    private void updateStatsUI() {
        if (currentDayData == null) return;
        animateCountUp(currentDayData.getStepCount());
        binding.tvSleepValue.setText(currentDayData.getSleepDuration());
        loadYesterdayTrend();
    }

    private void animateCountUp(int targetSteps) {
        ValueAnimator animator = ValueAnimator.ofInt(0, targetSteps);
        animator.setDuration(1200);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(a ->
                binding.tvStepsValue.setText(
                        String.format(Locale.US, "%,d", (int) a.getAnimatedValue())));
        animator.start();
    }

    private void loadYesterdayTrend() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        disposables.add(viewModel.getAppDao().getDailyHealthSync(yesterday)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(
                        yesterdayData -> {
                            if (binding == null) return;
                            int todaySteps = currentDayData.getStepCount();
                            int yesterdaySteps = yesterdayData.getStepCount();
                            int diff = todaySteps - yesterdaySteps;
                            if (yesterdaySteps > 0) {
                                binding.tvStepsTrend.setVisibility(View.VISIBLE);
                                if (diff >= 0) {
                                    binding.tvStepsTrend.setText("▲ " + String.format(Locale.US, "%,d", diff) + " vs yesterday");
                                    binding.tvStepsTrend.setTextColor(ContextCompat.getColor(requireContext(), R.color.trend_up));
                                } else {
                                    binding.tvStepsTrend.setText("▼ " + String.format(Locale.US, "%,d", Math.abs(diff)) + " vs yesterday");
                                    binding.tvStepsTrend.setTextColor(ContextCompat.getColor(requireContext(), R.color.trend_down));
                                }
                            }
                            binding.tvSleepTrend.setVisibility(View.GONE);
                        },
                        error -> {
                        }
                ));
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
        binding.chip250.setOnClickListener(v -> springChip(v, () -> addWater(250)));
        binding.chip500.setOnClickListener(v -> springChip(v, () -> addWater(500)));
        binding.chip750.setOnClickListener(v -> springChip(v, () -> addWater(750)));
        binding.chipCustom.setOnClickListener(v -> springChip(v, this::showCustomWaterDialog));
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
                addWater(Integer.parseInt(valueStr));
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
                if (binding != null) binding.lottieCelebration.setVisibility(View.GONE);
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

    private void generateGeminiTip() {
        if (currentDayData == null) return;

        // 1. Show Loading State
        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        String todayDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String savedDate = prefs.getString("daily_tip_date", "");
        String savedTip = prefs.getString("daily_tip_content", "");

        if (savedDate.equals(todayDateKey) && !savedTip.isEmpty()) {
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
                if (getActivity() != null && binding != null) {
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
                if (getActivity() != null && binding != null) {
                    getActivity().runOnUiThread(() -> {
                        String fallback = InsightEngine.getInsight(currentDayData, null);
                        binding.tvTipText.setText(fallback);
                    });
                }
            }
        });
    }

    private void setupSwipeToDelete() {
        androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback simpleItemTouchCallback =
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT) {

                    @Override
                    public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView r, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder v, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder t) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getBindingAdapterPosition();
                        if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION)
                            return;

                        // Get item safely from ListAdapter
                        if (foodAdapter.getCurrentList().size() <= position) return;
                        final FoodLog deletedItem = foodAdapter.getCurrentList().get(position);

                        // 1. Delete from DB
                        if (deletedItem != null) {
                            deleteFoodLogInternal(deletedItem);
                        }
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull androidx.recyclerview.widget.RecyclerView r, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder v, float dX, float dY, int action, boolean isActive) {
                        if (action == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                            View itemView = v.itemView;
                            Paint p = new Paint();
                            p.setColor(ContextCompat.getColor(requireContext(), R.color.bg_swipe_delete));
                            c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom(), p);

                            Drawable icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);

                            if (icon != null) {
                                icon.setTint(ContextCompat.getColor(requireContext(), R.color.icon_swipe_delete));
                                int itemHeight = itemView.getBottom() - itemView.getTop();
                                int intrinsicHeight = icon.getIntrinsicHeight();
                                int intrinsicWidth = icon.getIntrinsicWidth();
                                int iconTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
                                int iconMargin = (itemHeight - intrinsicHeight) / 2;
                                int iconLeft = itemView.getRight() - iconMargin - intrinsicWidth;
                                int iconRight = itemView.getRight() - iconMargin;
                                int iconBottom = iconTop + intrinsicHeight;

                                // Prevent icon from going too far if swipe is small
                                if (iconLeft > itemView.getRight() + dX) {
                                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                                    icon.draw(c);
                                }
                            }
                        }
                        super.onChildDraw(c, r, v, dX, dY, action, isActive);
                    }
                };

        new ItemTouchHelper(simpleItemTouchCallback).attachToRecyclerView(binding.rvFoodLogs);
    }

    // Internal delete method that triggers the Snack bar
    private void deleteFoodLogInternal(FoodLog log) {
        disposables.add(viewModel.deleteFoodLogCompletable(log)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    UiUtils.showCustomSnackbar(binding.getRoot(), requireContext(), "Food log deleted",
                            ContextCompat.getColor(requireContext(), R.color.bg_swipe_delete),
                            ContextCompat.getColor(requireContext(), R.color.icon_swipe_delete),
                            android.R.drawable.ic_menu_delete, binding.btnScanFood,
                            v -> viewModel.insertFoodLog(log));
                }, Throwable::printStackTrace));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }

    private void startHomeStatsShimmer() {
        if (binding == null) return;
        binding.shimmerHomeContainer.setVisibility(View.VISIBLE);
        if (binding.shimmerHomeContainer instanceof com.facebook.shimmer.ShimmerFrameLayout) {
            ((com.facebook.shimmer.ShimmerFrameLayout) binding.shimmerHomeContainer).startShimmer();
        }
    }

    private void stopHomeStatsShimmer() {
        if (binding == null) return;
        if (binding.shimmerHomeContainer instanceof com.facebook.shimmer.ShimmerFrameLayout) {
            ((com.facebook.shimmer.ShimmerFrameLayout) binding.shimmerHomeContainer).stopShimmer();
        }
        binding.shimmerHomeContainer.setVisibility(View.GONE);
        binding.rowStats.setVisibility(View.VISIBLE);
        binding.rowStats2.setVisibility(View.VISIBLE);
    }
}