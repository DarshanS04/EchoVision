package com.visionassist.commands;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.BatteryManager;
import android.os.Build;
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
    private boolean isFlashlightOn = false;

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
     * Open Wi-Fi settings or panel.
     */
    public void toggleWifi(CommandRouter.CommandCallback callback) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent = new Intent(Settings.Panel.ACTION_WIFI);
        } else {
            intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening Wi-Fi controls.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Bluetooth settings.
     */
    public void toggleBluetooth(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening Bluetooth controls.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Mobile Data / Connectivity panel.
     */
    public void toggleMobileData(CommandRouter.CommandCallback callback) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
        } else {
            intent = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening connectivity controls.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Toggle flashlight.
     */
    public void toggleFlashlight(String rawText, CommandRouter.CommandCallback callback) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            boolean turnOn = !isFlashlightOn;
            if (rawText.contains("off")) turnOn = false;
            else if (rawText.contains("on")) turnOn = true;

            cameraManager.setTorchMode(cameraId, turnOn);
            isFlashlightOn = turnOn;
            String response = "Flashlight turned " + (turnOn ? "on" : "off") + ".";
            tts.speak(response);
            callback.onResult(response);
        } catch (Exception e) {
            AppLogger.e(TAG, "Flashlight error", e);
            String response = "I couldn't control the flashlight.";
            tts.speak(response);
            callback.onResult(response);
        }
    }

    /**
     * Open Airplane Mode settings.
     */
    public void toggleAirplaneMode(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening airplane mode settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Adjust volume.
     */
    public void setVolume(String rawText, CommandRouter.CommandCallback callback) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String response;
        if (rawText.contains("up") || rawText.contains("louder") || rawText.contains("increase")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            response = "Increasing volume.";
        } else if (rawText.contains("down") || rawText.contains("quieter") || rawText.contains("decrease")) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            response = "Decreasing volume.";
        } else if (rawText.contains("mute") || rawText.contains("silent")) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
            response = "Muting volume.";
        } else {
            response = "Tell me if you want to increase or decrease the volume.";
        }
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Brightness settings.
     */
    public void setBrightness(String rawText, CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening brightness settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Do Not Disturb settings.
     */
    public void toggleDND(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening Do Not Disturb settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Auto-rotation settings.
     */
    public void toggleRotation(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening display rotation settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Battery Saver settings.
     */
    public void toggleBatterySaver(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening battery saver settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Open Location settings.
     */
    public void toggleLocation(CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        String response = "Opening location settings.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Navigates to the home screen.
     */
    public void goToHome(CommandRouter.CommandCallback callback) {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(startMain);
        String response = "Going to home screen.";
        tts.speak(response);
        callback.onResult(response);
    }

    /**
     * Speak a general help message listing available commands.
     */
    public void readHelp(CommandRouter.CommandCallback callback) {
        String help = "You can say: Open [app name]. Call [contact name]. "
                + "Send S M S to [name] saying [message]. WhatsApp [name] saying [message]. "
                + "Play [song] on YouTube. Play [song] on Spotify. "
                + "Set alarm for [time]. Set timer for [duration]. "
                + "Navigate to [place]. Find nearby [restaurants or hospitals]. "
                + "Search for [topic]. What is the weather. Show me the news. "
                + "Create calendar event for [title]. "
                + "Read notifications. What is in front of me. Battery status. "
                + "Turn on flashlight. Set volume up. Wi-Fi settings. "
                + "Scan barcode. Read text. S O S for emergency. "
                + "Long press Volume Up or press Volume Up and Down together to activate me.";
        tts.speak(help);
        callback.onResult(help);
    }
}
