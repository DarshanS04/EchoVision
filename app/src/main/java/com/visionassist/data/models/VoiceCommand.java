package com.visionassist.data.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a parsed voice command from the user.
 */
public class VoiceCommand {

    public enum CommandType {
        OPEN_APP,
        MAKE_CALL,
        READ_NOTIFICATIONS,
        READ_SCREEN,
        CAMERA_DETECT,
        CAMERA_OCR,
        CAMERA_BARCODE,
        NAVIGATE,
        EMERGENCY_SOS,
        BATTERY_STATUS,
        TIME_DATE,
        GEMINI_QUERY,
        HELP,
        STOP,
        SELECT_CONTACT,
        TOGGLE_WIFI,
        TOGGLE_BLUETOOTH,
        TOGGLE_DATA,
        TOGGLE_FLASHLIGHT,
        TOGGLE_AIRPLANE_MODE,
        SET_VOLUME,
        SET_BRIGHTNESS,
        TOGGLE_DND,
        TOGGLE_ROTATION,
        TOGGLE_BATTERY_SAVER,
        TOGGLE_LOCATION,
        YOUTUBE_PLAY,
        SPOTIFY_PLAY,
        SEND_SMS,
        SEND_WHATSAPP,
        SET_ALARM,
        SET_TIMER,
        CREATE_CALENDAR_EVENT,
        WEB_SEARCH,
        WEATHER_CHECK,
        NEWS_CHECK,
        NEARBY_SEARCH,
        GO_HOME,
        APP_NAVIGATION_START,
        APP_NAVIGATION_STOP,
        UNKNOWN
    }

    private final String rawText;
    private final CommandType commandType;
    private final Map<String, String> parameters;
    private final long timestamp;

    public VoiceCommand(String rawText, CommandType commandType) {
        this.rawText = rawText;
        this.commandType = commandType;
        this.parameters = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    public VoiceCommand(String rawText, CommandType commandType, Map<String, String> parameters) {
        this.rawText = rawText;
        this.commandType = commandType;
        this.parameters = parameters != null ? parameters : new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    public String getRawText() { return rawText; }
    public CommandType getCommandType() { return commandType; }
    public Map<String, String> getParameters() { return parameters; }
    public long getTimestamp() { return timestamp; }

    public String getParameter(String key) {
        return parameters.getOrDefault(key, "");
    }

    public void addParameter(String key, String value) {
        parameters.put(key, value);
    }

    @Override
    public String toString() {
        return "VoiceCommand{type=" + commandType + ", text='" + rawText + "'}";
    }
}
