package com.friday.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** Setup screen: permissions, start listening, show bubble, AI brain settings. */
public class MainActivity extends Activity {
    private TextView status, log;
    private EditText apiKeyEdit, baseUrlEdit, modelEdit, contactEdit;
    private Prefs prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R_C.layout(this, "activity_main"));
        prefs = new Prefs(this);

        status = findViewById(R_C.id(this, "statusText"));
        log = findViewById(R_C.id(this, "logText"));
        apiKeyEdit = findViewById(R_C.id(this, "apiKeyEdit"));
        baseUrlEdit = findViewById(R_C.id(this, "baseUrlEdit"));
        modelEdit = findViewById(R_C.id(this, "modelEdit"));
        contactEdit = findViewById(R_C.id(this, "contactEdit"));

        apiKeyEdit.setText(prefs.apiKey());
        baseUrlEdit.setText(prefs.baseUrl());
        modelEdit.setText(prefs.model());
        contactEdit.setText(prefs.contact());

        bindClick("btnMic", v -> {
            String[] ps = android.os.Build.VERSION.SDK_INT >= 33
                    ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                    : new String[]{Manifest.permission.RECORD_AUDIO};
            requestPermissions(ps, 1);
        });

        bindClick("btnOverlay", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        bindClick("btnAccessibility", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        bindClick("btnListening", v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Grant the microphone first.", Toast.LENGTH_SHORT).show();
                return;
            }
            startForegroundService(new Intent(this, WakeListenerService.class));
            Toast.makeText(this, "Friday is listening. Say \"Hey Friday\".", Toast.LENGTH_LONG).show();
            onResume();
        });

        bindClick("btnBubble", v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow 'Display over other apps' first.", Toast.LENGTH_SHORT).show();
                return;
            }
            startForegroundService(new Intent(this, BubbleOverlayService.class));
        });

        bindClick("btnTest", v -> {
            TtsHelper.init(this);
            TtsHelper.speak(FridayConfig.BOSS_LINE + " I am ready, Rishabh sir.");
            log.setText("Friday: " + FridayConfig.BOSS_LINE + "\n" + log.getText());
        });

        bindClick("btnSave", v -> {
            prefs.apiKey(apiKeyEdit.getText().toString().trim());
            String base = baseUrlEdit.getText().toString().trim();
            prefs.baseUrl(base.isEmpty() ? FridayConfig.DEFAULT_LLM_BASE : base);
            String model = modelEdit.getText().toString().trim();
            prefs.model(model.isEmpty() ? FridayConfig.DEFAULT_MODEL : model);
            String contact = contactEdit.getText().toString().trim();
            prefs.contact(contact.isEmpty() ? FridayConfig.DEFAULT_CONTACT : contact);
            Toast.makeText(this, "Friday AI settings saved.", Toast.LENGTH_SHORT).show();
        });
    }

    private void bindClick(String idName, View.OnClickListener listener) {
        int id = getResources().getIdentifier(idName, "id", getPackageName());
        if (id != 0) findViewById(id).setOnClickListener(listener);
    }

    @Override protected void onResume() {
        super.onResume();
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean acc = FridayAccessibilityService.isReady();
        status.setText("Microphone: " + (mic ? "OK" : "missing")
                + "\nFloating bubble permission: " + (overlay ? "OK" : "missing")
                + "\nAccessibility god controls: " + (acc ? "ON" : "OFF")
                + "\n\nBoss: " + FridayConfig.BOSS_LINE);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        onResume();
    }
}
