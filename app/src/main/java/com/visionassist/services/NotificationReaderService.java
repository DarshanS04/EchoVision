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
 * User can enable/disable this in Settings.
 */
public class NotificationReaderService extends NotificationListenerService {

    private static final String TAG = "NotificationReaderService";
    private TTSManager tts;
    private AppRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        tts = TTSManager.getInstance(this);
        repository = AppRepository.getInstance(this);
        AppLogger.i(TAG, "NotificationReaderService created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (!repository.shouldReadNotifications()) return;

        // Skip our own notifications
        if (sbn.getPackageName().equals(getPackageName())) return;

        android.os.Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        CharSequence title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);

        if (title == null && text == null) return;

        String appName = getAppLabel(sbn.getPackageName());
        String message = buildMessage(appName, title, text);

        AppLogger.i(TAG, "Reading notification from: " + sbn.getPackageName());
        tts.queue(message);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not used but required override
    }

    private String buildMessage(String appName, CharSequence title, CharSequence text) {
        StringBuilder sb = new StringBuilder("New notification from ").append(appName).append(". ");
        if (title != null && title.length() > 0) sb.append(title).append(". ");
        if (text != null && text.length() > 0) sb.append(text);
        return sb.toString().trim();
    }

    private String getAppLabel(String packageName) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
