package com.visionassist.commands;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;
import com.visionassist.voice.tts.TTSManager;

/**
 * Handles phone-related voice commands: calling contacts, reading contacts.
 */
public class PhoneCommands {

    private static final String TAG = "PhoneCommands";
    private final Context context;
    private final TTSManager tts;

    public PhoneCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    /**
     * Looks up a contact by name and dials them.
     * e.g. "Call John" → looks up John → dials their number
     */
    public void callContact(String commandText, CommandRouter.CommandCallback callback) {
        if (!PermissionUtils.hasContactsPermission(context)) {
            String msg = "I need contacts permission to make calls. Please grant it in settings.";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        String contactName = extractContactName(commandText);
        if (contactName.isEmpty()) {
            tts.speak("Who would you like to call?");
            callback.onResult("Who would you like to call?");
            return;
        }

        String phoneNumber = lookupContactNumber(contactName);
        if (phoneNumber == null) {
            String msg = "Sorry, I couldn't find " + contactName + " in your contacts.";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        if (!PermissionUtils.hasPhonePermission(context)) {
            // Fallback: open dialer
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(dialIntent);
            String msg = "Opening dialler for " + contactName;
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        // Direct call
        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(callIntent);
        String msg = "Calling " + contactName;
        tts.speak(msg);
        callback.onResult(msg);
        AppLogger.i(TAG, "Calling: " + contactName + " (" + phoneNumber + ")");
    }

    /**
     * Looks up a contact number by display name (case-insensitive, partial match).
     */
    private String lookupContactNumber(String name) {
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                + " LIKE ?";
        String[] selectionArgs = {"%" + name + "%"};

        try (Cursor cursor = context.getContentResolver()
                .query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);
                return numberIndex >= 0 ? cursor.getString(numberIndex) : null;
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Error looking up contact", e);
        }
        return null;
    }

    private String extractContactName(String commandText) {
        return commandText.toLowerCase()
                .replace("call", "")
                .replace("phone", "")
                .replace("dial", "")
                .replace("ring", "")
                .trim();
    }
}
