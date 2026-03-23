package com.vitalflow.app.ui.home;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.entity.FoodLog;
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
import com.vitalflow.app.workers.HealthSyncWorker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class HomeFragment extends Fragment implements SensorEventListener {

    @Inject
    AppDao appDao;

    @Inject
    GeminiRepository geminiRepository;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStepPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }

        setupButtons();
        setupAdditionalView(view);
    }

    private void setupButtons() {
        binding.btnExportPdf.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Gathering data & AI Insights...", Toast.LENGTH_SHORT).show();
            NotificationHelper.showDownloadProgress(requireContext());
            reportViewModel.generateFullReport();
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

            // Sync Logic: Only update if Native is larger (or similar), or simply trust Native for real-time
            // We update UI immediately for responsiveness
            updateStepsIfHigher(nativeSteps);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateStepsIfHigher(int newStepCount) {
        if (currentDayData != null) {
            // Logic: Update if new count is higher than current DB count
            // This ensures we capture the maximum value from either source
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

    private void setupAdditionalView(View view) {
        appDao.getDailyHealth(todayDateKey).observe(getViewLifecycleOwner(), dailyHealth -> {
            if (dailyHealth == null) {
                DailyHealth newEntry = new DailyHealth(todayDateKey, 0, 2000, 0, "0h 0m", 0);
                disposables.add(appDao.insertDailyHealth(newEntry)
                        .subscribeOn(Schedulers.io())
                        .subscribe());
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
            loadWeeklyData();
            generateGeminiTip();

            disposables.add(appDao.getLastSevenDaysSync()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(history -> {
                        if (binding == null) return;
                        String tip = InsightEngine.getInsight(currentDayData, history);
                        if (isAdded()) binding.tvTipText.setText(tip);
                    }, Throwable::printStackTrace));
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

    private void setupNutritionSection() {
        foodAdapter = new FoodLogAdapter();
        binding.rvFoodLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFoodLogs.setAdapter(foodAdapter);

        // 1. Load Logs
        appDao.getFoodLogsForDate(todayDateKey).observe(getViewLifecycleOwner(), logs -> {
            if (binding == null) return;
            foodAdapter.submitList(logs);
            binding.rvFoodLogs.setVisibility(logs.isEmpty() ? View.GONE : View.VISIBLE);
        });

        // 2. Load Total Calories
        appDao.getTotalCaloriesForDate(todayDateKey).observe(getViewLifecycleOwner(), total -> {
            if (binding == null) return;
            int cal = total != null ? total : 0;
            binding.tvCaloriesTotal.setText(cal + " kcal");
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

        new Thread(() -> {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(imageUri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, options);
                is.close();

                int reqWidth = 800;
                int reqHeight = 800;
                int inSampleSize = 1;

                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    final int halfHeight = options.outHeight / 2;
                    final int halfWidth = options.outWidth / 2;
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2;
                    }
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = inSampleSize;
                is = requireContext().getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                is.close();

                if (bitmap == null) throw new IOException("Image decode failed");

                geminiRepository.analyzeFoodImage(bitmap, new GeminiRepository.FoodAnalysisCallback() {
                    @Override
                    public void onSuccess(GeminiRepository.FoodAnalysisResult result) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                dismissLoadingDialog();
                                showConfirmFoodDialog(result);
                            });
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                dismissLoadingDialog();
                                Toast.makeText(getContext(), "AI Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });

            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        dismissLoadingDialog();
                        Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
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

                    disposables.add(appDao.insertFoodLog(log)
                            .subscribeOn(Schedulers.io())
                            .subscribe());

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
            String[] options = {"Off (No Reminders)", "Every 1 Hour", "Every 2 Hours", "Every 4 Hours", "Custom..."};
            int[] predefinedIntervals = {0, 1, 2, 4};

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
                                Toast.makeText(getContext(), "Reminding every " + selectedInterval + " hours", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            showCustomReminderDialog(prefs);
                        }
                    })
                    .show();
        });
    }

    private void showCustomReminderDialog(SharedPreferences prefs) {
        // Create an EditText programmatically so we don't need a new XML file
        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g. 3");

        // Add some padding to the input field so it looks nice
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;
        params.rightMargin = margin;
        input.setLayoutParams(params);
        container.addView(input);

        int currentInterval = prefs.getInt("water_reminder_interval", 0);
        if (currentInterval > 0) {
            input.setText(String.valueOf(currentInterval));
            input.setSelection(input.getText().length());
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Custom Interval")
                .setMessage("Remind me to drink water every X hours:")
                .setView(container)
                .setPositiveButton("Save", (dialog, whichButton) -> {
                    String valueStr = input.getText().toString().trim();
                    if (!valueStr.isEmpty()) {
                        try {
                            int hours = Integer.parseInt(valueStr);
                            if (hours > 0 && hours <= 24) {
                                prefs.edit().putInt("water_reminder_interval", hours).apply();
                                NotificationHelper.scheduleDailyReminder(requireContext(), hours);
                                Toast.makeText(getContext(), "Reminding every " + hours + " hours", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "Please enter between 1 and 24 hours", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(getContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                        }
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
                    disposables.add(appDao.insertVital(hrVital)
                            .subscribeOn(Schedulers.io())
                            .subscribe());
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
            binding.chipGoal.setText(MessageFormat.format("Goal: {0}ml", goal));
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
        disposables.add(appDao.getLastSevenDaysSync()
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
            disposables.add(appDao.insertDailyHealth(currentDayData)
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

    private void showCustomSnackbar(String message, int backgroundColor, int actionTextColor, int iconResId, View.OnClickListener onUndo) {
        if (binding == null) return;

        com.google.android.material.snackbar.Snackbar snackbar = com.google.android.material.snackbar.Snackbar.make(binding.getRoot(), message, 4000);

        // 1. Background Styling
        View view = snackbar.getView();
        view.setBackgroundTintList(null);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        // Convert dp to px for corner radius
        bg.setCornerRadius(28f * getResources().getDisplayMetrics().density);
        bg.setColor(backgroundColor);
        view.setBackground(bg);

        // 2. Positioning (Anchored to the Scan FAB)
        if (binding.btnScanFood != null) {
            snackbar.setAnchorView(binding.btnScanFood);
        }

        // 3. Text & Colors
        snackbar.setTextColor(Color.WHITE);
        snackbar.setActionTextColor(actionTextColor);

        // 4. Message Icon
        TextView tvMessage = view.findViewById(com.google.android.material.R.id.snackbar_text);
        // Using ContextCompat.getDrawable is safer
        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), iconResId);
        float d = getResources().getDisplayMetrics().density;

        if (icon != null) {
            int iconSize = (int) (24 * d);
            icon.setBounds(0, 0, iconSize, iconSize);
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, Color.WHITE);
            tvMessage.setCompoundDrawables(icon, null, null, null);
            tvMessage.setCompoundDrawablePadding((int) (12 * d));
        }

        // 5. Action Arrow Icon (Optional, check if you have ic_arrow_right, otherwise standard text is fine)
        TextView btnAction = view.findViewById(com.google.android.material.R.id.snackbar_action);
        // Using a standard android icon as fallback if you don't have ic_arrow_right locally
        android.graphics.drawable.Drawable arrowIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_media_play);
        // OR use R.drawable.ic_arrow_right if you have it from MedsFragment

        if (arrowIcon != null) {
            int arrowSize = (int) (14 * d); // Slightly smaller for arrow
            arrowIcon.setBounds(0, 0, arrowSize, arrowSize);
            androidx.core.graphics.drawable.DrawableCompat.setTint(arrowIcon, actionTextColor);
            btnAction.setCompoundDrawables(null, null, arrowIcon, null); // Uncomment if you want the arrow
            btnAction.setCompoundDrawablePadding((int) (4 * d));
        }

        snackbar.setAction("UNDO", onUndo);
        snackbar.show();
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
                            p.setColor(Color.parseColor("#FFEBEE"));
                            c.drawRect(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom(), p);

                            // Draw Trash Icon
                            // Ensure you have R.drawable.ic_trash_can or use android.R.drawable.ic_menu_delete
                            Drawable icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);

                            if (icon != null) {
                                icon.setTint(Color.parseColor("#C62828")); // Dark Red
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
        disposables.add(appDao.deleteFoodLog(log)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    // Show Custom Snackbar
                    showCustomSnackbar(
                            "Food log deleted",
                            Color.parseColor("#EF5350"), // Red
                            Color.parseColor("#B71C1C"), // Dark Red
                            android.R.drawable.ic_menu_delete, // Icon
                            v -> {
                                // Undo Logic: Re-insert
                                disposables.add(appDao.insertFoodLog(log)
                                        .subscribeOn(Schedulers.io())
                                        .subscribe());
                            }
                    );
                }, Throwable::printStackTrace));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }
}