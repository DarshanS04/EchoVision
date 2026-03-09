package com.visionassist.accessibility;

import android.view.accessibility.AccessibilityNodeInfo;
import com.visionassist.core.logger.AppLogger;
import java.util.List;

/**
 * Recursively parses AccessibilityNodeInfo trees to extract spoken content.
 * Extracts text, content descriptions, and role-based descriptions.
 */
public class UINodeParser {

    private static final String TAG = "UINodeParser";
    private static final int MAX_DEPTH = 8;
    private static final int MAX_CHARS = 1000;

    /**
     * Recursively extracts meaningful content from node tree into a StringBuilder.
     */
    public void parseNode(AccessibilityNodeInfo node, StringBuilder builder, int depth) {
        if (node == null || depth > MAX_DEPTH) return;
        if (builder.length() > MAX_CHARS) return;

        // Extract text content
        CharSequence text = node.getText();
        CharSequence contentDesc = node.getContentDescription();

        String spoken = null;
        if (text != null && text.length() > 0) {
            spoken = text.toString().trim();
        } else if (contentDesc != null && contentDesc.length() > 0) {
            spoken = contentDesc.toString().trim();
        }

        if (spoken != null && !spoken.isEmpty()) {
            // Add role hint for clickable items
            if (node.isClickable()) {
                builder.append("Button: ").append(spoken).append(". ");
            } else if (node.isCheckable()) {
                String checked = node.isChecked() ? "checked" : "unchecked";
                builder.append("Checkbox ").append(checked).append(": ").append(spoken).append(". ");
            } else if (node.isEditable()) {
                builder.append("Text field: ").append(spoken).append(". ");
            } else {
                builder.append(spoken).append(". ");
            }
        }

        // Recurse into children
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            parseNode(child, builder, depth + 1);
            if (child != null) child.recycle();
        }
    }

    /**
     * Returns a list of all clickable elements in the node tree.
     */
    public void findClickableNodes(AccessibilityNodeInfo node,
                                   List<AccessibilityNodeInfo> results, int depth) {
        if (node == null || depth > MAX_DEPTH) return;

        if (node.isClickable()) {
            results.add(AccessibilityNodeInfo.obtain(node));
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findClickableNodes(child, results, depth + 1);
                child.recycle();
            }
        }
    }
}
