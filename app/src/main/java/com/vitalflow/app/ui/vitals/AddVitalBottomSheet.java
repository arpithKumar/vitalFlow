package com.vitalflow.app.ui.vitals;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class AddVitalBottomSheet extends BottomSheetDialogFragment {

    private LayoutAddVitalSheetBinding binding;
    private OnVitalAddedListener listener;

    // --- BLUETOOTH VARIABLES ---
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt gatt;
    private BtDeviceAdapter btDeviceAdapter;
    private boolean isScanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final UUID SERVICE_HR = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_HR = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_SPO2 = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb");
    private static final UUID CHAR_SPO2 = UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb");
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

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
        binding.chipGroupType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipBP) {
                binding.inputLayout2.setVisibility(View.VISIBLE);
                binding.inputLayout1.setHint("Systolic");
            } else {
                binding.inputLayout2.setVisibility(View.GONE);
                binding.inputLayout1.setHint("Value");
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
        String val1 = binding.etValue1.getText().toString();
        String val2 = binding.etValue2.getText().toString(); // Can be empty

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

        String date = new SimpleDateFormat("Today, h:mm a", Locale.getDefault()).format(new Date());

        // Create Object
        Vital nv = new Vital(type, val1, val2.isEmpty() ? null : val2, unit, date, System.currentTimeMillis(), ctx.toString().trim(), symptoms.toString().trim());

        if (listener != null) listener.onVitalAdded(nv);
        stopScan(); // Ensure cleanup
        dismiss();
    }

    // --- BLUETOOTH LOGIC (Copied & Adapted) ---
    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 101);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
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
            Toast.makeText(getContext(), "Enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        btDeviceAdapter = new BtDeviceAdapter(this::connectGatt);

        binding.rvBtDevices.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvBtDevices.setAdapter(btDeviceAdapter);

        isScanning = true;
        bleScanner.startScan(leScanCallback);

        handler.postDelayed(this::stopScan, 15000);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (isScanning && bleScanner != null && hasPermissions()) {
            isScanning = false;
            bleScanner.stopScan(leScanCallback);
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (btDeviceAdapter != null) btDeviceAdapter.addDevice(result.getDevice());
        }
    };

    @SuppressLint("MissingPermission")
    private void connectGatt(BluetoothDevice device) {
        if (!hasPermissions()) return;
        stopScan();

        binding.tvBtStatus.setText("Connecting to " + device.getName() + "...");

        gatt = device.connectGatt(getContext(), false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    handler.post(() -> binding.tvBtStatus.setText("Connected! Discovering..."));
                    if (hasPermissions()) gatt.discoverServices();
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

            private void enableNotification(BluetoothGatt gatt, BluetoothGattService service, UUID charUuid) {
                if (!hasPermissions()) return;
                BluetoothGattCharacteristic c = service.getCharacteristic(charUuid);
                if (c != null) {
                    gatt.setCharacteristicNotification(c, true);
                    BluetoothGattDescriptor d = c.getDescriptor(CONFIG_DESCRIPTOR);
                    if (d != null) {
                        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(d);
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c) {
                if (CHAR_HR.equals(c.getUuid())) {
                    int flag = c.getProperties();
                    int format = (flag & 0x01) != 0 ? BluetoothGattCharacteristic.FORMAT_UINT16 : BluetoothGattCharacteristic.FORMAT_UINT8;
                    final int hr = c.getIntValue(format, 1);
                    handler.post(() -> {
                        binding.etValue1.setText(String.valueOf(hr));
                        binding.chipGroupType.check(R.id.chipHeartRate);
                        binding.tvBtStatus.setText("Live Data: " + hr + " bpm");
                    });
                } else if (CHAR_SPO2.equals(c.getUuid())) {
                    final int spo2 = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
                    handler.post(() -> {
                        binding.etValue1.setText(String.valueOf(spo2));
                        binding.chipGroupType.check(R.id.chipOxygen);
                        binding.tvBtStatus.setText("Live Data: " + spo2 + "% SpO2");
                    });
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (gatt != null && hasPermissions()) {
            try {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
        binding = null;
    }
}