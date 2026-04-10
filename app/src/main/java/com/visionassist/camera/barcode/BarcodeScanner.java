package com.visionassist.camera.barcode;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;
import java.util.List;

/**
 * Uses ML Kit Barcode Scanning to detect QR codes, EAN, UPC, etc.
 * Results are spoken via TTS.
 */
public class BarcodeScanner {

    private static final String TAG = "BarcodeScanner";

    public interface ScanCallback {
        void onBarcodeDetected(String barcodeValue, String barcodeType);
        void onNoBarcode();
        void onError(String error);
    }

    private final com.google.mlkit.vision.barcode.BarcodeScanner scanner;
    private final TTSManager tts;

    public BarcodeScanner(Context context) {
        this.scanner = BarcodeScanning.getClient();
        this.tts = TTSManager.getInstance(context);
    }

    /**
     * Scan a Bitmap for barcodes/QR codes.
     */
    public void scan(Bitmap bitmap, boolean isContinuous, ScanCallback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes.isEmpty()) {
                        if (!isContinuous) {
                            tts.speak("No barcode or QR code found in the image.");
                        }
                        callback.onNoBarcode();
                        return;
                    }
                    for (Barcode barcode : barcodes) {
                        String value = barcode.getRawValue();
                        String type = barcodeFormatToString(barcode.getFormat());
                        String spoken = "Found a " + type + ": " + value;
                        tts.speak(spoken);
                        callback.onBarcodeDetected(value, type);
                        AppLogger.i(TAG, "Barcode: " + value + " (" + type + ")");
                        break; // Speak only first result
                    }
                })
                .addOnFailureListener(e -> {
                    AppLogger.e(TAG, "Barcode scan failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /**
     * Scan from a CameraX ImageProxy.
     */
    @androidx.camera.core.ExperimentalGetImage
    public void scanFromProxy(ImageProxy imageProxy, ScanCallback callback) {
        if (imageProxy.getImage() == null) {
            callback.onError("No image");
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes.isEmpty()) {
                        callback.onNoBarcode();
                    } else {
                        Barcode first = barcodes.get(0);
                        callback.onBarcodeDetected(first.getRawValue(),
                                barcodeFormatToString(first.getFormat()));
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private String barcodeFormatToString(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE: return "QR code";
            case Barcode.FORMAT_EAN_13: return "EAN-13 barcode";
            case Barcode.FORMAT_EAN_8: return "EAN-8 barcode";
            case Barcode.FORMAT_UPC_A: return "UPC-A barcode";
            case Barcode.FORMAT_UPC_E: return "UPC-E barcode";
            case Barcode.FORMAT_CODE_128: return "Code 128 barcode";
            case Barcode.FORMAT_CODE_39: return "Code 39 barcode";
            case Barcode.FORMAT_PDF417: return "PDF 417 code";
            case Barcode.FORMAT_DATA_MATRIX: return "Data Matrix code";
            default: return "barcode";
        }
    }

    public void close() {
        scanner.close();
    }
}
