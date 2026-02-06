package com.vitalflow.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
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

    // Store the ID of the selected tab to calculate slide direction
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
                    // Initialize things that need permissions
                    NotificationHelper.createNotificationChannel(this);
                } else {
                    // Optional: Show a message if critical permissions are denied
                    Toast.makeText(this, "Some features may not work without permissions.", Toast.LENGTH_SHORT).show();
                    handleDeniedPermissions(deniedPermissions);
                }
            });

    private void handleDeniedPermissions(List<String> deniedPermissions) {
        var message = new StringBuilder("The following permissions are required for this app to function correctly:\n\n");

        for (String perm : deniedPermissions) {
            // Convert "android.permission.BODY_SENSORS" -> "BODY_SENSORS"
            String name = perm.substring(perm.lastIndexOf(".") + 1);
            name = name.replace("_", " ");
            message.append("• ").append(name).append("\n");
        }

        message.append("\nPlease enable them in Settings to continue.");

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Permissions Required")
                .setMessage(message.toString())
                .setCancelable(false) // Force user to read
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    try {
                        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
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
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        checkAndRequestAllPermissions();

        startMedicationScheduler();

        chatManager = new FloatingChatManager(this, geminiRepository);
        binding.getRoot().post(() -> chatManager.attachToActivity());

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        NotificationHelper.createNotificationChannel(this);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            // 1. Manual Bottom Navigation Setup (To enable smooth animations)
            binding.bottomNav.setOnItemSelectedListener(item -> {
                int destId = item.getItemId();

                // Don't reload if we are already on this tab
                if (destId == currentTabId) return false;

                // 2. Determine Direction (Slide Left or Right?)
                NavOptions.Builder builder = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false);

                if (getTabIndex(destId) > getTabIndex(currentTabId)) {
                    // Going Right (e.g. Home -> Meds)
                    builder.setEnterAnim(R.anim.slide_in_right)
                            .setExitAnim(R.anim.slide_out_left)
                            .setPopEnterAnim(R.anim.slide_in_left)
                            .setPopExitAnim(R.anim.slide_out_right);
                } else {
                    // Going Left (e.g. Meds -> Home)
                    builder.setEnterAnim(R.anim.slide_in_left)
                            .setExitAnim(R.anim.slide_out_right)
                            .setPopEnterAnim(R.anim.slide_in_right)
                            .setPopExitAnim(R.anim.slide_out_left);
                }

                // 3. Navigate
                navController.navigate(destId, null, builder.build());
                currentTabId = destId;
                return true;
            });

            // 4. Sync Selection (If user clicks Back button, update the bottom bar)
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();

                // Toggle Bottom Bar Visibility
                if (id == R.id.homeFragment || id == R.id.medsFragment || id == R.id.vitalsFragment || id == R.id.periodFragment) {
                    binding.bottomNav.setVisibility(View.VISIBLE);
                    // Update current tab ID variable to keep sync
                    currentTabId = id;
                    // Visually check the correct item in bottom bar
                    if (binding.bottomNav.getSelectedItemId() != id) {
                        binding.bottomNav.getMenu().findItem(id).setChecked(true);
                    }
                } else {
                    binding.bottomNav.setVisibility(View.GONE);
                }

                // --- B. Chatbot Visibility ---
                // Hide chat on Splash, Login, or Register screens
                if (id == R.id.onboardingFragment || id == R.id.splashFragment || id == R.id.loginFragment) {
                    if (chatManager != null) chatManager.setVisibility(false);
                } else {
                    // Show on all other screens (Home, Profile, etc.)
                    if (chatManager != null) chatManager.setVisibility(true);
                }
            });
        }
    }

    private void startMedicationScheduler() {
        PeriodicWorkRequest medRequest =
                new PeriodicWorkRequest.Builder(MedicationWorker.class, 15, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "MedicationCheck",
                ExistingPeriodicWorkPolicy.KEEP, // Don't restart if already running
                medRequest);
    }

    private void checkAndRequestAllPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BODY_SENSORS);
            }
        }

        // 1. NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Pre-13, we just create the channel
            NotificationHelper.createNotificationChannel(this);
        }

        // 2. BLUETOOTH (For Vitals/Devices)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Scan & Connect
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else {
            // Android 11 and below need Location for Bluetooth
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        // 3. STORAGE / MEDIA (For Chatbot Attachments & Profile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        // EXECUTE REQUEST
        if (!permissionsToRequest.isEmpty()) {
            PermissionBottomSheet sheet = new PermissionBottomSheet();
            sheet.setOnAllowClicked(() -> {
                multiplePermissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
            });
            sheet.show(getSupportFragmentManager(), "PermissionSheet");
        }
    }

    // Helper to order the tabs: Home(0) -> Meds(1) -> Vitals(2)
    private int getTabIndex(int itemId) {
        if (itemId == R.id.homeFragment) return 0;
        if (itemId == R.id.medsFragment) return 1;
        if (itemId == R.id.vitalsFragment) return 2;
        if (itemId == R.id.periodFragment) return 3;
        return 0;
    }
}