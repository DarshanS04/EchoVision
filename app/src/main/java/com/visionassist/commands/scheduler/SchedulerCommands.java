package com.visionassist.commands.scheduler;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles scheduling commands: Alarms, Timers, and Calendar events.
 * Uses standard Android AlarmClock and CalendarContract providers.
 * Works without any API key.
 */
public class SchedulerCommands {

    private static final String TAG = "SchedulerCommands";
    private final Context context;
    private final TTSManager tts;

    private enum ReminderState { NONE, AWAITING_REPETITION, AWAITING_DATE, AWAITING_TIME }
    private ReminderState currentReminderState = ReminderState.NONE;
    private String pendingTitle = "";
    private boolean isEveryday = false;
    private Calendar pendingDate = null;

    private enum MemoState { NONE, AWAITING_COUNT, AWAITING_TIME }
    private MemoState currentMemoState = MemoState.NONE;
    private int expectedMemoTimes = 0;
    private int currentMemoIndex = 0;

    public SchedulerCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    // ─── Alarms ─────────────────────────────────────────────────────────────

    /**
     * Sets an alarm at the specified time.
     * Voice examples:
     *   "Set alarm for 7 AM"
     *   "Set alarm at 6:30 PM"
     *   "Wake me up at 8"
     */
    public void setAlarm(String rawText, CommandRouter.CommandCallback callback) {
        int[] time = parseTime(rawText);

        if (time == null) {
            // Open alarm app for manual entry
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                String msg = "Opening alarms. Please set the time.";
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception e) {
                AppLogger.e(TAG, "Alarm app launch failed", e);
                String msg = "Sorry, I could not open the alarm app.";
                tts.speak(msg);
                callback.onResult(msg);
            }
            return;
        }

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, time[0]);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, time[1]);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true); // create automatically without opening UI
        intent.putExtra(AlarmClock.EXTRA_VIBRATE, true); // ensure it vibrates
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            String amPm = time[0] < 12 ? "A M" : "P M";
            int displayHour = time[0] > 12 ? time[0] - 12 : (time[0] == 0 ? 12 : time[0]);
            String timeStr = displayHour + (time[1] > 0 ? " " + time[1] : "") + " " + amPm;
            String msg = "Setting an alarm for " + timeStr;
            tts.speak(msg);
            callback.onResult(msg);
        } catch (Exception e) {
            AppLogger.e(TAG, "Alarm set failed", e);
            String msg = "Sorry, I could not set the alarm.";
            tts.speak(msg);
            callback.onResult(msg);
        }
    }

    // ─── Memos (Repeating Alarms) ───────────────────────────────────────────

    public boolean hasPendingMemoState() {
        return currentMemoState != MemoState.NONE;
    }

    public void cancelPendingMemo() {
        currentMemoState = MemoState.NONE;
        expectedMemoTimes = 0;
        currentMemoIndex = 0;
    }

    /**
     * Interactive flow for creating multiple daily repeating alarms (e.g., for taking tablets).
     */
    public void startMemoFlow(String rawText, CommandRouter.CommandCallback callback) {
        if (currentMemoState != MemoState.NONE) {
            handlePendingMemo(rawText, callback);
            return;
        }

        currentMemoState = MemoState.AWAITING_COUNT;
        String msg = "For a day, how many times should I remind you?";
        tts.speak(msg);
        callback.onResult(msg);
    }

    private void handlePendingMemo(String rawText, CommandRouter.CommandCallback callback) {
        switch (currentMemoState) {
            case AWAITING_COUNT:
                int count = parseNumber(rawText);
                if (count > 0 && count <= 10) {
                    expectedMemoTimes = count;
                    currentMemoIndex = 1;
                    currentMemoState = MemoState.AWAITING_TIME;
                    String msg = "What time should I remind you for time " + currentMemoIndex + "?";
                    tts.speak(msg);
                    callback.onResult(msg);
                } else {
                    tts.speak("Please say a valid number, like 2 or 3.");
                    callback.onResult("Please say a valid number.");
                }
                break;

            case AWAITING_TIME:
                int[] time = parseTime(rawText);
                if (time != null) {
                    createRepeatingMemoAlarm(time);
                    
                    if (currentMemoIndex < expectedMemoTimes) {
                        currentMemoIndex++;
                        String msg = "What time should I remind you for time " + currentMemoIndex + "?";
                        tts.speak(msg);
                        callback.onResult(msg);
                    } else {
                        cancelPendingMemo();
                        String msg = "All daily memo alarms have been set successfully.";
                        tts.speak(msg);
                        callback.onResult(msg);
                    }
                } else {
                    tts.speak("I didn't catch the time. Please say a time like 10 AM or 5 30 PM.");
                    callback.onResult("I didn't catch the time.");
                }
                break;

            default:
                cancelPendingMemo();
                break;
        }
    }

    private void createRepeatingMemoAlarm(int[] time) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, time[0]);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, time[1]);
        
        java.util.ArrayList<Integer> days = new java.util.ArrayList<>();
        days.add(Calendar.MONDAY);
        days.add(Calendar.TUESDAY);
        days.add(Calendar.WEDNESDAY);
        days.add(Calendar.THURSDAY);
        days.add(Calendar.FRIDAY);
        days.add(Calendar.SATURDAY);
        days.add(Calendar.SUNDAY);
        
        intent.putExtra(AlarmClock.EXTRA_DAYS, days);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Daily Memo");
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        intent.putExtra(AlarmClock.EXTRA_VIBRATE, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to start Alarm intent for memo", e);
        }
    }

    // ─── Timers ─────────────────────────────────────────────────────────────

    /**
     * Sets a countdown timer.
     * Voice examples:
     *   "Set timer for 5 minutes"
     *   "Start a 30 second timer"
     *   "Timer for 1 hour 30 minutes"
     */
    public void setTimer(String rawText, CommandRouter.CommandCallback callback) {
        int totalSeconds = parseDurationToSeconds(rawText);

        if (totalSeconds <= 0) {
            Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                String msg = "Opening timer. Please set the duration.";
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception e) {
                AppLogger.e(TAG, "Timer app launch failed", e);
                String msg = "Sorry, I could not open the timer app.";
                tts.speak(msg);
                callback.onResult(msg);
            }
            return;
        }

        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
        intent.putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true); // create automatically without opening UI
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            String msg = buildTimerDescription(totalSeconds);
            tts.speak(msg);
            callback.onResult(msg);
        } catch (Exception e) {
            AppLogger.e(TAG, "Timer set failed", e);
            String msg = "Sorry, I could not start the timer.";
            tts.speak(msg);
            callback.onResult(msg);
        }
    }

    // ─── Calendar / Reminders ───────────────────────────────────────────────

    public boolean hasPendingReminderState() {
        return currentReminderState != ReminderState.NONE;
    }

    public void cancelPendingReminder() {
        currentReminderState = ReminderState.NONE;
        pendingTitle = "";
        pendingDate = null;
    }

    /**
     * Interactive flow for creating a reminder/calendar event.
     */
    public void createCalendarEvent(String rawText, CommandRouter.CommandCallback callback) {
        if (currentReminderState != ReminderState.NONE) {
            handlePendingReminder(rawText, callback);
            return;
        }

        String title = extractEventTitle(rawText);
        if (title.isEmpty()) {
            title = "Reminder";
        }
        pendingTitle = title;
        currentReminderState = ReminderState.AWAITING_REPETITION;
        String msg = "Would you like this reminder everyday or just once?";
        tts.speak(msg);
        callback.onResult(msg);
    }

    private void handlePendingReminder(String rawText, CommandRouter.CommandCallback callback) {
        String t = rawText.toLowerCase().trim();

        switch (currentReminderState) {
            case AWAITING_REPETITION:
                if (t.contains("everyday") || t.contains("every day") || t.contains("daily")) {
                    isEveryday = true;
                    currentReminderState = ReminderState.AWAITING_TIME;
                    tts.speak("What time should I remind you?");
                    callback.onResult("What time should I remind you?");
                } else if (t.contains("once") || t.contains("just once") || t.contains("one time")) {
                    isEveryday = false;
                    currentReminderState = ReminderState.AWAITING_DATE;
                    tts.speak("What date should I remind you?");
                    callback.onResult("What date should I remind you?");
                } else {
                    tts.speak("Please say everyday or just once.");
                    callback.onResult("Please say everyday or just once.");
                }
                break;

            case AWAITING_DATE:
                pendingDate = parseDate(t);
                if (pendingDate != null) {
                    currentReminderState = ReminderState.AWAITING_TIME;
                    tts.speak("What time should I remind you?");
                    callback.onResult("What time should I remind you?");
                } else {
                    tts.speak("I didn't catch the date. Please say something like today, tomorrow, or next Monday.");
                    callback.onResult("I didn't catch the date.");
                }
                break;

            case AWAITING_TIME:
                int[] time = parseTime(t);
                if (time != null) {
                    if (isEveryday) {
                        createEverydayReminder(time, callback);
                    } else {
                        createOnceReminder(time, callback);
                    }
                    cancelPendingReminder();
                } else {
                    tts.speak("I didn't catch the time. Please say a time like 10 AM or 5 30 PM.");
                    callback.onResult("I didn't catch the time.");
                }
                break;

            default:
                cancelPendingReminder();
                break;
        }
    }

    private void createOnceReminder(int[] time, CommandRouter.CommandCallback callback) {
        pendingDate.set(Calendar.HOUR_OF_DAY, time[0]);
        pendingDate.set(Calendar.MINUTE, time[1]);
        pendingDate.set(Calendar.SECOND, 0);

        long startMillis = pendingDate.getTimeInMillis();
        long endMillis = startMillis + 60 * 60 * 1000; // 1 hour duration

        insertCalendarEvent(pendingTitle, startMillis, endMillis, null, callback);
    }

    private void createEverydayReminder(int[] time, CommandRouter.CommandCallback callback) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, time[0]);
        cal.set(Calendar.MINUTE, time[1]);
        cal.set(Calendar.SECOND, 0);

        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long startMillis = cal.getTimeInMillis();
        long endMillis = startMillis + 60 * 60 * 1000;

        insertCalendarEvent(pendingTitle, startMillis, endMillis, "FREQ=DAILY", callback);
    }

    private boolean hasCalendarPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void insertCalendarEvent(String title, long startMillis, long endMillis, String rrule, CommandRouter.CommandCallback callback) {
        if (!hasCalendarPermissions()) {
            AppLogger.e(TAG, "Calendar permissions missing, opening settings");
            String msg = "Calendar permissions are required to automatically create reminders. Please grant them in the settings that just opened.";
            tts.speak(msg);
            callback.onResult(msg);

            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        try {
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.DTSTART, startMillis);
            values.put(CalendarContract.Events.DTEND, endMillis);
            values.put(CalendarContract.Events.TITLE, title);
            values.put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId());
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(CalendarContract.Events.HAS_ALARM, 1);
            if (rrule != null) {
                values.put(CalendarContract.Events.RRULE, rrule);
            }

            android.net.Uri uri = context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            if (uri != null) {
                // Add an alert for this event
                long eventID = Long.parseLong(uri.getLastPathSegment());
                ContentValues reminderValues = new ContentValues();
                reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventID);
                reminderValues.put(CalendarContract.Reminders.MINUTES, 0); // At the time of event
                reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT); // Triggers the default calendar notification sound
                context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);

                String msg = "Reminder created successfully for " + title + ".";
                tts.speak(msg);
                callback.onResult(msg);
            } else {
                throw new Exception("Failed to insert event.");
            }
        } catch (SecurityException e) {
            AppLogger.e(TAG, "Calendar permission missing", e);
            fallbackToCalendarIntent(title, startMillis, endMillis, callback);
        } catch (Exception e) {
            AppLogger.e(TAG, "Error inserting calendar event", e);
            fallbackToCalendarIntent(title, startMillis, endMillis, callback);
        }
    }

    private long getDefaultCalendarId() {
        String[] projection = new String[]{CalendarContract.Calendars._ID};
        android.database.Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                CalendarContract.Calendars.VISIBLE + " = 1 AND " + CalendarContract.Calendars.IS_PRIMARY + " = 1",
                null,
                CalendarContract.Calendars._ID + " ASC");

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                cursor.close();
                return id;
            }
            cursor.close();
        }

        // Fallback: just get the first visible calendar
        cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                CalendarContract.Calendars.VISIBLE + " = 1",
                null,
                CalendarContract.Calendars._ID + " ASC");

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                cursor.close();
                return id;
            }
            cursor.close();
        }
        return 1; // Last resort default
    }

    private void fallbackToCalendarIntent(String title, long startMillis, long endMillis, CommandRouter.CommandCallback callback) {
        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        if (!title.isEmpty()) {
            intent.putExtra(CalendarContract.Events.TITLE, title);
        }
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            String msg = "Opening calendar to save reminder.";
            tts.speak(msg);
            callback.onResult(msg);
        } catch (Exception ex) {
            AppLogger.e(TAG, "Calendar fallback failed", ex);
            String msg = "Sorry, I could not save the reminder.";
            tts.speak(msg);
            callback.onResult(msg);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Parses a natural language date string into a Calendar object.
     */
    private Calendar parseDate(String text) {
        String t = text.toLowerCase().trim();
        Calendar cal = Calendar.getInstance();

        if (t.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            return cal;
        }
        if (t.contains("today")) {
            return cal;
        }

        String[] days = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
        for (int i = 0; i < days.length; i++) {
            if (t.contains(days[i])) {
                int targetDay = i + 1; // Calendar.SUNDAY = 1
                int currentDay = cal.get(Calendar.DAY_OF_WEEK);
                int daysToAdd = targetDay - currentDay;
                if (daysToAdd <= 0) {
                    daysToAdd += 7;
                }
                cal.add(Calendar.DAY_OF_YEAR, daysToAdd);
                return cal;
            }
        }

        // Basic check for specific dates like "15th" or "15"
        Pattern p = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\b");
        Matcher m = p.matcher(t);
        if (m.find()) {
            int day = Integer.parseInt(m.group(1));
            if (day >= 1 && day <= 31) {
                int currentDay = cal.get(Calendar.DAY_OF_MONTH);
                if (day < currentDay) {
                    cal.add(Calendar.MONTH, 1);
                }
                cal.set(Calendar.DAY_OF_MONTH, day);
                return cal;
            }
        }

        // Default if unable to parse accurately (fallback to today)
        return cal;
    }

    /**
     * Parses hour and minute from text. Returns int[]{hour24, minute} or null.
     */
    private int[] parseTime(String text) {
        String t = text.toLowerCase();
        boolean pm = t.contains("pm") || t.contains("p.m") || t.contains(" evening")
                || t.contains("night") || t.contains("afternoon");
        boolean am = t.contains("am") || t.contains("a.m") || t.contains("morning");

        int hour = -1;
        int minute = 0;

        // Try standard format with separator: "7:30", "7 30"
        Pattern p1 = Pattern.compile("(\\d{1,2})[:\\s](\\d{2})");
        Matcher m1 = p1.matcher(t);
        if (m1.find()) {
            hour = Integer.parseInt(m1.group(1));
            minute = Integer.parseInt(m1.group(2));
        } else {
            // No separator, extract digits to handle speech-to-text clumping like "742" for 7:42
            Pattern p2 = Pattern.compile("\\d+");
            Matcher m2 = p2.matcher(t);
            if (m2.find()) {
                String digits = m2.group();
                if (digits.length() == 3) {
                    hour = Integer.parseInt(digits.substring(0, 1));
                    minute = Integer.parseInt(digits.substring(1, 3));
                } else if (digits.length() == 4) {
                    hour = Integer.parseInt(digits.substring(0, 2));
                    minute = Integer.parseInt(digits.substring(2, 4));
                } else if (digits.length() <= 2) {
                    hour = Integer.parseInt(digits);
                    minute = 0;
                }
            }
        }

        if (hour != -1) {
            if (pm && hour < 12) hour += 12;
            if (am && hour == 12) hour = 0;

            if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
                return new int[]{hour, minute};
            }
        }
        return null;
    }

    /** Parses duration from text into total seconds. */
    private int parseDurationToSeconds(String text) {
        String t = text.toLowerCase();
        int totalSeconds = 0;

        Pattern hourPat = Pattern.compile("(\\d+)\\s*hour");
        Pattern minPat  = Pattern.compile("(\\d+)\\s*min");
        Pattern secPat  = Pattern.compile("(\\d+)\\s*sec");

        Matcher m;
        m = hourPat.matcher(t);
        if (m.find()) totalSeconds += Integer.parseInt(m.group(1)) * 3600;

        m = minPat.matcher(t);
        if (m.find()) totalSeconds += Integer.parseInt(m.group(1)) * 60;

        m = secPat.matcher(t);
        if (m.find()) totalSeconds += Integer.parseInt(m.group(1));

        return totalSeconds;
    }

    private String buildTimerDescription(int totalSeconds) {
        int h = totalSeconds / 3600;
        int m = (totalSeconds % 3600) / 60;
        int s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder("Starting a timer for ");
        if (h > 0) sb.append(h).append(h == 1 ? " hour " : " hours ");
        if (m > 0) sb.append(m).append(m == 1 ? " minute " : " minutes ");
        if (s > 0) sb.append(s).append(s == 1 ? " second" : " seconds");
        return sb.toString().trim();
    }

    private String extractEventTitle(String rawText) {
        return rawText.toLowerCase()
                .replace("create", "").replace("add", "").replace("new", "")
                .replace("calendar", "").replace("event", "").replace("reminder", "")
                .replace("schedule", "").replace("for", "").trim();
    }

    private int parseNumber(String text) {
        String t = text.toLowerCase().trim();
        if (t.contains("one") || t.contains("1") || t.contains("once")) return 1;
        if (t.contains("two") || t.contains("2") || t.contains("twice")) return 2;
        if (t.contains("three") || t.contains("3")) return 3;
        if (t.contains("four") || t.contains("4")) return 4;
        if (t.contains("five") || t.contains("5")) return 5;
        if (t.contains("six") || t.contains("6")) return 6;
        if (t.contains("seven") || t.contains("7")) return 7;
        if (t.contains("eight") || t.contains("8")) return 8;
        if (t.contains("nine") || t.contains("9")) return 9;
        if (t.contains("ten") || t.contains("10")) return 10;
        return -1;
    }
}
