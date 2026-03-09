package com.visionassist.voice.speech;

import android.content.Context;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;

/**
 * Receives recognised text from SpeechRecognizerManager and dispatches
 * to CommandRouter. Acts as the bridge between voice input and action.
 */
public class VoiceCommandListener implements SpeechRecognizerManager.SpeechCallback {

    private static final String TAG = "VoiceCommandListener";

    private final Context context;
    private final CommandRouter commandRouter;
    private final TTSManager ttsManager;

    /** Optional callback for UI updates */
    public interface UIUpdateCallback {
        void onListeningStateChanged(boolean isListening);
        void onPartialTextReceived(String partialText);
        void onCommandProcessed(String commandText, String response);
    }

    private UIUpdateCallback uiCallback;

    public VoiceCommandListener(Context context) {
        this.context = context.getApplicationContext();
        this.ttsManager = TTSManager.getInstance(context);
        this.commandRouter = new CommandRouter(context);
    }

    public void setUICallback(UIUpdateCallback callback) {
        this.uiCallback = callback;
    }

    @Override
    public void onPartialResult(String partialText) {
        AppLogger.v(TAG, "Partial: " + partialText);
        if (uiCallback != null) uiCallback.onPartialTextReceived(partialText);
    }

    @Override
    public void onFinalResult(String finalText) {
        if (finalText == null || finalText.trim().isEmpty()) return;
        String cleaned = finalText.trim().toLowerCase();
        AppLogger.i(TAG, "Processing command: " + cleaned);

        // Route command — async, will call TTS internally
        commandRouter.route(cleaned, response -> {
            if (uiCallback != null) uiCallback.onCommandProcessed(finalText, response);
        });
    }

    @Override
    public void onListeningStarted() {
        AppLogger.d(TAG, "Listening started");
        ttsManager.speak("Listening");
        if (uiCallback != null) uiCallback.onListeningStateChanged(true);
    }

    @Override
    public void onListeningStopped() {
        AppLogger.d(TAG, "Listening stopped");
        if (uiCallback != null) uiCallback.onListeningStateChanged(false);
    }

    @Override
    public void onError(int errorCode, String errorMessage) {
        AppLogger.e(TAG, "Recognition error: " + errorMessage);
        // Only announce non-trivial errors to the user
        if (errorCode != 7 && errorCode != 5) { // Not NO_MATCH or CLIENT
            ttsManager.speak("Sorry, I couldn't understand. Please try again.");
        }
        if (uiCallback != null) uiCallback.onListeningStateChanged(false);
    }
}
