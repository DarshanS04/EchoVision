package com.visionassist.ui.activities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import com.visionassist.R;
import com.visionassist.camera.CameraManager;
import com.visionassist.camera.OCR.OCRProcessor;
import com.visionassist.camera.OCR.TextReader;
import com.visionassist.camera.ObjectDetection.ObjectDetector;
import com.visionassist.camera.barcode.BarcodeScanner;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;
import com.visionassist.services.BackgroundProcessingService;
import com.visionassist.voice.tts.TTSManager;

/**
 * Camera activity with three modes: Object Detection, OCR, Barcode.
 * Mode is set via Intent extra "mode" or defaults to detection.
 */
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";

    private CameraManager cameraManager;
    private ObjectDetector objectDetector;
    private OCRProcessor ocrProcessor;
    private BarcodeScanner barcodeScanner;
    private TextReader textReader;
    private TTSManager tts;
    private PreviewView previewView;
    private TextView statusText;
    private String mode;
    private boolean analysisRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "CAMERA_DETECT";

        tts = TTSManager.getInstance(this);
        previewView = findViewById(R.id.preview_view);
        statusText = findViewById(R.id.camera_status_text);

        if (!PermissionUtils.hasCameraPermission(this)) {
            tts.speak("Camera permission is required. Please grant camera access in settings.");
            finish();
            return;
        }

        initDetectors();
        setupButtons();
        startCamera();

        // Auto-trigger depending on launch mode
        switch (mode) {
            case "CAMERA_OCR": tts.speak("Camera ready. Tap scan button to read text."); break;
            case "CAMERA_BARCODE": tts.speak("Camera ready. Tap scan button to detect barcodes."); break;
            default: tts.speak("Camera ready. Tap detect button to identify objects."); break;
        }
    }

    private void initDetectors() {
        ocrProcessor = new OCRProcessor();
        textReader = new TextReader(this);
        barcodeScanner = new BarcodeScanner(this);

        objectDetector = new ObjectDetector(this);
        BackgroundProcessingService.submit(() -> objectDetector.init());
    }

    private void setupButtons() {
        View detectBtn = findViewById(R.id.btn_detect);
        View ocrBtn = findViewById(R.id.btn_ocr);
        View barcodeBtn = findViewById(R.id.btn_barcode);

        if (detectBtn != null) detectBtn.setOnClickListener(v -> triggerDetection());
        if (ocrBtn != null) ocrBtn.setOnClickListener(v -> triggerOCR());
        if (barcodeBtn != null) barcodeBtn.setOnClickListener(v -> triggerBarcode());

        View backBtn = findViewById(R.id.btn_back_camera);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
    }

    private void startCamera() {
        cameraManager = new CameraManager(this);
        cameraManager.startCamera(this, previewView);
        AppLogger.i(TAG, "Camera started in mode: " + mode);
    }

    private void triggerDetection() {
        if (analysisRunning) return;
        tts.speak("Analysing scene...");
        statusText.setText("Detecting objects...");
        analysisRunning = true;

        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            tts.speak("Could not capture image.");
            analysisRunning = false;
            return;
        }

        BackgroundProcessingService.submit(() -> {
            objectDetector.detect(bitmap, results -> {
                runOnUiThread(() -> {
                    objectDetector.speakResults(results);
                    statusText.setText("Detection complete.");
                    analysisRunning = false;
                });
            });
        });
    }

    private void triggerOCR() {
        if (analysisRunning) return;
        tts.speak("Reading text...");
        statusText.setText("Extracting text...");
        analysisRunning = true;

        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            tts.speak("Could not capture image.");
            analysisRunning = false;
            return;
        }

        ocrProcessor.processImage(bitmap, new OCRProcessor.OCRCallback() {
            @Override public void onTextExtracted(String text) {
                runOnUiThread(() -> {
                    textReader.readText(text);
                    statusText.setText("OCR complete.");
                    analysisRunning = false;
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    tts.speak("Text reading failed.");
                    analysisRunning = false;
                });
            }
        });
    }

    private void triggerBarcode() {
        if (analysisRunning) return;
        tts.speak("Scanning for barcode...");
        statusText.setText("Scanning barcode...");
        analysisRunning = true;

        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            tts.speak("Could not capture image.");
            analysisRunning = false;
            return;
        }

        barcodeScanner.scan(bitmap, new BarcodeScanner.ScanCallback() {
            @Override public void onBarcodeDetected(String value, String type) {
                runOnUiThread(() -> { statusText.setText("Barcode found!"); analysisRunning = false; });
            }
            @Override public void onNoBarcode() {
                runOnUiThread(() -> { statusText.setText("No barcode found."); analysisRunning = false; });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> { statusText.setText("Error."); analysisRunning = false; });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraManager != null) cameraManager.stopCamera();
        if (objectDetector != null) objectDetector.close();
        if (ocrProcessor != null) ocrProcessor.close();
        if (barcodeScanner != null) barcodeScanner.close();
    }
}
