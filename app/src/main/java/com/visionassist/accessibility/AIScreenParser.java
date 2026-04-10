package com.visionassist.accessibility;

import android.util.SparseArray;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses AccessibilityNodeInfo into structured JSON format for Gemini API.
 * Maps elements to IDs for deterministic interaction.
 */
public class AIScreenParser {

    private static final int MAX_DEPTH = 20;
    private static final int MAX_ELEMENTS = 30;

    public static class ParseResult {
        public String jsonRepresentation;
        public SparseArray<AccessibilityNodeInfo> nodeMap;
        public String summaryStr;
    }

    public ParseResult parseActiveScreen(AccessibilityNodeInfo root) {
        ParseResult result = new ParseResult();
        result.nodeMap = new SparseArray<>();
        
        JSONArray elementsArray = new JSONArray();
        int[] idCounter = {1};
        
        parseNodeRecursive(root, elementsArray, result.nodeMap, idCounter, 0);

        JSONObject finalJson = new JSONObject();
        try {
            finalJson.put("elements", elementsArray);
            finalJson.put("screen_summary", elementsArray.length() > 0 ? "App screen with " + elementsArray.length() + " interactable items." : "Empty screen");
            result.jsonRepresentation = finalJson.toString();
            result.summaryStr = extractSummaries(elementsArray);
        } catch (JSONException e) {
            result.jsonRepresentation = "{}";
            result.summaryStr = "Failed to parse screen";
        }

        return result;
    }

    private void parseNodeRecursive(AccessibilityNodeInfo node, JSONArray array, 
                                    SparseArray<AccessibilityNodeInfo> map, int[] idCounter, int depth) {
        if (node == null || depth > MAX_DEPTH || array.length() >= MAX_ELEMENTS) return;

        boolean isVisible = node.isVisibleToUser();
        if (!isVisible) return; // ignore hidden things

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean clickable = node.isClickable();
        boolean focusable = node.isFocusable();
        boolean editable = node.isEditable();

        String nodeText = text != null ? text.toString().trim() : "";
        String nodeDesc = desc != null ? desc.toString().trim() : "";
        
        // Only include nodes that are actionable or contain meaningful text
        boolean hasMeaningfulContent = !nodeText.isEmpty() || !nodeDesc.isEmpty();
        boolean isActionable = clickable || focusable || editable;

        if (hasMeaningfulContent || isActionable) {
            try {
                int currentId = idCounter[0]++;
                JSONObject obj = new JSONObject();
                obj.put("id", currentId);
                if (!nodeText.isEmpty()) obj.put("text", nodeText);
                if (!nodeDesc.isEmpty()) obj.put("desc", nodeDesc);
                if (clickable) obj.put("clickable", true);
                if (editable) obj.put("editable", true);
                // "class" is no longer included to minimize tokens

                array.put(obj);
                // obtain a copy for caching, it's very important to do so 
                // in order to avoid recycled nodes crash
                map.put(currentId, AccessibilityNodeInfo.obtain(node));

            } catch (JSONException ignored) { }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            parseNodeRecursive(node.getChild(i), array, map, idCounter, depth + 1);
        }
    }
    
    private String extractSummaries(JSONArray elements) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < elements.length(); i++) {
            if (count > 5) break; 
            try {
                JSONObject obj = elements.getJSONObject(i);
                if (obj.has("text") && obj.has("clickable")) {
                    sb.append("Option ").append(obj.getInt("id")).append(": ").append(obj.getString("text")).append(". ");
                    count++;
                } else if (obj.has("desc") && obj.has("clickable")) {
                    sb.append("Option ").append(obj.getInt("id")).append(": ").append(obj.getString("desc")).append(". ");
                    count++;
                }
            } catch (JSONException ignored) {}
        }
        if (elements.length() > count) {
            sb.append("And ").append(elements.length() - count).append(" more options.");
        }
        return sb.toString();
    }
    
    public void cleanup(SparseArray<AccessibilityNodeInfo> nodeMap) {
        if (nodeMap != null) {
            for (int i = 0; i < nodeMap.size(); i++) {
                AccessibilityNodeInfo node = nodeMap.valueAt(i);
                if (node != null) {
                    node.recycle();
                }
            }
            nodeMap.clear();
        }
    }
}
