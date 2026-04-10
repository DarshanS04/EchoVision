package com.visionassist.volunteer;

import android.util.Log;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java.net.URISyntaxException;

public class SocketVolunteerCommunication implements VolunteerCommunication {
    private static final String TAG = "SocketVolComm";
    private static final String SERVER_URL = "http://10.0.2.2:3000"; // Emulator host

    private Socket mSocket;
    private CommunicationCallback mCallback;
    private boolean mIsConnected = false;

    @Override
    public void connect(String peerType, CommunicationCallback callback) {
        this.mCallback = callback;
        try {
            mSocket = IO.socket(SERVER_URL);
        } catch (URISyntaxException e) {
            callback.onError("Connection error: " + e.getMessage());
            return;
        }

        mSocket.on(Socket.EVENT_CONNECT, (args) -> {
            mIsConnected = true;
            Log.d(TAG, "Connected to server");
            if (peerType.equals("volunteer")) {
                mSocket.emit("join_volunteer");
            } else {
                mSocket.emit("join_blind");
            }
            if (mCallback != null) mCallback.onConnect();
        });

        mSocket.on(Socket.EVENT_DISCONNECT, (args) -> {
            mIsConnected = false;
            Log.d(TAG, "Disconnected from server");
            if (mCallback != null) mCallback.onDisconnect();
        });

        mSocket.on("blind_online", (args) -> {
            if (mCallback != null) mCallback.onPeerOnline();
        });

        mSocket.on("volunteer_online", (args) -> {
            if (mCallback != null) mCallback.onPeerOnline();
        });

        mSocket.on("blind_offline", (args) -> {
            if (mCallback != null) mCallback.onPeerOffline();
        });

        mSocket.on("volunteer_offline", (args) -> {
            if (mCallback != null) mCallback.onPeerOffline();
        });

        mSocket.on("video_frame", (args) -> {
            if (mCallback != null && args.length > 0) {
                mCallback.onVideoFrameReceived((byte[]) args[0]);
            }
        });

        mSocket.on("audio_chunk", (args) -> {
            if (mCallback != null && args.length > 0) {
                mCallback.onAudioChunkReceived((byte[]) args[0]);
            }
        });

        mSocket.on(Socket.EVENT_CONNECT_ERROR, (args) -> {
            if (mCallback != null) mCallback.onError("Socket connection error");
        });

        mSocket.connect();
    }

    @Override
    public void disconnect() {
        if (mSocket != null) {
            mSocket.disconnect();
            mSocket.off();
            mIsConnected = false;
        }
    }

    @Override
    public void sendVideoFrame(byte[] data) {
        if (mIsConnected) {
            mSocket.emit("video_frame", (Object) data);
        }
    }

    @Override
    public void sendAudioChunk(byte[] data) {
        if (mIsConnected) {
            mSocket.emit("audio_chunk", (Object) data);
        }
    }

    @Override
    public boolean isConnected() {
        return mIsConnected;
    }
}
