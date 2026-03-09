package com.visionassist.ai.gemini;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.visionassist.core.logger.AppLogger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads named prompts from assets/prompts/gemini_prompts.json
 * and substitutes variables to build context-aware Gemini prompts.
 */
public class GeminiPromptBuilder {

    private static final String TAG = "GeminiPromptBuilder";
    private static final String PROMPTS_FILE = "prompts/gemini_prompts.json";

    private JsonObject prompts;

    public GeminiPromptBuilder(Context context) {
        loadPrompts(context);
    }

    private void loadPrompts(Context context) {
        try (InputStream is = context.getAssets().open(PROMPTS_FILE);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            prompts = new Gson().fromJson(reader, JsonObject.class);
            AppLogger.i(TAG, "Prompts loaded: " + prompts.keySet());
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to load prompts", e);
            prompts = new JsonObject();
        }
    }

    /** Builds a scene description prompt for an image */
    public String buildSceneDescriptionPrompt() {
        String base = getPrompt("scene_description");
        return base + " Please describe what you see in plain, simple English so a visually "
                + "impaired person can understand the scene. Focus on people, objects, text, "
                + "and any hazards. Be concise and speak in 2-3 sentences.";
    }

    /** Builds a document summary prompt */
    public String buildDocumentSummaryPrompt(String documentText) {
        String base = getPrompt("document_summary");
        return base + " Document content: \"" + documentText + "\"\n"
                + "Please summarise this document in 2-3 clear sentences.";
    }

    /** Builds a barcode lookup prompt */
    public String buildBarcodeLookupPrompt(String barcodeValue) {
        String base = getPrompt("barcode_lookup");
        return base + " Barcode value: " + barcodeValue
                + ". What product does this barcode likely represent?";
    }

    /** Builds a conversational query prompt (general Q&A) */
    public String buildConversationalPrompt(String userQuery) {
        String base = getPrompt("conversational");
        return base + " User said: \"" + userQuery + "\"\n"
                + "Please respond naturally and concisely. Keep your response under 100 words "
                + "because it will be read aloud by a text-to-speech system.";
    }

    private String getPrompt(String key) {
        if (prompts != null && prompts.has(key)) {
            return prompts.get(key).getAsString();
        }
        return "You are VisionAssist, an AI assistant for visually impaired users.";
    }
}
