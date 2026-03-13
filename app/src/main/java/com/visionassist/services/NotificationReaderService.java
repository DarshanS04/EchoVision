package com.visionassist.services;

import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.voice.tts.TTSManager;

/**
 * Listens for incoming notifications and reads them aloud.
 *
 * Two modes:
 *  1. AUTO-READ: When a new notification arrives and the user has opted in,
 *     it is spoken immediately via TTS.
 *  2. ON-DEMAND: When the user says "Read notifications", CommandRouter calls
 *     readAllActiveNotifications() which iterates all current notifications
 *     and speaks them in order.
 *
 * User can enable/disable auto-read in Settings.
 */
public class NotificationReaderService extends NotificationListenerService {

    private static final String TAG = "NotificationReaderService";

    // Singleton reference so other classes can call readAllActiveNotifications()
    private static NotificationReaderService instance;

    private TTSManager tts;
    private AppRepository repository;

    public static NotificationReaderService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        tts = TTSManager.getInstance(this);
        repository = AppRepository.getInstance(this);
        AppLogger.i(TAG, "NotificationReaderService created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    // ─── Auto-read on arrival ─────────────────────────────────────────────────

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!repository.shouldReadNotifications()) return;

        // Skip our own notifications
        if (sbn.getPackageName().equals(getPackageName())) return;

        String message = buildMessage(sbn);
        if (message == null) return;

        AppLogger.i(TAG, "Auto-reading notification from: " + sbn.getPackageName());
        tts.queue(message);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not used but required override
    }

    // ─── On-demand reading ────────────────────────────────────────────────────

    /**
     * Called by CommandRouter when user says "Read notifications".
     * Iterates all active (visible) status bar notifications and speaks each one.
     */
    public void readAllActiveNotifications(TTSManager ttsManager) {
        try {
            StatusBarNotification[] notifications = getActiveNotifications();

            if (notifications == null || notifications.length == 0) {
                String msg = "You have no new notifications.";
                ttsManager.speak(msg);
                return;
            }

            // Filter out our own notifications & group summary
            int count = 0;
            StringBuilder intro = new StringBuilder();
            for (StatusBarNotification sbn : notifications) {
                if (sbn.getPackageName().equals(getPackageName())) continue;
                if ((sbn.getNotification().flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                count++;
            }

            if (count == 0) {
                String msg = "You have no new notifications.";
                ttsManager.speak(msg);
                return;
            }

            ttsManager.speak("You have " + count + (count == 1 ? " notification." : " notifications."));

            // Speak each notification with a short pause
            int idx = 1;
            for (StatusBarNotification sbn : notifications) {
                if (sbn.getPackageName().equals(getPackageName())) continue;
                if ((sbn.getNotification().flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) continue;

                String message = buildMessage(sbn);
                if (message != null) {
                    ttsManager.queue("Notification " + idx + ". " + message);
                    idx++;
                }
            }

        } catch (Exception e) {
            AppLogger.e(TAG, "Error reading active notifications", e);
            ttsManager.speak("I could not read the notifications. Make sure notification access is granted in settings.");
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String buildMessage(StatusBarNotification sbn) {
        android.os.Bundle extras = sbn.getNotification().extras;
        if (extras == null) return null;

        CharSequence title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence text  = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);

        if (title == null && text == null) return null;

        String appName = getAppLabel(sbn.getPackageName());
        StringBuilder sb = new StringBuilder("From ").append(appName).append(". ");
        if (title != null && title.length() > 0) sb.append(title).append(". ");
        if (text  != null && text.length()  > 0) sb.append(text);
        return sb.toString().trim();
    }

    private String getAppLabel(String packageName) {
        try {
            android.content.pm.ApplicationInfo info =
                    getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
