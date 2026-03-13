package com.visionassist.commands.web;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.visionassist.commands.CommandRouter;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.voice.tts.TTSManager;

/**
 * Handles web-based commands: Google Search, Weather, and News.
 * Opens the device's default browser — no API key required.
 */
public class WebCommands {

    private static final String TAG = "WebCommands";
    private final Context context;
    private final TTSManager tts;

    public WebCommands(Context context) {
        this.context = context.getApplicationContext();
        this.tts = TTSManager.getInstance(context);
    }

    // ─── Web Search ─────────────────────────────────────────────────────────

    /**
     * Performs a Google Search in the browser.
     * Voice examples:
     *   "Search for how to make pasta"
     *   "Google Albert Einstein"
     */
    public void performWebSearch(String rawText, CommandRouter.CommandCallback callback) {
        String query = extractSearchQuery(rawText);

        if (query.isEmpty()) {
            String msg = "What would you like me to search for?";
            tts.speak(msg);
            callback.onResult(msg);
            return;
        }

        String url = "https://www.google.com/search?q=" + Uri.encode(query);
        openBrowser(url, "Searching Google for " + query, callback);
    }

    // ─── Weather ────────────────────────────────────────────────────────────

    /**
     * Opens a weather page in the browser.
     * Voice examples:
     *   "What is the weather"
     *   "Weather today"
     *   "Weather in Mumbai"
     */
    public void checkWeather(String rawText, CommandRouter.CommandCallback callback) {
        String location = extractWeatherLocation(rawText);
        String query = location.isEmpty() ? "weather today" : "weather in " + location;
        String url = "https://www.google.com/search?q=" + Uri.encode(query);
        openBrowser(url, "Checking weather" + (location.isEmpty() ? "." : " in " + location + "."), callback);
    }

    // ─── News ───────────────────────────────────────────────────────────────

    /**
     * Opens a news search in the browser.
     * Voice examples:
     *   "Show me the news"
     *   "Latest news on technology"
     *   "News"
     */
    public void showNews(String rawText, CommandRouter.CommandCallback callback) {
        String topic = extractNewsTopic(rawText);
        String url;
        if (topic.isEmpty()) {
            url = "https://news.google.com";
        } else {
            url = "https://www.google.com/search?q=" + Uri.encode(topic + " news")
                    + "&tbm=nws";
        }
        openBrowser(url, topic.isEmpty() ? "Opening Google News." : "Searching news about " + topic + ".", callback);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void openBrowser(String url, String announcement, CommandRouter.CommandCallback callback) {
        tts.speak(announcement);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            callback.onResult(announcement);
        } catch (Exception e) {
            AppLogger.e(TAG, "Browser open failed: " + url, e);
            String msg = "Sorry, I could not open the browser.";
            tts.speak(msg);
            callback.onResult(msg);
        }
    }

    private String extractSearchQuery(String rawText) {
        return rawText.toLowerCase()
                .replace("search for", "").replace("search", "")
                .replace("google", "").replace("look up", "")
                .replace("find", "").trim();
    }

    private String extractWeatherLocation(String rawText) {
        return rawText.toLowerCase()
                .replace("what is the weather", "").replace("what's the weather", "")
                .replace("weather today", "").replace("weather", "")
                .replace("today", "").replace("in", "").trim();
    }

    private String extractNewsTopic(String rawText) {
        return rawText.toLowerCase()
                .replace("latest news", "").replace("show me the news", "")
                .replace("show news", "").replace("news on", "")
                .replace("news about", "").replace("news", "")
                .replace("latest", "").trim();
    }
}
