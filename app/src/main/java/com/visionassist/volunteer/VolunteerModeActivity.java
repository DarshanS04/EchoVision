package com.visionassist.volunteer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.visionassist.R;

public class VolunteerModeActivity extends AppCompatActivity implements VolunteerCommunication.CommunicationCallback {
    private static final String TAG = "VolunteerModeAct";

    private VolunteerCommunication comm;
    private TextView connectionStatus;
    private ImageView videoView;
    private AudioStreamer audioStreamer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_mode);

        connectionStatus = findViewById(R.id.connection_status);
        videoView = findViewById(R.id.remote_video_view);

        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        comm = SocketClientManager.getInstance().getCommunication();
        
        audioStreamer = new AudioStreamer(data -> {
            if (comm.isConnected()) {
                comm.sendAudioChunk(data);
            }
        });

        comm.connect("volunteer", this);
    }

    @Override
    public void onConnect() {
        runOnUiThread(() -> {
            connectionStatus.setText("Connected to signaling server. Waiting for blind user...");
        });
    }

    @Override
    public void onDisconnect() {
        runOnUiThread(() -> {
            connectionStatus.setText("Disconnected from server.");
            audioStreamer.stopRecording();
        });
    }

    @Override
    public void onPeerOnline() {
        runOnUiThread(() -> {
            connectionStatus.setText("Connected to blind user!");
            audioStreamer.startRecording();
        });
    }

    @Override
    public void onPeerOffline() {
        runOnUiThread(() -> {
            connectionStatus.setText("Blind user went offline.");
            videoView.setImageBitmap(null);
            audioStreamer.stopRecording();
        });
    }

    @Override
    public void onVideoFrameReceived(byte[] data) {
        runOnUiThread(() -> {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap != null) {
                videoView.setImageBitmap(bitmap);
            }
        });
    }

    @Override
    public void onAudioChunkReceived(byte[] data) {
        if (audioStreamer != null) {
            audioStreamer.playAudio(data);
        }
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            connectionStatus.setText("Error: " + message);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        comm.disconnect();
        if (audioStreamer != null) {
            audioStreamer.release();
        }
    }
}
