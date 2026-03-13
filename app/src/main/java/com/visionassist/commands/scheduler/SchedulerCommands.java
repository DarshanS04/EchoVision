package com.visionassist.commands.scheduler;

import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;
import java.util.Calendar;
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
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false); // show confirmation
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
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
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

    // ─── Calendar ───────────────────────────────────────────────────────────

    /**
     * Creates a new calendar event or opens the calendar.
     * Voice examples:
     *   "Create calendar event for meeting tomorrow"
     *   "Add reminder for doctor appointment"
     *   "Open my calendar"
     */
    public void createCalendarEvent(String rawText, CommandRouter.CommandCallback callback) {
        String title = extractEventTitle(rawText);

        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        if (!title.isEmpty()) {
            intent.putExtra(CalendarContract.Events.TITLE, title);
            // Default to 1 hour from now
            long now = System.currentTimeMillis();
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, now + 60 * 60 * 1000);
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, now + 2 * 60 * 60 * 1000);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
            String msg = title.isEmpty()
                    ? "Opening your calendar."
                    : "Opening calendar to create event: " + title;
            tts.speak(msg);
            callback.onResult(msg);
        } catch (Exception e) {
            AppLogger.e(TAG, "Calendar intent failed", e);
            // Fallback: open calendar app with a VIEW intent
            try {
                long now = System.currentTimeMillis();
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setData(android.net.Uri.parse("content://com.android.calendar/time/" + now));
                viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(viewIntent);
                String msg = "Opening your calendar.";
                tts.speak(msg);
                callback.onResult(msg);
            } catch (Exception ex) {
                String msg = "Sorry, I could not open the calendar.";
                tts.speak(msg);
                callback.onResult(msg);
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Parses hour and minute from text. Returns int[]{hour24, minute} or null.
     */
    private int[] parseTime(String text) {
        String t = text.toLowerCase();
        boolean pm = t.contains("pm") || t.contains("p.m") || t.contains(" evening")
                || t.contains("night") || t.contains("afternoon");
        boolean am = t.contains("am") || t.contains("a.m") || t.contains("morning");

        // Pattern e.g. "7:30", "6 30", "7"
        Pattern p = Pattern.compile("(\\d{1,2})(?:[:\\s](\\d{2}))?");
        Matcher m = p.matcher(t);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = (m.group(2) != null) ? Integer.parseInt(m.group(2)) : 0;

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
}
