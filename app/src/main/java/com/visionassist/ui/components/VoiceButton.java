package com.visionassist.ui.components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.widget.AppCompatImageButton;

/**
 * Custom pulsing voice activation button.
 * Pulses while listening, scales on press.
 * Fully accessible with content description.
 */
public class VoiceButton extends AppCompatImageButton {

    private static final long PULSE_DURATION = 800L;
    private AnimatorSet pulseAnimator;
    private boolean isListening = false;

    public VoiceButton(Context context) {
        super(context);
        init();
    }

    public VoiceButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VoiceButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setContentDescription("Activate voice assistant. Double tap to start listening.");
        setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
    }

    /**
     * Set listening state — starts or stops the pulse animation.
     */
    public void setListening(boolean listening) {
        this.isListening = listening;
        if (listening) {
            startPulse();
            setContentDescription("Listening. Double tap to stop.");
        } else {
            stopPulse();
            setContentDescription("Activate voice assistant. Double tap to start listening.");
        }
    }

    private void startPulse() {
        stopPulse();

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1.0f, 1.15f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1.0f, 1.15f, 1.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(this, "alpha", 1.0f, 0.7f, 1.0f);

        pulseAnimator = new AnimatorSet();
        pulseAnimator.setDuration(PULSE_DURATION);
        pulseAnimator.setInterpolator(new DecelerateInterpolator());
        pulseAnimator.playTogether(scaleX, scaleY, alpha);
        pulseAnimator.setRepeatCount(-1); // But each animator below needs repeat

        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        alpha.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setRepeatMode(ObjectAnimator.REVERSE);

        scaleX.start();
        scaleY.start();
        alpha.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        setScaleX(1.0f);
        setScaleY(1.0f);
        setAlpha(1.0f);
    }

    public boolean isListening() {
        return isListening;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPulse();
    }
}
