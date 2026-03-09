package com.visionassist.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.triggers.VolumeButtonTrigger;
import com.visionassist.voice.tts.TTSManager;

/**
 * VisionAssist Accessibility Service.
 * Reads screen content aloud on window state changes.
 * Also intercepts volume button hardware trigger for hands-free activation.
 */
public class VisionAccessibilityService extends AccessibilityService {

    private static final String TAG = "VisionAccessibilityService";

    private static VisionAccessibilityService instance;
    private TTSManager tts;
    private ScreenReader screenReader;
    private VolumeButtonTrigger volumeButtonTrigger;

    public static VisionAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        AppLogger.i(TAG, "VisionAccessibilityService created");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AppLogger.i(TAG, "Accessibility service connected");

        tts = TTSManager.getInstance(this);
        screenReader = new ScreenReader(this);
        volumeButtonTrigger = new VolumeButtonTrigger(this);

        // Configure service capabilities
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_ANNOUNCEMENT
                | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.notificationTimeout = 300;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        setServiceInfo(info);

        tts.speak("VisionAssist accessibility service is active. Press volume up and down together to activate the assistant.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // New window/app opened
            CharSequence packageName = event.getPackageName();
            CharSequence className = event.getClassName();
            AppLogger.d(TAG, "Window changed: " + packageName + " / " + className);

            // Read the screen content after a short delay to let it render
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    String description = screenReader.describeRootNode(root);
                    if (!description.isEmpty()) {
                        AppLogger.d(TAG, "Screen description: " + description);
                        // Speak screen description only if user requested
                        // (avoid reading every window change unless user asks)
                    }
                    root.recycle();
                }
            }, 500);
        }
    }

    /**
     * Called by CommandRouter when user says "What is on my screen?"
     */
    public String getCurrentScreenDescription() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "I couldn't read the screen right now.";
        String desc = screenReader.describeRootNode(root);
        root.recycle();
        return desc.isEmpty() ? "The screen appears to be empty." : desc;
    }

    /**
     * Intercepts hardware key events — used for Volume Up+Down trigger.
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (volumeButtonTrigger != null) {
            return volumeButtonTrigger.handleKeyEvent(event);
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onInterrupt() {
        AppLogger.w(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        AppLogger.i(TAG, "Accessibility service destroyed");
    }
}
