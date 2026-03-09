package com.visionassist.navigation;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;

/**
 * Navigation assistant using Google Maps URI intent for voice-guided navigation.
 * Fetches current GPS coordinates, then launches Maps navigation.
 */
public class NavigationAssistant {

    private static final String TAG = "NavigationAssistant";

    private final Context context;
    private final TTSManager tts;
    private final LocationManager locationManager;

    public NavigationAssistant(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
        this.locationManager = new LocationManager(context);
    }

    /**
     * Starts navigation to a named destination using Google Maps.
     * Gets current location first, then launches Maps with voice guidance.
     */
    public void navigateTo(String destination, CommandRouter.CommandCallback callback) {
        if (destination == null || destination.trim().isEmpty()) {
            tts.speak("Where would you like to navigate to?");
            callback.onResult("Destination not specified.");
            return;
        }

        AppLogger.i(TAG, "Navigating to: " + destination);
        tts.speak("Getting your location and starting navigation to " + destination);

        locationManager.getCurrentLocation(new LocationManager.LocationCallback2() {
            @Override
            public void onLocationReceived(Location location) {
                // Launch Google Maps with navigation intent
                String encodedDest = Uri.encode(destination);
                String mapsUri = "google.navigation:q=" + encodedDest + "&mode=w";
                Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapsUri));
                navIntent.setPackage("com.google.android.apps.maps");
                navIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                try {
                    context.startActivity(navIntent);
                    String response = "Navigation started. Follow voice directions to " + destination;
                    tts.speak(response);
                    callback.onResult(response);
                } catch (Exception e) {
                    // Maps not installed — fallback to browser
                    AppLogger.w(TAG, "Google Maps not found, fallback to browser");
                    String fallbackUri = "https://maps.google.com/?q=" + encodedDest + "&dirflg=w";
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browserIntent);
                    String response = "Opening maps in browser for " + destination;
                    tts.speak(response);
                    callback.onResult(response);
                }
            }

            @Override
            public void onError(String error) {
                AppLogger.e(TAG, "Location error: " + error);
                // Still try to navigate without current location
                String encodedDest = Uri.encode(destination);
                String mapsUri = "google.navigation:q=" + encodedDest;
                Intent navIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mapsUri));
                navIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(navIntent);
                } catch (Exception ex) {
                    AppLogger.e(TAG, "Could not start navigation", ex);
                }
                String response = "Starting navigation to " + destination;
                tts.speak(response);
                callback.onResult(response);
            }
        });
    }
}
