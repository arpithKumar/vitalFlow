package com.vitalflow.app.ui.onboarding;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.vitalflow.app.R;

public class SmoothPageIndicator extends View {

    private int count = 3;
    private int currentPosition = 0;
    private float positionOffset = 0f;

    private final float dotRadius = dp(4);
    private final float gap = dp(12);
    private final float activeWidth = dp(24);
    private final float height = dp(8);

    private final Paint paintInactive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public SmoothPageIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // Resolved from color tokens — respects light / dark mode
        paintInactive.setColor(ContextCompat.getColor(context, R.color.divider_color));
        paintActive.setColor(ContextCompat.getColor(context, R.color.primary));
    }

    public void attachToPager(ViewPager2 pager) {
        if (pager.getAdapter() != null) {
            this.count = pager.getAdapter().getItemCount();
        }
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                SmoothPageIndicator.this.currentPosition = position;
                SmoothPageIndicator.this.positionOffset = positionOffset;
                invalidate();
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float totalWidth = (count * dotRadius * 2) + ((count - 1) * gap) + (activeWidth - (dotRadius * 2));
        setMeasuredDimension(resolveSize((int) totalWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize((int) height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float startX = getPaddingLeft();
        float centerY = getHeight() / 2f;

        for (int i = 0; i < count; i++) {
            float cx = startX + (dotRadius) + (i * (dotRadius * 2 + gap));
            canvas.drawCircle(cx, centerY, dotRadius, paintInactive);
        }

        float currentDotX = startX + (dotRadius) + (currentPosition * (dotRadius * 2 + gap));
        float nextDotX = startX + (dotRadius) + ((currentPosition + 1) * (dotRadius * 2 + gap));

        float left = currentDotX - dotRadius;
        float right = currentDotX + dotRadius;

        if (positionOffset <= 0.5f) {
            float localProgress = positionOffset * 2;
            float targetRight = nextDotX + dotRadius;
            float totalGrowth = targetRight - right;
            right = right + (totalGrowth * localProgress);
            if (right - left < activeWidth) right = left + activeWidth;
        } else {
            float localProgress = (positionOffset - 0.5f) * 2;
            float targetLeft = nextDotX - dotRadius;
            float currentLeftStart = currentDotX - dotRadius;
            right = nextDotX + dotRadius;
            left = currentLeftStart + ((targetLeft - currentLeftStart) * localProgress);
            if (right - left > activeWidth + gap) left = right - (activeWidth + gap);
        }

        rect.set(left, centerY - dotRadius, right, centerY + dotRadius);
        canvas.drawRoundRect(rect, dotRadius, dotRadius, paintActive);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}