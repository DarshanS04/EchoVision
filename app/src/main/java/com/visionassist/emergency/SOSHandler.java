package com.visionassist.emergency;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.telephony.SmsManager;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;
import com.visionassist.data.repository.AppRepository;
import com.visionassist.navigation.LocationManager;
import com.visionassist.navigation.RouteHelper;
import com.visionassist.voice.tts.TTSManager;

/**
 * Handles SOS/emergency scenarios.
 * Calls the emergency contact and sends GPS location via SMS.
 */
public class SOSHandler {

    private static final String TAG = "SOSHandler";

    private final Context context;
    private final TTSManager tts;
    private final AppRepository repository;
    private final LocationManager locationManager;

    public SOSHandler(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
        this.repository = AppRepository.getInstance(context);
        this.locationManager = new LocationManager(context);
    }

    /**
     * Full SOS sequence:
     * 1. Speaks confirmation
     * 2. Gets GPS location
     * 3. Sends SMS with location to emergency contact
     * 4. Calls emergency contact
     */
    public void triggerSOS(CommandRouter.CommandCallback callback) {
        String contactNumber = repository.getEmergencyContactNumber();
        String contactName = repository.getEmergencyContactName();

        if (contactNumber == null || contactNumber.isEmpty()) {
            String msg = "No emergency contact is set. Please go to settings and add an emergency contact.";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        AppLogger.e(TAG, "!!! SOS TRIGGERED !!!");
        tts.speak("Emergency SOS activated. Contacting " + contactName + " and sending your location.");

        // Get location then send SMS + call
        locationManager.getCurrentLocation(new LocationManager.LocationCallback2() {
            @Override
            public void onLocationReceived(Location location) {
                String mapsUrl = RouteHelper.coordinatesToMapsUrl(
                        location.getLatitude(), location.getLongitude());
                String message = "EMERGENCY SOS from VisionAssist!\n"
                        + "I need help!\n"
                        + "My location: " + mapsUrl;
                sendSMS(contactNumber, message);
                makeEmergencyCall(contactNumber, callback);
            }

            @Override
            public void onError(String error) {
                AppLogger.e(TAG, "Could not get location for SOS: " + error);
                // Still call even without location
                String message = "EMERGENCY SOS from VisionAssist! I need help! "
                        + "(Location unavailable)";
                sendSMS(contactNumber, message);
                makeEmergencyCall(contactNumber, callback);
            }
        });
    }

    private void sendSMS(String number, String message) {
        if (!PermissionUtils.hasSmsPermission(context)) {
            AppLogger.w(TAG, "SMS permission not granted — cannot send SOS SMS");
            return;
        }
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, message, null, null);
            AppLogger.i(TAG, "SOS SMS sent to: " + number);
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to send SOS SMS", e);
        }
    }

    private void makeEmergencyCall(String number, CommandRouter.CommandCallback callback) {
        if (PermissionUtils.hasPhonePermission(context)) {
            Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(callIntent);
            String response = "Calling emergency contact now.";
            tts.speak(response);
            callback.onResult("SOS triggered. Calling " + number);
        } else {
            // Fallback to dialler
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(dialIntent);
            tts.speak("Please confirm the call to your emergency contact.");
            callback.onResult("SOS: opening dialler for emergency contact.");
        }
        AppLogger.e(TAG, "SOS call placed to: " + number);
    }
}
