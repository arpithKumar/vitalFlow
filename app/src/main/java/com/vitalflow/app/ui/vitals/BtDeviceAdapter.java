package com.vitalflow.app.ui.vitals;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vitalflow.app.R;

import java.util.ArrayList;
import java.util.List;

public class BtDeviceAdapter extends RecyclerView.Adapter<BtDeviceAdapter.ViewHolder> {

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    public BtDeviceAdapter(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void addDevice(BluetoothDevice device) {
        // Prevent duplicates and only show devices with names to keep UI clean
        if (!devices.contains(device) && device.getName() != null) {
            devices.add(device);
            notifyItemInserted(devices.size() - 1);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // CORRECTED: Inflate your custom layout, not the default Android one
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bt_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);

        String name = device.getName();
        if (name == null || name.isEmpty()) name = "Unknown Device";

        holder.tvName.setText(name);
        holder.tvAddress.setText(device.getAddress());

        holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress;

        public ViewHolder(View itemView) {
            super(itemView);
            // CORRECTED: Find the specific IDs from your custom XML
            tvName = itemView.findViewById(R.id.tvDeviceName);
            tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
        }
    }
}