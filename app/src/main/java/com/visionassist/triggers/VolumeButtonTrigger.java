package com.visionassist.triggers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;

/**
 * Detects two activation gestures from hardware volume buttons:
 *
 * 1. SIMULTANEOUS PRESS: Vol Up + Vol Down pressed within VOLUME_TRIGGER_WINDOW_MS
 *    → fires onTriggerActivated() (legacy behavior, retained)
 *
 * 2. LONG PRESS: Vol Up held for VOLUME_LONG_PRESS_DURATION_MS
 *    → fires onLongPressActivated() (new: used when screen is off / app in background)
 *
 * Wire into onKeyEvent() of AccessibilityService or Activity.
 */
public class VolumeButtonTrigger {

    private static final String TAG = "VolumeButtonTrigger";

    public interface TriggerCallback {
        /** Volume Up + Down pressed simultaneously */
        void onTriggerActivated();
        /** Volume Up held for the long-press duration */
        void onLongPressActivated();
    }

    private TriggerCallback callback;
    private long lastVolumeUpTime   = 0;
    private long lastVolumeDownTime = 0;

    // Long-press detection
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean volumeUpHeld = false;
    private final Runnable longPressRunnable = () -> {
        if (volumeUpHeld) {
            AppLogger.i(TAG, "Volume UP long-press activated");
            volumeUpHeld = false;
            if (callback != null) {
                callback.onLongPressActivated();
            }
        }
    };

    public VolumeButtonTrigger(Context context) {}

    public void setCallback(TriggerCallback callback) {
        this.callback = callback;
    }

    /**
     * Pass KeyEvents here from onKeyEvent / dispatchKeyEvent.
     * Returns true if the event was consumed by the trigger.
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (event == null) return false;

        int keyCode = event.getKeyCode();
        int action  = event.getAction();
        long now    = System.currentTimeMillis();

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (action == KeyEvent.ACTION_DOWN) {
                lastVolumeUpTime = now;
                volumeUpHeld = true;
                // Schedule long-press callback
                handler.removeCallbacks(longPressRunnable);
                handler.postDelayed(longPressRunnable, AppConstants.VOLUME_LONG_PRESS_DURATION_MS);
                AppLogger.v(TAG, "Volume UP pressed");
            } else if (action == KeyEvent.ACTION_UP) {
                volumeUpHeld = false;
                handler.removeCallbacks(longPressRunnable);
                AppLogger.v(TAG, "Volume UP released");
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == KeyEvent.ACTION_DOWN) {
                lastVolumeDownTime = now;
                AppLogger.v(TAG, "Volume DOWN pressed");
            }
        } else {
            return false;
        }

        // ── Simultaneous press check (Vol Up + Vol Down within window) ──
        if (action == KeyEvent.ACTION_DOWN) {
            long diff = Math.abs(lastVolumeUpTime - lastVolumeDownTime);
            if (diff <= AppConstants.VOLUME_TRIGGER_WINDOW_MS
                    && lastVolumeUpTime > 0
                    && lastVolumeDownTime > 0) {
                AppLogger.i(TAG, "Volume dual-press trigger activated! diff=" + diff + "ms");
                // Cancel long-press since dual-press takes priority
                handler.removeCallbacks(longPressRunnable);
                volumeUpHeld = false;
                lastVolumeUpTime   = 0;
                lastVolumeDownTime = 0;

                if (callback != null) {
                    callback.onTriggerActivated();
                    return true;
                }
            }
        }

        return false;
    }
}
