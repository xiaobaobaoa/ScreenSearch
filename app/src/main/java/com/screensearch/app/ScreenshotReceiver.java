package com.screensearch.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreenshotReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Intent serviceIntent = new Intent(context, FloatingWindowService.class);

        if ("com.screensearch.HIDE_WINDOW".equals(action)) {
            serviceIntent.putExtra("action", "hide");
        } else if ("com.screensearch.SHOW_WINDOW".equals(action)) {
            serviceIntent.putExtra("action", "show");
        } else {
            return;
        }

        try {
            context.startForegroundService(serviceIntent);
        } catch (Exception e) {
            try {
                context.startService(serviceIntent);
            } catch (Exception ignored) {}
        }
    }
}
