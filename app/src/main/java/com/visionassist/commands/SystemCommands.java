package com.visionassist.commands;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.provider.Settings;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles system-level voice commands:
 * battery status, time, date, volume/brightness control.
 */
public class SystemCommands {

    private static final String TAG = "SystemCommands";
    private final Context context;
    private final TTSManager tts;

    public SystemCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    /**
     * Read the current battery level.
     */
    public void readBatteryStatus(CommandRouter.CommandCallback callback) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int level = -1;
        if (bm != null) {
            level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        String response;
        if (level >= 0) {
            response = "Battery is at " + level + " percent.";
            if (level <= 15) response += " Please charge your device soon.";
            else if (level == 100) response += " Battery is fully charged.";
        } else {
            response = "I couldn't read the battery level.";
        }
        tts.speak(response);
        callback.onResult(response);
        AppLogger.d(TAG, "Battery: " + level);
    }

    /**
     * Announce the current time.
     */
    public void readCurrentTime(CommandRouter.CommandCallback callback) {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
        String time = sdf.format(new Date());
        String response = "The time is " + time;
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Announce today's date.
     */
    public void readCurrentDate(CommandRouter.CommandCallback callback) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH);
        String date = sdf.format(new Date());
        String response = "Today is " + date;
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Wi-Fi settings.
     */
    public void openWifiSettings(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening Wi-Fi settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Bluetooth settings.
     */
    public void openBluetoothSettings(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening Bluetooth settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Speak a general help message listing available commands.
     */
    public void readHelp(CommandRouter.CommandCallback callback) {
        String help = "You can say: Open [app name]. Call [contact name]. "
                + "Read notifications. What is in front of me. "
                + "Navigate to [place]. Battery status. What time is it. "
                + "Scan barcode. Read text. S O S for emergency.";
        tts.speak(help);
        callback.onResult(help);
    }
}
