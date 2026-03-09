package com.visionassist.data.models;

/**
 * Represents a navigation step or route result.
 */
public class NavigationResult {

    private final String destination;
    private final double distanceMetres;
    private final String instruction;
    private final String voicePrompt;
    private final double latitude;
    private final double longitude;

    public NavigationResult(String destination, double distanceMetres,
                            String instruction, double latitude, double longitude) {
        this.destination = destination;
        this.distanceMetres = distanceMetres;
        this.instruction = instruction;
        this.latitude = latitude;
        this.longitude = longitude;
        this.voicePrompt = buildVoicePrompt();
    }

    private String buildVoicePrompt() {
        String distance;
        if (distanceMetres >= 1000) {
            distance = String.format("%.1f kilometres", distanceMetres / 1000.0);
        } else {
            distance = String.format("%.0f metres", distanceMetres);
        }
        return instruction + " in " + distance + " to reach " + destination;
    }

    public String getDestination() { return destination; }
    public double getDistanceMetres() { return distanceMetres; }
    public String getInstruction() { return instruction; }
    public String getVoicePrompt() { return voicePrompt; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public String toString() {
        return "NavigationResult{destination='" + destination + "', distance=" + distanceMetres + "}";
    }
}
