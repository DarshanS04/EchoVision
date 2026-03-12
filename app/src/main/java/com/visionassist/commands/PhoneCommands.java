package com.visionassist.commands;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.core.utils.PermissionUtils;
import com.visionassist.voice.tts.TTSManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles phone-related voice commands: calling contacts, reading contacts.
 */
public class PhoneCommands {

    private static final String TAG = "PhoneCommands";
    private final Context context;
    private final TTSManager tts;

    private List<ContactMatch> pendingContacts;

    private static class ContactMatch {
        String name;
        String number;

        ContactMatch(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }


    public boolean hasPendingContacts() {
        return pendingContacts != null && !pendingContacts.isEmpty();
    }

    public void selectContact(String commandText, CommandRouter.CommandCallback callback) {
        if (!hasPendingContacts()) {
            tts.speak("No contact selection pending.");
            callback.onResult("No contact selection pending.");
            return;
        }

        int choice = parseChoice(commandText);

        if (choice < 1 || choice > pendingContacts.size()) {
            tts.speak("Invalid option. Please choose between 1 and " + pendingContacts.size());
            return;
        }

        ContactMatch selected = pendingContacts.get(choice - 1);
        String phoneNumber = selected.number;
        String contactDisplay = selected.name;

        // Direct call or dialer fallback based on permissions
        if (!PermissionUtils.hasPhonePermission(context)) {
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(dialIntent);
            String msg = "Opening dialer for " + contactDisplay;
            tts.speak(msg);
            callback.onResult(msg);
        } else {
            Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(callIntent);
            String msg = "Calling " + contactDisplay;
            tts.speak(msg);
            callback.onResult(msg);
        }

        pendingContacts = null; // Clear state after selection
    }

    private int parseChoice(String text) {
        String t = text.toLowerCase();
        if (t.contains("one") || t.contains("1st") || t.contains("first")) return 1;
        if (t.contains("two") || t.contains("2nd") || t.contains("second")) return 2;
        if (t.contains("three") || t.contains("3rd") || t.contains("third")) return 3;
        if (t.contains("four") || t.contains("4th") || t.contains("fourth")) return 4;
        if (t.contains("five") || t.contains("5th") || t.contains("fifth")) return 5;

        // Try to extract any digit
        for (char c : t.toCharArray()) {
            if (Character.isDigit(c)) {
                return Character.getNumericValue(c);
            }
        }
        return -1;
    }

    public PhoneCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    /**
     * Looks up a contact by name and dials them.
     * e.g. "Call John" → looks up John → dials their number
     */
    // public void callContact(String commandText, CommandRouter.CommandCallback callback) {
    //     if (!PermissionUtils.hasContactsPermission(context)) {
    //         String msg = "I need contacts permission to make calls. Please grant it in settings.";
    //         tts.speak(msg);
    //         callback.onResult(msg);
    //         return;
    //     }

    //     String contactName = extractContactName(commandText);
    //     if (contactName.isEmpty()) {
    //         tts.speak("Who would you like to call?");
    //         callback.onResult("Who would you like to call?");
    //         return;
    //     }

    //     String phoneNumber = lookupContactNumber(contactName);
    //     if (phoneNumber == null) {
    //         String msg = "Sorry, I couldn't find " + contactName + " in your contacts.";
    //         tts.speak(msg);
    //         callback.onResult(msg);
    //         return;
    //     }

    //     if (!PermissionUtils.hasPhonePermission(context)) {
    //         // Fallback: open dialer
    //         Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
    //         dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    //         context.startActivity(dialIntent);
    //         String msg = "Opening dialler for " + contactName;
    //         tts.speak(msg);
    //         callback.onResult(msg);
    //         return;
    //     }

    //     // Direct call
    //     Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
    //     callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    //     context.startActivity(callIntent);
    //     // String msg = "Calling " + contactName;
    //     String msg = "Calling " + contactDisplay;
    //     tts.speak(msg);
    //     callback.onResult(msg);
    //     AppLogger.i(TAG, "Calling: " + contactName + " (" + phoneNumber + ")");
    // }

