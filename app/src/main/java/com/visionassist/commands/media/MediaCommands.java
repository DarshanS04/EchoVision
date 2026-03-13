package com.visionassist.commands.media;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;

/**
 * Handles media-related voice commands.
 *
 * YouTube: Uses the YouTube deep-link URI (vnd.youtube://results?search_query=...)
 *   - This opens YouTube's search results screen where the first result is highlighted.
 *   - Falls back to browser search page if YouTube is not installed.
 *
 * Spotify: Uses the Spotify search URI (spotify:search:query) which opens Spotify
 *   directly to the track/artist. Falls back to browser.
 *
 * No API key required for either service.
 */
public class MediaCommands {

    private static final String TAG = "MediaCommands";
    private final Context context;
    private final TTSManager tts;

    private static final String PKG_YOUTUBE = "com.google.android.youtube";
    private static final String PKG_SPOTIFY = "com.spotify.music";

    public MediaCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    // ─── YouTube ─────────────────────────────────────────────────────────────

    /**
     * Opens YouTube and navigates to the search results for the given query.
     * The user will see the first/best result and can tap to play — or the
     * YouTube autoplay will start the top result.
     *
     * Uses the deep-link scheme: vnd.youtube://results?search_query=...
     * This is YouTube's internal URI that opens the in-app search directly.
     *
     * Voice examples:
     *   "Play Waka Waka on YouTube"  →  opens YouTube searching "waka waka"
     *   "YouTube Bohemian Rhapsody"  →  opens YouTube searching that song
     */
    public void playOnYouTube(String rawText, CommandRouter.CommandCallback callback) {
        String query = extractMediaQuery(rawText, "youtube", "play", "on", "search");

        if (query.isEmpty()) {
            String msg = "What would you like to play on YouTube?";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        tts.speak("Playing " + query + " on YouTube");

        boolean success = false;

        // Strategy 1: YouTube deep-link URI — opens search results instantly
        if (isAppInstalled(PKG_YOUTUBE)) {
            try {
                // vnd.youtube://results?search_query=... opens YouTube in-app search
                Intent ytIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("vnd.youtube://results?search_query=" + Uri.encode(query)));
                ytIntent.setPackage(PKG_YOUTUBE);
                ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(ytIntent);
                callback.onResult("Opening YouTube for " + query);
                AppLogger.i(TAG, "YouTube deep-link: " + query);
                success = true;
            } catch (Exception e1) {
                AppLogger.w(TAG, "YouTube deep-link failed, trying web URL: " + e1.getMessage());
            }

            // Strategy 2: Open youtube.com search in YouTube app
            if (!success) {
                try {
                    String url = "https://www.youtube.com/results?search_query=" + Uri.encode(query);
                    Intent ytWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    ytWeb.setPackage(PKG_YOUTUBE);
                    ytWeb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(ytWeb);
                    callback.onResult("Opening YouTube for " + query);
                    AppLogger.i(TAG, "YouTube web URL in app: " + query);
                    success = true;
                } catch (Exception e2) {
                    AppLogger.w(TAG, "YouTube in-app web failed: " + e2.getMessage());
                }
            }
        }

        // Strategy 3: Browser fallback
        if (!success) {
            try {
                String url = "https://www.youtube.com/results?search_query=" + Uri.encode(query);
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(browser);
                String msg = "Opening YouTube in browser for " + query;
                tts.speak("Opening browser for " + query);
                callback.onResult(msg);
                AppLogger.i(TAG, "YouTube browser fallback: " + query);
            } catch (Exception e3) {
                AppLogger.e(TAG, "All YouTube strategies failed", e3);
                String msg = "Sorry, I could not open YouTube.";
                tts.speak(msg);
                callback.onResult(msg);
            }
        }
    }

    // ─── Spotify ─────────────────────────────────────────────────────────────

    /**
     * Opens Spotify with the search results for the given query.
     *
     * Uses the Spotify URI: spotify:search:query
     * This opens Spotify directly on the search results page.
     *
     * Voice examples:
     *   "Play Waka Waka on Spotify"  →  opens Spotify searching "waka waka"
     *   "Play Queen on Spotify"       →  opens Spotify searching "queen"
     */
    public void playOnSpotify(String rawText, CommandRouter.CommandCallback callback) {
        String query = extractMediaQuery(rawText, "spotify", "play", "on", "search");

        if (query.isEmpty()) {
            String msg = "What would you like to play on Spotify?";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        tts.speak("Playing " + query + " on Spotify");

        boolean success = false;

        if (isAppInstalled(PKG_SPOTIFY)) {
            // Strategy 1: Spotify native URI search
            try {
                Intent spotifyIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("spotify:search:" + Uri.encode(query)));
                spotifyIntent.setPackage(PKG_SPOTIFY);
                spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(spotifyIntent);
                callback.onResult("Opening Spotify for " + query);
                AppLogger.i(TAG, "Spotify URI search: " + query);
                success = true;
            } catch (Exception e1) {
                AppLogger.w(TAG, "Spotify URI failed: " + e1.getMessage());
            }

            // Strategy 2: Spotify web URL in app
            if (!success) {
                try {
                    String url = "https://open.spotify.com/search/" + Uri.encode(query);
                    Intent spotifyWeb = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    spotifyWeb.setPackage(PKG_SPOTIFY);
                    spotifyWeb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(spotifyWeb);
                    callback.onResult("Opening Spotify for " + query);
                    success = true;
                } catch (Exception e2) {
                    AppLogger.w(TAG, "Spotify web URL failed: " + e2.getMessage());
                }
            }
        }

        // Strategy 3: Browser fallback
        if (!success) {
            try {
                String url = "https://open.spotify.com/search/" + Uri.encode(query);
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(browser);
                tts.speak("Opening Spotify in browser for " + query);
                callback.onResult("Opening Spotify in browser for " + query);
            } catch (Exception e3) {
                AppLogger.e(TAG, "All Spotify strategies failed", e3);
                String msg = "Sorry, I could not open Spotify. Is it installed?";
                tts.speak(msg);
                callback.onResult(msg);
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String extractMediaQuery(String rawText, String... wordsToRemove) {
        String t = rawText.toLowerCase().trim();
        for (String word : wordsToRemove) {
            t = t.replace(word, "");
        }
        t = t.replace(" a ", " ").replace(" the ", " ")
              .replace("song", "").replace("music", "")
              .replace("video", "").replaceAll("\\s+", " ").trim();
        return t;
    }
}
