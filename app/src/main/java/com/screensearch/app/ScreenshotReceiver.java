package com.screensearch.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class ScreenshotReceiver extends BroadcastReceiver {

    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Intent serviceIntent = new Intent(context, FloatingWindowService.class);

        if ("com.screensearch.HIDE_WINDOW".equals(action)) {
            serviceIntent.putExtra("action", "hide");
        } else if ("com.screensearch.SHOW_WINDOW".equals(action)) {
            serviceIntent.putExtra("action", "show");
        }

        try {
            context.startService(serviceIntent);
        } catch (Exception e) {
            // Service might not be running
        }
    }
}
