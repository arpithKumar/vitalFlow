package com.vitalflow.app.ui.splash;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.vitalflow.app.R;
import com.vitalflow.app.enums.AppState;
import com.vitalflow.app.utils.BiometricAuthListener;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SplashFragment extends Fragment {

    public SplashFragment() {
        super(R.layout.fragment_splash);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvAppName = view.findViewById(R.id.tvAppName);
        TextView tvTagline = view.findViewById(R.id.tvTagline);

        animateTextSmoothly(tvAppName, "VitalFlow");

        if (tvTagline != null) {
            tvTagline.setAlpha(0f);
            tvTagline.animate().alpha(1f).setStartDelay(1000).setDuration(500).start();
            Animation popBounce = AnimationUtils.loadAnimation(requireContext(), R.anim.pop_bounce);
            popBounce.setStartOffset(300);
            tvTagline.startAnimation(popBounce);
        }

        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("IS_FIRST_RUN", true);
        boolean isBiometricEnabled = prefs.getBoolean("biometric_enabled", false); // Lock Setting

        Runnable navigationRunnable = () -> {
            int destination = getDestination(isFirstRun);
            if (destination == R.id.action_splash_to_home && isBiometricEnabled) {
                showBiometricPrompt(view);
            } else {
                navigate(view, destination);
            }
        };

        getViewLifecycleOwner().getLifecycle().addObserver(new SplashTimer(3000, navigationRunnable));
    }

    private void showBiometricPrompt(View view) {
        new BiometricAuthListener(requireActivity(), new BiometricAuthListener.OnAuthListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(requireContext(), "Unlocked! 🔓", Toast.LENGTH_SHORT).show();
                navigate(view, R.id.action_splash_to_home);
            }

            @Override
            public void onError(String error) {
                requireActivity().finishAffinity();
            }
        }).startAuth();
    }

    private void navigate(View view, int destinationId) {
        NavController navController = Navigation.findNavController(view);
        if (navController.getCurrentDestination() == null || navController.getCurrentDestination().getId() != R.id.splashFragment) {
            return;
        }

        NavOptions navOptions = new NavOptions.Builder()
                .setPopUpTo(R.id.splashFragment, true)
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .build();

        navController.navigate(destinationId, null, navOptions);
    }

    private int getDestination(boolean isFirstRun) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        AppState state = isFirstRun
                ? AppState.FIRST_RUN
                : (currentUser != null ? AppState.LOGGED_IN : AppState.LOGGED_OUT);
        return switch (state) {
            case FIRST_RUN -> R.id.action_splash_to_onboarding;
            case LOGGED_IN -> R.id.action_splash_to_home;
            case LOGGED_OUT -> R.id.action_splash_to_login;
        };
    }

    private void animateTextSmoothly(TextView textView, String fullText) {
        SpannableString spannableString = new SpannableString(fullText);
        ForegroundColorSpan transparentSpan = new ForegroundColorSpan(Color.TRANSPARENT);
        spannableString.setSpan(transparentSpan, 0, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        textView.setText(spannableString);
        textView.setVisibility(View.VISIBLE);

        ValueAnimator animator = ValueAnimator.ofInt(0, fullText.length());
        animator.setDuration(1500);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            int index = (int) animation.getAnimatedValue();
            SpannableString newSpannable = new SpannableString(fullText);
            newSpannable.setSpan(new ForegroundColorSpan(Color.WHITE), 0, index, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            newSpannable.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), index, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(newSpannable);
        });

        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        animator.start();
    }
}