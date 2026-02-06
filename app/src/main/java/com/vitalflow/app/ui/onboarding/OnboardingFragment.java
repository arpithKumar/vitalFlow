package com.vitalflow.app.ui.onboarding;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.vitalflow.app.R;
import com.vitalflow.app.databinding.FragmentOnboardingBinding;
import com.vitalflow.app.ui.chatbot.GeminiKeyBottomSheet;

public class OnboardingFragment extends Fragment {

    private FragmentOnboardingBinding binding;
    private OnboardingAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Setup Adapter
        adapter = new OnboardingAdapter(this);
        binding.viewPager.setAdapter(adapter);

        // 2. Setup Beautiful Animations
        setupSmoothTransitions();

        // 3. Connect Dots
        binding.pageIndicator.attachToPager(binding.viewPager);

        // 4. Handle Page Changes (Update Button Text)
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == adapter.getItemCount() - 1) {
                    binding.btnNext.setText("Get Started");
                    binding.btnNext.setIcon(null);
                    binding.btnSkip.setVisibility(View.GONE);
                } else {
                    binding.btnNext.setText("Next");
                    binding.btnNext.setIconResource(R.drawable.ic_arrow_forward);
                    binding.btnSkip.setVisibility(View.VISIBLE);
                }
            }
        });

        // 5. Button Actions
        binding.btnNext.setOnClickListener(v -> {
            int current = binding.viewPager.getCurrentItem();
            if (current < adapter.getItemCount() - 1) {
                binding.viewPager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });

        binding.btnSkip.setOnClickListener(v -> finishOnboarding());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If we are NOT on the first page, go back one page
                if (binding.viewPager.getCurrentItem() > 0) {
                    binding.viewPager.setCurrentItem(binding.viewPager.getCurrentItem() - 1);
                } else {
                    // If on the first page, disable this callback and let the system handle it (Close App)
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupSmoothTransitions() {
        CompositePageTransformer transformer = new CompositePageTransformer();
        transformer.addTransformer(new MarginPageTransformer(60));
        transformer.addTransformer((page, position) -> {
            float r = 1 - Math.abs(position);
            page.setScaleY(0.85f + r * 0.15f);
            page.setAlpha(0.5f + r * 0.5f);
        });
        binding.viewPager.setPageTransformer(transformer);
    }

    private void finishOnboarding() {
        // Save flag
        SharedPreferences prefs = requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("IS_FIRST_RUN", false).apply();
        Navigation.findNavController(requireView()).navigate(R.id.action_onboarding_to_login);
    }

    static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideHolder> {

        private final Fragment fragment;

        // Using your existing icons
        private final int[] images = {
                R.drawable.ic_medicine_pro,
                R.drawable.ic_heart,
                R.drawable.ic_period,
                R.drawable.ic_robot_face
        };

        private final String[] titles = {
                "Smart Medication Tracking",
                "Monitor Critical Vitals",
                "Cycle & Health Predictions",
                "Meet Your AI Coach"
        };

        private final String[] descriptionPerPage = {
                "Never miss a dose again. Set schedules, track inventory, and get timely reminders.",
                "Connect devices via Bluetooth or log manually to keep track of Heart Rate, BP, and Weight.",
                "AI-powered insights for your cycle and hydration to keep your health in perfect flow.",
                "Connect your Gemini API key now to unlock personalized health tips and a 24/7 chat assistant." // New Desc
        };

        public OnboardingAdapter(Fragment fragment) {
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SlideHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_slide, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SlideHolder holder, int position) {
            holder.img.setImageResource(images[position]);
            holder.title.setText(titles[position]);
            holder.desc.setText(descriptionPerPage[position]);

            if (position == 3) {
                holder.btnConnect.setVisibility(View.VISIBLE);
                holder.btnConnect.setOnClickListener(v -> {
                    GeminiKeyBottomSheet sheet = new GeminiKeyBottomSheet();
                    sheet.setListener(() -> {
                        holder.btnConnect.setText("Connected ✅");
                        holder.btnConnect.setEnabled(false);
                        holder.btnConnect.setIconTint(null);
                    });
                    sheet.show(fragment.getChildFragmentManager(), "GeminiSetup");
                });
                SharedPreferences prefs = fragment.requireContext().getSharedPreferences("vital_prefs", Context.MODE_PRIVATE);
                if (prefs.getString("user_gemini_key", null) != null) {
                    holder.btnConnect.setText("Connected ✅");
                    holder.btnConnect.setEnabled(false);
                }
            } else {
                holder.btnConnect.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return titles.length;
        }

        static class SlideHolder extends RecyclerView.ViewHolder {
            ImageView img;
            TextView title, desc;
            MaterialButton btnConnect;

            SlideHolder(View v) {
                super(v);
                img = v.findViewById(R.id.imgSlide);
                title = v.findViewById(R.id.tvTitle);
                desc = v.findViewById(R.id.tvDesc);
                btnConnect = v.findViewById(R.id.btnAction);
            }
        }
    }
}