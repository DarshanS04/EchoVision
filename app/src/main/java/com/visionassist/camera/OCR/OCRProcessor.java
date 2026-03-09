package com.visionassist.camera.OCR;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.visionassist.core.logger.AppLogger;

/**
 * Uses ML Kit Text Recognition to extract text from bitmaps or image proxies.
 */
public class OCRProcessor {

    private static final String TAG = "OCRProcessor";

    public interface OCRCallback {
        void onTextExtracted(String text);
        void onError(String error);
    }

    private final TextRecognizer recognizer;

    public OCRProcessor() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    /**
     * Processes a Bitmap and returns extracted text via callback.
     */
    public void processImage(Bitmap bitmap, OCRCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String extracted = visionText.getText().trim();
                    AppLogger.i(TAG, "OCR extracted " + extracted.length() + " chars");
                    callback.onTextExtracted(extracted);
                })
                .addOnFailureListener(e -> {
                    AppLogger.e(TAG, "OCR failed", e);
                    callback.onError("Text recognition failed: " + e.getMessage());
                });
    }

    /**
     * Processes an ImageProxy from CameraX analysis and returns extracted text.
     */
    @androidx.camera.core.ExperimentalGetImage
    public void processImageProxy(ImageProxy imageProxy, OCRCallback callback) {
        if (imageProxy.getImage() == null) {
            callback.onError("No image in proxy");
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String extracted = visionText.getText().trim();
                    AppLogger.i(TAG, "OCR proxy: " + extracted.length() + " chars");
                    callback.onTextExtracted(extracted);
                })
                .addOnFailureListener(e -> {
                    AppLogger.e(TAG, "OCR proxy failed", e);
                    callback.onError(e.getMessage());
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    public void close() {
        recognizer.close();
    }
}
