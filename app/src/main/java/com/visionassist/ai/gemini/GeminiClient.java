package com.visionassist.ai.gemini;

import android.content.Context;
import android.util.Base64;
import com.visionassist.core.logger.AppLogger;
import com.visionassist.data.repository.AppRepository;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.visionassist.data.local.chat.ChatMessage;

/**
 * HTTP client for Google Gemini API.
 * Supports both text-only and text+image (vision) queries.
 */
public class GeminiClient {

    private static final String TAG = "GeminiClient";
    private static final String GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String MODEL = "gemini-2.5-flash";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    public interface GeminiCallback {
        void onResponse(String text);
        void onError(String error);
    }

    private final Context context;
    private final AppRepository repository;
    private final OkHttpClient httpClient;

    public GeminiClient(Context context) {
        this.context = context.getApplicationContext();
        this.repository = AppRepository.getInstance(context);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Send a text-only query to Gemini.
     */
    public void sendTextQuery(String prompt, GeminiCallback callback) {
        sendTextQuery(prompt, null, callback);
    }

    /**
     * Send a text-only query to Gemini with history.
     */
    public void sendTextQuery(String prompt, List<ChatMessage> history, GeminiCallback callback) {
        if (!repository.shouldUseGemini()) {
            callback.onError("Offline mode is enabled.");
            return;
        }
        String apiKey = repository.getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Gemini API key not configured.");
            return;
        }

        try {
            JSONObject requestBody = buildTextRequest(prompt, history);
            String url = GEMINI_API_BASE + MODEL + ":generateContent?key=" + apiKey;
            executeRequest(url, requestBody.toString(), callback);
        } catch (JSONException e) {
            AppLogger.e(TAG, "JSON build error", e);
            callback.onError("Failed to build request");
        }
    }

    /**
     * Send a text + image query to Gemini Vision.
     */
    public void sendVisionQuery(String prompt, byte[] imageBytes, String mimeType,
                                GeminiCallback callback) {
        if (!repository.shouldUseGemini()) {
            callback.onError("Offline mode is enabled.");
            return;
        }
        String apiKey = repository.getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Gemini API key not configured.");
            return;
        }

