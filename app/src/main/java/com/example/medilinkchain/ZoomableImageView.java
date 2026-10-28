package com.example.medilinkchain;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    private Matrix matrix;
    private int mode = NONE;

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;

    private PointF last = new PointF();
    private PointF start = new PointF();
    private float minScale = 1f;
    private float maxScale = 5f;
    private float[] m;

    private int viewWidth, viewHeight;
    private static final int CLICK = 3;
    private float saveScale = 1f;
    private float origWidth, origHeight;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    public ZoomableImageView(@NonNull Context context) {
        super(context);
        sharedConstructing(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        sharedConstructing(context);
    }

    private void sharedConstructing(Context context) {
        super.setClickable(true);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(context, new GestureListener());
        matrix = new Matrix();
        m = new float[9];
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (saveScale > minScale) {
                resetZoom();
            } else {
                zoomTo(2.5f, e.getX(), e.getY());
            }
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= mScaleFactor;
            if (saveScale > maxScale) {
                saveScale = maxScale;
                mScaleFactor = maxScale / origScale;
            } else if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }

            if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
                matrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f);
            } else {
                matrix.postScale(mScaleFactor, mScaleFactor, detector.getFocusX(), detector.getFocusY());
            }

            fixTrans();
            return true;
        }
    }

    public void zoomIn() {
        float targetScale = Math.min(saveScale * 1.35f, maxScale);
        zoomTo(targetScale, viewWidth / 2f, viewHeight / 2f);
    }

    public void zoomOut() {
        float targetScale = Math.max(saveScale / 1.35f, minScale);
        zoomTo(targetScale, viewWidth / 2f, viewHeight / 2f);
    }

    public void resetZoom() {
        saveScale = 1f;
        fitImageToView();
    }

    private void zoomTo(float targetScale, float focusX, float focusY) {
        float scaleFactor = targetScale / saveScale;
        saveScale = targetScale;
        matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
        fixTrans();
        setImageMatrix(matrix);
        invalidate();
    }

    private void fixTrans() {
        matrix.getValues(m);
        float transX = m[Matrix.MTRANS_X];
        float transY = m[Matrix.MTRANS_Y];

        float fixTransX = getFixTrans(transX, viewWidth, origWidth * saveScale);
        float fixTransY = getFixTrans(transY, viewHeight, origHeight * saveScale);

        if (fixTransX != 0 || fixTransY != 0) {
            matrix.postTranslate(fixTransX, fixTransY);
        }
    }

    private float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans)
            return -trans + minTrans;
        if (trans > maxTrans)
            return -trans + maxTrans;
        return 0;
    }

    private float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (origWidth == 0 || origHeight == 0) {
            fitImageToView();
        }
    }

    public void fitImageToView() {
        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() == 0 || drawable.getIntrinsicHeight() == 0)
            return;
        if (viewWidth == 0 || viewHeight == 0)
            return;

        int bmWidth = drawable.getIntrinsicWidth();
        int bmHeight = drawable.getIntrinsicHeight();

        float scaleX = (float) viewWidth / (float) bmWidth;
        float scaleY = (float) viewHeight / (float) bmHeight;
        float scale = Math.min(scaleX, scaleY);

        matrix.setScale(scale, scale);

        float redundantYSpace = (float) viewHeight - (scale * (float) bmHeight);
        float redundantXSpace = (float) viewWidth - (scale * (float) bmWidth);
        redundantYSpace /= 2;
        redundantXSpace /= 2;

        matrix.postTranslate(redundantXSpace, redundantYSpace);

        origWidth = viewWidth - 2 * redundantXSpace;
        origHeight = viewHeight - 2 * redundantYSpace;
        setImageMatrix(matrix);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);

        PointF curr = new PointF(event.getX(), event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                last.set(curr);
                start.set(last);
                mode = DRAG;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float deltaX = curr.x - last.x;
                    float deltaY = curr.y - last.y;
                    float fixTransX = getFixDragTrans(deltaX, viewWidth, origWidth * saveScale);
                    float fixTransY = getFixDragTrans(deltaY, viewHeight, origHeight * saveScale);
                    matrix.postTranslate(fixTransX, fixTransY);
                    fixTrans();
                    last.set(curr.x, curr.y);
                }
                break;

            case MotionEvent.ACTION_UP:
                mode = NONE;
                int xDiff = (int) Math.abs(curr.x - start.x);
                int yDiff = (int) Math.abs(curr.y - start.y);
                if (xDiff < CLICK && yDiff < CLICK)
                    performClick();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }

        setImageMatrix(matrix);
        invalidate();
        return true;
    }
}
