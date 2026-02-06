package com.vitalflow.app.ui.onboarding;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

public class SmoothPageIndicator extends View {

    private int count = 3; // Default
    private int currentPosition = 0;
    private float positionOffset = 0f;

    // Dimensions (You can tweak these)
    private final float dotRadius = dp(4);      // Radius of the small dots
    private final float gap = dp(12);           // Space between indicators
    private final float activeWidth = dp(24);   // Width of the "Dash"
    private final float height = dp(8);

    // Colors
    private final Paint paintInactive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public SmoothPageIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paintInactive.setColor(0xFFE0E0E0); // Light Gray
        paintActive.setColor(0xFF00897B);   // Teal
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
                invalidate(); // Redraw constantly as you swipe
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate total width needed: (NumDots * width) + Gaps + Expansion room
        float totalWidth = (count * dotRadius * 2) + ((count - 1) * gap) + (activeWidth - (dotRadius * 2));
        setMeasuredDimension(resolveSize((int) totalWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize((int) height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // Center the content vertically and horizontally
        float startX = getPaddingLeft();
        float centerY = getHeight() / 2f;

        // 1. Draw Background Dots (The "Track")
        for (int i = 0; i < count; i++) {
            // Calculate center X for this dot
            // Standard position assuming all are small dots
            float cx = startX + (dotRadius) + (i * (dotRadius * 2 + gap));

            // Draw gray circle
            canvas.drawCircle(cx, centerY, dotRadius, paintInactive);
        }

        // 2. Draw the Active "Worm" / Dash
        // Logic: The dash stretches to the next dot, then the tail pulls in.

        // Start X of the current dot
        float currentDotX = startX + (dotRadius) + (currentPosition * (dotRadius * 2 + gap));
        // Start X of the NEXT dot
        float nextDotX = startX + (dotRadius) + ((currentPosition + 1) * (dotRadius * 2 + gap));

        float left = currentDotX - dotRadius;
        float right = currentDotX + dotRadius;

        // WORM EFFECT LOGIC:
        if (positionOffset <= 0.5f) {
            // First half: Head stretches to next dot, Tail stays put
            // Scale offset 0.0->0.5 to 0.0->1.0
            float localProgress = positionOffset * 2;

            // Right side grows from "current right" to "next right"
            float targetRight = nextDotX + dotRadius;
            float totalGrowth = targetRight - right;
            right = right + (totalGrowth * localProgress);

            // Fix width constraints for visual balance
            if (right - left < activeWidth) right = left + activeWidth;

        } else {
            // Second half: Head stays at next dot, Tail catches up
            // Scale offset 0.5->1.0 to 0.0->1.0
            float localProgress = (positionOffset - 0.5f) * 2;

            // Left side moves from "current left" to "next left"
            float targetLeft = nextDotX - dotRadius;
            float currentLeftStart = currentDotX - dotRadius; // original start

            // But we need to ensure the dash doesn't shrink smaller than a dot before moving
            // The visual effect is the tail pulling in.

            // Simplified worm end state:
            right = nextDotX + dotRadius; // Head is already there

            // Calculate where the tail should be to maintain Dash Width
            float targetDashTail = right - activeWidth;

            // Animate tail towards the final "Dash" position
            // Start position of tail was (right - huge_stretch)
            // It's easier to just interpolate the Left side now
            left = currentLeftStart + ((targetLeft - currentLeftStart) * localProgress);

            // Clamp width so it doesn't look weirdly small
            if (right - left > activeWidth + gap) left = right - (activeWidth + gap);
        }

        // Draw the Teal Rounded Rect
        rect.set(left, centerY - dotRadius, right, centerY + dotRadius);
        canvas.drawRoundRect(rect, dotRadius, dotRadius, paintActive);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}