package com.screensearch.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class FloatActivity extends Activity {

    private WindowManager windowManager;
    private View floatingView;
    private View resultPanel;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight;
    private int densityDpi;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private OCRProcessor ocrProcessor;
    private AISearchService aiSearchService;
    private float initialX, initialY, initialTouchX, initialTouchY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置 FLAG_SECURE
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ocrProcessor = new OCRProcessor();
        aiSearchService = new AISearchService(this);

        backgroundThread = new HandlerThread("background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        showFloatingWidget();

        int code = getIntent().getIntExtra("code", -1);
        Intent data = getIntent().getParcelableExtra("data");
        if (code != -1 && data != null) {
            startProjection(code, data);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void startProjection(int code, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(code, data);
        if (mediaProjection == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection.registerCallback(new MediaProjection.Callback() {}, backgroundHandler);
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        densityDpi = metrics.densityDpi;

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, android.graphics.PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenSearch",
                screenWidth, screenHeight, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private void showFloatingWidget() {
        floatingView = getLayoutInflater().inflate(R.layout.floating_window, null);

        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowManager.addView(floatingView, params);

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

            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(bufferWidth, screenHeight, android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            image.close();

            int copyWidth = Math.min(screenWidth, bufferWidth);
            android.graphics.Bitmap cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, copyWidth, screenHeight);
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

        resultPanel = getLayoutInflater().inflate(R.layout.result_panel, null);
        TextView resultText = resultPanel.findViewById(R.id.result_text);
        Button searchBtn = resultPanel.findViewById(R.id.btn_search);

        resultText.setText("识别结果:\n" + text);

        searchBtn.setOnClickListener(v -> {
            searchBtn.setEnabled(false);
            searchBtn.setText("搜索中...");
            aiSearchService.search(text, new AISearchService.Callback() {
                @Override
                public void onResult(String answer) {
                    new Handler(getMainLooper()).post(() -> {
                        resultText.setText("识别结果:\n" + text + "\n\n---\nAI回答:\n" + answer);
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
    }

    @Override
    public void onDestroy() {
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
        if (floatingView != null) windowManager.removeView(floatingView);
        if (resultPanel != null) windowManager.removeView(resultPanel);
        if (backgroundThread != null) backgroundThread.quitSafely();
        if (ocrProcessor != null) ocrProcessor.close();
        super.onDestroy();
    }
}
