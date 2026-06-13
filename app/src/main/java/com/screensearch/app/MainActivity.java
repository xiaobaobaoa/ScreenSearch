package com.screensearch.app;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> mediaProjectionLauncher;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private boolean waitingForOverlay = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    waitingForOverlay = false;
                    if (Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show();
                        requestScreenCapture();
                    } else {
                        Toast.makeText(this, "悬浮窗权限未授予，无法使用", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
        );

        mediaProjectionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Toast.makeText(this, "正在启动悬浮窗...", Toast.LENGTH_SHORT).show();
                        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
                        serviceIntent.putExtra("code", result.getResultCode());
                        serviceIntent.putExtra("data", result.getData());
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent);
                        } else {
                            startService(serviceIntent);
                        }
                    } else {
                        Toast.makeText(this, "屏幕录制授权失败", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        checkAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForOverlay && Settings.canDrawOverlays(this)) {
            waitingForOverlay = false;
            requestScreenCapture();
        }
    }

    private void checkAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlay = true;
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            requestScreenCapture();
        }
    }

    private void requestScreenCapture() {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent());
    }
}