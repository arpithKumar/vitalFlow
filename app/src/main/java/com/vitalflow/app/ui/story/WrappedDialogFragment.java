package com.vitalflow.app.ui.story;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.airbnb.lottie.LottieDrawable;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.DailyHealth;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.FragmentWrappedBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class WrappedDialogFragment extends DialogFragment {

    @Inject
    AppDao appDao;

    @Inject
    GeminiRepository geminiRepository;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private int currentPage = 0;
    private final int TOTAL_PAGES = 3;

    private FragmentWrappedBinding binding;

    private ProgressBar[] progressBars;

    // Story Data
    private final String[] pageTitles = new String[TOTAL_PAGES];
    private final String[] pageBodies = new String[TOTAL_PAGES];
    private final int[] pageLotties = new int[TOTAL_PAGES];

    // Auto-advance timer
    private final Handler storyHandler = new Handler(Looper.getMainLooper());
    private Runnable autoAdvanceRunnable;

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWrappedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind UI
        progressBars = new ProgressBar[]{binding.progress1, binding.progress2, binding.progress3};
        binding.btnClose.setOnClickListener(v -> dismiss());

        binding.tapZoneLeft.setEnabled(false);
        binding.tapZoneRight.setEnabled(false);

        fetchLastMonthData();
    }

    private void fetchLastMonthData() {
        // Calculate last month (e.g., "2026-02")
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        String lastMonthPrefix = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.getTime());
        String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime());

        disposables.add(appDao.getDailyHealthForMonthSync(lastMonthPrefix)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(healthList -> {
                    if (healthList.isEmpty()) {
                        showNoDataState(monthName);
                    } else {
                        generateStoryContent(monthName, healthList);
                    }
                }, Throwable::printStackTrace));
    }

    private void generateStoryContent(String monthName, List<DailyHealth> healthList) {
        int totalSteps = 0;
        int totalWater = 0;

        for (DailyHealth dh : healthList) {
            totalSteps += dh.getStepCount();
            totalWater += dh.getWaterIntakeMl();
        }

        // --- PAGE 1: Water ---
        pageTitles[0] = "Hydration Hero 💧";
        pageBodies[0] = "In " + monthName + ", you drank " + (totalWater / 1000) + " liters of water!";
        pageLotties[0] = R.raw.anim_water_splash;

        // --- PAGE 2: Steps ---
        pageTitles[1] = "On The Move 🚶‍♀️";
        pageBodies[1] = "You took " + String.format(Locale.US, "%,d", totalSteps) + " steps. Keep that momentum!";
        pageLotties[1] = R.raw.anim_sync;

        // --- PAGE 3: AI Insight (Generating dynamically) ---
        pageTitles[2] = "Your AI Summary ✨";
        pageBodies[2] = "Analyzing your vibe...";
        pageLotties[2] = R.raw.confetti;

        // Start the story while Gemini generates the final page
        setupTaps();
        renderPage(0);
        askGeminiForSummary(totalSteps, totalWater, monthName);
    }

    private void askGeminiForSummary(int steps, int water, String monthName) {
        String stats = "Month: " + monthName + ". Total Steps: " + steps + ". Total Water: " + water + "ml.";
        geminiRepository.getDailyTip(stats, new GeminiRepository.GeminiCallback() {
            @Override
            public void onSuccess(String tip) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        pageBodies[2] = "\"" + tip + "\"";
                        if (currentPage == 2) renderPage(2);
                    });
                }
            }

            @Override
            public void onError(Throwable t) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> pageBodies[2] = "You crushed it this month! Keep tracking to see more insights.");
                }
            }
        });
    }

    private void setupTaps() {
        binding.tapZoneLeft.setEnabled(true);
        binding.tapZoneRight.setEnabled(true);

        binding.tapZoneLeft.setOnClickListener(v -> {
            if (currentPage > 0) renderPage(currentPage - 1);
        });

        binding.tapZoneRight.setOnClickListener(v -> {
            if (currentPage < TOTAL_PAGES - 1) {
                renderPage(currentPage + 1);
            } else {
                dismiss(); // End of story
            }
        });
    }

    private void renderPage(int pageIndex) {
        currentPage = pageIndex;

        for (int i = 0; i < TOTAL_PAGES; i++) {
            progressBars[i].setProgress(i < pageIndex ? 100 : 0);
        }

        binding.tvStoryTitle.setAlpha(0f);
        binding.tvStoryBody.setAlpha(0f);
        binding.tvStoryTitle.animate().alpha(1f).setDuration(500).start();
        binding.tvStoryBody.animate().alpha(1f).setDuration(500).start();

        binding.tvStoryTitle.setText(pageTitles[pageIndex]);
        binding.tvStoryBody.setText(pageBodies[pageIndex]);
        binding.lottieStory.setAnimation(pageLotties[pageIndex]);

        if (pageIndex == 2) binding.lottieStory.setRepeatCount(0);
        else binding.lottieStory.setRepeatCount(LottieDrawable.INFINITE);

        binding.lottieStory.playAnimation();
        startPageTimer();
    }

    private void startPageTimer() {
        if (autoAdvanceRunnable != null) {
            storyHandler.removeCallbacks(autoAdvanceRunnable);
        }

        // Animate the current progress bar smoothly
        progressBars[currentPage].setProgress(0);
        ObjectAnimator animation = ObjectAnimator.ofInt(progressBars[currentPage], "progress", 0, 100);
        int PAGE_DURATION_MS = 5000;
        animation.setDuration(PAGE_DURATION_MS);
        animation.start();

        autoAdvanceRunnable = () -> {
            if (isAdded()) return;
            if (currentPage < TOTAL_PAGES - 1) {
                renderPage(currentPage + 1);
            } else {
                dismiss();
            }
        };
        storyHandler.postDelayed(autoAdvanceRunnable, PAGE_DURATION_MS);
    }

    private void showNoDataState(String monthName) {
        binding.tvStoryTitle.setText("No Data Yet!");
        binding.tvStoryBody.setText("We don't have enough data for " + monthName + ". Keep logging to unlock your next Recap!");
        binding.lottieStory.setVisibility(View.GONE);
        binding.progress1.setProgress(100);
        storyHandler.postDelayed(this::dismiss, 4000);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (autoAdvanceRunnable != null) {
            storyHandler.removeCallbacks(autoAdvanceRunnable);
        }
        disposables.clear();
        binding = null;
    }
}
