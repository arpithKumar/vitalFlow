package com.vitalflow.app.ui.custom;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.vitalflow.app.databinding.BottomSheetPermissionBinding;

public class PermissionBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetPermissionBinding binding;
    private Runnable onAllowClicked;

    public void setOnAllowClicked(Runnable listener) {
        this.onAllowClicked = listener;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = BottomSheetPermissionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Make background transparent so rounded corners show
        ((View) view.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);

        binding.btnAllow.setOnClickListener(v -> {
            dismiss();
            if (onAllowClicked != null) onAllowClicked.run();
        });

        binding.btnSkip.setOnClickListener(v -> dismiss());
    }
}