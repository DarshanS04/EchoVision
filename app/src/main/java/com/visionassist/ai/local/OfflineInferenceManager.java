package com.visionassist.ai.local;

import android.content.Context;
import com.visionassist.ai.gemini.GeminiClient;
import com.visionassist.ai.gemini.GeminiPromptBuilder;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.voice.tts.TTSManager;

/**
 * Decides whether to use online Gemini or offline LocalAIProcessor
 * based on connectivity and API key availability.
 */
public class OfflineInferenceManager {

    private static final String TAG = "OfflineInferenceManager";

    private final Context context;
    private final AppRepository repository;
    private final LocalAIProcessor localProcessor;
    private final TTSManager tts;

    public OfflineInferenceManager(Context context) {
        this.context = context.getApplicationContext();
        this.repository = AppRepository.getInstance(context);
        this.localProcessor = new LocalAIProcessor(context);
        this.tts = TTSManager.getInstance(context);
    }

    /**
     * Process a natural language query.
     * Priority: local keyword → Gemini online → local fallback message.
     */
    public void processQuery(String query, CommandRouter.CommandCallback callback) {
        AppLogger.d(TAG, "Processing query: " + query);

        // Try local processor first (fast, no network needed)
        boolean handledLocally = localProcessor.processQuery(query, callback);
        if (handledLocally) {
            AppLogger.d(TAG, "Handled locally");
            return;
        }

        // Try Gemini if online and API key available
        if (repository.shouldUseGemini()) {
            AppLogger.d(TAG, "Forwarding to Gemini");
            tts.speak("Let me think about that...");
            GeminiClient gemini = new GeminiClient(context);
            GeminiPromptBuilder builder = new GeminiPromptBuilder(context);
            String prompt = builder.buildConversationalPrompt(query);

            gemini.sendTextQuery(prompt, new GeminiClient.GeminiCallback() {
                @Override
                public void onResponse(String text) {
                    tts.speak(text);
                    callback.onResult(text);
                }

                @Override
                public void onError(String error) {
                    AppLogger.e(TAG, "Gemini error: " + error);
                    String speakableError = "I encountered an error: " + error;
                    tts.speak(speakableError);
                    callback.onResult(speakableError);
                }
            });
        } else {
            // Fully offline fallback
            String response = "I'm in offline mode and I don't have a local answer for that. "
                    + "Please connect to the internet for advanced queries.";
            tts.speak(response);
            callback.onResult(response);
        }
    }
}
