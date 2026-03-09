package com.visionassist.core.permissions;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.visionassist.core.utils.PermissionUtils;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;

/**
 * Centralised manager for runtime permissions.
 * Provides one-shot request for all required permissions and
 * app-settings deep-link for permanently denied cases.
 */
public class PermissionManager {

    private static final String TAG = "PermissionManager";

    public interface PermissionCallback {
        void onAllPermissionsGranted();
        void onPermissionsDenied(String[] deniedPermissions);
    }

    private final Activity activity;
    private PermissionCallback callback;

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    public void setCallback(PermissionCallback callback) {
        this.callback = callback;
    }

    /**
     * Requests all permissions required by VisionAssist at once.
     */
    public void requestAllPermissions() {
        AppLogger.d(TAG, "Requesting all permissions");
        String[] permissions = PermissionUtils.getAllRequiredPermissions();
        ActivityCompat.requestPermissions(activity, permissions, AppConstants.REQUEST_CODE_ALL_PERMISSIONS);
    }

    /**
     * Should be called from onRequestPermissionsResult in the Activity.
     */
    public void onRequestPermissionsResult(int requestCode,
                                            @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        if (requestCode != AppConstants.REQUEST_CODE_ALL_PERMISSIONS) return;

        java.util.List<String> denied = new java.util.ArrayList<>();
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                denied.add(permissions[i]);
                AppLogger.w(TAG, "Permission denied: " + permissions[i]);
            }
        }

        if (denied.isEmpty()) {
            AppLogger.i(TAG, "All permissions granted");
            if (callback != null) callback.onAllPermissionsGranted();
        } else {
            AppLogger.w(TAG, "Denied permissions: " + denied.size());
            if (callback != null) callback.onPermissionsDenied(denied.toArray(new String[0]));
        }
    }

    /**
     * Opens app settings so user can manually grant a permanently denied permission.
     */
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    /**
     * Checks if all critical permissions are already granted (avoids unnecessary dialog).
     */
    public boolean hasAllCriticalPermissions(Context context) {
        return PermissionUtils.hasAllCriticalPermissions(context);
    }
}
