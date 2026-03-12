package com.visionassist.commands;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import com.visionassist.core.constants.AppConstants;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;
import java.util.List;

/**
 * Handles app-launching voice commands.
 * Resolves app name → package name → launch intent.
 */
public class AppCommands {

    private static final String TAG = "AppCommands";
    private final Context context;
    private final TTSManager tts;

    public AppCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    /**
     * Attempts to open an app by display name.
     * e.g. "Open WhatsApp" → extracts "whatsapp" → looks up package → launches
     */
    public void openApp(String commandText, CommandRouter.CommandCallback callback) {
        String appName = extractAppName(commandText);
        if (appName.isEmpty()) {
            tts.speak("Which app would you like to open?");
            callback.onResult("Which app would you like to open?");
            return;
        }

        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo info : apps) {
            String label = info.loadLabel(pm).toString().toLowerCase();
            // if (label.contains(appName)) {
            AppLogger.d(TAG, "Installed app: " + label);

            String normalizedLabel = label.replaceAll("[^a-z0-9 ]", "");
            String normalizedQuery = appName.replaceAll("[^a-z0-9 ]", "");

            if (normalizedLabel.contains(normalizedQuery)){
                String packageName = info.activityInfo.packageName;
                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    String response = "Opening " + info.loadLabel(pm).toString();
                    tts.speak(response);
                    callback.onResult(response);
                    AppLogger.i(TAG, "Launched: " + packageName);
                    return;
                }
            }
        }

        String response = "Sorry, I couldn't find an app called " + appName;
        tts.speak(response);
        callback.onResult(response);
        AppLogger.w(TAG, "App not found: " + appName);
    }

    /**
     * Lists all installed apps aloud.
     */
    public void listInstalledApps(CommandRouter.CommandCallback callback) {
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        StringBuilder sb = new StringBuilder("You have ");
        sb.append(apps.size()).append(" apps installed. ");

        int limit = Math.min(apps.size(), 10);
        for (int i = 0; i < limit; i++) {
            sb.append(apps.get(i).loadLabel(pm).toString());
            if (i < limit - 1) sb.append(", ");
        }
        if (apps.size() > 10) sb.append("... and more.");

        tts.speak(sb.toString());
        callback.onResult(sb.toString());
    }

    // private String extractAppName(String commandText) {
    //     // Remove trigger words and extract the app name
    //     String cleaned = commandText.toLowerCase()
    //             .replace("open", "")
    //             .replace("launch", "")
    //             .replace("start", "")
    //             .replace("run", "")
    //             .trim();
    //     return cleaned;
    // }

    private String extractAppName(String commandText) {
        commandText = commandText.toLowerCase().trim();

        String[] triggers = {"open", "launch", "start", "run"};

        for (String trigger : triggers) {
            if (commandText.startsWith(trigger)) {
                commandText = commandText.substring(trigger.length()).trim();
                break;
            }
        }

        commandText = commandText.replace("the", "")
                .replace("app", "")
                .trim();

        return commandText;
    }

}
