package com.visionassist.camera.ObjectDetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import androidx.camera.core.ImageProxy;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.models.DetectionResult;
import com.visionassist.voice.tts.TTSManager;
import org.tensorflow.lite.Interpreter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs TFLite object detection on image frames.
 * Returns DetectionResult list and generates voice descriptions.
 */
public class ObjectDetector {

    private static final String TAG = "ObjectDetector";
    private static final int NUM_DETECTIONS = 10;
    private static final int BYTES_PER_CHANNEL = 4; // float32

    private final Context context;
    private final TFLiteModelLoader modelLoader;
    private final TTSManager tts;
    private boolean isInitialised = false;

    public interface DetectionCallback {
        void onDetected(List<DetectionResult> results);
        void onError(String error);
    }

    public ObjectDetector(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
        this.modelLoader = new TFLiteModelLoader();
    }

    /**
     * Initialise the detector. Call on background thread.
     */
    public void init() {
        modelLoader.load(context);
        isInitialised = modelLoader.isLoaded();
        if (!isInitialised) {
            AppLogger.e(TAG, "ObjectDetector failed to initialise — model not found");
            tts.speak("Object detection model not available. Please add the model file.");
        }
    }

    /**
     * Run detection on a Bitmap. Results are returned via callback.
     */
    public void detect(Bitmap bitmap, DetectionCallback callback) {
        if (!isInitialised) {
            callback.onError("Model not loaded");
            return;
        }

        try {
            Bitmap resized = Bitmap.createScaledBitmap(bitmap,
                    AppConstants.TFLITE_INPUT_SIZE, AppConstants.TFLITE_INPUT_SIZE, true);

            ByteBuffer inputBuffer = convertBitmapToByteBuffer(resized);

            // Output arrays for SSD MobileNet
            float[][][] outputBoxes = new float[1][NUM_DETECTIONS][4];
            float[][] outputClasses = new float[1][NUM_DETECTIONS];
            float[][] outputScores = new float[1][NUM_DETECTIONS];
            float[] numDetections = new float[1];

            Object[] inputs = {inputBuffer};
            java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
            outputs.put(0, outputBoxes);
            outputs.put(1, outputClasses);
            outputs.put(2, outputScores);
            outputs.put(3, numDetections);

            modelLoader.getInterpreter().runForMultipleInputsOutputs(inputs, outputs);

            List<DetectionResult> results = new ArrayList<>();
            int count = (int) numDetections[0];
            List<String> labels = modelLoader.getLabels();

            for (int i = 0; i < count; i++) {
                float score = outputScores[0][i];
                if (score < AppConstants.DETECTION_CONFIDENCE_THRESHOLD) continue;

                int classIdx = (int) outputClasses[0][i];
                String label = (classIdx < labels.size()) ? labels.get(classIdx) : "unknown";

                float top = outputBoxes[0][i][0];
                float left = outputBoxes[0][i][1];
                float bottom = outputBoxes[0][i][2];
                float right = outputBoxes[0][i][3];
                RectF box = new RectF(left, top, right, bottom);

                results.add(new DetectionResult(label, score, box));
            }

            callback.onDetected(results);
        } catch (Exception e) {
            AppLogger.e(TAG, "Detection failed", e);
            callback.onError("Detection error: " + e.getMessage());
        }
    }

    /**
     * Speak detection results aloud.
     */
    public void speakResults(List<DetectionResult> results) {
        if (results.isEmpty()) {
            tts.speak("I couldn't detect any objects clearly. Please ensure good lighting.");
            return;
        }
        StringBuilder sb = new StringBuilder("I can see: ");
        for (int i = 0; i < Math.min(results.size(), 5); i++) {
            sb.append(results.get(i).getVoiceDescription());
            if (i < results.size() - 1) sb.append(" Also, ");
        }
        tts.speak(sb.toString());
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        int inputSize = AppConstants.TFLITE_INPUT_SIZE;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(
                BYTES_PER_CHANNEL * inputSize * inputSize * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputSize * inputSize];
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);

        for (int pixel : pixels) {
            byteBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // R
            byteBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);  // G
            byteBuffer.putFloat((pixel & 0xFF) / 255.0f);          // B
        }
        return byteBuffer;
    }

    public void close() {
        modelLoader.close();
        isInitialised = false;
    }
}
