package com.screensearch.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AISearchService {

    private static final String PREFS_NAME = "ai_config";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";

    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";

    private final OkHttpClient client = new OkHttpClient();
    private final SharedPreferences prefs;

    public interface OnSearchResultListener {
        void onResult(String answer);
        void onError(String error);
    }

    public AISearchService(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveConfig(String apiUrl, String apiKey, String model) {
        prefs.edit()
                .putString(KEY_API_URL, apiUrl)
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_MODEL, model)
                .apply();
    }

    public String getApiUrl() {
        return prefs.getString(KEY_API_URL, DEFAULT_API_URL);
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public String getModel() {
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL);
    }

    public boolean isConfigured() {
        return !getApiKey().isEmpty();
    }

    public void search(String ocrText, OnSearchResultListener listener) {
        if (!isConfigured()) {
            listener.onError("请先在设置中配置 API 信息");
            return;
        }

        String prompt = "以下是屏幕识别出的文字，请帮我搜索/解答相关问题，给出简洁有用的回答：\n\n" + ocrText;

        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("model", getModel());

            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个智能搜题助手，根据用户屏幕识别出的文字，帮助解答问题或提供相关信息。回答要简洁准确。");
            messages.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
        } catch (JSONException e) {
            listener.onError("请求构造失败: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(getApiUrl())
                .addHeader("Authorization", "Bearer " + getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() != null ? response.body().string() : "无响应";
                    listener.onError("请求失败 (" + response.code() + "): " + errBody);
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(responseBody);
                JSONArray choices = json.getJSONArray("choices");
                if (choices.length() > 0) {
                    String content = choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    listener.onResult(content.trim());
                } else {
                    listener.onError("AI 未返回有效回答");
                }
            } catch (IOException e) {
                listener.onError("网络错误: " + e.getMessage());
            } catch (JSONException e) {
                listener.onError("解析响应失败: " + e.getMessage());
            }
        }).start();
    }
}
