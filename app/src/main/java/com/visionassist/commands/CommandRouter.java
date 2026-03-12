package com.visionassist.commands;

import android.content.Context;
import android.content.Intent;
import com.visionassist.ai.local.OfflineInferenceManager;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.models.VoiceCommand;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.emergency.EmergencyManager;
import com.visionassist.navigation.NavigationAssistant;
import com.visionassist.ui.activities.CameraActivity;
import com.visionassist.voice.tts.TTSManager;

/**
 * Central command router. Receives raw voice text, classifies it into a VoiceCommand
 * and dispatches to the appropriate handler module. Falls back to Gemini if online
 * and no local command matches.
 */
public class CommandRouter {

    private static final String TAG = "CommandRouter";

    public interface CommandCallback {
        void onResult(String response);
    }

    private final Context context;
    private final AppCommands appCommands;
    private final PhoneCommands phoneCommands;
    private final SystemCommands systemCommands;
    private final EmergencyManager emergencyManager;
    private final OfflineInferenceManager inferenceManager;
    private final TTSManager tts;
    private final AppRepository repository;

    public CommandRouter(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
        this.repository = AppRepository.getInstance(context);
        this.appCommands = new AppCommands(context);
        this.phoneCommands = new PhoneCommands(context);
        this.systemCommands = new SystemCommands(context);
        this.emergencyManager = new EmergencyManager(context);
        this.inferenceManager = new OfflineInferenceManager(context);
    }

    /**
     * Main entry point. Classifies and routes the voice command.
     */
    public void route(String rawText, CommandCallback callback) {
        AppLogger.i(TAG, "Routing: " + rawText);
        VoiceCommand command = classify(rawText);

        switch (command.getCommandType()) {
            case OPEN_APP:
                appCommands.openApp(rawText, callback);
                break;

            case MAKE_CALL:
                phoneCommands.callContact(rawText, callback);
                break;

            case SELECT_CONTACT:
                phoneCommands.selectContact(rawText, callback);
                break;

            case BATTERY_STATUS:
                systemCommands.readBatteryStatus(callback);
                break;

            case TIME_DATE:
                if (rawText.contains("date")) {
                    systemCommands.readCurrentDate(callback);
                } else {
                    systemCommands.readCurrentTime(callback);
                }
                break;

            case CAMERA_DETECT:
            case CAMERA_OCR:
            case CAMERA_BARCODE:
            case READ_SCREEN:
                launchCameraActivity(command.getCommandType(), callback);
                break;

            case NAVIGATE:
                String destination = extractDestination(rawText);
                NavigationAssistant nav = new NavigationAssistant(context);
                nav.navigateTo(destination, callback);
                break;

            case EMERGENCY_SOS:
                emergencyManager.triggerSOS(callback);
                break;

            case READ_NOTIFICATIONS:
                String msg = "Opening notification reader.";
                tts.speak(msg);
                callback.onResult(msg);
                break;

            case HELP:
                systemCommands.readHelp(callback);
                break;

            case STOP:
                tts.stop();
                callback.onResult("Stopped.");
                break;
            
            case TOGGLE_WIFI:
                systemCommands.toggleWifi(callback);
                break;
                
            case TOGGLE_BLUETOOTH:
                systemCommands.toggleBluetooth(callback);
                break;
                
            case TOGGLE_DATA:
                systemCommands.toggleMobileData(callback);
                break;
                
            case TOGGLE_FLASHLIGHT:
                systemCommands.toggleFlashlight(rawText, callback);
                break;
                
            case TOGGLE_AIRPLANE_MODE:
                systemCommands.toggleAirplaneMode(callback);
                break;
                
            case SET_VOLUME:
                systemCommands.setVolume(rawText, callback);
                break;
                
            case SET_BRIGHTNESS:
                systemCommands.setBrightness(rawText, callback);
                break;
                
            case TOGGLE_DND:
                systemCommands.toggleDND(callback);
                break;

            case TOGGLE_ROTATION:
                systemCommands.toggleRotation(callback);
                break;

            case TOGGLE_BATTERY_SAVER:
                systemCommands.toggleBatterySaver(callback);
                break;

            case TOGGLE_LOCATION:
                systemCommands.toggleLocation(callback);
                break;

            case GEMINI_QUERY:
            default:
                // Fall back to AI layer (Gemini online, or local offline)
                inferenceManager.processQuery(rawText, callback);
                break;
        }
    }

