package com.visionassist.navigation;

import android.content.Context;
import android.location.Location;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;

/**
 * Manages GPS location updates using Fused Location Provider.
 */
public class LocationManager {

    private static final String TAG = "LocationManager";

    public interface LocationCallback2 {
        void onLocationReceived(Location location);
        void onError(String error);
    }

    private final Context context;
    private final FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private boolean isTracking = false;

    public LocationManager(Context context) {
        this.context = context.getApplicationContext();
        this.fusedClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Get a single location fix.
     */
    public void getCurrentLocation(LocationCallback2 callback) {
        if (!PermissionUtils.hasLocationPermission(context)) {
            callback.onError("Location permission not granted");
            return;
        }

        try {
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    AppLogger.d(TAG, "Location: " + location.getLatitude() + "," + location.getLongitude());
                    callback.onLocationReceived(location);
                } else {
                    // Request fresh fix
                    requestFreshLocation(callback);
                }
            }).addOnFailureListener(e -> {
                AppLogger.e(TAG, "Location fetch failed", e);
                callback.onError("Could not get location: " + e.getMessage());
            });
        } catch (SecurityException e) {
            AppLogger.e(TAG, "Location permission error", e);
            callback.onError("Location permission denied");
        }
    }

    private void requestFreshLocation(LocationCallback2 callback) {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMaxUpdates(1)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) callback.onLocationReceived(loc);
                fusedClient.removeLocationUpdates(this);
            }
        };

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onError("Location permission denied");
        }
    }

    /**
     * Start continuous location tracking.
     */
    public void startTracking(LocationCallback2 callback) {
        if (!PermissionUtils.hasLocationPermission(context)) {
            callback.onError("Location permission not granted");
            return;
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(3000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) callback.onLocationReceived(loc);
            }
        };

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
            isTracking = true;
            AppLogger.i(TAG, "Location tracking started");
        } catch (SecurityException e) {
            callback.onError("Location permission denied");
        }
    }

    /**
     * Stop location tracking.
     */
    public void stopTracking() {
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
            isTracking = false;
            AppLogger.i(TAG, "Location tracking stopped");
        }
    }

    public boolean isTracking() { return isTracking; }
}
