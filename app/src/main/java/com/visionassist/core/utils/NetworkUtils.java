package com.visionassist.core.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Utility class for network connectivity checks.
 * Used to decide between online (Gemini) and offline (local) AI modes.
 */
public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * Returns true if the device currently has active internet connectivity.
     */
    public static boolean isInternetAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) return false;

        // NET_CAPABILITY_VALIDATED can sometimes be false even with connectivity
        // We prioritize INTERNET capability.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /**
     * Returns true if the device is connected to WiFi.
     */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) return false;

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /**
     * Returns true if the device is connected via mobile data.
     */
    public static boolean isMobileDataConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) return false;

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }
}