    /**
     * Classifies raw text into a VoiceCommand by keyword matching.
     */
    private VoiceCommand classify(String text) {
        String t = text.toLowerCase().trim();

        if (t.contains(AppConstants.CMD_OPEN) || t.contains("launch") || t.startsWith("start ")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.OPEN_APP);
        }
        if (t.contains(AppConstants.CMD_CALL) || t.contains("phone") || t.contains("dial")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.MAKE_CALL);
        }
        if (t.contains(AppConstants.CMD_BATTERY) || t.contains("charge")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.BATTERY_STATUS);
        }
        if (t.contains("time") || t.contains("clock")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TIME_DATE);
        }
        if (t.contains("date") || t.contains("today") || t.contains("day")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TIME_DATE);
        }
        if (t.contains(AppConstants.CMD_WHAT_FRONT) || t.contains("detect") || t.contains("see")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.CAMERA_DETECT);
        }
        if (t.contains(AppConstants.CMD_WHAT_SCREEN) || t.contains("screen")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.READ_SCREEN);
        }
        if (t.contains(AppConstants.CMD_SCAN) || t.contains("qr") || t.contains("barcode")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.CAMERA_BARCODE);
        }
        if (t.contains("read text") || t.contains("ocr") || t.contains("read the text")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.CAMERA_OCR);
        }
        if (t.contains(AppConstants.CMD_NAVIGATE) || t.contains("go to") || t.contains("directions")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.NAVIGATE);
        }
        if (t.contains(AppConstants.CMD_SOS) || t.contains(AppConstants.CMD_EMERGENCY)
                || t.contains("help me")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.EMERGENCY_SOS);
        }
        if (t.contains(AppConstants.CMD_NOTIFICATIONS) || t.contains("message")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.READ_NOTIFICATIONS);
        }
        if (t.contains(AppConstants.CMD_HELP) || t.contains("what can you do")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.HELP);
        }
        if (t.equals(AppConstants.CMD_STOP) || t.equals("cancel") || t.equals("quiet")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.STOP);
        }

        // System Controls
        if (t.contains(AppConstants.CMD_WIFI)) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_WIFI);
        }
        if (t.contains(AppConstants.CMD_BLUETOOTH)) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_BLUETOOTH);
        }
        if (t.contains(AppConstants.CMD_DATA) || (t.contains("mobile") && t.contains("internet"))) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_DATA);
        }
        if (t.contains(AppConstants.CMD_FLASHLIGHT) || t.contains(AppConstants.CMD_TORCH)) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_FLASHLIGHT);
        }
        if (t.contains(AppConstants.CMD_AIRPLANE)) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_AIRPLANE_MODE);
        }
        if (t.contains(AppConstants.CMD_VOLUME) || t.contains("sound") || t.contains("louder") || t.contains("quieter")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.SET_VOLUME);
        }
        if (t.contains(AppConstants.CMD_BRIGHTNESS) || t.contains("light") || t.contains("darker")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.SET_BRIGHTNESS);
        }
        if (t.contains(AppConstants.CMD_DND) || t.contains("not disturb") || t.contains("silent mode")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_DND);
        }
        if (t.contains(AppConstants.CMD_ROTATION) || t.contains("rotate")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_ROTATION);
        }
        if (t.contains(AppConstants.CMD_BATTERY_SAVER) || t.contains("power saver")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_BATTERY_SAVER);
        }
        if (t.contains(AppConstants.CMD_LOCATION) || t.contains("gps")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.TOGGLE_LOCATION);
        }

        // Check for contact selection if pending
        if (phoneCommands.hasPendingContacts()) {
            if (t.contains("option") || t.contains("number") || t.contains("choice") ||
                t.matches(".*\\bone\\b.*") || t.matches(".*\\btwo\\b.*") ||
                t.matches(".*\\bthree\\b.*") || t.matches(".*\\bfour\\b.*") ||
                t.matches(".*\\bfive\\b.*") || t.contains("1") || t.contains("2") ||
                t.contains("3") || t.contains("4") || t.contains("5")) {
                return new VoiceCommand(text, VoiceCommand.CommandType.SELECT_CONTACT);
            }
        }

        return new VoiceCommand(text, VoiceCommand.CommandType.GEMINI_QUERY);
    }

    private void launchCameraActivity(VoiceCommand.CommandType type, CommandCallback callback) {
        Intent intent = new Intent(context, CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("mode", type.name());
        context.startActivity(intent);
        String response = "Opening camera.";
        tts.speak(response);
        callback.onResult(response);
    }

    private String extractDestination(String text) {
        return text.toLowerCase()
                .replace("navigate to", "")
                .replace("go to", "")
                .replace("directions to", "")
                .replace("take me to", "")
                .trim();
    }
}
