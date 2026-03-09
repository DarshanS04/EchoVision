package com.visionassist.triggers;

import android.content.Context;
import com.visionassist.core.logger.AppLogger;

/**
 * Handles activation via Android's built-in accessibility shortcut.
 * When the user uses the accessibility shortcut gesture (hold both volume buttons),
 * VisionAssist can be configured as the shortcut target.
 */
public class AccessibilityShortcutTrigger {

    private static final String TAG = "AccessibilityShortcutTrigger";

    public interface ShortcutCallback {
        void onShortcutActivated();
    }

    private final Context context;
    private ShortcutCallback callback;

    public AccessibilityShortcutTrigger(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setCallback(ShortcutCallback callback) {
        this.callback = callback;
    }

    /**
     * Called by the VisionAccessibilityService when the system-level
     * accessibility shortcut is activated.
     */
    public void onShortcutActivated() {
        AppLogger.i(TAG, "Accessibility shortcut activated");
        if (callback != null) {
            callback.onShortcutActivated();
        }
    }
}
