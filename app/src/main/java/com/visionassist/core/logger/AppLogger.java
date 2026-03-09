package com.visionassist.core.logger;

import android.util.Log;

/**
 * Centralised logging wrapper for VisionAssist.
 * All log calls go through here so we can easily enable/disable or redirect.
 */
public final class AppLogger {

    private static final String GLOBAL_TAG = "VisionAssist";
    private static boolean debugEnabled = true;

    private AppLogger() {}

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void d(String tag, String message) {
        if (debugEnabled) Log.d(GLOBAL_TAG + "/" + tag, message);
    }

    public static void i(String tag, String message) {
        Log.i(GLOBAL_TAG + "/" + tag, message);
    }

    public static void w(String tag, String message) {
        Log.w(GLOBAL_TAG + "/" + tag, message);
    }

    public static void e(String tag, String message) {
        Log.e(GLOBAL_TAG + "/" + tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(GLOBAL_TAG + "/" + tag, message, throwable);
    }

    public static void v(String tag, String message) {
        if (debugEnabled) Log.v(GLOBAL_TAG + "/" + tag, message);
    }

    /**
     * Log method entry — useful for tracing service flows.
     */
    public static void enter(String tag, String methodName) {
        if (debugEnabled) Log.v(GLOBAL_TAG + "/" + tag, "→ " + methodName);
    }

    public static void exit(String tag, String methodName) {
        if (debugEnabled) Log.v(GLOBAL_TAG + "/" + tag, "← " + methodName);
    }
}
