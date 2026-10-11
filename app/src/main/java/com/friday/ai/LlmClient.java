package com.friday.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Friday's AI brain: calls any OpenAI-compatible chat API to write
 * long notes (about 1000 characters) for WhatsApp messages.
 */
public class LlmClient {
    public interface Callback { void onDone(String text, String error); }

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    public static void generateNote(final Context c, final String topic, final Callback cb) {
        final Prefs prefs = new Prefs(c);
        POOL.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (prefs.apiKey().trim().isEmpty()) {
                        throw new IllegalStateException("no_api_key");
                    }
                    String sys = "You are Friday, the personal AI assistant of " + FridayConfig.USER_NAME
                            + ". " + FridayConfig.BOSS_LINE
                            + " Write a clear, well-structured note in English about the user's topic."
                            + " Target length 900-1000 characters. Use short simple paragraphs."
                            + " Return only the note text, no title, no markdown.";
                    JSONObject body = new JSONObject();
                    body.put("model", prefs.model());
                    JSONArray msgs = new JSONArray();
                    msgs.put(new JSONObject().put("role", "system").put("content", sys));
                    msgs.put(new JSONObject().put("role", "user").put("content", topic));
                    body.put("messages", msgs);
                    body.put("max_tokens", 800);
                    body.put("temperature", 0.7);

                    HttpURLConnection conn = (HttpURLConnection) new URL(prefs.baseUrl()).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(30000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + prefs.apiKey().trim());
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    os.close();

                    int code = conn.getResponseCode();
                    if (code != 200) throw new IllegalStateException("http_" + code);
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject resp = new JSONObject(sb.toString());
                    String text = resp.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");
                    done(cb, text.trim(), null);
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "error" : e.getMessage();
                    done(cb, null, msg);
                }
            }
        });
    }

    private static void done(final Callback cb, final String text, final String err) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { cb.onDone(text, err); }
        });
    }
}
