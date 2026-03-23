package com.vitalflow.app.ui.meds;

import static androidx.core.content.ContextCompat.getDrawable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.FragmentMedsBinding;
import com.vitalflow.app.utils.NotificationHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class MedsFragment extends Fragment implements AddMedBottomSheet.OnMedicationAddedListener {

    @Inject
    GeminiRepository geminiRepository;

    @Inject
    AppDao appDao;

    private FragmentMedsBinding binding;
    private MedsAdapter adapter;
    private List<Medication> allMeds = new ArrayList<>();

    private final CompositeDisposable disposables = new CompositeDisposable();

    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri currentPhotoUri;
    private AlertDialog loadingDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && currentPhotoUri != null) {
                saveToVaultAndAnalyze(currentPhotoUri);
            }
        });
    }

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
        binding.fabScanMed.setOnClickListener(v -> launchCamera());

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

    private void launchCamera() {
        try {
            java.io.File photoFile = java.io.File.createTempFile("scan_", ".jpg", requireContext().getExternalCacheDir());
            currentPhotoUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", photoFile);
            takePictureLauncher.launch(currentPhotoUri);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Camera error", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToVaultAndAnalyze(android.net.Uri tempUri) {
        showLoadingDialog();
        new Thread(() -> {
            try {
                // 1. SAVE TO VAULT (Permanent internal storage)
                File vaultFile = new File(requireContext().getFilesDir(), "rx_" + System.currentTimeMillis() + ".jpg");
                InputStream in = requireContext().getContentResolver().openInputStream(tempUri);
                FileOutputStream out = new FileOutputStream(vaultFile);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                in.close();
                out.close();
                String finalVaultUri = Uri.fromFile(vaultFile).toString();

                // 2. SHRINK FOR AI (Prevent Crash)
                InputStream is = requireContext().getContentResolver().openInputStream(tempUri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, options);
                is.close();

                int inSampleSize = 1;
                if (options.outHeight > 800 || options.outWidth > 800) {
                    final int halfH = options.outHeight / 2, halfW = options.outWidth / 2;
                    while ((halfH / inSampleSize) >= 800 && (halfW / inSampleSize) >= 800)
                        inSampleSize *= 2;
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = inSampleSize;
                is = requireContext().getContentResolver().openInputStream(tempUri);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is, null, options);
                is.close();

                // 3. CALL GEMINI
                geminiRepository.analyzePrescription(bitmap, new GeminiRepository.PrescriptionCallback() {
                    @Override
                    public void onSuccess(GeminiRepository.PrescriptionResult result) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            dismissLoadingDialog();
                            AddMedBottomSheet sheet = new AddMedBottomSheet();
                            sheet.setAutoFillData(result.name, result.dosage, result.type, result.frequency, finalVaultUri);
                            sheet.setListener(MedsFragment.this);
                            sheet.show(getParentFragmentManager(), "AddMedSheet");
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            dismissLoadingDialog();
                            Toast.makeText(getContext(), "AI couldn't read label", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } catch (Exception e) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(getContext(), "Processing error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setView(getLayoutInflater().inflate(R.layout.dialog_loading, null))
                    .setCancelable(false).create();
            if (loadingDialog.getWindow() != null)
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
    }

    private void checkDailyResets(List<Medication> meds) {
        for (Medication m : meds) {
            String previousDate = m.getLastActionDate();
            m.checkDailyReset();
            // If the date changed, it means the logic ran and reset the counters
            if (!m.getLastActionDate().equals(previousDate)) {
                disposables.add(appDao.insertMedication(m)
                        .subscribeOn(Schedulers.io())
                        .subscribe());
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
        disposables.add(appDao.insertMedication(medication)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()) // Observe on Main for Toast
                .subscribe(() -> {
                    // 1. Show Feedback
                    Toast.makeText(getContext(), "Medication Saved", Toast.LENGTH_SHORT).show();

                    // 2. FORCE WORKER CHECK (The Fix)
                    NotificationHelper.triggerImmediateMedicationCheck(requireContext());

                }, Throwable::printStackTrace));
    }

    private void applyFilters() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String currentDayOfWeek = new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date());

        List<Medication> activeMeds = allMeds.stream()
                .filter(m -> m.getEndDate() == null || m.getEndDate().equals("Forever") || m.getEndDate().compareTo(today) >= 0)
                .filter(m -> {
                    String days = m.getSelectedDays();
                    return days == null || days.equals("Everyday") || days.contains(currentDayOfWeek);
                })
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

                disposables.add(appDao.insertMedication(med)
                        .subscribeOn(Schedulers.io())
                        .subscribe());

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
                                disposables.add(appDao.insertMedication(med)
                                        .subscribeOn(Schedulers.io())
                                        .subscribe());
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
                    disposables.add(appDao.deleteMedication(deletedItem)
                            .subscribeOn(Schedulers.io())
                            .subscribe());
                }

                // NEW: Use the helper to show delete snackbar
                showCustomSnackbar(
                        "Medication deleted",
                        Color.parseColor("#EF5350"), // Red
                        Color.parseColor("#B71C1C"), // Dark Red
                        android.R.drawable.ic_menu_delete, // Trash Icon
                        v -> {
                            if (deletedItem != null) {
                                disposables.add(appDao.insertMedication(deletedItem)
                                        .subscribeOn(Schedulers.io())
                                        .subscribe());
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
        disposables.clear();
        binding = null;
    }
}