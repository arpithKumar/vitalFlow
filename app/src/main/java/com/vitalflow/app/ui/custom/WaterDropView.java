package com.vitalflow.app.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vitalflow.app.R;

public class WaterDropView extends View {

    // Paints
    private Paint wavePaint; // The water
    private Paint bgPaint;   // The empty part of the drop

    // Paths
    private Path wavePath;
    private Path dropPath;

    // Dimensions
    private int width, height;

    // State
    private float currentPercent = 0f; // The actual value being drawn (animated)
    private float waveShift = 0f; // For wave wobble

    // Animation
    private ValueAnimator waveAnimator; // Infinite wobble
    private ValueAnimator levelAnimator; // Smooth rising/falling

    public WaterDropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 1. Setup Paints
        // Water Color: Brand Teal
        wavePaint = new Paint();
        wavePaint.setStyle(Paint.Style.FILL);
        wavePaint.setAntiAlias(true);

        // Background Color: Very Light Teal (Empty part)
        bgPaint = new Paint();
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAntiAlias(true);

        updateColors();

        // 2. Setup Paths
        wavePath = new Path();
        dropPath = new Path();

        // 3. Setup Animation (Infinite Loop)
        waveAnimator = ValueAnimator.ofFloat(0f, 1f);
        waveAnimator.setDuration(1500); // Speed of the wave
        waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
        waveAnimator.setInterpolator(new LinearInterpolator());
        waveAnimator.addUpdateListener(animation -> {
            waveShift = (float) animation.getAnimatedValue();
            invalidate(); // Redraw view
        });
        waveAnimator.start();
    }

    public void updateColors() {
        Context context = getContext();

        // Water Color: Brand Teal (Primary)
        int waterColor = ContextCompat.getColor(context, R.color.primary);
        wavePaint.setColor(waterColor);

        int emptyColor = ContextCompat.getColor(context, R.color.app_surface);
        bgPaint.setColor(emptyColor);

        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        createDropPath();
    }

    // Creates the distinct Water Drop shape
    private void createDropPath() {
        dropPath.reset();
        float halfW = width / 2f;

        // Start at top tip
        dropPath.moveTo(halfW, 0);

        // Curve down to right
        dropPath.cubicTo(width, height * 0.35f, width, height, halfW, height);

        // Curve up to left
        dropPath.cubicTo(0, height, 0, height * 0.35f, halfW, 0);

        dropPath.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 1. Clip the drawing area to the Drop Shape
        // This ensures the water stays inside the drop
        canvas.save();
        canvas.clipPath(dropPath);

        // 2. Draw the background (Empty drop color)
        canvas.drawRect(0, 0, width, height, bgPaint);

        // 3. Calculate Water Height based on progress
        // We invert it because Y=0 is the top
        float waterHeight = height * (currentPercent / 100f);
        float topY = height - waterHeight;

        // 4. Draw the Wave
        wavePath.reset();
        wavePath.moveTo(0, height); // Start bottom left
        wavePath.lineTo(0, topY);   // Go to water level

        // Create Sine Wave
        // Amplitude: How tall the wave is
        // Length: How wide the wave is
        float waveAmplitude = 20f;
        float waveLength = width * 1.5f;

        for (int x = 0; x <= width; x += 10) {
            float y = (float) (waveAmplitude * Math.sin(2 * Math.PI * (x / waveLength) + (waveShift * 2 * Math.PI)));
            wavePath.lineTo(x, topY + y);
        }

        wavePath.lineTo(width, height); // Bottom right
        wavePath.close();

        canvas.drawPath(wavePath, wavePaint);

        // Restore canvas (remove clipping)
        canvas.restore();
    }

    /**
     * Call this from your Fragment to update hydration
     *
     * @param currentAmount Current ml drunk
     * @param maxAmount     Goal ml
     * @param animate       Whether to animate the change
     */
    public void setProgress(float currentAmount, float maxAmount, boolean animate) {
        if (maxAmount == 0) maxAmount = 2000; // Prevent divide by zero

        // Calculate target percentage (0 to 100)
        float targetPercent = (currentAmount / maxAmount) * 100f;
        targetPercent = Math.max(0, Math.min(100, targetPercent));

        if (animate) {
            // Cancel existing animation if user clicks fast
            if (levelAnimator != null && levelAnimator.isRunning()) {
                levelAnimator.cancel();
            }

            // Animate from whatever the current level is to the new target
            levelAnimator = ValueAnimator.ofFloat(this.currentPercent, targetPercent);
            levelAnimator.setDuration(1000); // 1 second smooth transition
            levelAnimator.setInterpolator(new DecelerateInterpolator());
            levelAnimator.addUpdateListener(animation -> {
                this.currentPercent = (float) animation.getAnimatedValue();
                invalidate(); // Redraw with new height
            });
            levelAnimator.start();
        } else {
            // Instant update (e.g., initial load)
            this.currentPercent = targetPercent;
            invalidate();
        }
    }
}