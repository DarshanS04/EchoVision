package com.visionassist.ui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.visionassist.R;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.storage.PreferencesManager;
import com.visionassist.voice.tts.TTSManager;

/**
 * Settings screen for VisionAssist.
 * Configure: Gemini API key, emergency contact, TTS speed/pitch, offline mode.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private PreferencesManager prefs;
    private TTSManager tts;

    private EditText apiKeyInput;
    private EditText emergencyNumberInput;
    private EditText emergencyNameInput;
    private SeekBar ttsSpeedBar;
    private SeekBar ttsPitchBar;
    private Switch offlineModeSwitch;
    private Switch notificationSwitch;
    private Switch wakeWordSwitch;
    private TextView ttsSpeedLabel;
    private TextView ttsPitchLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = PreferencesManager.getInstance(this);
        tts = TTSManager.getInstance(this);

        initViews();
        loadCurrentSettings();
        setupListeners();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("VisionAssist Settings");
        }
    }

    private void initViews() {
        apiKeyInput = findViewById(R.id.api_key_input);
        emergencyNumberInput = findViewById(R.id.emergency_number_input);
        emergencyNameInput = findViewById(R.id.emergency_name_input);
        ttsSpeedBar = findViewById(R.id.tts_speed_bar);
        ttsPitchBar = findViewById(R.id.tts_pitch_bar);
        offlineModeSwitch = findViewById(R.id.offline_mode_switch);
        notificationSwitch = findViewById(R.id.notification_switch);
        wakeWordSwitch = findViewById(R.id.wake_word_switch);
        ttsSpeedLabel = findViewById(R.id.tts_speed_label);
        ttsPitchLabel = findViewById(R.id.tts_pitch_label);
    }

    private void loadCurrentSettings() {
        apiKeyInput.setText(prefs.getGeminiApiKey());
        emergencyNumberInput.setText(prefs.getEmergencyContactNumber());
        emergencyNameInput.setText(prefs.getEmergencyContactName());
        offlineModeSwitch.setChecked(prefs.isOfflineModeForced());
        notificationSwitch.setChecked(prefs.shouldReadNotifications());
        wakeWordSwitch.setChecked(prefs.isWakeWordEnabled());

        // Speed: 0.5 - 2.0 mapped to 0-100
        int speedProgress = (int) ((prefs.getTtsSpeed() - 0.5f) / 1.5f * 100);
        ttsSpeedBar.setProgress(Math.max(0, Math.min(100, speedProgress)));
        ttsSpeedLabel.setText(String.format("Speed: %.1fx", prefs.getTtsSpeed()));

        int pitchProgress = (int) ((prefs.getTtsPitch() - 0.5f) / 1.5f * 100);
        ttsPitchBar.setProgress(Math.max(0, Math.min(100, pitchProgress)));
        ttsPitchLabel.setText(String.format("Pitch: %.1fx", prefs.getTtsPitch()));
    }

    private void setupListeners() {
        // Save button
        findViewById(R.id.save_button).setOnClickListener(v -> saveSettings());

        // TTS speed
        ttsSpeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float speed = 0.5f + (progress / 100.0f) * 1.5f;
                ttsSpeedLabel.setText(String.format("Speed: %.1fx", speed));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                float speed = 0.5f + (sb.getProgress() / 100.0f) * 1.5f;
                prefs.setTtsSpeed(speed);
                tts.applyPreferences();
                tts.speak("Speech speed set to " + String.format("%.1f", speed));
            }
        });

        // TTS pitch
        ttsPitchBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float pitch = 0.5f + (progress / 100.0f) * 1.5f;
                ttsPitchLabel.setText(String.format("Pitch: %.1fx", pitch));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                float pitch = 0.5f + (sb.getProgress() / 100.0f) * 1.5f;
                prefs.setTtsPitch(pitch);
                tts.applyPreferences();
            }
        });

        // Test TTS button
        View testBtn = findViewById(R.id.test_tts_button);
        if (testBtn != null) {
            testBtn.setOnClickListener(v ->
                    tts.speak("This is how VisionAssist will sound with your current settings."));
        }

        // Notification Switch
        notificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !isNotificationServiceEnabled()) {
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "Please enable notification access for VisionAssist.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveSettings() {
        String apiKey = apiKeyInput.getText().toString().trim();
        String emergencyNumber = emergencyNumberInput.getText().toString().trim();
        String emergencyName = emergencyNameInput.getText().toString().trim();

        prefs.setGeminiApiKey(apiKey);
        prefs.setEmergencyContactNumber(emergencyNumber);
        prefs.setEmergencyContactName(emergencyName.isEmpty() ? "Emergency Contact" : emergencyName);
        prefs.setOfflineMode(offlineModeSwitch.isChecked());
        prefs.setReadNotifications(notificationSwitch.isChecked());
        prefs.setWakeWordEnabled(wakeWordSwitch.isChecked());

        android.content.Intent intent = new android.content.Intent(this, com.visionassist.services.AssistantService.class);
        intent.setAction(com.visionassist.core.constants.AppConstants.ACTION_UPDATE_WAKEWORD);
        startService(intent);

        AppLogger.i(TAG, "Settings saved");
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        tts.speak("Settings saved successfully.");
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(name);
                if (cn != null && cn.getPackageName().equals(pkgName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (notificationSwitch.isChecked() && !isNotificationServiceEnabled()) {
            // Permission was revoked or not granted
            notificationSwitch.setChecked(false);
            prefs.setReadNotifications(false);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
