package com.visionassist.commands.communication;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;
import com.visionassist.voice.tts.TTSManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles communication commands: SMS and WhatsApp messaging.
 * Uses Android system Intents — works without any API key.
 */
public class CommunicationCommands {

    private static final String TAG = "CommunicationCommands";
    private final Context context;
    private final TTSManager tts;

    private List<ContactMatch> pendingSmsContacts;
    private String pendingSmsBody;

    private static class ContactMatch {
        String name;
        String number;

        ContactMatch(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    public boolean hasPendingSmsContacts() {
        return pendingSmsContacts != null && !pendingSmsContacts.isEmpty();
    }

    private static final String PKG_WHATSAPP = "com.whatsapp";

    public CommunicationCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    // ─── SMS ────────────────────────────────────────────────────────────────

    public void selectSmsContact(String commandText, CommandRouter.CommandCallback callback) {
        if (!hasPendingSmsContacts()) {
            tts.speak("No contact selection pending.");
            callback.onResult("No contact selection pending.");
            return;
        }

        int choice = parseChoice(commandText);

        if (choice < 1 || choice > pendingSmsContacts.size()) {
            tts.speak("Invalid option. Please choose between 1 and " + pendingSmsContacts.size());
            return;
        }

        ContactMatch selected = pendingSmsContacts.get(choice - 1);
        String phoneNumber = selected.number;
        String contactDisplay = selected.name;

        if (PermissionUtils.hasSmsPermission(context)) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, pendingSmsBody.isEmpty() ? " " : pendingSmsBody, null, null);
                String msg = "Message sent to " + contactDisplay;
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception e) {
                AppLogger.e(TAG, "Failed to send SMS directly", e);
                String msg = "Sorry, I could not send the message.";
                tts.speak(msg);
                callback.onResult(msg);
            }
        } else {
            String msg = "I don't have permission to send messages directly.";
            tts.speak(msg);
            callback.onResult(msg);
        }

        pendingSmsContacts = null;
        pendingSmsBody = null;
    }

    private int parseChoice(String text) {
        String t = text.toLowerCase();
        if (t.contains("one") || t.contains("1st") || t.contains("first")) return 1;
        if (t.contains("two") || t.contains("2nd") || t.contains("second")) return 2;
        if (t.contains("three") || t.contains("3rd") || t.contains("third")) return 3;
        if (t.contains("four") || t.contains("4th") || t.contains("fourth")) return 4;
        if (t.contains("five") || t.contains("5th") || t.contains("fifth")) return 5;

        for (char c : t.toCharArray()) {
            if (Character.isDigit(c)) {
                return Character.getNumericValue(c);
            }
        }
        return -1;
    }

    /**
     * Opens the default SMS app pre-filled with the contact and message body, or sends directly if permission granted.
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

        List<ContactMatch> matches = lookupContacts(contactName);

        if (matches.isEmpty()) {
            String msg = "Sorry, I couldn't find " + contactName + " in your contacts.";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        if (matches.size() > 1) {
            pendingSmsContacts = matches;
            pendingSmsBody = body;

            StringBuilder msg = new StringBuilder();
            msg.append("I found ").append(matches.size())
               .append(" contacts matching ").append(contactName).append(". ");

            for (int i = 0; i < matches.size(); i++) {
                msg.append("Option ").append(i + 1).append(": ")
                   .append(matches.get(i).name).append(". ");
            }

            tts.speak(msg.toString());
            callback.onResult(msg.toString());
            return;
        }

        ContactMatch selected = matches.get(0);
        String phoneNumber = selected.number;

        if (PermissionUtils.hasSmsPermission(context)) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, body.isEmpty() ? " " : body, null, null);
                String msg = "Message sent to " + selected.name;
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception e) {
                AppLogger.e(TAG, "Failed to send SMS directly", e);
                String msg = "Sorry, I could not send the message.";
                tts.speak(msg);
                callback.onResult(msg);
            }
        } else {
            String uriStr = "smsto:" + phoneNumber;
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(uriStr));
            intent.putExtra("sms_body", body.isEmpty() ? "" : body);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                context.startActivity(intent);
                String msg = "Opening messages to send an S M S to " + selected.name;
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception e) {
                AppLogger.e(TAG, "SMS intent failed", e);
                String msg = "Sorry, I could not open the messages app.";
                tts.speak(msg);
                callback.onResult(msg);
            }
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

        List<ContactMatch> matches = lookupContacts(contactName);
        String phoneNumber = matches.isEmpty() ? null : matches.get(0).number;

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

    private List<ContactMatch> lookupContacts(String name) {
        List<ContactMatch> results = new ArrayList<>();
        if (!PermissionUtils.hasContactsPermission(context)) return results;

        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = {"%" + name + "%"};

        try (Cursor cursor = context.getContentResolver()
                .query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (cursor.moveToNext()) {
                    results.add(new ContactMatch(cursor.getString(nameIdx), cursor.getString(numIdx)));
                }
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Contact lookup error", e);
        }
        return results;
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
