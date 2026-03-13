package com.visionassist.commands.communication;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;
import com.visionassist.voice.tts.TTSManager;

/**
 * Handles communication commands: SMS and WhatsApp messaging.
 * Uses Android system Intents — works without any API key.
 */
public class CommunicationCommands {

    private static final String TAG = "CommunicationCommands";
    private final Context context;
    private final TTSManager tts;

    private static final String PKG_WHATSAPP = "com.whatsapp";

    public CommunicationCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    // ─── SMS ────────────────────────────────────────────────────────────────

    /**
     * Opens the default SMS app pre-filled with the contact and message body.
     * Voice input example: "Send SMS to John saying I'm on my way"
     */
    public void sendSms(String rawText, CommandRouter.CommandCallback callback) {
        String contactName = extractContactName(rawText);
        String body = extractMessageBody(rawText);

        if (contactName.isEmpty()) {
            String msg = "Who would you like to send a message to?";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        String phoneNumber = lookupContactNumber(contactName);

        String uriStr = (phoneNumber != null)
                ? "smsto:" + phoneNumber
                : "smsto:";

        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(uriStr));
        intent.putExtra("sms_body", body.isEmpty() ? "" : body);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            String msg;
            if (phoneNumber != null) {
                msg = "Opening messages to send an S M S to " + contactName;
            } else {
                msg = "Opening S M S app. I couldn't find " + contactName
                        + " in your contacts, please type their number.";
            }
            tts.speak(msg);
            callback.onResult(msg);
        } catch (Exception e) {
            AppLogger.e(TAG, "SMS intent failed", e);
            String msg = "Sorry, I could not open the messages app.";
            tts.speak(msg);
            callback.onResult(msg);
        }
    }

    // ─── WhatsApp ───────────────────────────────────────────────────────────

    /**
     * Opens WhatsApp chat with a contact.
     * Voice input example: "WhatsApp Mom let's meet at 5"
     */
    public void sendWhatsApp(String rawText, CommandRouter.CommandCallback callback) {
        String contactName = extractContactName(rawText);
        String body = extractMessageBody(rawText);

        if (contactName.isEmpty()) {
            String msg = "Who would you like to WhatsApp?";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        String phoneNumber = lookupContactNumber(contactName);

        try {
            Intent intent;
            if (phoneNumber != null) {
                // Direct WhatsApp chat via phone number
                String cleanedNumber = phoneNumber.replaceAll("[^0-9+]", "");
                String url = "https://api.whatsapp.com/send?phone=" + cleanedNumber
                        + (body.isEmpty() ? "" : "&text=" + Uri.encode(body));
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage(PKG_WHATSAPP);
            } else {
                // Can't resolve number — open WhatsApp picker
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.setPackage(PKG_WHATSAPP);
                intent.putExtra(Intent.EXTRA_TEXT, body.isEmpty() ? "" : body);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            String msg;
            if (phoneNumber != null) {
                msg = "Opening WhatsApp chat with " + contactName;
            } else {
                msg = "Opening WhatsApp. I couldn't find " + contactName
                        + " in your contacts.";
            }
            tts.speak(msg);
            callback.onResult(msg);

        } catch (Exception e) {
            AppLogger.e(TAG, "WhatsApp intent failed", e);
            // Fallback: open WhatsApp without package filter
            try {
                String url = "https://api.whatsapp.com/send";
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                String msg = "Opening WhatsApp.";
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception ex) {
                String msg = "Sorry, I could not open WhatsApp. Is it installed?";
                tts.speak(msg);
                callback.onResult(msg);
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Looks up the first phone number matching a contact name */
    private String lookupContactNumber(String name) {
        if (!PermissionUtils.hasContactsPermission(context)) return null;

        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = {"%" + name + "%"};

        try (Cursor cursor = context.getContentResolver()
                .query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                return (idx >= 0) ? cursor.getString(idx) : null;
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Contact lookup error", e);
        }
        return null;
    }

    /**
     * Extracts contact name from raw text.
     * Handles: "send sms to John saying ...", "text John ...", "whatsapp John ..."
     */
    private String extractContactName(String rawText) {
        String t = rawText.toLowerCase()
                .replace("send", "").replace("sms", "")
                .replace("whatsapp", "").replace("message", "")
                .replace("text", "").replace("to", "")
                .trim();

        // If there's a 'saying' / 'saying that', everything before is the name
        int sayingIdx = t.indexOf("saying");
        if (sayingIdx > 0) {
            t = t.substring(0, sayingIdx).trim();
        }
        int thatIdx = t.indexOf(" that ");
        if (thatIdx > 0) {
            t = t.substring(0, thatIdx).trim();
        }
        return t;
    }

    /**
     * Extracts the message body from raw text.
     * Handles: "... saying I'm on my way"
     */
    private String extractMessageBody(String rawText) {
        String t = rawText.toLowerCase();
        int idx = t.indexOf("saying");
        if (idx >= 0) {
            return rawText.substring(idx + "saying".length()).trim();
        }
        return "";
    }
}
