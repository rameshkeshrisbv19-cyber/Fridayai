package com.friday.ai;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

/** Friday's voice. */
public class TtsHelper {
    private static TextToSpeech tts;
    private static boolean ready = false;

    public static synchronized void init(Context c) {
        if (tts != null) return;
        tts = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                ready = (status == TextToSpeech.SUCCESS);
                if (ready) {
                    try { tts.setLanguage(Locale.US); } catch (Exception ignored) {}
                }
            }
        });
    }

    public static synchronized void speak(String text) {
        if (tts == null || !ready || text == null || text.length() == 0) return;
        final String shortened = text.length() > 350 ? text.substring(0, 350) + "…" : text;
        try {
            tts.speak(shortened, TextToSpeech.QUEUE_ADD, null, "friday_" + System.currentTimeMillis());
        } catch (Exception ignored) {}
    }
}
