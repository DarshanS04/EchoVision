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

        if (intent != null && AppConstants.ACTION_START_LISTENING.equals(intent.getAction())) {
            startListening();
        } else if (intent != null && AppConstants.ACTION_STOP_LISTENING.equals(intent.getAction())) {
            stopListening();
        }

        startForeground(AppConstants.NOTIFICATION_ID_ASSISTANT, buildNotification());
        isRunning = true;
        return START_STICKY; // Restart if killed
    }

    private void initComponents() {
        tts = TTSManager.getInstance(this);

        voiceCommandListener = new VoiceCommandListener(this);
        speechRecognizerManager = new SpeechRecognizerManager(this);
        speechRecognizerManager.setCallback(voiceCommandListener);

        // Init on main thread (SpeechRecognizer requires Looper)
        mainHandler.post(() -> speechRecognizerManager.init());

        // Volume button trigger
        volumeButtonTrigger = new VolumeButtonTrigger(this);
        volumeButtonTrigger.setCallback(() -> {
            AppLogger.i(TAG, "Volume trigger — starting listening");
            startListening();
        });
    }

    /**
     * Start the speech recognizer.
     */
    public void startListening() {
        if (speechRecognizerManager != null && !speechRecognizerManager.isListening()) {
            mainHandler.post(() -> speechRecognizerManager.startListening());
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
                .setContentTitle("VisionAssist Active")
                .setContentText("Press Vol Up + Vol Down to activate the assistant")
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
        if (speechRecognizerManager != null) speechRecognizerManager.destroy();
        AppLogger.i(TAG, "AssistantService destroyed");
    }
}
