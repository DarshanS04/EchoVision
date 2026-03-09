package com.visionassist.camera.ObjectDetection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import org.tensorflow.lite.Interpreter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a TFLite model and its label map from assets.
 */
public class TFLiteModelLoader {

    private static final String TAG = "TFLiteModelLoader";

    private MappedByteBuffer modelBuffer;
    private Interpreter interpreter;
    private final List<String> labels = new ArrayList<>();
    private boolean loaded = false;

    public TFLiteModelLoader() {}

    /**
     * Loads the TFLite model and label map from assets.
     * Call this on a background thread.
     */
    public void load(Context context) {
        try {
            modelBuffer = loadModelFile(context, AppConstants.TFLITE_MODEL_PATH);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(modelBuffer, options);
            loadLabels(context, AppConstants.TFLITE_LABELS_PATH);
            loaded = true;
            AppLogger.i(TAG, "TFLite model loaded with " + labels.size() + " labels");
        } catch (IOException e) {
            AppLogger.e(TAG, "Failed to load TFLite model", e);
            loaded = false;
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String assetPath) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(assetPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY,
                startOffset, declaredLength);
        fileDescriptor.close();
        return buffer;
    }

    private void loadLabels(Context context, String assetPath) throws IOException {
        labels.clear();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(assetPath)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line.trim());
            }
        }
    }

    public Interpreter getInterpreter() { return interpreter; }
    public List<String> getLabels() { return labels; }
    public boolean isLoaded() { return loaded; }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        loaded = false;
    }
}
