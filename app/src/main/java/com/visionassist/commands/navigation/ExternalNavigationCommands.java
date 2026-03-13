package com.visionassist.commands.navigation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;

/**
 * Handles navigation commands using the device's native Google Maps via Geo Intents.
 * No Google Maps API key required.
 *
 * Uses:
 *   - geo:0,0?q=destination  for turn-by-turn navigation
 *   - geo:0,0?q=type+near+me for nearby search
 */
public class ExternalNavigationCommands {

    private static final String TAG = "ExternalNavCommands";
    private final Context context;
    private final TTSManager tts;

    public ExternalNavigationCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    // ─── Turn-by-turn Navigation ─────────────────────────────────────────────

    /**
     * Starts turn-by-turn navigation to a destination using the default maps app.
     * Voice examples:
     *   "Navigate to Central Park"
     *   "Take me to the airport"
     *   "Directions to Mumbai"
     *   "Go to my home"
     */
    public void navigateTo(String rawText, CommandRouter.CommandCallback callback) {
        String destination = extractDestination(rawText);

        if (destination.isEmpty()) {
            String msg = "Where would you like to go?";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        tts.speak("Starting navigation to " + destination);

        // Use the navigation action for turn-by-turn
        Uri navUri = Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&mode=d");
        Intent intent = new Intent(Intent.ACTION_VIEW, navUri);
        intent.setPackage("com.google.android.apps.maps");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            String msg = "Navigation started to " + destination;
            callback.onResult(msg);
            AppLogger.i(TAG, "Navigating to: " + destination);
        } catch (Exception e) {
            AppLogger.w(TAG, "Google Maps not found, using geo fallback");
            // Fallback: generic geo intent that any maps app can handle
            try {
                Uri geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(destination));
                Intent fallback = new Intent(Intent.ACTION_VIEW, geoUri);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                String msg = "Opening maps for " + destination;
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception ex) {
                String msg = "Sorry, no maps application found on this device.";
                tts.speak(msg);
                callback.onResult(msg);
            }
        }
    }

    // ─── Nearby Search ───────────────────────────────────────────────────────

    /**
     * Opens maps to show nearby points of interest.
     * Voice examples:
     *   "Find nearby hospitals"
     *   "Nearby restaurants"
     *   "Find petrol stations near me"
     *   "Closest ATM"
     */
    public void findNearby(String rawText, CommandRouter.CommandCallback callback) {
        String placeType = extractPlaceType(rawText);

        if (placeType.isEmpty()) {
            String msg = "What would you like to find nearby?";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        tts.speak("Searching for nearby " + placeType);

        // Use geo intent with "near me" query — works in Google Maps and OsmAnd
        Uri geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(placeType + " near me"));
        Intent intent = new Intent(Intent.ACTION_VIEW, geoUri);
        intent.setPackage("com.google.android.apps.maps");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            String msg = "Showing nearby " + placeType;
            callback.onResult(msg);
        } catch (Exception e) {
            AppLogger.w(TAG, "Google Maps not found, using generic geo intent");
            try {
                Uri fallbackUri = Uri.parse("geo:0,0?q=" + Uri.encode(placeType + " near me"));
                Intent fallback = new Intent(Intent.ACTION_VIEW, fallbackUri);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                String msg = "Opening maps to find " + placeType;
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception ex) {
                String msg = "Sorry, no maps application found on this device.";
                tts.speak(msg);
                callback.onResult(msg);
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String extractDestination(String text) {
        return text.toLowerCase()
                .replace("navigate to", "").replace("navigation to", "")
                .replace("go to", "").replace("take me to", "")
                .replace("directions to", "").replace("drive to", "")
                .replace("get me to", "").replace("open maps for", "")
                .replace("open maps to", "").trim();
    }

    private String extractPlaceType(String text) {
        return text.toLowerCase()
                .replace("find nearby", "").replace("nearby", "")
                .replace("find near me", "").replace("near me", "")
                .replace("closest", "").replace("nearest", "")
                .replace("find", "").replace("search for", "")
                .replace("show me", "").replace("locate", "").trim();
    }
}
