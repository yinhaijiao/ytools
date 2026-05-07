package com.example.codespaceandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ConvertedFilesActivity extends Activity {
    public static final String EXTRA_IMAGE_PATHS = "image_paths";
    public static final String EXTRA_IMAGE_INDEX = "image_index";

    private final List<ConvertedFile> convertedFiles = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final ArrayList<String> imagePaths = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Button tab012;
    private Button tab013;
    private String activeFolder = "012";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        TextView title = new TextView(this);
        title.setText("转换后的文件");
        title.setTextColor(Color.rgb(30, 30, 30));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(28, getStatusBarHeight() + 20, 28, 20);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(20, 0, 20, 12);
        tab012 = createTabButton("012");
        tab013 = createTabButton("013");
        tabs.addView(tab012, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabs.addView(tab013, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ListView listView = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> openFile(position));
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showFileActions(position);
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        loadConvertedFiles();
    }

    private Button createTabButton(String folderName) {
        Button button = new Button(this);
        button.setText(folderName);
        button.setAllCaps(false);
        button.setOnClickListener(v -> {
            activeFolder = folderName;
            loadConvertedFiles();
        });
        return button;
    }

    private void loadConvertedFiles() {
        convertedFiles.clear();
        imagePaths.clear();
        scanFolder(activeFolder);
        Collections.sort(convertedFiles, new Comparator<ConvertedFile>() {
            @Override
            public int compare(ConvertedFile first, ConvertedFile second) {
                return Long.compare(second.file.lastModified(), first.file.lastModified());
            }
        });

        labels.clear();
        for (ConvertedFile convertedFile : convertedFiles) {
            labels.add(convertedFile.file.getName());
            if (convertedFile.isImage) {
                imagePaths.add(convertedFile.file.getAbsolutePath());
            }
        }
        if (labels.isEmpty()) {
            labels.add(activeFolder + " 中没有找到转换后的文件");
        }
        updateTabs();
        adapter.notifyDataSetChanged();
    }

    private void updateTabs() {
        styleTab(tab012, "012".equals(activeFolder));
        styleTab(tab013, "013".equals(activeFolder));
    }

    private void styleTab(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : Color.rgb(30, 30, 30));
        button.setBackgroundColor(selected ? Color.rgb(20, 95, 210) : Color.rgb(230, 230, 230));
    }

    private void scanFolder(String folderName) {
        File folder = new File(Environment.getExternalStorageDirectory(), folderName);
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith("1")) {
                continue;
            }
            convertedFiles.add(new ConvertedFile(folderName, file, isImageFile(file.getName())));
        }
    }

    private void openFile(int position) {
        if (position < 0 || position >= convertedFiles.size()) {
            return;
        }

        ConvertedFile convertedFile = convertedFiles.get(position);
        if (!convertedFile.isImage) {
            Toast.makeText(this, "当前只支持在 App 内浏览图片", Toast.LENGTH_SHORT).show();
            return;
        }

        int imageIndex = imagePaths.indexOf(convertedFile.file.getAbsolutePath());
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putStringArrayListExtra(EXTRA_IMAGE_PATHS, imagePaths);
        intent.putExtra(EXTRA_IMAGE_INDEX, imageIndex);
        startActivity(intent);
    }

    private void showFileActions(int position) {
        if (position < 0 || position >= convertedFiles.size()) {
            return;
        }
        String[] actions = {"重命名", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(convertedFiles.get(position).file.getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        renameFile(position);
                    } else if (which == 1) {
                        confirmDeleteFile(position);
                    }
                })
                .show();
    }

    private void renameFile(int position) {
        if (position < 0 || position >= convertedFiles.size()) {
            return;
        }

        ConvertedFile convertedFile = convertedFiles.get(position);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(convertedFile.file.getName());
        input.setSelectAllOnFocus(false);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("修改文件名")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File target = new File(convertedFile.file.getParentFile(), newName);
                    if (target.exists()) {
                        Toast.makeText(this, "文件名已存在", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (convertedFile.file.renameTo(target)) {
                        Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show();
                        loadConvertedFiles();
                    } else {
                        Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void confirmDeleteFile(int position) {
        if (position < 0 || position >= convertedFiles.size()) {
            return;
        }

        ConvertedFile convertedFile = convertedFiles.get(position);
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage(convertedFile.file.getName())
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (convertedFile.file.delete()) {
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        loadConvertedFiles();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg1")
                || lower.endsWith(".jpeg1")
                || lower.endsWith(".png1")
                || lower.endsWith(".webp1")
                || lower.endsWith(".bmp1")
                || lower.endsWith(".gif1")
                || lower.endsWith(".heic1")
                || lower.endsWith(".heif1");
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private static final class ConvertedFile {
        final String folder;
        final File file;
        final boolean isImage;

        ConvertedFile(String folder, File file, boolean isImage) {
            this.folder = folder;
            this.file = file;
            this.isImage = isImage;
        }
    }
}
