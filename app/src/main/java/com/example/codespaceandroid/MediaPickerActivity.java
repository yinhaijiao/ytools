package com.example.codespaceandroid;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPickerActivity extends Activity {
    public static final String EXTRA_DESTINATION_FOLDER = "destination_folder";
    private static final int REQUEST_READ_MEDIA = 2001;

    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final LruCache<String, Bitmap> thumbnailCache =
            new LruCache<String, Bitmap>(24 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(3);
    private MediaAdapter adapter;
    private GridView mediaGrid;
    private String destinationFolder = "012";
    private int thumbnailSizePx;
    private boolean isFastScrolling;
    private boolean isDragSelecting;
    private boolean dragSelectState;
    private int lastDragPosition = GridView.INVALID_POSITION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        destinationFolder = getIntent().getStringExtra(EXTRA_DESTINATION_FOLDER);
        if (destinationFolder == null || destinationFolder.trim().isEmpty()) {
            destinationFolder = "012";
        }
        thumbnailSizePx = dp(96);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(28, getStatusBarHeight() + 18, 20, 18);

        TextView title = new TextView(this);
        title.setText("选择图片和视频");
        title.setTextColor(Color.rgb(30, 30, 30));
        title.setTextSize(20);
        topBar.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        Button doneButton = new Button(this);
        doneButton.setText("done");
        doneButton.setAllCaps(false);
        doneButton.setOnClickListener(v -> moveCheckedItems());
        topBar.addView(doneButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        mediaGrid = new GridView(this);
        mediaGrid.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE);
        mediaGrid.setNumColumns(5);
        mediaGrid.setHorizontalSpacing(dp(3));
        mediaGrid.setVerticalSpacing(dp(3));
        mediaGrid.setPadding(dp(3), dp(3), dp(3), dp(3));
        mediaGrid.setClipToPadding(false);
        mediaGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        mediaGrid.setOnItemClickListener((parent, view, position, id) -> {
            boolean nextChecked = !mediaGrid.isItemChecked(position);
            mediaGrid.setItemChecked(position, nextChecked);
            adapter.notifyDataSetChanged();
        });
        mediaGrid.setOnTouchListener((view, event) -> handleGridTouch(event));
        mediaGrid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                isFastScrolling = scrollState == SCROLL_STATE_FLING;
                if (scrollState == SCROLL_STATE_IDLE) {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onScroll(
                    AbsListView view,
                    int firstVisibleItem,
                    int visibleItemCount,
                    int totalItemCount
            ) {
            }
        });
        adapter = new MediaAdapter();
        mediaGrid.setAdapter(adapter);
        root.addView(mediaGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);

        if (hasReadMediaPermission()) {
            loadMedia();
        } else {
            requestReadMediaPermission();
        }
    }

    private boolean hasReadMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestReadMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                    },
                    REQUEST_READ_MEDIA
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_MEDIA
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_MEDIA && hasReadMediaPermission()) {
            loadMedia();
        } else if (requestCode == REQUEST_READ_MEDIA) {
            Toast.makeText(this, "需要媒体读取权限", Toast.LENGTH_LONG).show();
        }
    }

    private void loadMedia() {
        mediaItems.clear();
        adapter.notifyDataSetChanged();
        new LoadMediaTask().execute();
    }

    private final class LoadMediaTask extends AsyncTask<Void, Void, List<MediaItem>> {
        @Override
        protected List<MediaItem> doInBackground(Void... params) {
            List<MediaItem> items = new ArrayList<>();
            queryMediaStore(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                "图片",
                false,
                items
            );
            queryMediaStore(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                "视频",
                true,
                items
            );
            sortByDateAdded(items);
            return items;
        }

        @Override
        protected void onPostExecute(List<MediaItem> items) {
            mediaItems.clear();
            mediaItems.addAll(items);
            if (mediaItems.isEmpty()) {
                Toast.makeText(MediaPickerActivity.this, "没有找到图片或视频", Toast.LENGTH_LONG).show();
            }
            mediaGrid.clearChoices();
            adapter.notifyDataSetChanged();
        }
    }

    private void queryMediaStore(
            Uri collection,
            String idColumn,
            String nameColumn,
            String dateColumn,
            String typeLabel,
            boolean isVideo,
            List<MediaItem> target
    ) {
        String[] projection = {idColumn, nameColumn, dateColumn};
        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                null,
                null,
                dateColumn + " DESC"
        )) {
            if (cursor == null) {
                return;
            }

            int idIndex = cursor.getColumnIndexOrThrow(idColumn);
            int nameIndex = cursor.getColumnIndexOrThrow(nameColumn);
            int dateIndex = cursor.getColumnIndexOrThrow(dateColumn);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String displayName = cursor.getString(nameIndex);
                long dateAdded = cursor.getLong(dateIndex);
                Uri uri = ContentUris.withAppendedId(collection, id);
                target.add(new MediaItem(uri, id, displayName, dateAdded, typeLabel, isVideo));
            }
        } catch (Exception ignored) {
        }
    }

    private void sortByDateAdded(List<MediaItem> items) {
        Collections.sort(items, new Comparator<MediaItem>() {
            @Override
            public int compare(MediaItem first, MediaItem second) {
                return Long.compare(second.dateAdded, first.dateAdded);
            }
        });
    }

    private void moveCheckedItems() {
        SparseBooleanArray checked = mediaGrid.getCheckedItemPositions();
        List<MediaItem> selected = new ArrayList<>();
        for (int index = 0; index < mediaItems.size(); index++) {
            if (checked.get(index)) {
                selected.add(mediaItems.get(index));
            }
        }

        if (selected.isEmpty()) {
            Toast.makeText(this, "请选择图片或视频", Toast.LENGTH_SHORT).show();
            return;
        }

        MoveResult result = moveSelectedMedia(selected, destinationFolder);
        Toast.makeText(
                this,
                "完成: " + result.movedCount + "，失败: " + result.failedCount,
                Toast.LENGTH_LONG
        ).show();
        loadMedia();
    }

    private MoveResult moveSelectedMedia(List<MediaItem> selectedItems, String destinationFolder) {
        MoveResult result = new MoveResult();
        File destinationDir = new File(Environment.getExternalStorageDirectory(), destinationFolder);
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            result.failedCount = selectedItems.size();
            return result;
        }

        for (MediaItem item : selectedItems) {
            if (moveSingleMedia(item, destinationDir)) {
                result.movedCount++;
            } else {
                result.failedCount++;
            }
        }
        return result;
    }

    private boolean moveSingleMedia(MediaItem item, File destinationDir) {
        String displayName = item.displayName;
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "media_" + System.currentTimeMillis();
        }

        File destination = new File(destinationDir, displayName + "1");
        if (destination.exists()) {
            return false;
        }

        try (
                InputStream input = getContentResolver().openInputStream(item.uri);
                FileOutputStream output = new FileOutputStream(destination)
        ) {
            if (input == null) {
                return false;
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();

            if (!deleteOriginal(item.uri)) {
                if (destination.exists()) {
                    destination.delete();
                }
                return false;
            }
            return true;
        } catch (Exception exception) {
            if (destination.exists()) {
                destination.delete();
            }
            return false;
        }
    }

    private boolean deleteOriginal(Uri uri) {
        try {
            ContentResolver resolver = getContentResolver();
            return resolver.delete(uri, null, null) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean handleGridTouch(MotionEvent event) {
        int position = mediaGrid.pointToPosition((int) event.getX(), (int) event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isDragSelecting = position != GridView.INVALID_POSITION;
                lastDragPosition = position;
                if (isDragSelecting) {
                    dragSelectState = !mediaGrid.isItemChecked(position);
                    mediaGrid.setItemChecked(position, dragSelectState);
                    adapter.notifyDataSetChanged();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!isDragSelecting) {
                    return false;
                }
                if (position != GridView.INVALID_POSITION && position != lastDragPosition) {
                    mediaGrid.setItemChecked(position, dragSelectState);
                    lastDragPosition = position;
                    adapter.notifyDataSetChanged();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragSelecting) {
                    isDragSelecting = false;
                    lastDragPosition = GridView.INVALID_POSITION;
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    protected void onDestroy() {
        thumbnailExecutor.shutdownNow();
        super.onDestroy();
    }

    private final class MediaAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mediaItems.size();
        }

        @Override
        public MediaItem getItem(int position) {
            return mediaItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowViews row;
            if (convertView == null) {
                row = createRow();
                convertView = row.root;
                convertView.setTag(row);
            } else {
                row = (RowViews) convertView.getTag();
            }

            MediaItem item = getItem(position);
            bindThumbnail(row.thumbnail, item);
            row.videoBadge.setVisibility("视频".equals(item.typeLabel) ? View.VISIBLE : View.GONE);
            boolean checked = mediaGrid.isItemChecked(position);
            row.selectionOverlay.setVisibility(checked ? View.VISIBLE : View.GONE);
            row.checkMark.setVisibility(checked ? View.VISIBLE : View.GONE);

            return convertView;
        }

        private RowViews createRow() {
            FrameLayout root = new FrameLayout(MediaPickerActivity.this);
            root.setBackgroundColor(Color.rgb(230, 230, 230));
            root.setLayoutParams(new GridView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(76)
            ));

            ImageView thumbnail = new ImageView(MediaPickerActivity.this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackgroundColor(Color.rgb(230, 230, 230));
            root.addView(thumbnail, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            TextView videoBadge = new TextView(MediaPickerActivity.this);
            videoBadge.setText("视频");
            videoBadge.setTextColor(Color.WHITE);
            videoBadge.setTextSize(12);
            videoBadge.setGravity(Gravity.CENTER);
            videoBadge.setBackgroundColor(Color.argb(170, 0, 0, 0));
            videoBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            badgeParams.gravity = Gravity.START | Gravity.BOTTOM;
            badgeParams.setMargins(dp(8), 0, 0, dp(8));
            root.addView(videoBadge, badgeParams);

            View selectionOverlay = new View(MediaPickerActivity.this);
            selectionOverlay.setBackgroundColor(Color.argb(115, 0, 0, 0));
            root.addView(selectionOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            TextView checkMark = new TextView(MediaPickerActivity.this);
            checkMark.setText("✓");
            checkMark.setTextColor(Color.WHITE);
            checkMark.setTextSize(24);
            checkMark.setGravity(Gravity.CENTER);
            checkMark.setBackgroundColor(Color.rgb(20, 95, 210));
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                    dp(34),
                    dp(34)
            );
            checkParams.gravity = Gravity.END | Gravity.TOP;
            checkParams.setMargins(0, dp(5), dp(5), 0);
            root.addView(checkMark, checkParams);

            return new RowViews(root, thumbnail, videoBadge, selectionOverlay, checkMark);
        }

        private void bindThumbnail(ImageView imageView, MediaItem item) {
            String cacheKey = item.uri.toString();
            imageView.setTag(cacheKey);

            Bitmap cached = thumbnailCache.get(cacheKey);
            if (cached != null) {
                imageView.setImageBitmap(cached);
                return;
            }

            imageView.setImageDrawable(null);
            imageView.setBackgroundColor(Color.rgb(230, 230, 230));
            if (isFastScrolling) {
                return;
            }
            thumbnailExecutor.execute(() -> {
                Bitmap thumbnail = loadThumbnail(item);
                if (thumbnail == null) {
                    return;
                }
                thumbnailCache.put(cacheKey, thumbnail);
                runOnUiThread(() -> {
                    Object currentTag = imageView.getTag();
                    if (cacheKey.equals(currentTag)) {
                        imageView.setImageBitmap(thumbnail);
                    }
                });
            });
        }
    }

    private Bitmap loadThumbnail(MediaItem item) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getContentResolver().loadThumbnail(
                        item.uri,
                        new Size(thumbnailSizePx, thumbnailSizePx),
                        null
                );
            }
            if (item.isVideo) {
                return MediaStore.Video.Thumbnails.getThumbnail(
                        getContentResolver(),
                        item.id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                );
            }
            return MediaStore.Images.Thumbnails.getThumbnail(
                    getContentResolver(),
                    item.id,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class RowViews {
        final FrameLayout root;
        final ImageView thumbnail;
        final TextView videoBadge;
        final View selectionOverlay;
        final TextView checkMark;

        RowViews(
                FrameLayout root,
                ImageView thumbnail,
                TextView videoBadge,
                View selectionOverlay,
                TextView checkMark
        ) {
            this.root = root;
            this.thumbnail = thumbnail;
            this.videoBadge = videoBadge;
            this.selectionOverlay = selectionOverlay;
            this.checkMark = checkMark;
        }
    }

    private static final class MediaItem {
        final Uri uri;
        final long id;
        final String displayName;
        final long dateAdded;
        final String typeLabel;
        final boolean isVideo;

        MediaItem(
                Uri uri,
                long id,
                String displayName,
                long dateAdded,
                String typeLabel,
                boolean isVideo
        ) {
            this.uri = uri;
            this.id = id;
            this.displayName = displayName;
            this.dateAdded = dateAdded;
            this.typeLabel = typeLabel;
            this.isVideo = isVideo;
        }
    }

    private static final class MoveResult {
        int movedCount;
        int failedCount;
    }
}
