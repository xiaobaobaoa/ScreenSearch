package com.screensearch.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EditText etApiUrl = findViewById(R.id.et_api_url);
        EditText etApiKey = findViewById(R.id.et_api_key);
        EditText etModel = findViewById(R.id.et_model);
        Button btnSave = findViewById(R.id.btn_save);

        AISearchService aiService = new AISearchService(this);

        etApiUrl.setText(aiService.getApiUrl());
        etApiKey.setText(aiService.getApiKey());
        etModel.setText(aiService.getModel());

        btnSave.setOnClickListener(v -> {
            String url = etApiUrl.getText().toString().trim();
            String key = etApiKey.getText().toString().trim();
            String model = etModel.getText().toString().trim();

            if (key.isEmpty()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
                return;
            }

            aiService.saveConfig(url, key, model);
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
