package com.visionassist.commands;

import android.content.Context;
import android.content.Intent;
import com.visionassist.ai.local.OfflineInferenceManager;
import com.visionassist.commands.communication.CommunicationCommands;
import com.visionassist.commands.media.MediaCommands;
import com.visionassist.commands.navigation.ExternalNavigationCommands;
import com.visionassist.commands.scheduler.SchedulerCommands;
import com.visionassist.commands.web.WebCommands;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.models.VoiceCommand;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.emergency.EmergencyManager;
import com.visionassist.navigation.NavigationAssistant;
import com.visionassist.services.NotificationReaderService;
import com.visionassist.ui.activities.CameraActivity;
import com.visionassist.voice.tts.TTSManager;
import com.visionassist.navigation.AppCommandNavigator;

/**
 * Central command router. Receives raw voice text, classifies it into a VoiceCommand
 * and dispatches to the appropriate handler module.
 *
 * Priority order for classification (most specific first):
 * 1. Pending state (contact selection)
 * 2. Media (YouTube / Spotify)
 * 3. Communication (SMS / WhatsApp)
 * 4. Scheduling (Alarm / Timer / Calendar)
 * 5. Navigation / Nearby
 * 6. Web (Search / Weather / News)
 * 7. System controls
 * 8. Core commands (call, open, battery, etc.)
 * 9. Fallback to AI (Gemini online or local offline)
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
    private final MediaCommands mediaCommands;
    private final CommunicationCommands communicationCommands;
    private final SchedulerCommands schedulerCommands;
    private final WebCommands webCommands;
    private final ExternalNavigationCommands externalNavigationCommands;
    private final EmergencyManager emergencyManager;
    private final OfflineInferenceManager inferenceManager;
    private final TTSManager tts;
    private final AppRepository repository;
    private final AppCommandNavigator appCommandNavigator;

    public CommandRouter(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
        this.repository = AppRepository.getInstance(context);
        this.appCommands = new AppCommands(context);
        this.phoneCommands = new PhoneCommands(context);
        this.systemCommands = new SystemCommands(context);
        this.mediaCommands = new MediaCommands(context);
        this.communicationCommands = new CommunicationCommands(context);
        this.schedulerCommands = new SchedulerCommands(context);
        this.webCommands = new WebCommands(context);
        this.externalNavigationCommands = new ExternalNavigationCommands(context);
        this.emergencyManager = new EmergencyManager(context);
        this.inferenceManager = new OfflineInferenceManager(context);
        this.appCommandNavigator = new AppCommandNavigator(context);
    }

    /**
     * Main entry point. Classifies and routes the voice command.
     */
    public void route(String rawText, CommandCallback callback) {
        AppLogger.i(TAG, "Routing: " + rawText);
        VoiceCommand command = classify(rawText);

        if (command.getCommandType() == VoiceCommand.CommandType.APP_NAVIGATION_STOP) {
            appCommandNavigator.stopNavigation();
            String response = "Exited app navigation mode.";
            tts.speak(response);
            callback.onResult(response);
            return;
        }

        if (appCommandNavigator.isActive()) {
            appCommandNavigator.processUserCommand(rawText, new AppCommandNavigator.ExecutionCallback() {
                @Override
                public void onSuccess(String spokenResponse) {
                    tts.speak(spokenResponse);
                    callback.onResult(spokenResponse);
                }

                @Override
                public void onError(String errorMessage) {
                    tts.speak(errorMessage);
                    callback.onResult(errorMessage);
                }
            });
            return;
        }

        switch (command.getCommandType()) {

            // ── Media ───────────────────────────────────────────────────────
            case YOUTUBE_PLAY:
                mediaCommands.playOnYouTube(rawText, callback);
                break;

            case SPOTIFY_PLAY:
                mediaCommands.playOnSpotify(rawText, callback);
                break;

            // ── Communication ───────────────────────────────────────────────
            case SEND_SMS:
                communicationCommands.sendSms(rawText, callback);
                break;

            case SEND_WHATSAPP:
                communicationCommands.sendWhatsApp(rawText, callback);
                break;

            // ── Scheduling ──────────────────────────────────────────────────
            case SET_ALARM:
                schedulerCommands.setAlarm(rawText, callback);
                break;

            case SET_TIMER:
                schedulerCommands.setTimer(rawText, callback);
                break;

            case CREATE_CALENDAR_EVENT:
                schedulerCommands.createCalendarEvent(rawText, callback);
                break;

            // ── Navigation / Nearby ─────────────────────────────────────────
            case NAVIGATE:
                // First try ExternalNavigationCommands (no API key needed)
                externalNavigationCommands.navigateTo(rawText, callback);
                break;

            case NEARBY_SEARCH:
                externalNavigationCommands.findNearby(rawText, callback);
                break;

            // ── Web ─────────────────────────────────────────────────────────
            case WEB_SEARCH:
                webCommands.performWebSearch(rawText, callback);
                break;

            case WEATHER_CHECK:
                webCommands.checkWeather(rawText, callback);
                break;

            case NEWS_CHECK:
                webCommands.showNews(rawText, callback);
                break;

            // ── App / Phone / Core ──────────────────────────────────────────
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
                launchCameraActivity(command.getCommandType(), callback);
                break;

            case READ_SCREEN:
                com.visionassist.accessibility.VisionAccessibilityService accessibilityService = 
                        com.visionassist.accessibility.VisionAccessibilityService.getInstance();
                if (accessibilityService != null) {
                    String screenDesc = accessibilityService.getCurrentScreenDescription();
                    tts.speak(screenDesc);
                    callback.onResult(screenDesc);
                } else {
                    String msg = "Accessibility service is not running. Please enable EchoVision in Accessibility settings.";
                    tts.speak(msg);
                    callback.onResult(msg);
                }
                break;

            case EMERGENCY_SOS:
                emergencyManager.triggerSOS(callback);
                break;

            case READ_NOTIFICATIONS: {
                NotificationReaderService notifService = NotificationReaderService.getInstance();
                if (notifService != null) {
                    tts.speak("Reading your notifications.");
                    notifService.readAllActiveNotifications(tts);
                } else {
                    String msg = "Notification access is not granted. Please enable EchoVision in Notification Access settings.";
                    tts.speak(msg);
                    callback.onResult(msg);
                    
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
                break;
            }

            case GO_HOME:
                systemCommands.goToHome(callback);
                break;

            case HELP:
                systemCommands.readHelp(callback);
                break;

            case STOP:
                tts.stop();
                callback.onResult("Stopped.");
                break;

            // ── System Controls ─────────────────────────────────────────────
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

            case APP_NAVIGATION_START:
                appCommandNavigator.startNavigation();
                String msg = "App navigation mode started. What would you like to do on this screen?";
                tts.speak(msg);
                callback.onResult(msg);
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
     * More specific patterns are checked before generic ones to avoid false positives.
     */
    private VoiceCommand classify(String text) {
        String t = text.toLowerCase().trim();

        // ── Check for pending contact state first ─────────────────────────
        if (phoneCommands.hasPendingContacts()) {
            if (t.contains("option") || t.contains("number") || t.contains("choice") ||
                t.matches(".*\\bone\\b.*") || t.matches(".*\\btwo\\b.*") ||
                t.matches(".*\\bthree\\b.*") || t.matches(".*\\bfour\\b.*") ||
                t.matches(".*\\bfive\\b.*") || t.contains("1") || t.contains("2") ||
                t.contains("3") || t.contains("4") || t.contains("5")) {
                return new VoiceCommand(text, VoiceCommand.CommandType.SELECT_CONTACT);
            }
        }

        // ── Emergency (highest priority after state) ──────────────────────
        if (t.contains(AppConstants.CMD_SOS) || t.contains(AppConstants.CMD_EMERGENCY)
                || t.contains("help me")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.EMERGENCY_SOS);
        }

        // ── Media ─────────────────────────────────────────────────────────
        // Check YouTube: "play X on youtube" or "youtube X" or "play X youtube"
        if (t.contains(AppConstants.CMD_YOUTUBE) ||
                (t.contains(AppConstants.CMD_PLAY) && t.contains("youtube"))) {
            return new VoiceCommand(text, VoiceCommand.CommandType.YOUTUBE_PLAY);
        }
        // Check Spotify
        if (t.contains(AppConstants.CMD_SPOTIFY) ||
                (t.contains(AppConstants.CMD_PLAY) && t.contains("spotify"))) {
            return new VoiceCommand(text, VoiceCommand.CommandType.SPOTIFY_PLAY);
        }

        // ── Communication ─────────────────────────────────────────────────
        // WhatsApp before SMS (more specific)
        if (t.contains(AppConstants.CMD_WHATSAPP)) {
            return new VoiceCommand(text, VoiceCommand.CommandType.SEND_WHATSAPP);
        }
        // SMS / text message
        if ((t.contains(AppConstants.CMD_SEND) || t.contains("text")) &&
                (t.contains(AppConstants.CMD_SMS) || t.contains("message") || t.contains("text"))) {
            return new VoiceCommand(text, VoiceCommand.CommandType.SEND_SMS);
        }

        // ── Scheduling ────────────────────────────────────────────────────
        // Timer before alarm (both might contain "set")
        if (t.contains(AppConstants.CMD_TIMER) || t.contains("countdown")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.SET_TIMER);
        }
        if (t.contains(AppConstants.CMD_ALARM) || t.contains("wake me up") ||
                t.contains("wake me at")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.SET_ALARM);
        }
        if (t.contains(AppConstants.CMD_CALENDAR) || t.contains(AppConstants.CMD_REMINDER) ||
                t.contains(AppConstants.CMD_EVENT) || t.contains(AppConstants.CMD_SCHEDULE)) {
            return new VoiceCommand(text, VoiceCommand.CommandType.CREATE_CALENDAR_EVENT);
        }

        // ── Navigation / Nearby ───────────────────────────────────────────
        if (t.contains("go to home") || t.contains("go home") || t.contains("home screen") || t.equals("home")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.GO_HOME);
        }

        // Nearby before navigate (e.g. "find nearby restaurants")
        if (t.contains(AppConstants.CMD_NEARBY) || t.contains("near me") ||
                t.contains("close to me") || t.contains("closest") ||
                t.contains("nearest") || t.contains("find near")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.NEARBY_SEARCH);
        }
        if (t.contains(AppConstants.CMD_NAVIGATE) || t.contains("go to") ||
                t.contains("directions to") || t.contains("take me to") ||
                t.contains("drive to") || t.contains("open maps")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.NAVIGATE);
        }

        // ── Web ───────────────────────────────────────────────────────────
        // Weather before generic search to avoid "weather" → search
        if (t.contains(AppConstants.CMD_WEATHER)) {
            return new VoiceCommand(text, VoiceCommand.CommandType.WEATHER_CHECK);
        }
        if (t.contains(AppConstants.CMD_NEWS) || t.contains("headlines")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.NEWS_CHECK);
        }
        if (t.contains(AppConstants.CMD_SEARCH) || t.contains(AppConstants.CMD_GOOGLE)
                || t.startsWith("what is") || t.startsWith("who is") || t.startsWith("how to")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.WEB_SEARCH);
        }

        // ── App / Phone / Core ────────────────────────────────────────────
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
        if (t.contains(AppConstants.CMD_NOTIFICATIONS) || t.contains("notification") || t.contains("message")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.READ_NOTIFICATIONS);
        }
        if (t.contains(AppConstants.CMD_HELP) || t.contains("what can you do")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.HELP);
        }
        if (t.equals(AppConstants.CMD_STOP) || t.equals("cancel") || t.equals("quiet")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.STOP);
        }

        // ── System Controls ───────────────────────────────────────────────
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
        if (t.contains(AppConstants.CMD_VOLUME) || t.contains("sound") ||
                t.contains("louder") || t.contains("quieter")) {
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

        if (t.equals("exit app navigation") || t.equals("stop navigation") || t.equals("stop app navigation") || t.equals("exit navigation")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.APP_NAVIGATION_STOP);
        }

        if (t.equals("start app navigation") || t.equals("start navigation") || t.equals("start control")) {
            return new VoiceCommand(text, VoiceCommand.CommandType.APP_NAVIGATION_START);
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
}
