package com.screensearch.app;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class OCRProcessor {

    private final TextRecognizer recognizer;

    public OCRProcessor() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    public void recognizeText(Bitmap bitmap, OnTextResultListener listener) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    if (listener != null) listener.onResult(visionText.getText());
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onResult("识别失败: " + e.getMessage());
                });
    }

    public void close() {
        recognizer.close();
    }

    public interface OnTextResultListener {
        void onResult(String text);
    }
}