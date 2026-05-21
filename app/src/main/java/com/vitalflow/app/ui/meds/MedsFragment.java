package com.vitalflow.app.ui.meds;

import static androidx.core.content.ContextCompat.getDrawable;

import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.FragmentMedsBinding;
import com.vitalflow.app.utils.BitmapUtils;
import com.vitalflow.app.utils.NotificationHelper;
import com.vitalflow.app.utils.UiUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class MedsFragment extends Fragment implements AddMedBottomSheet.OnMedicationAddedListener {

    private MedsViewModel viewModel;
    private GeminiRepository geminiRepository;

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
        viewModel = new ViewModelProvider(this).get(MedsViewModel.class);
        geminiRepository = viewModel.getGeminiRepository();

        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());
        binding.fabScanMed.setOnClickListener(v -> launchCamera());

        setupRecyclerView();

        // OBSERVE DATABASE
        viewModel.getAllMedications().observe(getViewLifecycleOwner(), meds -> {
            this.allMeds = meds;
            checkDailyResets(meds);
            applyFilters();
        });

        binding.fabAddMed.setOnClickListener(v ->
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                                .withEndAction(() -> showAddSheet(null)).start()).start());

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

    private void saveToVaultAndAnalyze(Uri tempUri) {
        showLoadingDialog();
        disposables.add(
                Single.fromCallable(() -> {
                            File vaultFile = new File(requireContext().getFilesDir(), "rx_" + System.currentTimeMillis() + ".jpg");
                            InputStream in = requireContext().getContentResolver().openInputStream(tempUri);
                            FileOutputStream out = new FileOutputStream(vaultFile);
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                            in.close();
                            out.close();
                            String finalVaultUri = Uri.fromFile(vaultFile).toString();

                            Bitmap bitmap = BitmapUtils.decodeSampledBitmap(
                                    requireContext().getContentResolver(), tempUri, 800, 800);
                            return new android.util.Pair<>(bitmap, finalVaultUri);
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                pair -> {
                                    Bitmap bitmap = pair.first;
                                    String finalVaultUri = pair.second;
                                    geminiRepository.analyzePrescription(bitmap, new GeminiRepository.PrescriptionCallback() {
                                        @Override
                                        public void onSuccess(GeminiRepository.PrescriptionResult result) {
                                            if (getActivity() != null)
                                                getActivity().runOnUiThread(() -> {
                                                    dismissLoadingDialog();
                                                    AddMedBottomSheet sheet = new AddMedBottomSheet();
                                                    sheet.setAutoFillData(result.name, result.dosage, result.type, result.frequency, finalVaultUri);
                                                    sheet.setListener(MedsFragment.this);
                                                    sheet.show(getParentFragmentManager(), "AddMedSheet");
                                                });
                                        }

                                        @Override
                                        public void onError(Throwable t) {
                                            if (getActivity() != null)
                                                getActivity().runOnUiThread(() -> {
                                                    dismissLoadingDialog();
                                                    Toast.makeText(getContext(), "AI couldn't read label", Toast.LENGTH_SHORT).show();
                                                });
                                        }
                                    });
                                },
                                error -> {
                                    dismissLoadingDialog();
                                    Toast.makeText(getContext(), "Processing error", Toast.LENGTH_SHORT).show();
                                }
                        )
        );
    }

    @SuppressLint("InflateParams")
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
                viewModel.insertMedication(m);
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
        disposables.add(viewModel.insertMedicationCompletable(medication)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
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

        if (checkedId == R.id.chipMorning) filteredList = filterByTime("Morning", activeMeds);
        else if (checkedId == R.id.chipAfternoon)
            filteredList = filterByTime("Afternoon", activeMeds);
        else if (checkedId == R.id.chipNight) filteredList = filterByTime("Night", activeMeds);
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

    private List<Medication> filterByTime(String category, List<Medication> source) {
        return source.stream()
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

                viewModel.insertMedication(med);

                // NEW: Use the helper to show success snackbar
                if ("TAKEN".equals(newStatus)) {
                    UiUtils.showCustomSnackbar(binding.getRoot(), requireContext(), "Marked as Taken",
                            ContextCompat.getColor(requireContext(), R.color.primary),
                            ContextCompat.getColor(requireContext(), R.color.text_on_primary_soft),
                            R.drawable.ic_check_circle, binding.fabAddMed,
                            v -> {
                                med.setDbStatus(oldStatus);
                                med.setTimesTakenToday(oldTaken);
                                med.setPillsLeft(oldStock);
                                viewModel.insertMedication(med);
                            });
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
                    viewModel.deleteMedication(deletedItem);
                }

                UiUtils.showCustomSnackbar(binding.getRoot(), requireContext(), "Medication deleted",
                        ContextCompat.getColor(requireContext(), R.color.bg_swipe_delete),
                        ContextCompat.getColor(requireContext(), R.color.icon_swipe_delete),
                        android.R.drawable.ic_menu_delete, binding.fabAddMed,
                        v -> {
                            if (deletedItem != null) {
                                viewModel.insertMedication(deletedItem);
                            }
                        });
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, float dX, float dY, int action, boolean isActive) {
                if (action == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View itemView = v.itemView;
                    ColorDrawable bg = new ColorDrawable(ContextCompat.getColor(requireContext(), R.color.bg_swipe_delete));
                    bg.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    bg.draw(c);
                    Drawable icon = getDrawable(requireContext(), R.drawable.ic_trash_can);
                    if (icon != null) {
                        icon.setTint(ContextCompat.getColor(requireContext(), R.color.icon_swipe_delete));
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }
}