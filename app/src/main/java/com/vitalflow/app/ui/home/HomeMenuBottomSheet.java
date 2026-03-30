package com.vitalflow.app.ui.home;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vitalflow.app.databinding.LayoutHomeMenuSheetBinding;

public class HomeMenuBottomSheet extends BottomSheetDialogFragment {

    private LayoutHomeMenuSheetBinding binding;

    public interface MenuClickListener {
        void onExportPdfClicked();

        void onExportExcelClicked();

        void onShareClicked();

        void onRestoreClicked();
    }

    private MenuClickListener listener;

    public void setMenuClickListener(MenuClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LayoutHomeMenuSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnMenuExportPdf.setOnClickListener(v -> {
            if (listener != null) listener.onExportPdfClicked();
            dismiss();
        });

        binding.btnMenuExportExcel.setOnClickListener(v -> {
            if (listener != null) listener.onExportExcelClicked();
            dismiss();
        });

        binding.btnMenuShare.setOnClickListener(v -> {
            if (listener != null) listener.onShareClicked();
            dismiss();
        });

        binding.btnMenuRestore.setOnClickListener(v -> {
            if (listener != null) listener.onRestoreClicked();
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}