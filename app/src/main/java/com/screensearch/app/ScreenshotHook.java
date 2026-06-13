package com.screensearch.app;

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
    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.systemui")) return;

        XposedBridge.log("ScreenSearch: Hooking SystemUI for screenshot detection");

        try {
            hookScreenshotHelper(lpparam);
        } catch (Throwable t) {
            XposedBridge.log("ScreenSearch: ScreenshotHelper hook failed: " + t.getMessage());
            try {
                hookSurfaceControl(lpparam);
            } catch (Throwable t2) {
                XposedBridge.log("ScreenSearch: SurfaceControl hook failed: " + t2.getMessage());
            }
        }
    }

    private void hookScreenshotHelper(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> screenshotHelperClass = XposedHelpers.findClass(
                "com.android.systemui.screenshot.ScreenshotHelper", lpparam.classLoader);

        XposedBridge.hookAllMethods(screenshotHelperClass, "takeScreenshot", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                sendBroadcast(param.thisObject, ACTION_HIDE);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mainHandler.postDelayed(() -> sendBroadcast(param.thisObject, ACTION_SHOW), 2000);
            }
        });

        XposedBridge.hookAllMethods(screenshotHelperClass, "takeScreenshotForSmartActions", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                sendBroadcast(param.thisObject, ACTION_HIDE);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mainHandler.postDelayed(() -> sendBroadcast(param.thisObject, ACTION_SHOW), 2000);
            }
        });

        XposedBridge.log("ScreenSearch: ScreenshotHelper hooked successfully");
    }

    private void hookSurfaceControl(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> surfaceControlClass = XposedHelpers.findClass(
                "android.view.SurfaceControl", lpparam.classLoader);

        XposedBridge.hookAllMethods(surfaceControlClass, "screenshot", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object[] args = param.args;
                if (args != null && args.length > 0) {
                    sendBroadcast(param.thisObject, ACTION_HIDE);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mainHandler.postDelayed(() -> sendBroadcast(param.thisObject, ACTION_SHOW), 2000);
            }
        });

        XposedBridge.hookAllMethods(surfaceControlClass, "screenshotToBuffer", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                sendBroadcast(param.thisObject, ACTION_HIDE);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mainHandler.postDelayed(() -> sendBroadcast(param.thisObject, ACTION_SHOW), 2000);
            }
        });

        XposedBridge.log("ScreenSearch: SurfaceControl hooked successfully");
    }

    private void sendBroadcast(Object caller, String action) {
        try {
            Context context = null;
            if (caller instanceof Context) {
                context = (Context) caller;
            } else if (caller != null) {
                // Try to get context from the object
                try {
                    java.lang.reflect.Field contextField = caller.getClass().getDeclaredField("mContext");
                    contextField.setAccessible(true);
                    context = (Context) contextField.get(caller);
                } catch (Exception ignored) {}
            }

            if (context != null) {
                Intent intent = new Intent(action);
                intent.setPackage("com.screensearch.app");
                context.sendBroadcast(intent);
                XposedBridge.log("ScreenSearch: Sent broadcast " + action);
            }
        } catch (Throwable t) {
            XposedBridge.log("ScreenSearch: Send broadcast failed: " + t.getMessage());
        }
    }
}
