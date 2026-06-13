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
                // 用 su 启动 getevent 监听所有输入设备
                process = Runtime.getRuntime().exec("su");
                java.io.OutputStream os = process.getOutputStream();
                // 不指定设备，监听所有输入事件
                os.write("getevent -lt\n".getBytes());
                os.flush();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                boolean powerDown = false;
                boolean volumeDown = false;
                boolean volumeUp = false;
                int touchCount = 0;
                long lastTouchTime = 0;

                String line;
                while (running && (line = reader.readLine()) != null) {
                    // 检测电源键+音量下（标准截屏）
                    if (line.contains("EV_KEY") && line.contains("DOWN")) {
                        if (line.contains("KEY_POWER")) {
                            powerDown = true;
                        } else if (line.contains("KEY_VOLUMEDOWN")) {
                            volumeDown = true;
                        } else if (line.contains("KEY_VOLUMEUP")) {
                            volumeUp = true;
                        }
                    } else if (line.contains("EV_KEY") && line.contains("UP")) {
                        if (line.contains("KEY_POWER")) {
                            powerDown = false;
                        } else if (line.contains("KEY_VOLUMEDOWN")) {
                            volumeDown = false;
                        } else if (line.contains("KEY_VOLUMEUP")) {
                            volumeUp = false;
                        }
                    }

                    // 电源+音量下 = 标准截屏
                    if (powerDown && volumeDown) {
                        powerDown = false;
                        volumeDown = false;
                        triggerHide("power+volume_down");
                    }
                    // 电源+音量上 = 部分设备截屏
                    if (powerDown && volumeUp) {
                        powerDown = false;
                        volumeUp = false;
                        triggerHide("power+volume_up");
                    }

                    // 检测多点触摸（三指截屏）
                    if (line.contains("ABS_MT_POSITION")) {
                        long now = System.currentTimeMillis();
                        if (now - lastTouchTime < 100) {
                            touchCount++;
                        } else {
                            touchCount = 1;
                        }
                        lastTouchTime = now;

                        // 快速出现多个触摸点可能是三指手势
                        if (touchCount >= 6) {
                            touchCount = 0;
                            triggerHide("multi_touch");
                        }
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
        Log.d(TAG, "Root screenshot detector started - monitoring ALL input devices");
    }

    private void triggerHide(String reason) {
        Log.d(TAG, "Screenshot detected: " + reason);
        mainHandler.post(() -> {
            if (callback != null) callback.onHide();
            mainHandler.postDelayed(() -> {
                if (callback != null) callback.onShow();
            }, 2000);
        });
    }

    public void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        instance = null;
    }
}
