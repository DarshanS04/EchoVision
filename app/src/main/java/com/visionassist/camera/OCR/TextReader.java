package com.visionassist.camera.OCR;

import android.content.Context;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.voice.tts.TTSManager;

/**
 * Takes raw OCR text output, cleans it, and produces a voice-friendly reading.
 * Also optionally sends to Gemini for summarisation if document is long.
 */
public class TextReader {

    private static final String TAG = "TextReader";
    private static final int LONG_TEXT_THRESHOLD = 500; // chars

    private final Context context;
    private final TTSManager tts;
    private final AppRepository repository;

    public TextReader(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
        this.repository = AppRepository.getInstance(context);
    }

    /**
     * Reads extracted text aloud. If text is long and Gemini is available,
     * summarises first. Otherwise reads directly.
     */
    public void readText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            tts.speak("I couldn't find any text in the image.");
            return;
        }

        String cleaned = cleanText(rawText);
        AppLogger.i(TAG, "Reading text of length: " + cleaned.length());
        repository.setLastDetectedText(cleaned);

        if (cleaned.length() > LONG_TEXT_THRESHOLD && repository.shouldUseGemini()) {
            // Summarise via Gemini
            tts.speak("This is a long document. Let me summarise it for you.");
            // Trigger summarisation — will be handled by GeminiClient
            summariseWithGemini(cleaned);
        } else if (cleaned.length() > LONG_TEXT_THRESHOLD) {
            // Long text, but offline
            tts.speak("I found a long document. I'll read the first part for you since I'm offline. " + cleaned);
        } else {
            // Read directly
            tts.speak("I found the following text: " + cleaned);
        }
    }

    private void summariseWithGemini(String text) {
        com.visionassist.ai.gemini.GeminiClient gemini =
                new com.visionassist.ai.gemini.GeminiClient(context);

        com.visionassist.ai.gemini.GeminiPromptBuilder builder =
                new com.visionassist.ai.gemini.GeminiPromptBuilder(context);

        String prompt = builder.buildDocumentSummaryPrompt(text);
        gemini.sendTextQuery(prompt, new com.visionassist.ai.gemini.GeminiClient.GeminiCallback() {
            @Override
            public void onResponse(String response) {
                tts.speak("Summary: " + response);
                AppLogger.i(TAG, "Gemini summary complete");
            }

            @Override
            public void onError(String error) {
                tts.speak("Failed to summarise document.");
                AppLogger.e(TAG, "Gemini summary error: " + error);
            }
        });
    }

    private String cleanText(String raw) {
        // Remove excessive whitespace and control chars
        return raw.replaceAll("\\s+", " ")
                  .replaceAll("[^\\x20-\\x7E\\n]", "")
                  .trim();
    }
}
