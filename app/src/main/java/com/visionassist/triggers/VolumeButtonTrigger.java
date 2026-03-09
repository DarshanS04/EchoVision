package com.visionassist.triggers;

import android.content.Context;
import android.view.KeyEvent;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;

/**
 * Detects the Volume Up + Volume Down hardware trigger combination.
 * When both volume buttons are pressed within the configured window,
 * the assistant activation callback is fired.
 *
 * Usage: Wire into onKeyEvent() of AccessibilityService or Activity.
 */
public class VolumeButtonTrigger {

    private static final String TAG = "VolumeButtonTrigger";

    public interface TriggerCallback {
        void onTriggerActivated();
    }

    private TriggerCallback callback;
    private long lastVolumeUpTime = 0;
    private long lastVolumeDownTime = 0;

    public VolumeButtonTrigger(Context context) {}

    public void setCallback(TriggerCallback callback) {
        this.callback = callback;
    }

    /**
     * Pass KeyEvents here from onKeyEvent / dispatchKeyEvent.
     * Returns true if the event was consumed by the trigger (both buttons pressed together).
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (event == null) return false;

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (action != KeyEvent.ACTION_DOWN) return false;

        long now = System.currentTimeMillis();

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            lastVolumeUpTime = now;
            AppLogger.v(TAG, "Volume UP pressed");
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            lastVolumeDownTime = now;
            AppLogger.v(TAG, "Volume DOWN pressed");
        } else {
            return false;
        }

        // Check if both buttons were pressed within the trigger window
        long diff = Math.abs(lastVolumeUpTime - lastVolumeDownTime);
        if (diff <= AppConstants.VOLUME_TRIGGER_WINDOW_MS
                && lastVolumeUpTime > 0
                && lastVolumeDownTime > 0) {
            AppLogger.i(TAG, "Volume trigger activated! diff=" + diff + "ms");
            // Reset timestamps to prevent double-fire
            lastVolumeUpTime = 0;
            lastVolumeDownTime = 0;

            if (callback != null) {
                callback.onTriggerActivated();
                return true; // Consume the event
            }
        }

        return false;
    }
}
