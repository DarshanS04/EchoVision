package com.visionassist.navigation;

import android.content.Context;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityNodeInfo;
import com.visionassist.accessibility.AIScreenParser;
import com.visionassist.accessibility.VisionAccessibilityService;
import com.visionassist.ai.gemini.GeminiClient;
import com.visionassist.core.logger.AppLogger;
import org.json.JSONObject;

/**
 * Orchestrates the AI App Navigation continuous loop.
 */
public class AppCommandNavigator {
    
    private static final String TAG = "AppCommandNavigator";
    
    private final GeminiClient geminiClient;
    private boolean isActive = false;

    // Cache the last parsed screen to clean up properly
    private SparseArray<AccessibilityNodeInfo> lastNodeMap;

    public interface ExecutionCallback {
        void onSuccess(String spokenResponse);
        void onError(String errorMessage);
    }

    public AppCommandNavigator(Context context) {
        this.geminiClient = new GeminiClient(context);
    }

    public boolean isActive() {
        return isActive;
    }

    public void startNavigation() {
        isActive = true;
        AppLogger.i(TAG, "App Navigation started.");
    }

    public void stopNavigation() {
        isActive = false;
        cleanupLastScreen();
        AppLogger.i(TAG, "App Navigation stopped.");
    }

    public void processUserCommand(String command, ExecutionCallback callback) {
        if (!isActive) {
            callback.onError("Navigation is not active.");
            return;
        }

        VisionAccessibilityService accessibilityService = VisionAccessibilityService.getInstance();
        if (accessibilityService == null) {
            callback.onError("Accessibility Service is not running.");
            return;
        }

        // Cleanup previous screen map to prevent leaks
        cleanupLastScreen();

        // 1. Capture current screen
        AIScreenParser.ParseResult screenResult = accessibilityService.captureScreenForAI();
        if (screenResult == null || screenResult.nodeMap.size() == 0) {
            callback.onError("Could not read the screen at this time.");
            return;
        }

        this.lastNodeMap = screenResult.nodeMap;
        String screenContext = screenResult.summaryStr;

        // 2. Call Gemini
        geminiClient.sendAppControlQuery(command, screenResult.jsonRepresentation, screenContext, new GeminiClient.GeminiCallback() {
            @Override
            public void onResponse(String jsonText) {
                try {
                    // Try to clean markdown tags if the LLM leaked them
                    String cleanJson = jsonText.trim();
                    if (cleanJson.startsWith("```json")) {
                        cleanJson = cleanJson.substring(7, cleanJson.length() - 3).trim();
                    } else if (cleanJson.startsWith("```")) {
                        cleanJson = cleanJson.substring(3, cleanJson.length() - 3).trim();
                    }

                    JSONObject actionObj = new JSONObject(cleanJson);
                    String action = actionObj.optString("action", "ask");
                    int targetId = actionObj.optInt("target_id", -1);
                    String inputText = actionObj.optString("input_text", "");
                    String reason = actionObj.optString("reason", "");

                    // 3. Execute
                    boolean executed = false;
                    AccessibilityNodeInfo targetNode = null;

                    if (targetId != -1 && lastNodeMap != null) {
                        targetNode = lastNodeMap.get(targetId);
                    }

                    if ("ask".equalsIgnoreCase(action) || ("back".equalsIgnoreCase(action) && targetNode == null)) {
                        executed = accessibilityService.executeNodeAction(null, action, inputText);
                    } else if (targetNode != null) {
                        executed = accessibilityService.executeNodeAction(targetNode, action, inputText);
                    } else {
                        // Node not found
                        callback.onError("I couldn't find the requested element on screen.");
                        return;
                    }

                    // 4. Summarize Next steps
                    if (executed) {
                        if (reason.isEmpty()) {
                            callback.onSuccess("Done.");
                        } else {
                            callback.onSuccess(reason);
                        }
                    } else if ("ask".equalsIgnoreCase(action)) {
                        callback.onSuccess(reason.isEmpty() ? "What would you like to do?" : reason);
                    } else {
                        callback.onError("Failed to execute action.");
                    }

                } catch (Exception e) {
                    AppLogger.e(TAG, "Failed to parse Gemini output: " + jsonText, e);
                    callback.onError("I got confused. Please try differently.");
                }
            }

            @Override
            public void onError(String error) {
                AppLogger.e(TAG, "Gemini API error: " + error);
                callback.onError("Network error during intelligence processing.");
            }
        });
    }

    private void cleanupLastScreen() {
        if (lastNodeMap != null) {
            for (int i = 0; i < lastNodeMap.size(); i++) {
                AccessibilityNodeInfo node = lastNodeMap.valueAt(i);
                if (node != null) {
                    node.recycle();
                }
            }
            lastNodeMap.clear();
            lastNodeMap = null;
        }
    }
}
