package com.vitalflow.app.ui.meds;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vitalflow.app.R;
import com.vitalflow.app.data.dao.AppDao;
import com.vitalflow.app.data.entity.Medication;
import com.vitalflow.app.data.repository.GeminiRepository;
import com.vitalflow.app.databinding.BottomSheetAddMedBinding;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@AndroidEntryPoint
public class AddMedBottomSheet extends BottomSheetDialogFragment {

    @Inject
    AppDao appDao;

    @Inject
    GeminiRepository geminiRepository;

    private BottomSheetAddMedBinding binding;
    private OnMedicationAddedListener listener;
    private Medication medToEdit = null;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private int frequency = 1;

    private String autoName, autoDosage, autoType, savedPrescriptionUri;
    private int autoFreq = 1;

    public interface OnMedicationAddedListener {
        void onMedicationAdded(Medication medication);
    }

    public void setListener(OnMedicationAddedListener listener) {
        this.listener = listener;
    }

    public void setMedToEdit(Medication med) {
        this.medToEdit = med;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    public void setAutoFillData(String name, String dosage, String type, int freq, String uri) {
        this.autoName = name;
        this.autoDosage = dosage;
        this.autoType = type;
        this.autoFreq = freq;
        this.savedPrescriptionUri = uri;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAddMedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (medToEdit != null) {
            prefillData();
        } else if (autoName != null) {
            binding.etName.setText(autoName);
            binding.etDosage.setText(autoDosage);
            frequency = autoFreq > 0 ? autoFreq : 1;
            binding.tvFrequency.setText(String.valueOf(frequency));

            if ("Liquid".equalsIgnoreCase(autoType)) binding.chipLiquid.setChecked(true);
            else if ("Capsule".equalsIgnoreCase(autoType)) binding.chipCapsule.setChecked(true);
            else if ("Injection".equalsIgnoreCase(autoType))
                binding.chipInjections.setChecked(true);
            else binding.chipPill.setChecked(true);

            setDefaultDateTime();
            updateQuantityHint(binding.chipGroupType.getCheckedChipId());
        } else {
            setDefaultDateTime();
            updateQuantityHint(R.id.chipPill);
        }

        binding.chipGroupType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                updateQuantityHint(checkedIds.get(0));
            }
        });
        binding.cardTimePicker.setOnClickListener(v -> showTimePicker());
        binding.cardDatePicker.setOnClickListener(v -> showDatePicker("start"));
        binding.cardEndDatePicker.setOnClickListener(v -> showDatePicker("end"));
        binding.btnSaveMed.setOnClickListener(v -> initiateSaveProcess());
        binding.btnPlusFreq.setOnClickListener(v -> {
            if (frequency < 5) frequency++;
            binding.tvFrequency.setText(String.valueOf(frequency));
        });
        binding.btnMinusFreq.setOnClickListener(v -> {
            if (frequency > 1) frequency--;
            binding.tvFrequency.setText(String.valueOf(frequency));
        });
    }

    private void initiateSaveProcess() {
        String name = binding.etName.getText().toString().trim();
        String dosage = binding.etDosage.getText().toString().trim();

        if (name.isEmpty() || dosage.isEmpty()) {
            Toast.makeText(getContext(), "Please enter Name and Dosage", Toast.LENGTH_SHORT).show();
            return;
        }

        // If editing, skip AI check (assuming user knows)
        if (medToEdit != null) {
            finalizeSave(name, dosage);
            return;
        }

        // 1. Show Loading UI
        binding.btnSaveMed.setEnabled(false);
        binding.btnSaveMed.setText("Checking Interactions...");

        // 2. Fetch Existing Meds in Background
        disposables.add(appDao.getAllMedicationsSync()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(existingMeds -> {
                    if (existingMeds.isEmpty()) {
                        finalizeSave(name, dosage);
                    } else {
                        checkInteractionsWithGemini(existingMeds, name, dosage);
                    }
                }, throwable -> {
                    finalizeSave(name, dosage);
                }));
    }

    private void checkInteractionsWithGemini(List<Medication> existingMeds, String name, String dosage) {
        List<String> medNames = existingMeds.stream().map(Medication::getName).toList();

        String prompt = "I am currently taking: " + medNames.toString() +
                ". I am about to add: " + name + " (" + dosage + "). " +
                "Are there any known severe drug interactions? " +
                "Answer strictly in this format: " +
                "YES/NO: [Short explanation of why]. Keep it under 2 sentences.";

        geminiRepository.sendMessage(prompt, null, new GeminiRepository.GeminiCallback() {
            @Override
            public void onSuccess(String response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> handleAiResponse(response, name, dosage));
                }
            }

            @Override
            public void onError(Throwable t) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> finalizeSave(name, dosage));
                }
            }
        });
    }

    private void handleAiResponse(String response, String name, String dosage) {
        binding.btnSaveMed.setEnabled(true);
        binding.btnSaveMed.setText("Save Schedule");

        if (response.toUpperCase().startsWith("YES")) {
            String warning = response.replaceFirst("YES:", "").trim();

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("⚠️ Interaction Warning")
                    .setMessage("Gemini AI detected a potential conflict:\n\n" + warning + "\n\nDo you still want to add this medication?")
                    .setIcon(R.drawable.ic_warning) // Ensure you have a warning icon or use generic
                    .setPositiveButton("Add Anyway", (dialog, which) -> finalizeSave(name, dosage))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            finalizeSave(name, dosage);
        }
    }

    private void prefillData() {
        binding.tvSheetTitle.setText("Edit Prescription");
        binding.btnSaveMed.setText("Update Schedule");

        binding.etName.setText(medToEdit.getName());
        binding.etDosage.setText(medToEdit.getDosage());
        binding.etDoctor.setText(medToEdit.getDoctorName());
        binding.etHospital.setText(medToEdit.getHospitalName());
        binding.etQuantity.setText(String.valueOf(medToEdit.getPillsLeft()));
        binding.tvSelectedTime.setText(medToEdit.getTime());
        binding.tvStartDate.setText(medToEdit.getStartDate());
        frequency = medToEdit.getFrequencyPerDay();
        binding.tvFrequency.setText(String.valueOf(frequency));
        binding.tvEndDate.setText(medToEdit.getEndDate());

        String type = medToEdit.getType();
        if (type != null) {
            if (type.equals("Pill")) binding.chipPill.setChecked(true);
            else if (type.equals("Capsule")) binding.chipCapsule.setChecked(true);
            else if (type.equals("Liquid")) binding.chipLiquid.setChecked(true);
            else if (type.equals("Injection")) binding.chipInjections.setChecked(true);
        }
        updateQuantityHint(binding.chipGroupType.getCheckedChipId());

        String savedDays = medToEdit.getSelectedDays();
        if (savedDays != null && !savedDays.equals("Everyday")) {
            if (savedDays.contains("Mon")) binding.chipMon.setChecked(true);
            if (savedDays.contains("Tue")) binding.chipTue.setChecked(true);
            if (savedDays.contains("Wed")) binding.chipWed.setChecked(true);
            if (savedDays.contains("Thu")) binding.chipThu.setChecked(true);
            if (savedDays.contains("Fri")) binding.chipFri.setChecked(true);
            if (savedDays.contains("Sat")) binding.chipSat.setChecked(true);
            if (savedDays.contains("Sun")) binding.chipSun.setChecked(true);
        }
    }

    private void setupActions(Map<Integer, Runnable> actions) {
        actions.put(R.id.chipLiquid, () -> {
            binding.tvQuantityLabel.setText("Total Volume (ml)");
            binding.etQuantity.setHint("e.g. 200");
        });

        actions.put(R.id.chipInjections, () -> {
            binding.tvQuantityLabel.setText("Total Units / Vials");
            binding.etQuantity.setHint("e.g. 5");
        });

        actions.put(R.id.chipCapsule, () -> {
            binding.tvQuantityLabel.setText("Total Quantity (Capsules)");
            binding.etQuantity.setHint("e.g. 30");
        });
    }

    private void updateQuantityHint(int checkedId) {
        final Map<Integer, Runnable> actions = new HashMap<>();
        final Runnable defaultAction = () -> {
            binding.tvQuantityLabel.setText("Total Quantity (Pills)");
            binding.etQuantity.setHint("e.g. 30");
        };

        setupActions(actions);
        actions.getOrDefault(checkedId, defaultAction).run();
    }

    private void finalizeSave(String name, String dosage) {
        String doctor = binding.etDoctor.getText().toString().trim();
        String hospital = binding.etHospital.getText().toString().trim();
        String time = binding.tvSelectedTime.getText().toString();
        String startDate = binding.tvStartDate.getText().toString();
        String endDate = binding.tvEndDate.getText().toString();

        String qtyStr = binding.etQuantity.getText().toString().trim();
        int quantity = 30;
        if (!qtyStr.isEmpty()) {
            try {
                quantity = Integer.parseInt(qtyStr);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Invalid quantity, defaulting to 30", Toast.LENGTH_SHORT).show();
            }
        }

        String type = "Pill";
        int selectedId = binding.chipGroupType.getCheckedChipId();
        if (selectedId != View.NO_ID) {
            Chip selectedChip = binding.getRoot().findViewById(selectedId);
            if (selectedChip != null) type = selectedChip.getText().toString();
        }

        List<String> selectedDaysList = new ArrayList<>();
        if (binding.chipMon.isChecked()) selectedDaysList.add("Mon");
        if (binding.chipTue.isChecked()) selectedDaysList.add("Tue");
        if (binding.chipWed.isChecked()) selectedDaysList.add("Wed");
        if (binding.chipThu.isChecked()) selectedDaysList.add("Thu");
        if (binding.chipFri.isChecked()) selectedDaysList.add("Fri");
        if (binding.chipSat.isChecked()) selectedDaysList.add("Sat");
        if (binding.chipSun.isChecked()) selectedDaysList.add("Sun");

        String finalDaysString = "Everyday";
        if (!selectedDaysList.isEmpty() && selectedDaysList.size() < 7) {
            finalDaysString = TextUtils.join(",", selectedDaysList);
        }

        if (name.isEmpty() || dosage.isEmpty()) {
            Toast.makeText(getContext(), "Please enter Name and Dosage", Toast.LENGTH_SHORT).show();
            return;
        }

        if (medToEdit == null) {
            Medication newMed = new Medication(name, dosage, type, time, doctor, hospital,
                    startDate, endDate, quantity, frequency, finalDaysString, savedPrescriptionUri);
            if (listener != null) listener.onMedicationAdded(newMed);
        } else {
            medToEdit.setName(name);
            medToEdit.setDosage(dosage);
            medToEdit.setType(type);
            medToEdit.setTime(time);
            medToEdit.setDoctorName(doctor);
            medToEdit.setHospitalName(hospital);
            medToEdit.setStartDate(startDate);
            medToEdit.setFrequencyPerDay(frequency);
            medToEdit.setEndDate(endDate);
            medToEdit.setPillsLeft(quantity);
            medToEdit.setSelectedDays(finalDaysString);
            if (savedPrescriptionUri != null) {
                medToEdit.setPrescriptionUri(savedPrescriptionUri);
            }
            if (listener != null) listener.onMedicationAdded(medToEdit);
        }
        dismiss();
    }

    private void setDefaultDateTime() {
        Calendar c = Calendar.getInstance();
        updateTimeLabel(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        updateDateLabel("", c);
    }

    private void showTimePicker() {
        Calendar c = Calendar.getInstance();
        new TimePickerDialog(getContext(), (view, hourOfDay, minute) ->
                updateTimeLabel(hourOfDay, minute),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
    }

    private void updateTimeLabel(int hourOfDay, int minute) {
        String amPm = (hourOfDay >= 12) ? "PM" : "AM";
        int hour12 = (hourOfDay > 12) ? hourOfDay - 12 : hourOfDay;
        if (hour12 == 0) hour12 = 12;
        binding.tvSelectedTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, amPm));
    }

    private void showDatePicker(String type) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day);
            updateDateLabel(type, selected);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel(String type, Calendar c) {
        String dateStr = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(c.getTime());
        if ("start".equals(type)) {
            binding.tvStartDate.setText(dateStr);
        } else if ("end".equals(type)) {
            binding.tvEndDate.setText(dateStr);
        } else {
            binding.tvStartDate.setText(dateStr);
            binding.tvEndDate.setText("Forever");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }
}