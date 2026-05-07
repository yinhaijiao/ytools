package com.example.codespaceandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;

public class ImageViewerActivity extends Activity {
    private final ArrayList<String> imagePaths = new ArrayList<>();
    private ZoomImageView imageView;
    private TextView counter;
    private TextView fileNameView;
    private int currentIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<String> paths = getIntent().getStringArrayListExtra(
                ConvertedFilesActivity.EXTRA_IMAGE_PATHS
        );
        if (paths != null) {
            imagePaths.addAll(paths);
        }
        currentIndex = getIntent().getIntExtra(ConvertedFilesActivity.EXTRA_IMAGE_INDEX, 0);
        if (currentIndex < 0) {
            currentIndex = 0;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        imageView = new ZoomImageView(this);
        imageView.setNavigationListener(new ZoomImageView.NavigationListener() {
            @Override
            public void onPrevious() {
                showImage(currentIndex - 1);
            }

            @Override
            public void onNext() {
                showImage(currentIndex + 1);
            }
        });
        root.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setPadding(18, getStatusBarHeight() + 12, 18, 12);

        fileNameView = new TextView(this);
        fileNameView.setTextColor(Color.rgb(30, 30, 30));
        fileNameView.setTextSize(16);
        fileNameView.setSingleLine(true);
        fileNameView.setOnLongClickListener(v -> {
            showCurrentFileActions();
            return true;
        });
        topBar.addView(fileNameView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        counter = new TextView(this);
        counter.setTextColor(Color.rgb(30, 30, 30));
        counter.setTextSize(13);
        topBar.addView(counter, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(topBar);

        setContentView(root);
        showImage(currentIndex);
    }

    private void showImage(int index) {
        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "没有可浏览的图片", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (index < 0 || index >= imagePaths.size()) {
            return;
        }
        currentIndex = index;
        imageView.setImagePath(imagePaths.get(currentIndex));
        fileNameView.setText(new File(imagePaths.get(currentIndex)).getName());
        counter.setText((currentIndex + 1) + " / " + imagePaths.size());
    }

    private void showCurrentFileActions() {
        if (currentIndex < 0 || currentIndex >= imagePaths.size()) {
            return;
        }
        String[] actions = {"重命名", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(new File(imagePaths.get(currentIndex)).getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        renameCurrentFile();
                    } else if (which == 1) {
                        confirmDeleteCurrentFile();
                    }
                })
                .show();
    }

    private void renameCurrentFile() {
        if (currentIndex < 0 || currentIndex >= imagePaths.size()) {
            return;
        }

        File currentFile = new File(imagePaths.get(currentIndex));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(currentFile.getName());
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
                    File parent = currentFile.getParentFile();
                    if (parent == null) {
                        Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File target = new File(parent, newName);
                    if (target.exists()) {
                        Toast.makeText(this, "文件名已存在", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (currentFile.renameTo(target)) {
                        imagePaths.set(currentIndex, target.getAbsolutePath());
                        showImage(currentIndex);
                        Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void confirmDeleteCurrentFile() {
        if (currentIndex < 0 || currentIndex >= imagePaths.size()) {
            return;
        }

        File currentFile = new File(imagePaths.get(currentIndex));
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage(currentFile.getName())
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    if (currentFile.delete()) {
                        imagePaths.remove(currentIndex);
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                        if (imagePaths.isEmpty()) {
                            finish();
                            return;
                        }
                        if (currentIndex >= imagePaths.size()) {
                            currentIndex = imagePaths.size() - 1;
                        }
                        showImage(currentIndex);
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public static final class ZoomImageView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private Bitmap bitmap;
        private NavigationListener navigationListener;
        private float baseScale = 1f;
        private float scale = 1f;
        private float translateX;
        private float translateY;

        public ZoomImageView(Activity activity) {
            super(activity);
            setBackgroundColor(Color.WHITE);
            scaleDetector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scale *= detector.getScaleFactor();
                    if (scale < 1f) {
                        scale = 1f;
                    } else if (scale > 5f) {
                        scale = 5f;
                    }
                    constrainTranslation();
                    invalidate();
                    return true;
                }
            });
            gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent event) {
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent event) {
                    if (scale > 1.05f) {
                        scale = 1f;
                        translateX = 0f;
                        translateY = 0f;
                    } else {
                        scale = 2.5f;
                        translateX = getWidth() / 2f - event.getX();
                        translateY = getHeight() / 2f - event.getY();
                        constrainTranslation();
                    }
                    invalidate();
                    return true;
                }

                @Override
                public boolean onScroll(
                        MotionEvent first,
                        MotionEvent second,
                        float distanceX,
                        float distanceY
                ) {
                    if (scale > 1f) {
                        translateX -= distanceX;
                        translateY -= distanceY;
                        constrainTranslation();
                        invalidate();
                    }
                    return true;
                }

                @Override
                public boolean onFling(
                        MotionEvent first,
                        MotionEvent second,
                        float velocityX,
                        float velocityY
                ) {
                    if (scale > 1.05f || first == null || second == null) {
                        return false;
                    }
                    float deltaX = second.getX() - first.getX();
                    if (Math.abs(deltaX) > 120 && Math.abs(velocityX) > 350) {
                        if (deltaX > 0 && navigationListener != null) {
                            navigationListener.onPrevious();
                        } else if (deltaX < 0 && navigationListener != null) {
                            navigationListener.onNext();
                        }
                        return true;
                    }
                    return false;
                }
            });
        }

        public void setNavigationListener(NavigationListener navigationListener) {
            this.navigationListener = navigationListener;
        }

        public void setImagePath(String path) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            bitmap = decodeBitmap(path);
            scale = 1f;
            translateX = 0f;
            translateY = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.WHITE);
            if (bitmap == null) {
                return;
            }

            float imageWidth = bitmap.getWidth();
            float imageHeight = bitmap.getHeight();
            baseScale = Math.min(getWidth() / imageWidth, getHeight() / imageHeight);
            float drawScale = baseScale * scale;
            float left = (getWidth() - imageWidth * drawScale) / 2f + translateX;
            float top = (getHeight() - imageHeight * drawScale) / 2f + translateY;

            matrix.reset();
            matrix.postScale(drawScale, drawScale);
            matrix.postTranslate(left, top);
            canvas.drawBitmap(bitmap, matrix, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                constrainTranslation();
                invalidate();
            }
            return true;
        }

        private Bitmap decodeBitmap(String path) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);

            int sampleSize = 1;
            int maxSize = 2400;
            while ((bounds.outWidth / sampleSize) > maxSize
                    || (bounds.outHeight / sampleSize) > maxSize) {
                sampleSize *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, options);
        }

        private void constrainTranslation() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }
            float drawWidth = bitmap.getWidth() * baseScale * scale;
            float drawHeight = bitmap.getHeight() * baseScale * scale;
            float maxX = Math.max(0, (drawWidth - getWidth()) / 2f);
            float maxY = Math.max(0, (drawHeight - getHeight()) / 2f);

            if (translateX > maxX) {
                translateX = maxX;
            } else if (translateX < -maxX) {
                translateX = -maxX;
            }

            if (translateY > maxY) {
                translateY = maxY;
            } else if (translateY < -maxY) {
                translateY = -maxY;
            }
        }

        public interface NavigationListener {
            void onPrevious();

            void onNext();
        }
    }
}
