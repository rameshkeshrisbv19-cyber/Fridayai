package com.friday.ai;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import java.util.Arrays;

import java.util.ArrayList;

/** Always-on "Hey Friday" wake word listener (god-level ears). */
public class WakeListenerService extends Service {
    private static final String CHANNEL = "friday_listening";

    private SpeechRecognizer recognizer;
    private Handler handler;
    private CommandRouter router;
    private boolean woke;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        router = new CommandRouter(this, text -> {
            TtsHelper.speak(text);
            handler.post(() -> Toast.makeText(this, "Friday: " + text, Toast.LENGTH_LONG).show());
        });
        startForeground(1, buildNotif());
        TtsHelper.init(this);
        startListening();
    }

    private Notification buildNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "Friday listening", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("Friday is listening")
         .setContentText("Say \"Hey Friday\" any time.")
         .setSmallIcon(getIconRes())
         .setOngoing(true);
        return b.build();
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            handler.postDelayed(this::startListening, 4000);
            return;
        }
        try {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            recognizer.setRecognitionListener(listener);
            recognizer.startListening(i);
        } catch (Exception e) {
            handler.postDelayed(this::startListening, 4000);
        }
    }

    private void restart(long delayMs) {
        handler.postDelayed(() -> { woke = false; startListening(); }, delayMs);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float rmsdB) { }
        @Override public void onBufferReceived(byte[] buffer) { }
        @Override public void onEndOfSpeech() { }
        @Override public void onEvent(int eventType, Bundle params) { }

        @Override public void onPartialResults(Bundle partialResults) {
            if (woke) return;
            ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty()
                    && list.get(0).toLowerCase().contains(FridayConfig.WAKE_WORD)) {
                woke = true;
                TtsHelper.speak(FridayConfig.WAKE_PROMPT);
                BubbleOverlayService.openPanelNext = true;
                startService(new Intent(WakeListenerService.this, BubbleOverlayService.class));
            }
        }

        @Override public void onResults(Bundle results) {
            ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String heard = list != null && !list.isEmpty() ? list.get(0) : "";
            if (heard.toLowerCase().contains(FridayConfig.WAKE_WORD)) {
                String command = CommandRouter.norm(heard);
                if (command.length() > 2) {
                    router.handle(command);
                } else if (!woke) {
                    TtsHelper.speak(FridayConfig.WAKE_PROMPT);
                    BubbleOverlayService.openPanelNext = true;
                    startService(new Intent(WakeListenerService.this, BubbleOverlayService.class));
                }
            }
            restart(300);
        }

        @Override public void onError(int error) {
            long delay = (error == SpeechRecognizer.ERROR_NO_MATCH
                    || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    || error == SpeechRecognizer.ERROR_CLIENT) ? 600 : 2500;
            restart(delay);
        }
    };

    private int getIconRes() {
        int id = getResources().getIdentifier("ic_stat_friday", "drawable", getPackageName());
        return id == 0 ? android.R.drawable.ic_btn_speak_now : id;
    }

    @Override public void onDestroy() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
        super.onDestroy();
    }
}