        try {
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            JSONObject requestBody = buildVisionRequest(prompt, base64Image, mimeType);
            String url = GEMINI_API_BASE + MODEL + ":generateContent?key=" + apiKey;
            executeRequest(url, requestBody.toString(), callback);
        } catch (JSONException e) {
            AppLogger.e(TAG, "Vision JSON build error", e);
            callback.onError("Failed to build vision request");
        }
    }

    private void executeRequest(String url, String bodyJson, GeminiCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(bodyJson, JSON_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull Call call,
                                  @androidx.annotation.NonNull IOException e) {
                AppLogger.e(TAG, "Gemini request failed", e);
                callback.onError("Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull Call call,
                                   @androidx.annotation.NonNull Response response) {
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        AppLogger.e(TAG, "Gemini error response: " + body);
                        String errorMsg = parseErrorResponse(body);
                        callback.onError("API error (" + response.code() + "): " + errorMsg);
                        return;
                    }
                    String text = parseResponse(body);
                    AppLogger.i(TAG, "Gemini response: " + text.substring(0, Math.min(100, text.length())));
                    callback.onResponse(text);
                } catch (Exception e) {
                    AppLogger.e(TAG, "Response parse error", e);
                    callback.onError("Failed to parse response: " + e.getMessage());
                }
            }
        });
    }

    private String parseErrorResponse(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            if (root.has("error")) {
                JSONObject error = root.getJSONObject("error");
                return error.optString("message", "Unknown API error");
            }
        } catch (Exception e) {
            AppLogger.e(TAG, "Error parsing error response", e);
        }
        return "Unknown error";
    }

    private String parseResponse(String responseJson) throws JSONException {
        JSONObject root = new JSONObject(responseJson);
        JSONArray candidates = root.getJSONArray("candidates");
        JSONObject first = candidates.getJSONObject(0);
        JSONObject content = first.getJSONObject("content");
        JSONArray parts = content.getJSONArray("parts");
        return parts.getJSONObject(0).getString("text");
    }

    private JSONObject buildTextRequest(String prompt, List<ChatMessage> history) throws JSONException {
        JSONArray contents = new JSONArray();

        if (history != null) {
            for (ChatMessage msg : history) {
                JSONObject part = new JSONObject().put("text", msg.getContent());
                JSONObject content = new JSONObject()
                        .put("role", msg.getRole())
                        .put("parts", new JSONArray().put(part));
                contents.put(content);
            }
        }

        JSONObject currentPart = new JSONObject().put("text", prompt);
        JSONObject currentContent = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(currentPart));
        contents.put(currentContent);

        return new JSONObject()
                .put("contents", contents)
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 512));
    }

    private JSONObject buildTextRequestWithJson(String prompt, List<ChatMessage> history) throws JSONException {
        JSONArray contents = new JSONArray();

        if (history != null) {
            for (ChatMessage msg : history) {
                JSONObject part = new JSONObject().put("text", msg.getContent());
                JSONObject content = new JSONObject()
                        .put("role", msg.getRole())
                        .put("parts", new JSONArray().put(part));
                contents.put(content);
            }
        }

        JSONObject currentPart = new JSONObject().put("text", prompt);
        JSONObject currentContent = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(currentPart));
        contents.put(currentContent);

        return new JSONObject()
                .put("contents", contents)
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.1)
                        .put("responseMimeType", "application/json")
                        .put("maxOutputTokens", 512));
    }

    /**
     * Send a query to Gemini for App Navigation (forcing JSON output).
     */
    public void sendAppControlQuery(String userCommand, String screenJson, String contextStr, GeminiCallback callback) {
        sendAppControlQuery(userCommand, screenJson, contextStr, null, callback);
    }

    public void sendAppControlQuery(String userCommand, String screenJson, String contextStr, List<ChatMessage> history, GeminiCallback callback) {
        if (!repository.shouldUseGemini()) {
            callback.onError("Offline mode is enabled.");
            return;
        }
        String apiKey = repository.getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Gemini API key not configured.");
            return;
        }

        String prompt = "You are an AI assistant helping a blind user operate ANY Android app.\n" +
                "You are given:\n" +
                "1. A list of UI elements with IDs (JSON)\n" +
                "2. A user command\n" +
                "3. Context\n\n" +
                "Your job: Return EXACTLY ONE action in JSON.\n" +
                "STRICT RULES:\n" +
                "- Only use provided element IDs\n" +
                "- Do NOT assume UI\n" +
                "- Do NOT hallucinate\n" +
                "- Prefer safest meaningful action\n" +
                "- If multiple similar elements -> choose most relevant\n" +
                "- If unclear -> return 'ask'\n\n" +
                "Allowed actions: click, scroll, scroll_backward, input_text, back, ask\n\n" +
                "Output format EXACTLY like this (ONLY JSON):\n" +
                "{\"action\": \"click\", \"target_id\": 2, \"input_text\": \"\", \"reason\": \"to submit\"}\n\n" +
                "Elements: " + screenJson + "\n" +
                "Context: " + contextStr + "\n" +
                "Command: " + userCommand;

        try {
            JSONObject requestBody = buildTextRequestWithJson(prompt, history);
            String url = GEMINI_API_BASE + MODEL + ":generateContent?key=" + apiKey;
            executeRequest(url, requestBody.toString(), callback);
        } catch (JSONException e) {
            AppLogger.e(TAG, "JSON build error", e);
            callback.onError("Failed to build request");
        }
    }

    private JSONObject buildVisionRequest(String prompt, String base64Image,
                                          String mimeType) throws JSONException {
        JSONObject textPart = new JSONObject().put("text", prompt);
        JSONObject imageData = new JSONObject()
                .put("mime_type", mimeType)
                .put("data", base64Image);
        JSONObject imagePart = new JSONObject().put("inline_data", imageData);
        JSONArray parts = new JSONArray().put(textPart).put(imagePart);
        JSONObject content = new JSONObject().put("parts", parts);
        JSONArray contents = new JSONArray().put(content);
        return new JSONObject()
                .put("contents", contents)
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.7)
                        .put("maxOutputTokens", 512));
    }
}
