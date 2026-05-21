package com.vitalflow.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.ActivityMainBinding;
import com.vitalflow.app.ui.chatbot.FloatingChatManager;
import com.vitalflow.app.ui.custom.PermissionBottomSheet;
import com.vitalflow.app.utils.MedicationWorker;
import com.vitalflow.app.utils.NotificationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private int currentTabId = R.id.homeFragment;

    @Inject
    GeminiRepository geminiRepository;
    private FloatingChatManager chatManager;

    private final ActivityResultLauncher<String[]> multiplePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                List<String> deniedPermissions = new ArrayList<>();
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        deniedPermissions.add(entry.getKey());
                    }
                }
                if (allGranted) {
                    NotificationHelper.createNotificationChannel(this);
                } else {
                    Toast.makeText(this, "Some features may not work without permissions.", Toast.LENGTH_SHORT).show();
                    handleDeniedPermissions(deniedPermissions);
                }
            });

    private void handleDeniedPermissions(List<String> deniedPermissions) {
        var message = new StringBuilder("The following permissions are required for this app to function correctly:\n\n");
        for (String perm : deniedPermissions) {
            String name = perm.substring(perm.lastIndexOf(".") + 1).replace("_", " ");
            message.append("• ").append(name).append("\n");
        }
        message.append("\nPlease enable them in Settings to continue.");

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Permissions Required")
                .setMessage(message.toString())
                .setCancelable(false)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("Not Now", null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("is_dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkAndRequestAllPermissions();
        startMedicationScheduler();

        chatManager = new FloatingChatManager(this, geminiRepository);
        binding.getRoot().post(() -> chatManager.attachToActivity());

        setupNavigation();
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        NotificationHelper.createNotificationChannel(this);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            binding.bottomNav.setItemActiveIndicatorColor(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.primary)));

            binding.bottomNav.setOnItemSelectedListener(item -> {
                int destId = item.getItemId();
                if (destId == currentTabId) return false;

                NavOptions.Builder builder = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false);

                if (getTabIndex(destId) > getTabIndex(currentTabId)) {
                    builder.setEnterAnim(R.anim.slide_in_right)
                            .setExitAnim(R.anim.slide_out_left)
                            .setPopEnterAnim(R.anim.slide_in_left)
                            .setPopExitAnim(R.anim.slide_out_right);
                } else {
                    builder.setEnterAnim(R.anim.slide_in_left)
                            .setExitAnim(R.anim.slide_out_right)
                            .setPopEnterAnim(R.anim.slide_in_right)
                            .setPopExitAnim(R.anim.slide_out_left);
                }

                navController.navigate(destId, null, builder.build());
                currentTabId = destId;
                return true;
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                boolean isMainScreen = (id == R.id.homeFragment || id == R.id.medsFragment ||
                        id == R.id.vitalsFragment || id == R.id.periodFragment);

                if (isMainScreen) {
                    showNavBar();
                } else {
                    hideNavBar();
                }

                if (isMainScreen) {
                    currentTabId = id;
                    if (binding.bottomNav.getSelectedItemId() != id) {
                        binding.bottomNav.getMenu().findItem(id).setChecked(true);
                    }
                }

                boolean hideChat = (id == R.id.onboardingFragment ||
                        id == R.id.splashFragment ||
                        id == R.id.loginFragment);
                if (chatManager != null) chatManager.setVisibility(!hideChat);
            });
        }
    }

    private void showNavBar() {
        if (binding.navCard.getVisibility() == View.VISIBLE) return;
        binding.navCard.setAlpha(0f);
        binding.navCard.setTranslationY(100f);
        binding.navCard.setVisibility(View.VISIBLE);
        binding.navCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void hideNavBar() {
        if (binding.navCard.getVisibility() == View.GONE) return;
        binding.navCard.animate()
                .translationY(binding.navCard.getHeight() > 0 ? binding.navCard.getHeight() + 32f : 300f)
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> binding.navCard.setVisibility(View.GONE))
                .start();
    }

    private void startMedicationScheduler() {
        PeriodicWorkRequest medRequest =
                new PeriodicWorkRequest.Builder(MedicationWorker.class, 15, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "MedicationCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                medRequest);
    }

    private void checkAndRequestAllPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            NotificationHelper.createNotificationChannel(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            PermissionBottomSheet sheet = new PermissionBottomSheet();
            sheet.setOnAllowClicked(() ->
                    multiplePermissionLauncher.launch(permissionsToRequest.toArray(new String[0])));
            sheet.show(getSupportFragmentManager(), "PermissionSheet");
        }
    }

    private int getTabIndex(int itemId) {
        if (itemId == R.id.homeFragment) return 0;
        if (itemId == R.id.medsFragment) return 1;
        if (itemId == R.id.vitalsFragment) return 2;
        if (itemId == R.id.periodFragment) return 3;
        return 0;
    }
}