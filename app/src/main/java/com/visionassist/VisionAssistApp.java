package com.visionassist;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.data.storage.PreferencesManager;
import com.visionassist.voice.tts.TTSManager;

/**
 * VisionAssist Application class.
 * Bootstraps singletons and performs one-time initialisation.
 */
public class VisionAssistApp extends Application {

    private static final String TAG = "VisionAssistApp";
    private static VisionAssistApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        AppLogger.i(TAG, "VisionAssist starting up");

        // Init core singletons
        PreferencesManager.getInstance(this);
        AppRepository.getInstance(this);

        // Init TTS engine early so it's ready when MainActivity starts
        TTSManager.getInstance(this);

        // Create notification channels
        createNotificationChannels();

        AppLogger.i(TAG, "VisionAssist initialised successfully");
    }

    public static VisionAssistApp getInstance() {
        return instance;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);

            // Assistant foreground service channel
            NotificationChannel assistantChannel = new NotificationChannel(
                    AppConstants.NOTIFICATION_CHANNEL_ASSISTANT,
                    "VisionAssist Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            assistantChannel.setDescription("Keeps VisionAssist running in the background");
            assistantChannel.setShowBadge(false);
            manager.createNotificationChannel(assistantChannel);

            // SOS channel — high priority
            NotificationChannel sosChannel = new NotificationChannel(
                    AppConstants.NOTIFICATION_CHANNEL_SOS,
                    "Emergency SOS",
                    NotificationManager.IMPORTANCE_HIGH
            );
            sosChannel.setDescription("Emergency SOS alerts");
            sosChannel.enableVibration(true);
            manager.createNotificationChannel(sosChannel);

            AppLogger.i(TAG, "Notification channels created");
        }
    }
}
