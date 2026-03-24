package com.visionassist.commands.media;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles media-related voice commands.
 *
 * YouTube: Uses the YouTube deep-link URI
 * (vnd.youtube://results?search_query=...)
 * - This opens YouTube's search results screen where the first result is
 * highlighted.
 * - Falls back to browser search page if YouTube is not installed.
 *
 * Spotify: Uses the Spotify search URI (spotify:search:query) which opens
 * Spotify
 * directly to the track/artist. Falls back to browser.
 *
 * No API key required for either service.
 */
public class MediaCommands {

    private static final String TAG = "MediaCommands";
    private final Context context;
    private final TTSManager tts;

    private static final String PKG_YOUTUBE = "com.google.android.youtube";
    private static final String PKG_SPOTIFY = "com.spotify.music";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
     * "Play Waka Waka on YouTube" → opens YouTube searching "waka waka"
     * "YouTube Bohemian Rhapsody" → opens YouTube searching that song
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

        executor.execute(() -> {
            boolean success = false;

            // Strategy 1: Headless search to find first video ID and play directly
            String videoId = searchYouTubeForVideoId(query);
            if (videoId != null && !videoId.isEmpty()) {
                if (isAppInstalled(PKG_YOUTUBE)) {
                    try {
                        Intent ytIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + videoId));
                        ytIntent.setPackage(PKG_YOUTUBE);
                        ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(ytIntent);
                        callback.onResult("Playing YouTube video for " + query);
                        AppLogger.i(TAG, "YouTube direct-play by scraped id: " + videoId);
                        success = true;
                    } catch (Exception e1) {
                        AppLogger.w(TAG, "YouTube direct-play failed, fallback to default: " + e1.getMessage());
                    }
                }
            }

            // Strategy 2: Default intent search
            if (!success && isAppInstalled(PKG_YOUTUBE)) {
                try {
                    Intent ytIntent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("vnd.youtube://results?search_query=" + Uri.encode(query)));
                    ytIntent.setPackage(PKG_YOUTUBE);
                    ytIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(ytIntent);
                    callback.onResult("Opening YouTube for " + query);
                    AppLogger.i(TAG, "YouTube default intent search: " + query);
                    success = true;
                } catch (Exception e2) {
                    AppLogger.w(TAG, "YouTube default search failed: " + e2.getMessage());
                }
            }

            // Strategy 3: Browser fallback (if youtube isn't installed)
            if (!success) {
                try {
                    String url = videoId != null ? "https://www.youtube.com/watch?v=" + videoId
                            : "https://www.youtube.com/results?search_query=" + Uri.encode(query);
                    Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(browser);
                    String msg = "Opening YouTube in browser for " + query;
                    callback.onResult(msg);
                    AppLogger.i(TAG, "YouTube browser fallback: " + query);
                } catch (Exception e3) {
                    AppLogger.e(TAG, "All YouTube strategies failed", e3);
                    String msg = "Sorry, I could not open YouTube.";
                    tts.speak(msg);
                    callback.onResult(msg);
                }
            }
        });
    }

    // ─── Spotify ─────────────────────────────────────────────────────────────

    /**
     * Opens Spotify with the search results for the given query.
     *
     * Uses the Spotify URI: spotify:search:query
     * This opens Spotify directly on the search results page.
     *
     * Voice examples:
     * "Play Waka Waka on Spotify" → opens Spotify searching "waka waka"
     * "Play Queen on Spotify" → opens Spotify searching "queen"
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
            // Strategy 1: Spotify native auto-play intent
            try {
                Intent spotifyIntent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                spotifyIntent.setPackage(PKG_SPOTIFY);
                spotifyIntent.putExtra(SearchManager.QUERY, query);
                spotifyIntent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*");
                spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(spotifyIntent);
                callback.onResult("Opening Spotify for " + query);
                AppLogger.i(TAG, "Spotify auto-play intent: " + query);
                success = true;
            } catch (Exception e1) {
                AppLogger.w(TAG, "Spotify auto-play intent failed: " + e1.getMessage());
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

    private String searchYouTubeForVideoId(String query) {
        try {
            String encodedQuery = Uri.encode(query);
            String urlStr = "https://www.youtube.com/results?search_query=" + encodedQuery;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            // The JSON with videoIds is often embedded inside youtube initial data script
            // tag
            Pattern compile = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"");
            while ((inputLine = in.readLine()) != null) {
                Matcher m = compile.matcher(inputLine);
                if (m.find()) {
                    in.close();
                    return m.group(1);
                }
            }
            in.close();
        } catch (Exception e) {
            AppLogger.e(TAG, "Failed to scrape YouTube for direct ID", e);
        }
        return null;
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
