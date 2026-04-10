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
import android.os.Bundle;

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
        volumeButtonTrigger.setCallback(new VolumeButtonTrigger.TriggerCallback() {
            @Override
            public void onTriggerActivated() {
                AppLogger.i(TAG, "Volume trigger in AccessibilityService");
                startAssistant();
            }

            @Override
            public void onLongPressActivated() {
                AppLogger.i(TAG, "Volume long-press in AccessibilityService");
                startAssistant();
            }

            private void startAssistant() {
                Intent intent = new Intent(VisionAccessibilityService.this,
                        com.visionassist.services.AssistantService.class);
                intent.setAction(AppConstants.ACTION_START_LISTENING);
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                } catch (Exception e) {
                    AppLogger.e(TAG, "Failed to start AssistantService from accessibility: " + e.getMessage());
                }
            }
        });

        // Configure service capabilities
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED
                | AccessibilityEvent.TYPE_ANNOUNCEMENT
                | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.notificationTimeout = 300;
        // FLAG_REQUEST_FILTER_KEY_EVENTS is critical — it tells Android to route
        // hardware key events (volume buttons) to this service EVEN when other
        // apps like YouTube are in the foreground.
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
                | AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);

        tts.speak("EchoVision accessibility service is active. Long press volume up to activate the assistant.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null)
            return;

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
        if (root == null)
            return "I couldn't read the screen right now.";
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

    /**
     * Used by AppCommandNavigator to construct a JSON parse result of the screen
     */
    public AIScreenParser.ParseResult captureScreenForAI() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AIScreenParser parser = new AIScreenParser();
        AIScreenParser.ParseResult result = parser.parseActiveScreen(root);
        root.recycle();
        return result;
    }

    /**
     * Used by AppCommandNavigator to execute an action on a node
     */
    public boolean executeNodeAction(AccessibilityNodeInfo node, String actionType, String textToSet) {
        if ("back".equals(actionType)) {
            return performGlobalAction(GLOBAL_ACTION_BACK);
        }

        if (node == null) return false;
        
        switch (actionType) {
            case "click":
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            case "scroll":
                return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            case "scroll_backward":
                return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            case "input_text":
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSet);
                return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            default:
                AppLogger.w(TAG, "Unknown action type: " + actionType);
                return false;
        }
    }
}
