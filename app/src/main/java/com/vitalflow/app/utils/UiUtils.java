package com.vitalflow.app.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.snackbar.Snackbar;
import com.vitalflow.app.R;

public class UiUtils {

    public static void showCustomSnackbar(
            View rootView,
            Context context,
            String message,
            int backgroundColor,
            int actionTextColor,
            int iconResId,
            View anchorView,
            View.OnClickListener onUndo) {

        Snackbar snackbar = Snackbar.make(rootView, message, 4000);
        View view = snackbar.getView();
        view.setBackgroundTintList(null);

        float d = context.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(28f * d);
        bg.setColor(backgroundColor);
        view.setBackground(bg);

        if (anchorView != null) snackbar.setAnchorView(anchorView);

        snackbar.setTextColor(Color.WHITE);
        snackbar.setActionTextColor(actionTextColor);

        TextView tvMessage = view.findViewById(com.google.android.material.R.id.snackbar_text);
        Drawable icon = ContextCompat.getDrawable(context, iconResId);
        if (icon != null) {
            int iconSize = (int) (24 * d);
            icon.setBounds(0, 0, iconSize, iconSize);
            DrawableCompat.setTint(icon, Color.WHITE);
            tvMessage.setCompoundDrawables(icon, null, null, null);
            tvMessage.setCompoundDrawablePadding((int) (12 * d));
        }

        TextView btnAction = view.findViewById(com.google.android.material.R.id.snackbar_action);
        Drawable arrowIcon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_right);
        if (arrowIcon != null) {
            int arrowSize = (int) (18 * d);
            arrowIcon.setBounds(0, 0, arrowSize, arrowSize);
            DrawableCompat.setTint(arrowIcon, actionTextColor);
            btnAction.setCompoundDrawables(null, null, arrowIcon, null);
            btnAction.setCompoundDrawablePadding((int) (4 * d));
        }

        snackbar.setAction("UNDO", onUndo);
        snackbar.show();
    }
}