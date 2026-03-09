package com.visionassist.navigation;

import android.location.Location;

/**
 * Formats route data into voice-friendly step descriptions.
 */
public class RouteHelper {

    /**
     * Formats a distance in metres into a natural language string.
     */
    public static String formatDistance(double metres) {
        if (metres >= 1000) {
            return String.format("%.1f kilometres", metres / 1000.0);
        } else if (metres >= 100) {
            return String.format("%.0f metres", metres);
        } else {
            return "a few metres";
        }
    }

    /**
     * Builds a voice navigation instruction from step data.
     */
    public static String buildStepInstruction(String maneuver, String roadName, double distMetres) {
        String dist = formatDistance(distMetres);
        if (maneuver == null || maneuver.isEmpty()) {
            return "Continue for " + dist + " on " + roadName;
        }
        switch (maneuver.toLowerCase()) {
            case "turn-left": return "In " + dist + ", turn left onto " + roadName;
            case "turn-right": return "In " + dist + ", turn right onto " + roadName;
            case "straight": return "Continue straight for " + dist + " on " + roadName;
            case "roundabout": return "In " + dist + ", enter the roundabout and take the exit for " + roadName;
            case "arrive": return "You have arrived at " + roadName;
            default: return "In " + dist + ", " + maneuver + " towards " + roadName;
        }
    }

    /**
     * Calculates distance between two GPS points in metres.
     */
    public static double distanceBetween(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    /**
     * Formats a GPS coordinate pair as a human-readable string for TTS.
     */
    public static String formatCoordinates(double lat, double lon) {
        String latDir = lat >= 0 ? "North" : "South";
        String lonDir = lon >= 0 ? "East" : "West";
        return String.format("%.4f degrees %s, %.4f degrees %s",
                Math.abs(lat), latDir, Math.abs(lon), lonDir);
    }

    /**
     * Formats coordinates as a Google Maps URL (for SMS sharing).
     */
    public static String coordinatesToMapsUrl(double lat, double lon) {
        return "https://maps.google.com/?q=" + lat + "," + lon;
    }
}
