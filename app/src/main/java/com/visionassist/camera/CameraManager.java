package com.visionassist.camera;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import com.visionassist.core.logger.AppLogger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraX lifecycle-aware wrapper.
 * Provides camera preview and image analysis frames to detectors.
 */
public class CameraManager {

    private static final String TAG = "CameraManager";

    public interface FrameAnalyzer {
        void analyze(@NonNull ImageProxy image);
    }

    private final Context context;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private FrameAnalyzer frameAnalyzer;

    public CameraManager(Context context) {
        this.context = context.getApplicationContext();
        this.cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public void setFrameAnalyzer(FrameAnalyzer analyzer) {
        this.frameAnalyzer = analyzer;
    }

    /**
     * Starts camera preview bound to the given lifecycle owner and preview view.
     * Also sets up image analysis if a frameAnalyzer is set.
     */
    public void startCamera(LifecycleOwner lifecycleOwner, PreviewView previewView) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Build preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Camera selector - use back camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Image analysis use case
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                if (frameAnalyzer != null) {
                    imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                        frameAnalyzer.analyze(image);
                    });
                }

                // Unbind all before rebinding
                cameraProvider.unbindAll();

                // Bind to lifecycle
                if (frameAnalyzer != null) {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector,
                            preview, imageAnalysis);
                } else {
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview);
                }

                AppLogger.i(TAG, "Camera started");
            } catch (Exception e) {
                AppLogger.e(TAG, "Camera start failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /**
     * Stop camera and release executor.
     */
    public void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        AppLogger.i(TAG, "Camera stopped");
    }
}
