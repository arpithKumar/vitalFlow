package com.vitalflow.app.ui.meds;

import static androidx.core.content.ContextCompat.getDrawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.databinding.FragmentMedsBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MedsFragment extends Fragment implements AddMedBottomSheet.OnMedicationAddedListener {

    @Inject
    AppDao appDao;

    private FragmentMedsBinding binding;
    private MedsAdapter adapter;
    private List<Medication> allMeds = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMedsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());

        setupRecyclerView();

        // OBSERVE DATABASE
        appDao.getAllMedications().observe(getViewLifecycleOwner(), meds -> {
            this.allMeds = meds;
            checkDailyResets(meds);
            applyFilters();
        });

        binding.fabAddMed.setOnClickListener(v -> showAddSheet(null));

        setupSwipeToDelete();

        binding.chipGroupFilters.setOnCheckedStateChangeListener((group, checkedId) -> applyFilters());
    }

    private void checkDailyResets(List<Medication> meds) {
        for (Medication m : meds) {
            String previousDate = m.getLastActionDate();
            m.checkDailyReset();
            // If the date changed, it means the logic ran and reset the counters
            if (!m.getLastActionDate().equals(previousDate)) {
                final Medication toUpdate = m;
                executor.execute(() -> appDao.insertMedication(toUpdate));
            }
        }
    }

    private void showAddSheet(Medication medToEdit) {
        AddMedBottomSheet sheet = new AddMedBottomSheet();
        if (medToEdit != null) sheet.setMedToEdit(medToEdit);
        sheet.setListener(this);
        sheet.show(getParentFragmentManager(), "AddMedSheet");
    }

    @Override
    public void onMedicationAdded(Medication medication) {
        executor.execute(() -> appDao.insertMedication(medication));
    }

    private void applyFilters() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        List<Medication> activeMeds = allMeds.stream()
                .filter(m -> m.getEndDate() == null || m.getEndDate().equals("Forever") || m.getEndDate().compareTo(today) >= 0)
                .toList();

        int checkedId = binding.chipGroupFilters.getCheckedChipId();
        List<Medication> filteredList;

        if (checkedId == R.id.chipMorning) filteredList = filterByTime("Morning");
        else if (checkedId == R.id.chipAfternoon) filteredList = filterByTime("Afternoon");
        else if (checkedId == R.id.chipNight) filteredList = filterByTime("Night");
        else {
            filteredList = new ArrayList<>(activeMeds);
            if (checkedId == -1) binding.chipAll.setChecked(true);
        }

        updateChipVisuals(binding.chipGroupFilters.getCheckedChipId(), filteredList.size());

        if (filteredList.isEmpty()) {
            if (allMeds.isEmpty()) {
                binding.tvEmptyTitle.setText("No medications yet");
                binding.tvEmptySubtitle.setText("Tap + to add your first prescription");
                binding.scrollFilters.setVisibility(View.GONE);
            } else {
                binding.tvEmptyTitle.setText("No medications found");
                binding.tvEmptySubtitle.setText("Try selecting a different time filter");
                binding.scrollFilters.setVisibility(View.VISIBLE);
            }
            binding.layoutEmpty.setVisibility(View.VISIBLE);
            binding.rvMeds.setVisibility(View.GONE);
        } else {
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.scrollFilters.setVisibility(View.VISIBLE);
            binding.rvMeds.setVisibility(View.VISIBLE);
            adapter.updateList(filteredList);
        }
    }

    private List<Medication> filterByTime(String category) {
        return allMeds.stream()
                .filter(m -> m.getTimeCategory().equals(category))
                .collect(Collectors.toList());
    }

    private void updateChipVisuals(int activeChipId, int count) {
        TransitionManager.beginDelayedTransition(binding.chipGroupFilters);
        binding.chipAll.setText("All");
        binding.chipMorning.setText("Morning");
        binding.chipAfternoon.setText("Afternoon");
        binding.chipNight.setText("Night");

        Chip activeChip = binding.getRoot().findViewById(activeChipId);
        if (activeChip != null) {
            String base = activeChip.getText().toString().split(" ")[0];
            activeChip.setText(base + " (" + count + ")");
        }
    }

    private void setupRecyclerView() {
        adapter = new MedsAdapter(new MedsAdapter.OnMedClickListener() {
            @Override
            public void onStatusChange(Medication med, String newStatus) {
                String oldStatus = med.getStatus();
                int oldTaken = med.getTimesTakenToday();
                int oldStock = med.getPillsLeft();

                if ("TAKEN".equals(newStatus)) {
                    int current = med.getTimesTakenToday();
                    if (current < med.getFrequencyPerDay()) {
                        med.setTimesTakenToday(current + 1);
                        if (med.getPillsLeft() > 0) med.setPillsLeft(med.getPillsLeft() - 1);
                    }
                } else if ("SKIPPED".equals(newStatus)) {
                    med.setDbStatus("SKIPPED");
                } else if ("PENDING".equals(newStatus)) {
                    int taken = med.getTimesTakenToday();
                    if (taken > 0) {
                        med.setTimesTakenToday(taken - 1);
                        med.setPillsLeft(med.getPillsLeft() + 1);
                    }
                    med.setDbStatus("PENDING");
                }

                executor.execute(() -> appDao.insertMedication(med));

                // NEW: Use the helper to show success snackbar
                if ("TAKEN".equals(newStatus)) {
                    showCustomSnackbar(
                            "Marked as Taken",
                            Color.parseColor("#00897B"), // Teal
                            Color.parseColor("#E0F2F1"), // Light Teal
                            R.drawable.ic_check_circle,
                            v -> {
                                med.setDbStatus(oldStatus);
                                med.setTimesTakenToday(oldTaken);
                                med.setPillsLeft(oldStock);
                                executor.execute(() -> appDao.insertMedication(med));
                            }
                    );
                }
            }

            @Override
            public void onCardClick(Medication med) {
                showAddSheet(med);
            }
        });

        binding.rvMeds.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvMeds.setAdapter(adapter);
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, @NonNull RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                final Medication deletedItem = adapter.getItem(position);
                if (deletedItem != null) {
                    executor.execute(() -> appDao.deleteMedication(deletedItem));
                }

                // NEW: Use the helper to show delete snackbar
                showCustomSnackbar(
                        "Medication deleted",
                        Color.parseColor("#EF5350"), // Red
                        Color.parseColor("#B71C1C"), // Dark Red
                        android.R.drawable.ic_menu_delete, // Trash Icon
                        v -> {
                            if (deletedItem != null) {
                                executor.execute(() -> appDao.insertMedication(deletedItem));
                            }
                        }
                );
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, float dX, float dY, int action, boolean isActive) {
                if (action == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View itemView = v.itemView;
                    ColorDrawable bg = new ColorDrawable(Color.parseColor("#FFEBEE"));
                    bg.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    bg.draw(c);
                    Drawable icon = getDrawable(requireContext(), R.drawable.ic_trash_can);
                    if (icon != null) {
                        icon.setTint(Color.parseColor("#C62828"));
                        int h = itemView.getBottom() - itemView.getTop();
                        int m = (h - icon.getIntrinsicHeight()) / 2;
                        int top = itemView.getTop() + m;
                        int left = itemView.getRight() - m - icon.getIntrinsicWidth();
                        icon.setBounds(left, top, itemView.getRight() - m, top + icon.getIntrinsicHeight());
                        icon.draw(c);
                    }
                }
                super.onChildDraw(c, r, v, dX, dY, action, isActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(binding.rvMeds);
    }

    // --- REUSABLE HELPER METHOD ---
    private void showCustomSnackbar(String message, int backgroundColor, int actionTextColor, int iconResId, View.OnClickListener onUndo) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), message, 4000);

        // 1. Background Styling
        View view = snackbar.getView();
        view.setBackgroundTintList(null);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28f * getResources().getDisplayMetrics().density);
        bg.setColor(backgroundColor);
        view.setBackground(bg);

        // 2. Positioning (Anchored to FAB)
        // This is better than hardcoded margins!
        if (binding.fabAddMed != null) {
            snackbar.setAnchorView(binding.fabAddMed);
        }

        // 3. Text & Colors
        snackbar.setTextColor(Color.WHITE);
        snackbar.setActionTextColor(actionTextColor);

        // 4. Message Icon
        TextView tvMessage = view.findViewById(com.google.android.material.R.id.snackbar_text);
        Drawable icon = getDrawable(requireContext(), iconResId);
        float d = getResources().getDisplayMetrics().density;

        if (icon != null) {
            int iconSize = (int) (24 * d);
            icon.setBounds(0, 0, iconSize, iconSize);
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, Color.WHITE);
            tvMessage.setCompoundDrawables(icon, null, null, null);
            tvMessage.setCompoundDrawablePadding((int) (12 * d));
        }

        // 5. Action Arrow Icon
        TextView btnAction = view.findViewById(com.google.android.material.R.id.snackbar_action);
        Drawable arrowIcon = getDrawable(requireContext(), R.drawable.ic_arrow_right);

        if (arrowIcon != null) {
            int arrowSize = (int) (18 * d);
            arrowIcon.setBounds(0, 0, arrowSize, arrowSize);
            androidx.core.graphics.drawable.DrawableCompat.setTint(arrowIcon, actionTextColor);
            btnAction.setCompoundDrawables(null, null, arrowIcon, null);
            btnAction.setCompoundDrawablePadding((int) (4 * d));
        }

        snackbar.setAction("UNDO", onUndo);
        snackbar.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}