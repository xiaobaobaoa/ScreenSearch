package com.screensearch.hook;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ScreenshotHook implements IXposedHookLoadPackage {

    private static final String TAG = "ScreenSearch";
    private static final String ACTION_HIDE = "com.screensearch.HIDE_WINDOW";
    private static final String ACTION_SHOW = "com.screensearch.SHOW_WINDOW";
    private static final String PKG = "com.screensearch.app";

    private static Context sSystemUIContext;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("[" + TAG + "] Loaded: " + lpparam.packageName);

        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        sSystemUIContext = (Context) XposedHelpers.getObjectField(
            XposedHelpers.getStaticObjectField(
                XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                "currentActivityThread"
            ),
            "mSystemContext"
        );

        if (sSystemUIContext == null) {
            XposedBridge.log("[" + TAG + "] Cannot get SystemUI context");
            return;
        }

        XposedBridge.log("[" + TAG + "] SystemUI context OK, hooking...");

        hookMethodSafe(lpparam.classLoader,
            "com.android.systemui.screenshot.ScreenshotHelper",
            "takeScreenshot", 6);

        hookMethodSafe(lpparam.classLoader,
            "com.android.systemui.screenshot.ScreenshotHelper",
            "takeScreenshotForSmartActions", 6);

        hookMethodSafe(lpparam.classLoader,
            "android.view.SurfaceControl",
            "screenshot", 4);

        hookMethodSafe(lpparam.classLoader,
            "android.view.SurfaceControl",
            "screenshotToBuffer", 5);

        XposedBridge.log("[" + TAG + "] All hooks done");
    }

    private void hookMethodSafe(ClassLoader cl, String className, String methodName, int maxParam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterTypes().length <= maxParam) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[" + TAG + "] >>> " + methodName + " intercepted!");
                            hide();
                        }
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            showDelayed();
                        }
                    });
                    XposedBridge.log("[" + TAG + "] Hooked " + className + "." + methodName
                        + " (params=" + m.getParameterTypes().length + ")");
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] FAILED " + className + "." + methodName + ": " + t.getMessage());
        }
    }

    private void hide() {
        try {
            Intent i = new Intent(ACTION_HIDE);
            i.setPackage(PKG);
            sSystemUIContext.sendBroadcast(i);
            XposedBridge.log("[" + TAG + "] HIDE sent");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] HIDE failed: " + t);
        }
    }

    private void showDelayed() {
        try {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent i = new Intent(ACTION_SHOW);
                    i.setPackage(PKG);
                    sSystemUIContext.sendBroadcast(i);
                    XposedBridge.log("[" + TAG + "] SHOW sent");
                } catch (Throwable t) {}
            }, 1500);
        } catch (Throwable t) {}
    }
}
