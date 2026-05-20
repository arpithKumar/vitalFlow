package com.vitalflow.app.ui.vitals;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.vitalflow.app.R;
import com.vitalflow.app.data.entity.Vital;
import com.vitalflow.app.databinding.LayoutAddVitalSheetBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class AddVitalBottomSheet extends BottomSheetDialogFragment {

    private LayoutAddVitalSheetBinding binding;
    private OnVitalAddedListener listener;
    private static final String TAG = "AddVitalBottomSheet";

    // --- BLUETOOTH VARIABLES ---
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt gatt;
    private BtDeviceAdapter btDeviceAdapter;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final UUID SERVICE_HR = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_SPO2 = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_BP = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb"); // Blood Pressure
    private static final UUID SERVICE_WEIGHT = UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb"); // Weight Scale

    private static final UUID CHAR_HR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_SPO2 = UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb");
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // --- LAUNCHERS (The modern way to handle results) ---
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue()) allGranted = false;
                }
                if (allGranted) {
                    startBleScan(); // Retry scan after permission grant
                } else {
                    Toast.makeText(getContext(), "Permissions required to scan", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    startBleScan(); // Retry scan after BT enabled
                } else {
                    Toast.makeText(getContext(), "Bluetooth is required", Toast.LENGTH_SHORT).show();
                }
            });

    public interface OnVitalAddedListener {
        void onVitalAdded(Vital vital);
    }

    public void setListener(OnVitalAddedListener listener) {
        this.listener = listener;
    }

    // --- 1. THEME & ANIMATION ---
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutAddVitalSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Chip Logic to toggle 2nd input field (BP needs diastolic)
        binding.chipGroupType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                int checkedId = checkedIds.get(0);
                if (checkedId == R.id.chipBP) {
                    binding.inputLayout2.setVisibility(View.VISIBLE);
                    binding.inputLayout1.setHint("Systolic");
                } else {
                    binding.inputLayout2.setVisibility(View.GONE);
                    binding.inputLayout1.setHint("Value");
                }
            }
        });

        // Bluetooth Actions
        binding.cardBluetoothContainer.setOnClickListener(v -> {
            binding.layoutBtInitial.setVisibility(View.GONE);
            binding.layoutBtScanning.setVisibility(View.VISIBLE);
            startBleScan();
        });

        binding.btnCancelBt.setOnClickListener(v -> {
            stopScan();
            binding.layoutBtScanning.setVisibility(View.GONE);
            binding.layoutBtInitial.setVisibility(View.VISIBLE);
        });

        binding.btnSaveVital.setOnClickListener(v -> saveVital());
    }

    private void saveVital() {
        String val1 = Objects.requireNonNull(binding.etValue1.getText()).toString();
        String val2 = Objects.requireNonNull(binding.etValue2.getText()).toString(); // Can be empty

        if (val1.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a value", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = "Heart Rate";
        String unit = "bpm";
        int idType = binding.chipGroupType.getCheckedChipId();

        if (idType == R.id.chipBP) {
            type = "Blood Pressure";
            unit = "mmHg";
            if (val2.isEmpty()) {
                Toast.makeText(getContext(), "Enter Diastolic Value", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (idType == R.id.chipOxygen) {
            type = "Oxygen (SpO2)";
            unit = "%";
        } else if (idType == R.id.chipWeight) {
            type = "Weight";
            unit = "kg";
        }

        StringBuilder ctx = new StringBuilder();
        for (int id : binding.chipGroupContext.getCheckedChipIds()) {
            Chip c = binding.getRoot().findViewById(id);
            if (c != null) ctx.append(c.getText()).append(", ");
        }

        StringBuilder symptoms = new StringBuilder();
        for (int id : binding.chipGroupSymptoms.getCheckedChipIds()) {
            Chip c = binding.getRoot().findViewById(id);
            if (c != null) symptoms.append(c.getText()).append(", ");
        }

        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // Create Object
        Vital nv = new Vital(type, val1, val2.isEmpty() ? null : val2, unit, date, System.currentTimeMillis(), ctx.toString().trim(), symptoms.toString().trim());

        if (listener != null) listener.onVitalAdded(nv);
        stopScan(); // Ensure cleanup
        dismiss();
    }

    // --- BLUETOOTH LOGIC (Copied & Adapted) ---
    private boolean hasPermissions() {
        Context context = getContext();
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void startBleScan() {
        if (!hasPermissions()) {
            requestBlePermissions();
            return;
        }

        BluetoothManager bm = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bm.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(enableBtIntent);
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Toast.makeText(getContext(),
                            "Bluetooth Error: Scanner not available",
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        btDeviceAdapter = new BtDeviceAdapter(this::connectGatt);

        binding.rvBtDevices.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvBtDevices.setAdapter(btDeviceAdapter);

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_HR)).build());
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_SPO2)).build());
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_BP)).build());
        filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_WEIGHT)).build());

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        isScanning = true;
        bleScanner.startScan(filters, settings, leScanCallback);

        handler.postDelayed(this::stopScan, 15000);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (isScanning && bleScanner != null && bluetoothAdapter != null
                && bluetoothAdapter.isEnabled() && hasPermissions()) {
            try {
                bleScanner.stopScan(leScanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop BLE scan", e);
            }
        }
        isScanning = false;

        if (binding != null && binding.layoutBtScanning.getVisibility() == View.VISIBLE) {
            handler.post(() -> {
                if (binding != null) {
                    binding.layoutBtScanning.setVisibility(View.GONE);
                    binding.layoutBtInitial.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (btDeviceAdapter != null && result.getDevice() != null) {
                btDeviceAdapter.addDevice(result.getDevice());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            if (binding != null) {
                handler.post(() -> Toast.makeText(getContext(), "Scan failed: Error " + errorCode, Toast.LENGTH_SHORT).show());
            }
        }
    };

    @SuppressLint({"MissingPermission", "SetTextI18n"})
    private void connectGatt(BluetoothDevice device) {
        if (!hasPermissions()) return;
        stopScan();

        binding.tvBtStatus.setText("Connecting to " + (device.getName() != null ? device.getName() : "Device") + "...");

        gatt = device.connectGatt(getContext(), false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    handler.post(() -> {
                        if (binding != null)
                            binding.tvBtStatus.setText("Connected! Discovering Services...");
                    });
                    if (hasPermissions()) {
                        gatt.requestMtu(247);
                        gatt.discoverServices();
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handler.post(() -> {
                        if (binding != null) binding.tvBtStatus.setText("Disconnected. Try again.");
                    });
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (!hasPermissions()) return;
                BluetoothGattService hrService = gatt.getService(SERVICE_HR);
                if (hrService != null) enableNotification(gatt, hrService, CHAR_HR);

                BluetoothGattService spo2Service = gatt.getService(SERVICE_SPO2);
                if (spo2Service != null) enableNotification(gatt, spo2Service, CHAR_SPO2);
            }

            @SuppressLint("MissingPermission")
            private void enableNotification(BluetoothGatt gatt, BluetoothGattService service, UUID charUuid) {
                if (!hasPermissions()) return;

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
                if (characteristic == null) return;

                gatt.setCharacteristicNotification(characteristic, true);

                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
                if (descriptor == null) return;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    writeDescriptorLegacy(gatt, descriptor);
                }
            }

            @SuppressWarnings("deprecation")
            private void writeDescriptorLegacy(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }

            @Override
            public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                                @NonNull BluetoothGattCharacteristic characteristic,
                                                @NonNull byte[] value
            ) {
                handleCharacteristic(characteristic.getUuid(), value);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onCharacteristicChanged(
                    BluetoothGatt gatt,
                    BluetoothGattCharacteristic characteristic
            ) {
                handleCharacteristic(characteristic.getUuid(), characteristic.getValue());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScan();
        if (gatt != null && hasPermissions()) {
            try {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.disconnect();
                    gatt.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting/closing GATT", e);
            }
        }
        binding = null;
    }

    private void handleCharacteristic(UUID uuid, byte[] data) {
        if (data == null || data.length < 2) return;

        if (CHAR_HR.equals(uuid)) {

            int flag = data[0] & 0xFF;
            boolean is16Bit = (flag & 0x01) != 0;

            int hr = is16Bit
                    ? ((data[2] & 0xFF) << 8) | (data[1] & 0xFF)
                    : data[1] & 0xFF;

            handler.post(() -> {
                if (binding != null) {
                    binding.etValue1.setText(String.valueOf(hr));
                    binding.chipGroupType.check(R.id.chipHeartRate);
                    binding.tvBtStatus.setText("Live Data: " + hr + " bpm");
                }
            });

        } else if (CHAR_SPO2.equals(uuid)) {

            int spo2 = data[1] & 0xFF;

            handler.post(() -> {
                if (binding != null) {
                    binding.etValue1.setText(String.valueOf(spo2));
                    binding.chipGroupType.check(R.id.chipOxygen);
                    binding.tvBtStatus.setText("Live Data: " + spo2 + "% SpO2");
                }
            });
        }
    }
}