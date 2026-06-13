package com.screensearch.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RootScreenshotDetector {

    private static final String TAG = "RootScreenshot";
    private static RootScreenshotDetector instance;
    private Thread monitorThread;
    private volatile boolean running = false;
    private HideCallback callback;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface HideCallback {
        void onHide();
        void onShow();
    }

    public static RootScreenshotDetector getInstance() {
        if (instance == null) {
            instance = new RootScreenshotDetector();
        }
        return instance;
    }

    public void setCallback(HideCallback cb) {
        this.callback = cb;
    }

    public boolean checkRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            reader.close();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    public void start() {
        if (running) return;
        running = true;

        monitorThread = new Thread(() -> {
            Process process = null;
            try {
                process = Runtime.getRuntime().exec("su");
                java.io.OutputStream os = process.getOutputStream();
                os.write("getevent -lt /dev/input/event2\n".getBytes());
                os.flush();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                boolean powerDown = false;
                boolean volumeDown = false;

                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (line.contains("EV_KEY") && line.contains("DOWN")) {
                        if (line.contains("KEY_POWER")) {
                            powerDown = true;
                        } else if (line.contains("KEY_VOLUMEDOWN")) {
                            volumeDown = true;
                        }
                    } else if (line.contains("EV_KEY") && line.contains("UP")) {
                        if (line.contains("KEY_POWER")) {
                            powerDown = false;
                        } else if (line.contains("KEY_VOLUMEDOWN")) {
                            volumeDown = false;
                        }
                    }

                    if (powerDown && volumeDown) {
                        Log.d(TAG, "Screenshot combo detected!");
                        powerDown = false;
                        volumeDown = false;
                        mainHandler.post(() -> {
                            if (callback != null) callback.onHide();
                            mainHandler.postDelayed(() -> {
                                if (callback != null) callback.onShow();
                            }, 2000);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Monitor error: " + e.getMessage());
            } finally {
                if (process != null) {
                    try { process.destroy(); } catch (Exception ignored) {}
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
        Log.d(TAG, "Root screenshot detector started");
    }

    public void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        instance = null;
    }
}
