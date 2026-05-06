package com.example.codespaceandroid;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

public class ToolsActivity extends Activity {
    private static final int REQUEST_WRITE_STORAGE = 1002;
    private String pendingDestinationFolder = "012";

    private static final String[] FEATURE_BUTTONS = {
        "工具1",
        "工具2",
        "文件工具",
        "设置"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout buttonList = new LinearLayout(this);
        buttonList.setGravity(Gravity.CENTER);
        buttonList.setOrientation(LinearLayout.VERTICAL);
        buttonList.setPadding(48, 48, 48, 48);

        for (String label : FEATURE_BUTTONS) {
            Button button = new Button(this);
            button.setText(label);
            button.setAllCaps(false);
            button.setTextSize(18);
            if ("工具1".equals(label)) {
                button.setOnClickListener(v -> openMediaTool("012"));
            } else if ("工具2".equals(label)) {
                button.setOnClickListener(v -> openMediaTool("013"));
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 12, 0, 12);
            buttonList.addView(button, params);
        }

        scrollView.addView(buttonList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(scrollView);
    }

    private void openMediaTool(String destinationFolder) {
        pendingDestinationFolder = destinationFolder;
        if (!hasRootStorageAccess()) {
            requestRootStorageAccess();
            return;
        }
        openMediaPicker(destinationFolder);
    }

    private boolean hasRootStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestRootStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请允许管理所有文件后再次点击工具按钮", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE
            );
        }
    }

    private void openMediaPicker(String destinationFolder) {
        Intent intent = new Intent(this, MediaPickerActivity.class);
        intent.putExtra(MediaPickerActivity.EXTRA_DESTINATION_FOLDER, destinationFolder);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openMediaPicker(pendingDestinationFolder);
        }
    }
}
