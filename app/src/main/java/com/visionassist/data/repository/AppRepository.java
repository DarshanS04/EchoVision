package com.visionassist.data.repository;

import android.content.Context;
import com.visionassist.data.storage.PreferencesManager;
import com.visionassist.core.utils.NetworkUtils;

/**
 * Central application repository.
 * Coordinates in-memory state and delegates persistence to PreferencesManager.
 */
public class AppRepository {

    private static AppRepository instance;

    private final PreferencesManager preferencesManager;
    private final Context context;

    // In-memory state
    private String lastDetectedText = "";
    private String lastSpokenResponse = "";
    private boolean isAssistantListening = false;

    private AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.preferencesManager = PreferencesManager.getInstance(context);
    }

    public static synchronized AppRepository getInstance(Context context) {
        if (instance == null) {
            instance = new AppRepository(context);
        }
        return instance;
    }

    // ---- Connectivity ----
    public boolean isOnline() {
        if (preferencesManager.isOfflineModeForced()) return false;
        return NetworkUtils.isInternetAvailable(context);
    }

    public boolean shouldUseGemini() {
        return isOnline() && preferencesManager.hasGeminiApiKey();
    }

    // ---- State accessors ----
    public String getLastDetectedText() { return lastDetectedText; }
    public void setLastDetectedText(String text) { this.lastDetectedText = text; }

    public String getLastSpokenResponse() { return lastSpokenResponse; }
    public void setLastSpokenResponse(String response) { this.lastSpokenResponse = response; }

    public boolean isAssistantListening() { return isAssistantListening; }
    public void setAssistantListening(boolean listening) { this.isAssistantListening = listening; }

    // ---- Preferences delegation ----
    public PreferencesManager getPreferencesManager() { return preferencesManager; }

    public String getGeminiApiKey() { return preferencesManager.getGeminiApiKey(); }
    public String getEmergencyContactNumber() { return preferencesManager.getEmergencyContactNumber(); }
    public String getEmergencyContactName() { return preferencesManager.getEmergencyContactName(); }
    public float getTtsSpeed() { return preferencesManager.getTtsSpeed(); }
    public float getTtsPitch() { return preferencesManager.getTtsPitch(); }
    public boolean shouldReadNotifications() { return preferencesManager.shouldReadNotifications(); }
}
