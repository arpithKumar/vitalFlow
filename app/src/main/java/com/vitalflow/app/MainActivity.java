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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.badge.BadgeDrawable;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;
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
    /** True once the user has passed biometric verification in this session. */
    private boolean isAuthenticated = false;

    @Inject
    GeminiRepository geminiRepository;
    @Inject
    AppDao appDao;
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

        if (savedInstanceState == null) {
            checkAndRequestAllPermissions();
        }
        startMedicationScheduler();

        chatManager = new FloatingChatManager(this, geminiRepository);
        binding.getRoot().post(() -> chatManager.attachToActivity());

        // Capture any notification deep-link from cold start
        pendingNavigation = getIntent().getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO);

        setupNavigation();
        setupOfflineBanner();
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

                // Consume any pending deep-link navigation once the user reaches home
                if (id == R.id.homeFragment && pendingNavigation != null) {
                    String nav = pendingNavigation;
                    pendingNavigation = null;
                    binding.getRoot().post(() -> navigateToPending(nav));
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

            // Pending meds badge on the Meds nav tab
            appDao.getAllMedications().observe(this, meds -> {
                int pending = countPendingMedsNow(meds);
                if (pending > 0) {
                    BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.medsFragment);
                    badge.setNumber(pending);
                    badge.setVisible(true);
                } else {
                    binding.bottomNav.removeBadge(R.id.medsFragment);
                }
            });
        }
    }

    private int countPendingMedsNow(List<Medication> meds) {
        if (meds == null) return 0;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        String currentCategory = hour < 12 ? "Morning" : (hour < 17 ? "Afternoon" : "Night");
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String currentDayOfWeek = new java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                .format(new java.util.Date());

        int count = 0;
        for (Medication m : meds) {
            // Must be active today
            if (m.getEndDate() != null && !m.getEndDate().equals("Forever")
                    && m.getEndDate().compareTo(today) < 0) continue;
            String days = m.getSelectedDays();
            if (days != null && !days.equals("Everyday") && !days.contains(currentDayOfWeek)) continue;
            // Must match current time category and be PENDING
            if (m.getTimeCategory().equals(currentCategory)
                    && !"TAKEN".equals(m.getStatus())
                    && !"SKIPPED".equals(m.getStatus())) {
                count++;
            }
        }
        return count;
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

    // -------------------------------------------------------------------------
    // OFFLINE BANNER
    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // NOTIFICATION DEEP-LINK ROUTING
    // -------------------------------------------------------------------------
    /** Destination set by an incoming notification intent; consumed once navigation fires. */
    private String pendingNavigation = null;

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String dest = intent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO);
        if (dest != null) navigateToPending(dest);
    }

    /**
     * Navigate to a tab by name if the NavController is already on a main tab
     * (i.e., the user is authenticated and past the splash/login screens).
     */
    private void navigateToPending(String dest) {
        NavHostFragment nhf = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (nhf == null) return;
        NavController nc = nhf.getNavController();
        if (nc.getCurrentDestination() == null) return;
        int cur = nc.getCurrentDestination().getId();
        boolean onMainTab = cur == R.id.homeFragment || cur == R.id.medsFragment
                || cur == R.id.vitalsFragment || cur == R.id.periodFragment;
        if (!onMainTab) return;

        switch (dest) {
            case NotificationHelper.DEST_MEDS:
                binding.bottomNav.setSelectedItemId(R.id.medsFragment);
                break;
            case NotificationHelper.DEST_VITALS:
                binding.bottomNav.setSelectedItemId(R.id.vitalsFragment);
                break;
            case NotificationHelper.DEST_PERIOD:
                binding.bottomNav.setSelectedItemId(R.id.periodFragment);
                break;
            case NotificationHelper.DEST_HOME:
            default:
                binding.bottomNav.setSelectedItemId(R.id.homeFragment);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // OFFLINE BANNER
    // -------------------------------------------------------------------------
    private android.net.ConnectivityManager.NetworkCallback networkCallback;

    private void setupOfflineBanner() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@androidx.annotation.NonNull android.net.Network network) {
                runOnUiThread(() -> binding.bannerOffline.setVisibility(View.GONE));
            }
            @Override
            public void onLost(@androidx.annotation.NonNull android.net.Network network) {
                runOnUiThread(() -> binding.bannerOffline.setVisibility(View.VISIBLE));
            }
        };

        android.net.NetworkRequest request = new android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        cm.registerNetworkCallback(request, networkCallback);

        // Set initial state
        android.net.Network active = cm.getActiveNetwork();
        android.net.NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
        boolean online = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        binding.bannerOffline.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    // -------------------------------------------------------------------------
    // BIOMETRIC LOCK
    // -------------------------------------------------------------------------
    @Override
    protected void onResume() {
        super.onResume();
        checkBiometricLock();
    }

    /**
     * Shows a {@link BiometricPrompt} when the user has enabled biometric lock in Profile and
     * has not yet authenticated in this session. Only fires while on the 4 main tabs or
     * the profile screen — never interrupts the splash/login/onboarding flow.
     */
    private void checkBiometricLock() {
        if (isAuthenticated) return;

        SharedPreferences prefs = getSharedPreferences("vital_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("biometric_enabled", false)) return;

        // Only enforce once the user is past splash/login/onboarding
        NavHostFragment nhf = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (nhf == null || nhf.getNavController().getCurrentDestination() == null) return;
        int destId = nhf.getNavController().getCurrentDestination().getId();
        boolean onProtectedScreen = (destId == R.id.homeFragment   ||
                                     destId == R.id.medsFragment   ||
                                     destId == R.id.vitalsFragment ||
                                     destId == R.id.periodFragment ||
                                     destId == R.id.profileFragment ||
                                     destId == R.id.reportFragment);
        if (!onProtectedScreen) return;

        // Confirm the hardware is ready before showing the prompt
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Hardware absent or not enrolled — treat as passed so the user isn't locked out
            isAuthenticated = true;
            return;
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("VitalFlow Locked")
                .setSubtitle("Verify your identity to continue")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        isAuthenticated = true;
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                            @NonNull CharSequence errString) {
                        // User explicitly cancelled or pressed Back — send to login
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                            navigateToLogin();
                        }
                        // Hardware lockout or other errors: let the user retry on next resume
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Single failed attempt — let the prompt handle retries naturally
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    /** Pops the entire back stack and lands the user on the login screen. */
    private void navigateToLogin() {
        NavHostFragment nhf = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (nhf == null) return;
        nhf.getNavController().navigate(R.id.loginFragment, null,
                new NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (cm != null && networkCallback != null) cm.unregisterNetworkCallback(networkCallback);
    }
}