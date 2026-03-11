package com.visionassist.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.visionassist.core.logger.AppLogger;

/**
 * Receives BOOT_COMPLETED broadcast and restarts AssistantService.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AppLogger.i(TAG, "Boot completed — starting AssistantService");
            Intent serviceIntent = new Intent(context, AssistantService.class);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                AppLogger.e(TAG, "Failed to start AssistantService on boot: " + e.getMessage());
            }
        }
    }
}
