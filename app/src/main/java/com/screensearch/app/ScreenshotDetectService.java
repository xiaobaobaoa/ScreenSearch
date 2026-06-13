package com.screensearch.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class ScreenshotDetectService extends AccessibilityService {

    private static ScreenshotDetectService instance;
    private static HideCallback callback;

    public interface HideCallback {
        void onHide();
        void onShow();
    }

    public static void setCallback(HideCallback cb) {
        callback = cb;
    }

    public static ScreenshotDetectService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED
                || event.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            CharSequence desc = event.getContentDescription();
            CharSequence className = event.getClassName();
            if (desc != null) {
                String text = desc.toString().toLowerCase();
                if (text.contains("截屏") || text.contains("截图") || text.contains("screenshot")
                        || text.contains("capture") || text.contains("scree")) {
                    hideWindow();
                }
            }
            if (className != null) {
                String cls = className.toString().toLowerCase();
                if (cls.contains("screenshot") || cls.contains("capture")) {
                    hideWindow();
                }
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            CharSequence text = event.getText() != null && !event.getText().isEmpty()
                    ? event.getText().get(0) : null;
            if (text != null) {
                String t = text.toString().toLowerCase();
                if (t.contains("截屏") || t.contains("截图") || t.contains("screenshot")) {
                    hideWindow();
                }
            }
        }
    }

    private void hideWindow() {
        if (callback != null) {
            callback.onHide();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (callback != null) callback.onShow();
            }, 2000);
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