    public void callContact(String commandText, CommandRouter.CommandCallback callback) {

        // Permission check for reading contacts
        if (!PermissionUtils.hasContactsPermission(context)) {
            String msg = "I need contacts permission to make calls. Please grant it in settings.";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        // Extract name from voice command
        String contactName = extractContactName(commandText);

        if (contactName.isEmpty()) {
            tts.speak("Who would you like to call?");
            callback.onResult("Who would you like to call?");
            return;
        }

        // -----------------------------
        // CHANGED SECTION START
        // Instead of fetching ONE contact,
        // fetch ALL matching contacts
        // -----------------------------

        List<ContactMatch> matches = lookupContacts(contactName);

        if (matches.isEmpty()) {
            String msg = "Sorry, I couldn't find " + contactName + " in your contacts.";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        // If multiple contacts found, list them
        if (matches.size() > 1) {

            pendingContacts = matches; // SAVE CONTACTS FOR USER CHOICE

            StringBuilder msg = new StringBuilder();

            msg.append("I found ").append(matches.size())
            .append(" contacts matching ")
            .append(contactName).append(". ");

            for (int i = 0; i < matches.size(); i++) {

                msg.append("Option ")
                .append(i + 1)
                .append(": ")
                .append(matches.get(i).name)
                .append(". ");
            }

            tts.speak(msg.toString());
            callback.onResult(msg.toString());

            return; // IMPORTANT: WAIT FOR USER CHOICE
        }

        // Selected contact
        ContactMatch selected = matches.get(0);

        String phoneNumber = selected.number;
        String contactDisplay = selected.name;

        // -----------------------------
        // CHANGED SECTION END
        // -----------------------------

        // Check phone permission
        if (!PermissionUtils.hasPhonePermission(context)) {

            // Fallback: open dialer
            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(dialIntent);

            String msg = "Opening dialer for " + contactDisplay;
            tts.speak(msg);
            callback.onResult(msg);

            return;
        }

        // Direct call
        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(callIntent);

        String msg = "Calling " + contactDisplay;

        tts.speak(msg);
        callback.onResult(msg);

        AppLogger.i(TAG, "Calling: " + contactDisplay + " (" + phoneNumber + ")");
    }



    /**
     * Looks up a contact number by display name (case-insensitive, partial match).
     */
    // private String lookupContactNumber(String name) {
    //     Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
    //     String[] projection = {
    //             ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
    //             ContactsContract.CommonDataKinds.Phone.NUMBER
    //     };
    //     String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
    //             + " LIKE ?";
    //     String[] selectionArgs = {"%" + name + "%"};

    //     try (Cursor cursor = context.getContentResolver()
    //             .query(uri, projection, selection, selectionArgs, null)) {
    //         if (cursor != null && cursor.moveToFirst()) {
    //             int numberIndex = cursor.getColumnIndex(
    //                     ContactsContract.CommonDataKinds.Phone.NUMBER);
    //             return numberIndex >= 0 ? cursor.getString(numberIndex) : null;
    //         }
    //     } catch (Exception e) {
    //         AppLogger.e(TAG, "Error looking up contact", e);
    //     }
    //     return null;
    // }

    private List<ContactMatch> lookupContacts(String name) {

        List<ContactMatch> results = new ArrayList<>();

        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;

        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = {"%" + name + "%"};

        try (Cursor cursor = context.getContentResolver().query(
                uri,
                projection,
                selection,
                selectionArgs,
                null)) {

            if (cursor != null) {

                int nameIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);

                int numberIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {

                    String contactName = cursor.getString(nameIndex);
                    String number = cursor.getString(numberIndex);

                    results.add(new ContactMatch(contactName, number));
                }
            }

        } catch (Exception e) {
            AppLogger.e(TAG, "Error looking up contact", e);
        }

        return results;
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
