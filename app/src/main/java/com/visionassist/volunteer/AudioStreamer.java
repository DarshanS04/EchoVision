package com.visionassist.volunteer;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class AudioStreamer {
    private static final String TAG = "AudioStreamer";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_FACTOR = 2;

    private AudioRecord mAudioRecord;
    private AudioTrack mAudioTrack;
    private Thread mRecordThread;
    private final AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private final AudioCallback mCallback;

    public interface AudioCallback {
        void onAudioDataCaptured(byte[] data);
    }

    public AudioStreamer(AudioCallback callback) {
        this.mCallback = callback;
        initPlayer();
    }

    private void initPlayer() {
        int minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT);
        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT,
                minBufSize * BUFFER_SIZE_FACTOR,
                AudioTrack.MODE_STREAM
        );
        mAudioTrack.play();
    }

    @SuppressLint("MissingPermission")
    public void startRecording() {
        if (mIsRecording.get()) return;

        int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT);
        mAudioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                minBufSize * BUFFER_SIZE_FACTOR
        );

        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            return;
        }

        // Enable Echo Cancellation if available
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler aec = AcousticEchoCanceler.create(mAudioRecord.getAudioSessionId());
            if (aec != null) {
                aec.setEnabled(true);
            }
        }

        mIsRecording.set(true);
        mAudioRecord.startRecording();

        mRecordThread = new Thread(() -> {
            byte[] buffer = new byte[minBufSize];
            while (mIsRecording.get()) {
                int read = mAudioRecord.read(buffer, 0, buffer.length);
                if (read > 0 && mCallback != null) {
                    byte[] data = new byte[read];
                    System.arraycopy(buffer, 0, data, 0, read);
                    mCallback.onAudioDataCaptured(data);
                }
            }
        }, "AudioRecordThread");
        mRecordThread.start();
    }

    public void stopRecording() {
        mIsRecording.set(false);
        if (mRecordThread != null) {
            try {
                mRecordThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mRecordThread = null;
        }
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }

    public void playAudio(byte[] data) {
        if (mAudioTrack != null && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            mAudioTrack.write(data, 0, data.length);
        }
    }

    public void release() {
        stopRecording();
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }
}
