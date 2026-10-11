package com.friday.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** The floating "Friday" bubble: always on screen, tap to type a command. */
public class BubbleOverlayService extends Service {
    /** Set before starting the service to open the typing panel immediately. */
    public static volatile boolean openPanelNext = false;

    private WindowManager wm;
    private Handler handler;
    private View bubble;
    private LinearLayout panel;
    private EditText input;
    private boolean panelShown = false;
    private CommandRouter router;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        router = new CommandRouter(this, this::respond);
        startForeground(2, buildNotif());
        TtsHelper.init(this);
        handler.postDelayed(this::showBubble, 250);
    }

    private void respond(String text) {
        TtsHelper.speak(text);
        handler.post(() -> Toast.makeText(this, "Friday: " + text, Toast.LENGTH_LONG).show());
    }

    private Notification buildNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("friday_bubble",
                    "Friday bubble", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "friday_bubble")
                : new Notification.Builder(this);
        b.setContentTitle("Friday bubble is on")
         .setContentText("Tap the bubble to type a command for Friday.")
         .setSmallIcon(getIconRes())
         .setOngoing(true);
        return b.build();
    }

    private float density() { return getResources().getDisplayMetrics().density; }

    private void showBubble() {
        if (bubble != null) return;
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' for Friday first.", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        TextView face = new TextView(this);
        face.setText("F");
        face.setTextColor(Color.WHITE);
        face.setTextSize(22);
        face.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColors(new int[]{0xFF3B4CCA, 0xFF7C5CFF});
        bg.setOrientation(GradientDrawable.Orientation.TL_BR);
        face.setBackground(bg);

        final int size = (int) (56 * density());
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 24;
        p.y = 260;

        face.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean dragged;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = p.x; startY = p.y;
                        dragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            dragged = true;
                            p.x = startX + (int) dx;
                            p.y = startY + (int) dy;
                            try { wm.updateViewLayout(face, p); } catch (Exception ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragged) togglePanel();
                        return true;
                }
                return false;
            }
        });

        try {
            wm.addView(face, p);
            bubble = face;
            if (openPanelNext) {
                openPanelNext = false;
                handler.postDelayed(this::togglePanel, 200);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Bubble failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void togglePanel() {
        if (panelShown) hidePanel(); else showPanel();
    }

    private void showPanel() {
        if (panel != null) return;
        float d = density();
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(18 * d);
        panel.setBackground(bg);
        panel.setElevation(12);

        TextView title = new TextView(this);
        title.setText("Friday - what is your work, Rishabh sir?");
        title.setTextSize(14);
        title.setTextColor(0xFF232B7A);
        panel.addView(title);

        input = new EditText(this);
        input.setHint("e.g. open WhatsApp and send a message to Rishika about how an AI agent works");
        input.setSingleLine(true);
        input.setTextSize(14);
        panel.addView(input);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView go = new TextView(this);
        go.setText("  Do it  ");
        go.setTextColor(Color.WHITE);
        go.setTextSize(15);
        GradientDrawable goBg = new GradientDrawable();
        goBg.setColor(0xFF3B4CCA);
        goBg.setCornerRadius(24 * d);
        go.setBackground(goBg);
        go.setPadding(pad, (int) (8 * d), pad, (int) (8 * d));
        go.setOnClickListener(v -> submit());

        TextView close = new TextView(this);
        close.setText("  Close  ");
        close.setTextColor(0xFF3B4CCA);
        close.setTextSize(15);
        GradientDrawable clBg = new GradientDrawable();
        clBg.setColor(0xFFECECFB);
        clBg.setCornerRadius(24 * d);
        close.setBackground(clBg);
        close.setPadding(pad, (int) (8 * d), pad, (int) (8 * d));
        close.setOnClickListener(v -> hidePanel());

        row.addView(go);
        row.addView(close);
        panel.addView(row);

        WindowManager.LayoutParams pp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        pp.gravity = Gravity.BOTTOM | Gravity.START;
        try {
            wm.addView(panel, pp);
            panelShown = true;
            input.requestFocus();
        } catch (Exception ignored) {}
    }

    private void submit() {
        String text = input == null ? "" : input.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Type something for Friday.", Toast.LENGTH_SHORT).show();
            return;
        }
        hidePanel();
        respond("Working on it, Rishabh sir.");
        router.handle(text);
    }

    private void hidePanel() {
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
            panel = null;
        }
        panelShown = false;
    }

    private int getIconRes() {
        int id = getResources().getIdentifier("ic_stat_friday", "drawable", getPackageName());
        return id == 0 ? android.R.drawable.ic_btn_speak_now : id;
    }

    @Override public void onDestroy() {
        hidePanel();
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        super.onDestroy();
    }
}
