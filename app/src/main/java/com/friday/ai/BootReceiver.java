package com.friday.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts Friday's listening + bubble after the phone restarts. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String a = intent == null ? "" : intent.getAction();
        if (a == null) return;
        if (a.equals(Intent.ACTION_BOOT_COMPLETED)
                || a.equals("android.intent.action.QUICKBOOT_POWERON")) {
            try { context.startForegroundService(new Intent(context, WakeListenerService.class)); }
            catch (Exception ignored) {}
            try { context.startForegroundService(new Intent(context, BubbleOverlayService.class)); }
            catch (Exception ignored) {}
        }
    }
}
