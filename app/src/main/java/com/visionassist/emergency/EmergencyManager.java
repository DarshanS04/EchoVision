package com.visionassist.emergency;

import android.content.Context;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.voice.tts.TTSManager;

/**
 * Public-facing emergency manager.
 * Validates prerequisites and delegates to SOSHandler.
 */
public class EmergencyManager {

    private static final String TAG = "EmergencyManager";

    private final Context context;
    private final SOSHandler sosHandler;
    private final TTSManager tts;
    private final AppRepository repository;
    private long lastSOSTime = 0;
    private static final long SOS_COOLDOWN_MS = 10_000; // 10 seconds debounce

    public EmergencyManager(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
        this.repository = AppRepository.getInstance(context);
        this.sosHandler = new SOSHandler(context);
    }

    /**
     * Trigger SOS. Includes 10-second cooldown to prevent accidental double-trigger.
     */
    public void triggerSOS(CommandRouter.CommandCallback callback) {
        long now = System.currentTimeMillis();
        if (now - lastSOSTime < SOS_COOLDOWN_MS) {
            AppLogger.w(TAG, "SOS cooldown active — ignoring duplicate trigger");
            tts.speak("SOS already in progress.");
            return;
        }
        lastSOSTime = now;
        AppLogger.e(TAG, "EmergencyManager: SOS triggered");

        // Validate emergency contact
        if (repository.getEmergencyContactNumber().isEmpty()) {
            tts.speak("Emergency contact not set. Please configure it in settings before using SOS.");
            callback.onResult("Emergency contact not configured.");
            return;
        }

        sosHandler.triggerSOS(callback);
    }

    /**
     * Check if everything is set up for emergency use.
     */
    public boolean isEmergencyReady() {
        return !repository.getEmergencyContactNumber().isEmpty();
    }
}
