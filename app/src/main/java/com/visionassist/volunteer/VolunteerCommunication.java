package com.visionassist.volunteer;

/**
 * Modular interface for volunteer communication.
 * Allows switching between Sockets and WebRTC in the future.
 */
public interface VolunteerCommunication {

    interface CommunicationCallback {
        void onConnect();
        void onDisconnect();
        void onPeerOnline();
        void onPeerOffline();
        void onVideoFrameReceived(byte[] data);
        void onAudioChunkReceived(byte[] data);
        void onError(String message);
    }

    void connect(String peerType, CommunicationCallback callback);
    void disconnect();
    void sendVideoFrame(byte[] data);
    void sendAudioChunk(byte[] data);
    boolean isConnected();
}
