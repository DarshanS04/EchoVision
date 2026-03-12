package com.visionassist.core.constants;

/**
 * App-wide constants for VisionAssist.
 */
public final class AppConstants {

    private AppConstants() {}

    // Gemini
    public static final String GEMINI_MODEL_NAME = "gemini-2.5-flash";
    public static final String GEMINI_VISION_MODEL = "gemini-2.5-flash";

    // TFLite Model
    public static final String TFLITE_MODEL_PATH = "models/object_detection.tflite";
    public static final String TFLITE_LABELS_PATH = "models/labelmap.txt";
    public static final int TFLITE_INPUT_SIZE = 300;
    public static final float DETECTION_CONFIDENCE_THRESHOLD = 0.5f;

    // SharedPreferences keys
    public static final String PREFS_NAME = "visionassist_prefs";
    public static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    public static final String KEY_EMERGENCY_CONTACT = "emergency_contact";
    public static final String KEY_EMERGENCY_CONTACT_NAME = "emergency_contact_name";
    public static final String KEY_TTS_SPEED = "tts_speed";
    public static final String KEY_TTS_PITCH = "tts_pitch";
    public static final String KEY_OFFLINE_MODE = "offline_mode";
    public static final String KEY_READ_NOTIFICATIONS = "read_notifications";
    public static final String KEY_FIRST_LAUNCH = "first_launch";

    // TTS defaults
    public static final float DEFAULT_TTS_SPEED = 1.0f;
    public static final float DEFAULT_TTS_PITCH = 1.0f;

    // Notification
    public static final int NOTIFICATION_ID_ASSISTANT = 1001;
    public static final int NOTIFICATION_ID_SOS = 1002;
    public static final String NOTIFICATION_CHANNEL_ASSISTANT = "assistant_channel";
    public static final String NOTIFICATION_CHANNEL_SOS = "sos_channel";

    // Volume trigger
    public static final long VOLUME_TRIGGER_WINDOW_MS = 1000L; // Both buttons within 1 second
    public static final int VOLUME_TRIGGER_BEEP_FREQUENCY = 880;
    public static final int VOLUME_TRIGGER_BEEP_DURATION = 200;

    // Intent actions
    public static final String ACTION_START_LISTENING = "com.visionassist.ACTION_START_LISTENING";
    public static final String ACTION_STOP_LISTENING = "com.visionassist.ACTION_STOP_LISTENING";
    public static final String ACTION_SPEAK = "com.visionassist.ACTION_SPEAK";
    public static final String ACTION_SOS = "com.visionassist.ACTION_SOS";

    // Intent extras
    public static final String EXTRA_SPEECH_TEXT = "speech_text";
    public static final String EXTRA_COMMAND_TYPE = "command_type";

    // Maps
    public static final String MAPS_API_KEY_PLACEHOLDER = "YOUR_MAPS_API_KEY_HERE";

    // Command keywords
    public static final String CMD_OPEN = "open";
    public static final String CMD_CALL = "call";
    public static final String CMD_READ = "read";
    public static final String CMD_WHAT_SCREEN = "what is on my screen";
    public static final String CMD_WHAT_FRONT = "what is in front";
    public static final String CMD_NAVIGATE = "navigate";
    public static final String CMD_SOS = "sos";
    public static final String CMD_EMERGENCY = "emergency";
    public static final String CMD_BATTERY = "battery";
    public static final String CMD_TIME = "time";
    public static final String CMD_DATE = "date";
    public static final String CMD_NOTIFICATIONS = "notifications";
    public static final String CMD_SCAN = "scan";
    public static final String CMD_DESCRIBE = "describe";
    public static final String CMD_HELP = "help";
    public static final String CMD_STOP = "stop";

    // SQL / Storage
    public static final String DB_NAME = "visionassist.db";
    public static final int DB_VERSION = 1;

    // Permissions request codes
    public static final int REQUEST_CODE_ALL_PERMISSIONS = 100;
    public static final int REQUEST_CODE_MICROPHONE = 101;
    public static final int REQUEST_CODE_CAMERA = 102;
    public static final int REQUEST_CODE_PHONE = 103;
    public static final int REQUEST_CODE_LOCATION = 104;
    public static final int REQUEST_CODE_CONTACTS = 105;
    public static final int REQUEST_CODE_SMS = 106;
}
