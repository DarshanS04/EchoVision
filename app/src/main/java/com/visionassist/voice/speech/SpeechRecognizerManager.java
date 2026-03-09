package com.visionassist.voice.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Manages the Android SpeechRecognizer lifecycle.
 * Supports continuous and one-shot recognition modes.
 * Fires callbacks on partial/final results and errors.
 */
public class SpeechRecognizerManager {

    private static final String TAG = "SpeechRecognizerManager";

    public interface SpeechCallback {
        void onPartialResult(String partialText);
        void onFinalResult(String finalText);
        void onListeningStarted();
        void onListeningStopped();
        void onError(int errorCode, String errorMessage);
    }

    private SpeechRecognizer speechRecognizer;
    private final Context context;
    private SpeechCallback callback;
    private boolean isListening = false;
    private boolean continuousMode = false;

    public SpeechRecognizerManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setCallback(SpeechCallback callback) {
        this.callback = callback;
    }

    public void setContinuousMode(boolean continuous) {
        this.continuousMode = continuous;
    }

    public boolean isListening() {
        return isListening;
    }

    /**
     * Initialise recognizer. Must be called from a thread with a Looper (e.g. main thread).
     */
    public void init() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            AppLogger.e(TAG, "Speech recognition not available on this device");
            return;
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(buildListener());
        AppLogger.i(TAG, "SpeechRecognizer initialised");
    }

    /**
     * Start listening for user speech.
     */
    public void startListening() {
        if (!PermissionUtils.hasMicrophonePermission(context)) {
            AppLogger.w(TAG, "Microphone permission not granted");
            if (callback != null) callback.onError(-1, "Microphone permission required");
            return;
        }

        if (speechRecognizer == null) init();
        if (speechRecognizer == null) return;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);

        speechRecognizer.startListening(intent);
        isListening = true;
        AppLogger.d(TAG, "Listening started");
    }

    /**
     * Stop listening explicitly.
     */
    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            AppLogger.d(TAG, "Listening stopped");
        }
    }

    /**
     * Cancel ongoing recognition without processing results.
     */
    public void cancel() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            isListening = false;
        }
    }

    /**
     * Release all resources. Call on service destroy.
     */
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
            isListening = false;
            AppLogger.i(TAG, "SpeechRecognizer destroyed");
        }
    }

    private RecognitionListener buildListener() {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                AppLogger.d(TAG, "Ready for speech");
                if (callback != null) callback.onListeningStarted();
            }

            @Override
            public void onBeginningOfSpeech() {
                AppLogger.d(TAG, "Speech beginning");
            }

            @Override
            public void onRmsChanged(float rmsdB) { /* Audio level change, unused */ }

            @Override
            public void onBufferReceived(byte[] buffer) { /* Unused */ }

            @Override
            public void onEndOfSpeech() {
                AppLogger.d(TAG, "Speech ended");
                isListening = false;
                if (callback != null) callback.onListeningStopped();
            }

            @Override
            public void onError(int error) {
                isListening = false;
                String message = speechErrorToString(error);
                AppLogger.e(TAG, "Speech error: " + message);
                if (callback != null) callback.onError(error, message);

                // Auto-restart in continuous mode on non-fatal errors
                if (continuousMode && error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    android.os.Handler mainHandler = new android.os.Handler(
                            android.os.Looper.getMainLooper());
                    mainHandler.postDelayed(() -> startListening(), 500);
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    AppLogger.i(TAG, "Final result: " + text);
                    if (callback != null) callback.onFinalResult(text);
                }

                // Auto-restart in continuous mode
                if (continuousMode) {
                    startListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    AppLogger.v(TAG, "Partial: " + text);
                    if (callback != null) callback.onPartialResult(text);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) { /* Unused */ }
        };
    }

    private String speechErrorToString(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT: return "Other client side errors";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Other network related errors";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network operation timed out";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No speech input";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "RecognitionService is busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server sends error status";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "No speech input";
            default: return "Unknown error " + error;
        }
    }
}
