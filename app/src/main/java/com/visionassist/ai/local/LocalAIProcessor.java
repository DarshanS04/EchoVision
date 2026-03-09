package com.visionassist.ai.local;

import android.content.Context;
import android.os.BatteryManager;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.commands.CommandRouter;
import com.visionassist.voice.tts.TTSManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles AI-style queries offline without internet.
 * Provides keyword-based responses for common questions.
 */
public class LocalAIProcessor {

    private static final String TAG = "LocalAIProcessor";

    private final Context context;
    private final TTSManager tts;

    public LocalAIProcessor(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    /**
     * Process a natural language query offline.
     * Returns true if handled locally, false if unknown.
     */
    public boolean processQuery(String query, CommandRouter.CommandCallback callback) {
        String q = query.toLowerCase().trim();

        if (q.contains("what time") || q.contains("current time")) {
            String time = new SimpleDateFormat("h:mm a", Locale.ENGLISH).format(new Date());
            String response = "The current time is " + time;
            tts.speak(response);
            callback.onResult(response);
            return true;
        }

        if (q.contains("what day") || q.contains("what date") || q.contains("today")) {
            String date = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(new Date());
            String response = "Today is " + date;
            tts.speak(response);
            callback.onResult(response);
            return true;
        }

        if (q.contains("battery") || q.contains("charge")) {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int level = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
            String response = level >= 0 ? "Battery is at " + level + " percent." : "Battery level unavailable.";
            tts.speak(response);
            callback.onResult(response);
            return true;
        }

        if (q.contains("who are you") || q.contains("what are you")) {
            String response = "I am VisionAssist, your AI-powered accessibility assistant. "
                    + "I help visually impaired users operate their phone through voice commands.";
            tts.speak(response);
            callback.onResult(response);
            return true;
        }

        if (q.contains("what can you do") || q.contains("help")) {
            String response = "I can open apps, make calls, read your screen, "
                    + "detect objects with the camera, read text, scan barcodes, "
                    + "navigate to places, read notifications, and respond to questions.";
            tts.speak(response);
            callback.onResult(response);
            return true;
        }

        if (q.contains("hello") || q.contains("hi") || q.contains("hey")) {
            String response = "Hello! I'm VisionAssist. How can I help you?";
            tts.speak(response);
            callback.onResult(response);
            return true;
        }

        if (q.contains("thank")) {
            String response = "You're welcome! Is there anything else I can help you with?";
            tts.speak(response);
            callback.onResult(response);
            return true;
        }

        return false; // Unknown query
    }
}
