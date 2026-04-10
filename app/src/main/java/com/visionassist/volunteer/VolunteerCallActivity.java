package com.visionassist.volunteer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.visionassist.R;
import com.visionassist.voice.tts.TTSManager;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VolunteerCallActivity extends AppCompatActivity implements VolunteerCommunication.CommunicationCallback {
    private static final String TAG = "VolunteerCallAct";
    
    private VolunteerCommunication comm;
    private TextView callStatus;
    private PreviewView previewView;
    private TTSManager tts;
    private ExecutorService analysisExecutor;
    private long lastFrameTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_call);

        callStatus = findViewById(R.id.call_status);
        previewView = findViewById(R.id.blind_preview_view);
        tts = TTSManager.getInstance(this);

        analysisExecutor = Executors.newSingleThreadExecutor();
        comm = SocketClientManager.getInstance().getCommunication();

        findViewById(R.id.end_call_button).setOnClickListener(v -> finish());

        startCamera();
        comm.connect("blind", this);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(analysisExecutor, this::processImage);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void processImage(ImageProxy image) {
        // Limit frame rate to ~10fps to avoid flooding the socket
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime < 100) {
            image.close();
            return;
        }
        lastFrameTime = currentTime;

        try {
            byte[] jpegData = imageToJpeg(image);
            if (comm.isConnected()) {
                comm.sendVideoFrame(jpegData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error encoding image", e);
        } finally {
            image.close();
        }
    }

    private byte[] imageToJpeg(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, android.graphics.ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 50, out);
        return out.toByteArray();
    }

    @Override
    public void onConnect() {
        runOnUiThread(() -> {
            callStatus.setText("Connected to signaling server.\nStarting stream...");
            tts.speak("Connected to server.");
        });
    }

    @Override
    public void onDisconnect() {
        runOnUiThread(() -> {
            callStatus.setText("Server disconnected.");
            tts.speak("Server disconnected.");
        });
    }

    @Override
    public void onPeerOnline() {
        runOnUiThread(() -> {
            callStatus.setText("Volunteer is now online!");
            tts.speak("Volunteer is now online and watching.");
        });
    }

    @Override
    public void onPeerOffline() {
        runOnUiThread(() -> {
            callStatus.setText("Volunteer went offline.");
            tts.speak("Volunteer went offline.");
        });
    }

    @Override
    public void onVideoFrameReceived(byte[] data) {
        // Blind user doesn't receive video typically in this mode
    }

    @Override
    public void onAudioChunkReceived(byte[] data) {
        // TODO: Play incoming volunteer audio
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            callStatus.setText("Error: " + message);
            tts.speak("Communication error.");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        comm.disconnect();
        analysisExecutor.shutdown();
    }
}
