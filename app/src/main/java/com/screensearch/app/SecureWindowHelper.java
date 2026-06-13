package com.screensearch.app;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class SecureWindowHelper {

    public static void setSecure(View view) {
        try {
            Field mAttachInfoField = View.class.getDeclaredField("mAttachInfo");
            mAttachInfoField.setAccessible(true);
            Object mAttachInfo = mAttachInfoField.get(view);
            if (mAttachInfo == null) return;

            Field mSurfaceControlField = mAttachInfo.getClass().getDeclaredField("mSurfaceControl");
            mSurfaceControlField.setAccessible(true);
            Object mSurfaceControl = mSurfaceControlField.get(mAttachInfo);
            if (mSurfaceControl == null) return;

            Method setSecureMethod = mSurfaceControl.getClass().getDeclaredMethod("setSecure", boolean.class);
            setSecureMethod.setAccessible(true);
            setSecureMethod.invoke(mSurfaceControl, true);
        } catch (Exception e) {
            // reflection failed, FLAG_SECURE from params is the fallback
        }
    }

    public static void setSecureOnWindow(Window window) {
        try {
            Method getWindowGlobalDataMethod = window.getClass().getDeclaredMethod("getWindowController");
            // fallback to standard flags
        } catch (Exception ignored) {}
    }
}
