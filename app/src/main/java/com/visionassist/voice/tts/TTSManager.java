package com.visionassist.voice.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.os.Bundle;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.storage.PreferencesManager;
import java.util.Locale;

/**
 * Singleton TTS Manager for VisionAssist.
 * Wraps Android TextToSpeech with accessibility-first defaults.
 * Queues speech and provides callbacks on completion.
 */
public class TTSManager {

    private static final String TAG = "TTSManager";
    private static volatile TTSManager instance;

    private TextToSpeech textToSpeech;
    private boolean isInitialised = false;
    private final Context context;

    /** Callback interface for external listeners */
    public interface TTSCallback {
        void onSpeechStart(String utteranceId);
        void onSpeechDone(String utteranceId);
        void onSpeechError(String utteranceId);
    }

    private TTSCallback callback;

    private TTSManager(Context context) {
        this.context = context.getApplicationContext();
        init();
    }

    public static TTSManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TTSManager.class) {
                if (instance == null) {
                    instance = new TTSManager(context);
                }
            }
        }
        return instance;
    }

    private void init() {
        textToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.ENGLISH);
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    AppLogger.e(TAG, "TTS language not supported");
                } else {
                    isInitialised = true;
                    applyPreferences();
                    AppLogger.i(TAG, "TTS engine initialised successfully");
                }
            } else {
                AppLogger.e(TAG, "TTS init failed with status: " + status);
            }
        });

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                AppLogger.d(TAG, "TTS started: " + utteranceId);
                if (callback != null) callback.onSpeechStart(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                AppLogger.d(TAG, "TTS done: " + utteranceId);
                if (callback != null) callback.onSpeechDone(utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                AppLogger.e(TAG, "TTS error: " + utteranceId);
                if (callback != null) callback.onSpeechError(utteranceId);
            }
        });
    }

    /**
     * Speak a string immediately, interrupting current speech.
     */
    public void speak(String text) {
        speak(text, TextToSpeech.QUEUE_FLUSH, "va_" + System.currentTimeMillis());
    }

    /**
     * Queue speech after current utterance.
     */
    public void queue(String text) {
        speak(text, TextToSpeech.QUEUE_ADD, "va_q_" + System.currentTimeMillis());
    }

    /**
     * Speak with explicit queue mode and utterance ID.
     */
    public void speak(String text, int queueMode, String utteranceId) {
        if (!isInitialised) {
            AppLogger.w(TAG, "TTS not yet initialised, queuing: " + text);
            return;
        }
        if (text == null || text.trim().isEmpty()) return;

        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        textToSpeech.speak(text, queueMode, params, utteranceId);
        AppLogger.d(TAG, "Speaking: " + text);
    }

    /**
     * Stop all speech immediately.
     */
    public void stop() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    /**
     * Returns whether TTS is currently speaking.
     */
    public boolean isSpeaking() {
        return textToSpeech != null && textToSpeech.isSpeaking();
    }

    /**
     * Apply user speed/pitch preferences.
     */
    public void applyPreferences() {
        if (!isInitialised) return;
        try {
            PreferencesManager prefs = PreferencesManager.getInstance(context);
            textToSpeech.setSpeechRate(prefs.getTtsSpeed());
            textToSpeech.setPitch(prefs.getTtsPitch());
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to apply TTS preferences", e);
        }
    }

    public void setCallback(TTSCallback callback) {
        this.callback = callback;
    }

    public boolean isInitialised() {
        return isInitialised;
    }

    /**
     * Release TTS resources — call on application termination.
     */
    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            isInitialised = false;
            AppLogger.i(TAG, "TTS shut down");
        }
    }
}
