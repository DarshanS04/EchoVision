package com.visionassist.ui.activities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
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
import com.visionassist.data.models.DetectionResult;
import com.visionassist.services.BackgroundProcessingService;
import com.visionassist.voice.tts.TTSManager;

import java.util.List;

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
    private boolean isVoiceCommand = false;
    private android.os.Handler autoRunHandler;
    private Runnable autoDetectRunnable;
    private Runnable autoBarcodeRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mode = getIntent().getStringExtra("mode");
        if (mode == null) {
            mode = "CAMERA_DETECT";
            isVoiceCommand = false;
        } else {
            isVoiceCommand = true;
        }

        autoRunHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        autoDetectRunnable = this::triggerDetection;
        autoBarcodeRunnable = this::triggerBarcode;

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
            case "CAMERA_OCR": 
                if (isVoiceCommand) {
                    tts.speak("Camera ready. Capturing text."); 
                    autoRunHandler.postDelayed(this::triggerOCR, 1500);
                } else {
                    tts.speak("Camera ready. Tap scan button to read text."); 
                }
                break;
            case "CAMERA_BARCODE": 
                if (isVoiceCommand) {
                    tts.speak("Camera ready. Scanning continuously for barcodes."); 
                    autoRunHandler.postDelayed(this::triggerBarcode, 1500);
                } else {
                    tts.speak("Camera ready. Tap scan button to detect barcodes."); 
                }
                break;
            default: 
                if (isVoiceCommand) {
                    tts.speak("Camera ready. Detecting objects continuously."); 
                    autoRunHandler.postDelayed(this::triggerDetection, 1500);
                } else {
                    tts.speak("Camera ready. Tap detect button to identify objects.");
                }
                break;
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
        if (!isVoiceCommand) tts.speak("Analysing scene...");
        statusText.setText("Detecting objects...");
        analysisRunning = true;

        Log.d("DETECT", "in triggerDetect()");
        
        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            Log.d("DETECT", "in bitmapNull");
            if (!isVoiceCommand) tts.speak("Could not capture image.");
            analysisRunning = false;
            scheduleNextDetectionIfContinuous();
            return;
        }
        
        BackgroundProcessingService.submit(() -> {
            objectDetector.detect(bitmap, new ObjectDetector.DetectionCallback() {
                @Override
                public void onDetected(List<DetectionResult> results) {
                    runOnUiThread(() -> {
                        Log.d("DETECT", "in thread of detect -- complete");
                        if (!results.isEmpty() || !isVoiceCommand) {
                            objectDetector.speakResults(results);
                        }
                        statusText.setText("Detection complete.");
                        analysisRunning = false;
                        scheduleNextDetectionIfContinuous();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Log.d("DETECT", "in thread of detect -- failed");
                        if (!isVoiceCommand) tts.speak("Detection failed.");
                        statusText.setText("Detection error.");
                        analysisRunning = false;
                        scheduleNextDetectionIfContinuous();
                    });
                }
            });
        });
    }

    private void scheduleNextDetectionIfContinuous() {
        if (isVoiceCommand && "CAMERA_DETECT".equals(mode)) {
            autoRunHandler.postDelayed(autoDetectRunnable, 5000);
        }
    }

    private void triggerOCR() {
        if (analysisRunning) return;
        if (!isVoiceCommand) tts.speak("Reading text...");
        statusText.setText("Extracting text...");
        analysisRunning = true;

        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            if (!isVoiceCommand) tts.speak("Could not capture image.");
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
                    if (!isVoiceCommand) tts.speak("Text reading failed.");
                    analysisRunning = false;
                });
            }
        });
    }

    private void triggerBarcode() {
        if (analysisRunning) return;
        if (!isVoiceCommand) tts.speak("Scanning for barcode...");
        statusText.setText("Scanning barcode...");
        analysisRunning = true;

        Bitmap bitmap = previewView.getBitmap();
        if (bitmap == null) {
            if (!isVoiceCommand) tts.speak("Could not capture image.");
            analysisRunning = false;
            scheduleNextBarcodeIfContinuous();
            return;
        }

        barcodeScanner.scan(bitmap, isVoiceCommand, new BarcodeScanner.ScanCallback() {
            @Override public void onBarcodeDetected(String value, String type) {
                runOnUiThread(() -> { 
                     statusText.setText("Barcode found!"); 
                     analysisRunning = false; 
                     scheduleNextBarcodeIfContinuous();
                });
            }
            @Override public void onNoBarcode() {
                runOnUiThread(() -> { 
                     statusText.setText("No barcode found."); 
                     analysisRunning = false; 
                     scheduleNextBarcodeIfContinuous();
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> { 
                     statusText.setText("Error."); 
                     analysisRunning = false; 
                     scheduleNextBarcodeIfContinuous();
                });
            }
        });
    }

    private void scheduleNextBarcodeIfContinuous() {
        if (isVoiceCommand && "CAMERA_BARCODE".equals(mode)) {
            autoRunHandler.postDelayed(autoBarcodeRunnable, 2000);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoRunHandler != null) {
            autoRunHandler.removeCallbacksAndMessages(null);
        }
        if (cameraManager != null) cameraManager.stopCamera();
        if (objectDetector != null) objectDetector.close();
        if (ocrProcessor != null) ocrProcessor.close();
        if (barcodeScanner != null) barcodeScanner.close();
    }
}
