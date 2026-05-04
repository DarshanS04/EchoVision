package com.visionassist.ai.local;

import android.content.Context;
import com.visionassist.ai.gemini.GeminiClient;
import com.visionassist.ai.gemini.GeminiPromptBuilder;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.data.local.chat.ChatDatabase;
import com.visionassist.data.local.chat.ChatDao;
import com.visionassist.data.local.chat.ChatMessage;
import com.visionassist.voice.tts.TTSManager;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

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
    private final ChatDao chatDao;
    private final ExecutorService executor;

    public OfflineInferenceManager(Context context) {
        this.context = context.getApplicationContext();
        this.repository = AppRepository.getInstance(context);
        this.localProcessor = new LocalAIProcessor(context);
        this.tts = TTSManager.getInstance(context);
        this.chatDao = ChatDatabase.getInstance(context).chatDao();
        this.executor = Executors.newSingleThreadExecutor();
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

            executor.execute(() -> {
                try {
                    // Fetch last 10 messages to maintain context
                    List<ChatMessage> history = chatDao.getRecentHistory("general", 10);
                    
                    gemini.sendTextQuery(prompt, history, new GeminiClient.GeminiCallback() {
                        @Override
                        public void onResponse(String text) {
                            tts.speak(text);
                            callback.onResult(text);
                            
                            // Save to history
                            executor.execute(() -> {
                                long now = System.currentTimeMillis();
                                chatDao.insertMessage(new ChatMessage("user", prompt, now, "general"));
                                chatDao.insertMessage(new ChatMessage("model", text, now + 1, "general"));
                            });
                        }

                        @Override
                        public void onError(String error) {
                            AppLogger.e(TAG, "Gemini error: " + error);
                            String speakableError;
                            if (error != null && error.contains("Offline mode")) {
                                speakableError = "I'm currently in Force Offline Mode as configured in your settings.";
                            } else {
                                speakableError = "I encountered an error connecting online: " + error;
                            }
                            tts.speak(speakableError);
                            callback.onResult(speakableError);
                        }
                    });
                } catch (Exception e) {
                    AppLogger.e(TAG, "Failed to execute Gemini chat query", e);
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
