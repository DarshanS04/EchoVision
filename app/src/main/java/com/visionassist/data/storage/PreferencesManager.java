package com.visionassist.data.storage;

import android.content.Context;
import android.content.SharedPreferences;
import com.visionassist.core.constants.AppConstants;

/**
 * Singleton manager for all SharedPreferences in VisionAssist.
 * Centralises reads/writes so no raw pref keys are scattered across the codebase.
 */
public class PreferencesManager {

    private static PreferencesManager instance;
    private final SharedPreferences prefs;

    private PreferencesManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreferencesManager(context);
        }
        return instance;
    }

    // ---- Gemini API Key ----
    public String getGeminiApiKey() {
        return prefs.getString(AppConstants.KEY_GEMINI_API_KEY, "");
    }

    public void setGeminiApiKey(String key) {
        prefs.edit().putString(AppConstants.KEY_GEMINI_API_KEY, key).apply();
    }

    public boolean hasGeminiApiKey() {
        String key = getGeminiApiKey();
        return key != null && !key.trim().isEmpty();
    }

    // ---- Emergency Contact ----
    public String getEmergencyContactNumber() {
        return prefs.getString(AppConstants.KEY_EMERGENCY_CONTACT, "");
    }

    public void setEmergencyContactNumber(String number) {
        prefs.edit().putString(AppConstants.KEY_EMERGENCY_CONTACT, number).apply();
    }

    public String getEmergencyContactName() {
        return prefs.getString(AppConstants.KEY_EMERGENCY_CONTACT_NAME, "Emergency Contact");
    }

    public void setEmergencyContactName(String name) {
        prefs.edit().putString(AppConstants.KEY_EMERGENCY_CONTACT_NAME, name).apply();
    }

    // ---- TTS Settings ----
    public float getTtsSpeed() {
        return prefs.getFloat(AppConstants.KEY_TTS_SPEED, AppConstants.DEFAULT_TTS_SPEED);
    }

    public void setTtsSpeed(float speed) {
        prefs.edit().putFloat(AppConstants.KEY_TTS_SPEED, speed).apply();
    }

    public float getTtsPitch() {
        return prefs.getFloat(AppConstants.KEY_TTS_PITCH, AppConstants.DEFAULT_TTS_PITCH);
    }

    public void setTtsPitch(float pitch) {
        prefs.edit().putFloat(AppConstants.KEY_TTS_PITCH, pitch).apply();
    }

    // ---- Mode ----
    public boolean isOfflineModeForced() {
        return prefs.getBoolean(AppConstants.KEY_OFFLINE_MODE, false);
    }

    public void setOfflineMode(boolean offlineMode) {
        prefs.edit().putBoolean(AppConstants.KEY_OFFLINE_MODE, offlineMode).apply();
    }

    // ---- Notifications ----
    public boolean shouldReadNotifications() {
        return prefs.getBoolean(AppConstants.KEY_READ_NOTIFICATIONS, true);
    }

    public void setReadNotifications(boolean read) {
        prefs.edit().putBoolean(AppConstants.KEY_READ_NOTIFICATIONS, read).apply();
    }

    // ---- First launch ----
    public boolean isFirstLaunch() {
        return prefs.getBoolean(AppConstants.KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchComplete() {
        prefs.edit().putBoolean(AppConstants.KEY_FIRST_LAUNCH, false).apply();
    }
}
