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

    private static final String ACTION_HIDE = "com.screensearch.HIDE_WINDOW";
    private static final String ACTION_SHOW = "com.screensearch.SHOW_WINDOW";
    private static final String TARGET_PKG = "com.screensearch.app";
    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        XposedBridge.log("ScreenSearch Hook: Loaded in SystemUI");

        hookScreenshotHelper(lpparam);
        hookSurfaceControl(lpparam);
    }

    private void hookScreenshotHelper(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.android.systemui.screenshot.ScreenshotHelper", lpparam.classLoader);

            XposedBridge.hookAllMethods(clazz, "takeScreenshot", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    sendToApp(ACTION_HIDE);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mainHandler.postDelayed(() -> sendToApp(ACTION_SHOW), 2000);
                }
            });

            XposedBridge.hookAllMethods(clazz, "takeScreenshotForSmartActions", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    sendToApp(ACTION_HIDE);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mainHandler.postDelayed(() -> sendToApp(ACTION_SHOW), 2000);
                }
            });

            XposedBridge.log("ScreenSearch Hook: ScreenshotHelper hooked OK");
        } catch (Throwable t) {
            XposedBridge.log("ScreenSearch Hook: ScreenshotHelper not found: " + t.getMessage());
        }
    }

    private void hookSurfaceControl(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "android.view.SurfaceControl", lpparam.classLoader);

            XposedBridge.hookAllMethods(clazz, "screenshot", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    sendToApp(ACTION_HIDE);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mainHandler.postDelayed(() -> sendToApp(ACTION_SHOW), 2000);
                }
            });

            XposedBridge.hookAllMethods(clazz, "screenshotToBuffer", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    sendToApp(ACTION_HIDE);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mainHandler.postDelayed(() -> sendToApp(ACTION_SHOW), 2000);
                }
            });

            XposedBridge.log("ScreenSearch Hook: SurfaceControl hooked OK");
        } catch (Throwable t) {
            XposedBridge.log("ScreenSearch Hook: SurfaceControl not found: " + t.getMessage());
        }
    }

    private void sendToApp(String action) {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApp = atClass.getMethod("currentApplication");
            Context context = (Context) currentApp.invoke(null);

            if (context != null) {
                Intent intent = new Intent(action);
                intent.setPackage(TARGET_PKG);
                context.sendBroadcast(intent);
                XposedBridge.log("ScreenSearch Hook: Sent " + action);
            }
        } catch (Throwable t) {
            XposedBridge.log("ScreenSearch Hook: send failed: " + t.getMessage());
        }
    }
}
