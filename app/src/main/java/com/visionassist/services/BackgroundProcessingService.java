package com.visionassist.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Nullable;
import com.visionassist.core.logger.AppLogger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background processing service for long-running AI/CV tasks
 * (object detection, OCR) that should not block the main thread.
 */
public class BackgroundProcessingService extends Service {

    private static final String TAG = "BackgroundProcessingService";
    private static ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newFixedThreadPool(2);
        AppLogger.i(TAG, "BackgroundProcessingService created");
    }

    /**
     * Submit a background task.
     */
    public static void submit(Runnable task) {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newFixedThreadPool(2);
            AppLogger.i(TAG, "BackgroundProcessingService executor lazily initialized");
        }
        executor.submit(task);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
        AppLogger.i(TAG, "BackgroundProcessingService destroyed");
    }
}
