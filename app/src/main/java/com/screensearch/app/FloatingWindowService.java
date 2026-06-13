package com.screensearch.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class FloatingWindowService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight;
    private int densityDpi;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private OCRProcessor ocrProcessor;
    private AISearchService aiSearchService;
    private View resultPanel;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ocrProcessor = new OCRProcessor();
        aiSearchService = new AISearchService(this);
    }

    @Override
    public void onDestroy() {
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (floatingView != null) windowManager.removeView(floatingView);
        if (resultPanel != null) {
            windowManager.removeView(resultPanel);
            resultPanel = null;
        }
        if (backgroundThread != null) backgroundThread.quitSafely();
        if (ocrProcessor != null) ocrProcessor.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 处理 LSPosed hook 发来的隐藏/显示广播
        if (intent != null && intent.hasExtra("action")) {
            String action = intent.getStringExtra("action");
            if ("hide".equals(action)) {
                if (floatingView != null) floatingView.setVisibility(View.INVISIBLE);
                if (resultPanel != null) resultPanel.setVisibility(View.INVISIBLE);
                return START_STICKY;
            } else if ("show".equals(action)) {
                if (floatingView != null) floatingView.setVisibility(View.VISIBLE);
                if (resultPanel != null) resultPanel.setVisibility(View.VISIBLE);
                return START_STICKY;
            }
        }

        try {
            startForeground(1, createNotification());
        } catch (Exception e) {
            Toast.makeText(this, "通知权限异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        if (intent != null && intent.hasExtra("code") && intent.hasExtra("data")) {
            try {
                int code = intent.getIntExtra("code", -1);
                Intent data = intent.getParcelableExtra("data", Intent.class);
                startProjection(code, data);
            } catch (Exception e) {
                Toast.makeText(this, "屏幕录制初始化失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        try {
            showFloatingWidget();
        } catch (Exception e) {
            Toast.makeText(this, "悬浮窗创建失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        return START_STICKY;
    }

    private void startProjection(int code, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(code, data);

        if (mediaProjection == null) {
            Toast.makeText(this, "屏幕录制授权失败", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection.registerCallback(new MediaProjection.Callback() {}, backgroundHandler);
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        densityDpi = metrics.densityDpi;

        backgroundThread = new HandlerThread("ScreenCapture");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                screenWidth, screenHeight, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private void showFloatingWidget() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        floatingView = inflater.inflate(R.layout.floating_window, null);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_SECURE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        }

        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowManager.addView(floatingView, params);
        SecureWindowHelper.setSecure(floatingView);

        floatingView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = (int) (initialX + event.getRawX() - initialTouchX);
                    params.y = (int) (initialY + event.getRawY() - initialTouchY);
                    windowManager.updateViewLayout(floatingView, params);
                    return true;
            }
            return false;
        });

        floatingView.findViewById(R.id.btn_screen).setOnClickListener(v -> captureAndOCR());
        floatingView.findViewById(R.id.btn_settings).setOnClickListener(v -> {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);
        });
    }

    private float initialX, initialY;
    private float initialTouchX, initialTouchY;

    private void captureAndOCR() {
        if (imageReader == null) return;

        floatingView.setVisibility(View.INVISIBLE);
        new Handler(getMainLooper()).postDelayed(() -> {
            Image image = imageReader.acquireLatestImage();
            if (image == null) {
                floatingView.setVisibility(View.VISIBLE);
                return;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int bufferWidth = rowStride / pixelStride;

            Bitmap bitmap = Bitmap.createBitmap(bufferWidth, screenHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();

            int copyWidth = Math.min(screenWidth, bufferWidth);
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, copyWidth, screenHeight);
            if (cropped != bitmap) bitmap.recycle();

            ocrProcessor.recognizeText(cropped, result -> {
                cropped.recycle();
                floatingView.setVisibility(View.VISIBLE);
                showResultPanel(result);
            });
        }, 300);
    }

    private void showResultPanel(String text) {
        if (resultPanel != null) {
            windowManager.removeView(resultPanel);
            resultPanel = null;
        }

        resultPanel = LayoutInflater.from(this).inflate(R.layout.result_panel, null);
        TextView resultText = resultPanel.findViewById(R.id.result_text);
        resultText.setText(text);

        Button searchBtn = resultPanel.findViewById(R.id.btn_search);
        searchBtn.setOnClickListener(v -> {
            if (!aiSearchService.isConfigured()) {
                Toast.makeText(this, "请先点击右上角齿轮设置 API", Toast.LENGTH_SHORT).show();
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settingsIntent);
                return;
            }
            searchBtn.setEnabled(false);
            searchBtn.setText("搜索中...");
            aiSearchService.search(text, new AISearchService.OnSearchResultListener() {
                @Override
                public void onResult(String answer) {
                    new Handler(getMainLooper()).post(() -> {
                        resultText.setText(answer);
                        searchBtn.setEnabled(true);
                        searchBtn.setText("搜题");
                    });
                }

                @Override
                public void onError(String error) {
                    new Handler(getMainLooper()).post(() -> {
                        resultText.setText("识别结果:\n" + text + "\n\n---\n搜索失败: " + error);
                        searchBtn.setEnabled(true);
                        searchBtn.setText("搜题");
                    });
                }
            });
        });

        Button closeBtn = resultPanel.findViewById(R.id.btn_close);
        closeBtn.setOnClickListener(v -> {
            windowManager.removeView(resultPanel);
            resultPanel = null;
        });

        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) (screenWidth * 0.8),
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;
        windowManager.addView(resultPanel, params);
        SecureWindowHelper.setSecure(resultPanel);
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("floating", "Floating Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, "floating");
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("Screen Search")
                .setContentText("识屏搜题运行中")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build();
    }
}