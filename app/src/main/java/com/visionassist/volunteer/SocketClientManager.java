package com.visionassist.volunteer;

/**
 * Singleton manager to provide access to the communication module.
 * Following the requested modular pattern.
 */
public class SocketClientManager {
    private static SocketClientManager instance;
    private final VolunteerCommunication communication;

    private SocketClientManager() {
        // Here we inject the specific implementation
        this.communication = new SocketVolunteerCommunication();
    }

    public static synchronized SocketClientManager getInstance() {
        if (instance == null) {
            instance = new SocketClientManager();
        }
        return instance;
    }

    public VolunteerCommunication getCommunication() {
        return communication;
    }
}
