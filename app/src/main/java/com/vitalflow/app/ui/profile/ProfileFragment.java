package com.vitalflow.app.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.vitalflow.app.R;
import com.vitalflow.app.data.repository.SyncRepository;
import com.vitalflow.app.databinding.FragmentProfileBinding;
import com.vitalflow.app.utils.NotificationHelper;
import com.vitalflow.app.workers.SyncWorker;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private SharedPreferences prefs;

    // For live "Last synced: X seconds ago" updates
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private Runnable timeRunnable;

    @Inject
    SyncRepository syncRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Initialize Preferences
        prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);

        // 2. Setup Animations (Cascade Effect for a premium feel)
        animateEntry(binding.cardProfile, 0);
        animateEntry(binding.cardPro, 50);
        animateEntry(binding.lblCloud, 100);
        animateEntry(binding.cardSync, 150);

        animateEntry(binding.lblAi, 200);
        animateEntry(binding.cardGeminiConfig, 250);

        animateEntry(binding.lblPref, 300);
        animateEntry(binding.cardPrefs, 350);
        animateEntry(binding.btnLogout, 400);


        // 3. Back Button
        binding.btnBack.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(v);
            if (!navController.popBackStack()) {
                navController.navigate(R.id.homeFragment);
            }
        });

        // 4. Load User Data (Image & Name)
        updateUserUI();

        // 5. Edit Profile Logic
        binding.btnEditProfile.setOnClickListener(v -> showEditNameDialog());

        // 6. Pro Membership Logic (Gold Card)
        updateProCardUI();
        binding.cardPro.setOnClickListener(v -> handleProClick());
        binding.btnProAction.setOnClickListener(v -> handleProClick());

        // 7. Sync Logic (Dropdown & Button)
        setupSyncDropdown();
        binding.cardSync.setOnClickListener(v -> startSync());

        // -----------------------------------------------------------
        // 8. GEMINI AI CONFIGURATION
        // -----------------------------------------------------------
        binding.cardGeminiConfig.setOnClickListener(v -> {
            com.vitalflow.app.ui.chatbot.GeminiKeyBottomSheet sheet = new com.vitalflow.app.ui.chatbot.GeminiKeyBottomSheet();
            sheet.setListener(() -> updateGeminiStatusUI());
            sheet.show(getChildFragmentManager(), "GeminiKey");
        });
        updateGeminiStatusUI();

        // 9. Start the "Last Synced" ticker
        startLiveTimeUpdater();

        // 10. Preferences (Dark Mode/Notifs) & Logout
        setupPreferences();
        binding.btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            NavController navController = Navigation.findNavController(view);
            navController.navigate(R.id.action_profile_to_login);
        });
    }

    // ------------------------------------------------------------------------
    // SECTION: PRO MEMBERSHIP
    // ------------------------------------------------------------------------

    private void updateProCardUI() {
        boolean isPro = prefs.getBoolean("is_pro_member", false);

        if (isPro) {
            // PRO STATE (Gold)
            binding.bgPro.setBackgroundResource(R.drawable.bg_pro_gradient);
            binding.tvProTitle.setText("Gold Member");
            binding.tvProSubtitle.setText("Premium features active");
            binding.btnProAction.setText("ACTIVE");
            binding.btnProAction.setEnabled(false); // Can't "upgrade" again
        } else {
            // FREE STATE (White)
            binding.bgPro.setBackgroundColor(0xFFFFFFFF);
            binding.tvProTitle.setText("Upgrade to Pro");
            binding.tvProSubtitle.setText("Unlock advanced insights");
            binding.btnProAction.setText("UPGRADE");
            binding.btnProAction.setEnabled(true);
        }
    }

    private void handleProClick() {
        if (prefs.getBoolean("is_pro_member", false)) {
            Toast.makeText(getContext(), "You are already a Premium Member!", Toast.LENGTH_SHORT).show();
        } else {
            showUpgradeDialog();
        }
    }

    private void showUpgradeDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Get VitalFlow Pro")
                .setMessage("Unlock unlimited history, cloud backup, and advanced health charts for just $4.99/mo.")
                .setIcon(R.drawable.ic_crown)
                .setPositiveButton("Subscribe Now", (dialog, which) -> {
                    // MOCK PURCHASE SUCCESS
                    prefs.edit().putBoolean("is_pro_member", true).apply();
                    updateProCardUI();

                    // Celebrate Animation using the Sync Lottie view momentarily
                    binding.lottieSync.playAnimation();
                    Toast.makeText(getContext(), "Welcome to Pro Club!", Toast.LENGTH_LONG).show();

                    // Stop celebration after 2 seconds
                    new Handler(Looper.getMainLooper()).postDelayed(() -> binding.lottieSync.cancelAnimation(), 2000);
                })
                .setNegativeButton("Maybe Later", null)
                .show();
    }

    // ------------------------------------------------------------------------
    // SECTION: USER PROFILE (Edit Name)
    // ------------------------------------------------------------------------

    private void updateUserUI() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            binding.tvName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Healthy User");
            binding.tvEmail.setText(user.getEmail());
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).placeholder(R.drawable.ic_user_dummy).into(binding.imgProfile);
            }
        }
    }

    private void showEditNameDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        EditText etName = new EditText(requireContext());
        etName.setText(user.getDisplayName());
        etName.setHint("Enter your name");
        etName.setPadding(50, 40, 50, 40);
        etName.setBackgroundResource(android.R.color.transparent);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Update Profile")
                .setMessage("Change your display name visible in the app.")
                .setView(etName)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = etName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        UserProfileChangeRequest updates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(newName)
                                .build();
                        user.updateProfile(updates).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                binding.tvName.setText(newName);
                                Toast.makeText(getContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------------
    // SECTION: CLOUD SYNC & SCHEDULING
    // ------------------------------------------------------------------------

    private void setupSyncDropdown() {
        String[] frequencies = new String[]{"Manual (Off)", "Daily", "Weekly", "Monthly"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, frequencies);
        AutoCompleteTextView dropdown = binding.dropdownSyncFreq;

        String savedFreq = prefs.getString("sync_frequency", "Manual (Off)");
        dropdown.setText(savedFreq, false);

        dropdown.setAdapter(adapter);
        dropdown.dismissDropDown();
        dropdown.clearFocus();

        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selected = frequencies[position];
            prefs.edit().putString("sync_frequency", selected).apply();
            scheduleAutoSync(selected);
            Toast.makeText(getContext(), "Auto-sync set to: " + selected, Toast.LENGTH_SHORT).show();

            dropdown.clearFocus();
        });
    }

    private void scheduleAutoSync(String frequency) {
        WorkManager workManager = WorkManager.getInstance(requireContext());
        String workName = "vitalflow_auto_sync";

        if (frequency.equals("Manual (Off)")) {
            workManager.cancelUniqueWork(workName);
            return;
        }

        long interval = 1;
        TimeUnit unit = TimeUnit.DAYS;

        switch (frequency) {
            case "Daily":
                interval = 1;
                unit = TimeUnit.DAYS;
                break;
            case "Weekly":
                interval = 7;
                unit = TimeUnit.DAYS;
                break;
            case "Monthly":
                interval = 30;
                unit = TimeUnit.DAYS;
                break;
        }

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(SyncWorker.class, interval, unit).build();
        workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, syncRequest);
    }

    private void startSync() {
        // UI: Start Animation
        binding.lottieSync.playAnimation();
        binding.tvSyncStatus.setText("Preparing...");
        binding.cardSync.setClickable(false); // Lock button

        // Execute Sync
        syncRepository.syncDataToCloud(new SyncRepository.OnSyncListener() {
            @Override
            public void onProgress(int current, int total) {
                // Update text live: "Syncing: 5 / 20"
                binding.tvSyncStatus.setText("Syncing: " + current + " / " + total);
            }

            @Override
            public void onComplete(boolean success, String message) {
                // UI: Stop Animation
                binding.lottieSync.cancelAnimation();
                binding.lottieSync.setProgress(0f);
                binding.cardSync.setClickable(true); // Unlock button

                if (success) {
                    // Save timestamp
                    prefs.edit().putLong("last_sync_timestamp", System.currentTimeMillis()).apply();
                    updateSyncStatusUI(); // Update text immediately
                    Toast.makeText(getContext(), "Backup Complete!", Toast.LENGTH_SHORT).show();
                } else {
                    binding.tvSyncStatus.setText("Failed: " + message);
                    Toast.makeText(getContext(), "Error: " + message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ------------------------------------------------------------------------
    // SECTION: LIVE TIME UPDATER
    // ------------------------------------------------------------------------

    private void startLiveTimeUpdater() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                // Only update if we are NOT syncing (so we don't overwrite "Syncing: 5/20")
                if (binding.cardSync.isClickable()) {
                    updateSyncStatusUI();
                }
                // Run again in 1 second
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void updateSyncStatusUI() {
        long lastSync = prefs.getLong("last_sync_timestamp", 0);
        if (lastSync == 0) {
            binding.tvSyncStatus.setText("Last synced: Never");
        } else {
            // Returns "5 seconds ago", "2 minutes ago", etc.
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    lastSync,
                    System.currentTimeMillis(),
                    DateUtils.SECOND_IN_MILLIS // High resolution
            );
            binding.tvSyncStatus.setText("Last synced: " + timeAgo);
        }
    }

    // ------------------------------------------------------------------------
    // SECTION: Connect to Gemini
    // ------------------------------------------------------------------------

    private void updateGeminiStatusUI() {
        String key = prefs.getString("user_gemini_key", null);
        if (key != null && !key.isEmpty()) {
            binding.tvGeminiStatus.setText("Connected");
            binding.tvGeminiStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));// Success Green
        } else {
            binding.tvGeminiStatus.setText("Tap to Connect");
            binding.tvGeminiStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary_light)); // Gray
        }
    }

    // ------------------------------------------------------------------------
    // SECTION: PREFERENCES & UTILS
    // ------------------------------------------------------------------------

    private void setupPreferences() {
        binding.switchDarkMode.setChecked(prefs.getBoolean("dark_mode", false));
        binding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        binding.switchNotifications.setChecked(prefs.getBoolean("notifications", true));
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notifications", isChecked).apply();
            if (isChecked) NotificationHelper.scheduleDailyReminder(requireContext());
            else NotificationHelper.cancelReminders(requireContext());
        });
    }

    private void animateEntry(View view, int delay) {
        view.setAlpha(0f);
        view.setTranslationY(50f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop the clock when leaving the screen
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }
}