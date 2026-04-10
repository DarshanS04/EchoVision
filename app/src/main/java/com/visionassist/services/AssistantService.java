package com.visionassist.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.visionassist.R;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.triggers.VolumeButtonTrigger;
import com.visionassist.ui.activities.MainActivity;
import com.visionassist.voice.speech.SpeechRecognizerManager;
import com.visionassist.voice.speech.VoiceCommandListener;
import com.visionassist.voice.tts.TTSManager;

/**
 * Main foreground service for VisionAssist.
 * Keeps the speech recognizer and TTS alive in the background.
 * Listens for volume button trigger to activate voice recognition.
 */
public class AssistantService extends Service {

    private static final String TAG = "AssistantService";

    private SpeechRecognizerManager speechRecognizerManager;
    private VoiceCommandListener voiceCommandListener;
    private VolumeButtonTrigger volumeButtonTrigger;
    private TTSManager tts;
    private Handler mainHandler;
    private boolean wakeWordEnabled = false;

    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.i(TAG, "AssistantService created");
        mainHandler = new Handler(Looper.getMainLooper());
        initComponents();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AppLogger.i(TAG, "AssistantService started");

        if (intent != null) {
            String action = intent.getAction();
            if (AppConstants.ACTION_START_LISTENING.equals(action)) {
                startCommandListening();
            } else if (AppConstants.ACTION_STOP_LISTENING.equals(action)) {
                stopListening();
                if (wakeWordEnabled) startWakeWordListening();
            } else if (AppConstants.ACTION_UPDATE_WAKEWORD.equals(action)) {
                wakeWordEnabled = com.visionassist.data.storage.PreferencesManager.getInstance(this).isWakeWordEnabled();
                if (wakeWordEnabled) {
                    startWakeWordListening();
                } else {
                    stopListening();
                }
            }
        }

        try {
            startForeground(AppConstants.NOTIFICATION_ID_ASSISTANT, buildNotification());
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to start foreground: " + e.getMessage());
        }
        isRunning = true;
        return START_STICKY; // Restart if killed
    }

    private void initComponents() {
        tts = TTSManager.getInstance(this);
        wakeWordEnabled = com.visionassist.data.storage.PreferencesManager.getInstance(this).isWakeWordEnabled();

        voiceCommandListener = new VoiceCommandListener(this);
        speechRecognizerManager = new SpeechRecognizerManager(this);
        speechRecognizerManager.setCallback(voiceCommandListener);

        voiceCommandListener.setModeSwitchCallback(new VoiceCommandListener.ModeSwitchCallback() {
            @Override
            public void onWakeWordDetected() {
                AppLogger.i(TAG, "Wake word triggered command mode");
                startCommandListening();
            }

            @Override
            public void onCommandFinished() {
                AppLogger.i(TAG, "Command finished, reverting to wake word if enabled");
                if (wakeWordEnabled) {
                    startWakeWordListening();
                }
            }
        });

        // Init on main thread (SpeechRecognizer requires Looper)
        mainHandler.post(() -> speechRecognizerManager.init());

        // Volume button trigger (implements both simultaneous press & long press)
        volumeButtonTrigger = new VolumeButtonTrigger(this);
        volumeButtonTrigger.setCallback(new VolumeButtonTrigger.TriggerCallback() {
            @Override
            public void onTriggerActivated() {
                AppLogger.i(TAG, "Volume dual-press trigger — starting listening");
                startCommandListening();
            }

            @Override
            public void onLongPressActivated() {
                AppLogger.i(TAG, "Volume long-press trigger — starting listening");
                startCommandListening();
            }
        });

        if (wakeWordEnabled) {
            startWakeWordListening();
        }
    }

    public void startCommandListening() {
        if (speechRecognizerManager != null) {
            mainHandler.post(() -> {
                speechRecognizerManager.cancel();
                speechRecognizerManager.setContinuousMode(false);
                voiceCommandListener.setWakeWordMode(false);
                tts.speak("Listening.");
                mainHandler.postDelayed(() -> {
                    if (speechRecognizerManager != null) speechRecognizerManager.startListening();
                }, 500);
            });
        }
    }

    public void startWakeWordListening() {
        if (speechRecognizerManager != null) {
            mainHandler.post(() -> {
                AppLogger.d(TAG, "Starting wake word listening mode");
                speechRecognizerManager.cancel();
                speechRecognizerManager.setContinuousMode(true);
                voiceCommandListener.setWakeWordMode(true);
                mainHandler.postDelayed(() -> {
                    if (speechRecognizerManager != null) speechRecognizerManager.startListening();
                }, 300);
            });
        }
    }

    /**
     * Stop the speech recognizer.
     */
    public void stopListening() {
        if (speechRecognizerManager != null) {
            mainHandler.post(() -> speechRecognizerManager.stopListening());
        }
    }

    /**
     * Pass hardware key events (called from AccessibilityService).
     */
    public boolean handleKeyEvent(KeyEvent event) {
        return volumeButtonTrigger != null && volumeButtonTrigger.handleKeyEvent(event);
    }

    /**
     * Set a UI callback for the current foreground activity.
     */
    public void setUICallback(VoiceCommandListener.UIUpdateCallback callback) {
        if (voiceCommandListener != null) {
            voiceCommandListener.setUICallback(callback);
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ASSISTANT)
                .setContentTitle("EchoVision Active")
                .setContentText("Long-press Vol Up (or Vol Up+Down) to activate assistant")
                .setSmallIcon(R.drawable.ic_assistant)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Started service only
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (speechRecognizerManager != null)
            speechRecognizerManager.destroy();
        AppLogger.i(TAG, "AssistantService destroyed");
    }
}
