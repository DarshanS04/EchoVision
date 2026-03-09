package com.visionassist.ui.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.visionassist.R;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.permissions.PermissionManager;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.services.AssistantService;
import com.visionassist.triggers.VolumeButtonTrigger;
import com.visionassist.ui.components.VoiceButton;
import com.visionassist.ui.fragments.AssistantFragment;
import com.visionassist.voice.speech.VoiceCommandListener;
import com.visionassist.voice.tts.TTSManager;

/**
 * Main entry Activity for VisionAssist.
 * Shows central voice button and assistant status.
 * Starts AssistantService and coordinates permission requests.
 */
public class MainActivity extends AppCompatActivity
        implements VoiceCommandListener.UIUpdateCallback {

    private static final String TAG = "MainActivity";

    private PermissionManager permissionManager;
    private VolumeButtonTrigger volumeButtonTrigger;
    private TTSManager tts;
    private AppRepository repository;
    private VoiceButton voiceButton;
    private TextView statusText;
    private TextView responseText;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tts = TTSManager.getInstance(this);
        repository = AppRepository.getInstance(this);

        initViews();
        setupPermissions();
        setupVolumeButtonTrigger();
        startAssistantService();

        // Load assistant fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new AssistantFragment())
                    .commit();
        }
    }

    private void initViews() {
        voiceButton = findViewById(R.id.voice_button);
        statusText = findViewById(R.id.status_text);
        responseText = findViewById(R.id.response_text);

        // Voice button tap = start/stop listening
        voiceButton.setOnClickListener(v -> {
            if (AssistantService.isRunning) {
                Intent intent = new Intent(this, AssistantService.class);
                if (!isListening) {
                    intent.setAction(AppConstants.ACTION_START_LISTENING);
                } else {
                    intent.setAction(AppConstants.ACTION_STOP_LISTENING);
                }
                startService(intent);
            } else {
                startAssistantService();
            }
        });

        // Settings button
        View settingsBtn = findViewById(R.id.settings_button);
        if (settingsBtn != null) {
            settingsBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));
        }

        // Camera button
        View cameraBtn = findViewById(R.id.camera_button);
        if (cameraBtn != null) {
            cameraBtn.setOnClickListener(v ->
                    startActivity(new Intent(this, CameraActivity.class)));
        }
    }

    private void setupPermissions() {
        permissionManager = new PermissionManager(this);
        permissionManager.setCallback(new PermissionManager.PermissionCallback() {
            @Override
            public void onAllPermissionsGranted() {
                AppLogger.i(TAG, "All permissions granted");
                tts.speak("All permissions granted. VisionAssist is ready.");
            }

            @Override
            public void onPermissionsDenied(String[] deniedPermissions) {
                AppLogger.w(TAG, "Some permissions denied: " + deniedPermissions.length);
                tts.speak("Some permissions were denied. Core features may be unavailable.");
            }
        });

        if (!permissionManager.hasAllCriticalPermissions(this)) {
            permissionManager.requestAllPermissions();
        } else {
            AppLogger.i(TAG, "All permissions already granted");
        }
    }

    private void setupVolumeButtonTrigger() {
        volumeButtonTrigger = new VolumeButtonTrigger(this);
        volumeButtonTrigger.setCallback(() -> {
            AppLogger.i(TAG, "Volume trigger in MainActivity");
            Intent intent = new Intent(this, AssistantService.class);
            intent.setAction(AppConstants.ACTION_START_LISTENING);
            startService(intent);
        });
    }

    private void startAssistantService() {
        Intent serviceIntent = new Intent(this, AssistantService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        AppLogger.i(TAG, "AssistantService started");
    }

    // Volume button hardware trigger
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (volumeButtonTrigger != null && volumeButtonTrigger.handleKeyEvent(event)) {
            return true; // Consumed
        }
        return super.dispatchKeyEvent(event);
    }

    // VoiceCommandListener.UIUpdateCallback implementations
    @Override
    public void onListeningStateChanged(boolean listening) {
        isListening = listening;
        runOnUiThread(() -> {
            if (voiceButton != null) voiceButton.setListening(listening);
            if (statusText != null) {
                statusText.setText(listening ? "Listening…" : "Press button or Vol Up+Down");
            }
        });
    }

    @Override
    public void onPartialTextReceived(String partialText) {
        runOnUiThread(() -> {
            if (statusText != null) statusText.setText(partialText);
        });
    }

    @Override
    public void onCommandProcessed(String commandText, String response) {
        runOnUiThread(() -> {
            if (responseText != null) responseText.setText(response);
            if (statusText != null) statusText.setText("Ready");
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissionManager != null) {
            permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tts.speak("VisionAssist is ready. Press the voice button or volume up and down to activate.");
    }
}
