package com.visionassist.accessibility;

import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;
import com.visionassist.core.logger.AppLogger;

/**
 * Builds a spoken description of the current screen from an accessibility node
 * tree.
 */
public class ScreenReader {

    private static final String TAG = "ScreenReader";
    private final Context context;
    private final UINodeParser nodeParser;

    public ScreenReader(Context context) {
        this.context = context.getApplicationContext();
        this.nodeParser = new UINodeParser();
    }

    /**
     * Produces a human-readable spoken description of the root node.
     */
    public String describeRootNode(AccessibilityNodeInfo root) {
        if (root == null)
            return "";

        StringBuilder builder = new StringBuilder();
        nodeParser.parseNode(root, builder, 0);

        String result = builder.toString().trim();
        AppLogger.d(TAG, "Screen description length: " + result.length());
        return result;
    }

    /**
     * Summarises screen as a short phrase for TTS — app name + first major element.
     */
    public String getShortDescription(AccessibilityNodeInfo root) {
        if (root == null)
            return "Unknown screen";

        String packageName = root.getPackageName() != null
                ? root.getPackageName().toString()
                : "unknown app";

        // Try to get window title
        CharSequence text = root.getText();
        if (text != null && text.length() > 0) {
            return "You are in " + packageName + ". Showing: " + text;
        }
        return "You are in " + packageName;
    }
}
