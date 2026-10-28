package com.example.medilinkchain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CustomChartView extends View {

    private Paint barPaint;
    private Paint linePaint;
    private Paint textPaint;
    private float[] dataPoints = new float[]{70, 75, 72, 80, 85, 90, 78}; // default mock points
    private String[] labels = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private float maxValue = 120f;
    private int primaryColor = 0xFF0D47A1; // Medical Blue

    public CustomChartView(Context context) {
        super(context);
        init();
    }

    public CustomChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(primaryColor);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFFE2E8F0); // grid lines light gray
        linePaint.setStrokeWidth(3f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF64748B); // Slate text
        textPaint.setTextSize(30f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(float[] points, String[] horizontalLabels, float maxVal, int color) {
        this.dataPoints = points;
        this.labels = horizontalLabels;
        this.maxValue = maxVal;
        this.primaryColor = color;
        barPaint.setColor(color);
        invalidate(); // redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0 || dataPoints == null || dataPoints.length == 0) return;

        float paddingLeft = 50f;
        float paddingRight = 50f;
        float paddingTop = 40f;
        float paddingBottom = 60f;

        float graphWidth = width - paddingLeft - paddingRight;
        float graphHeight = height - paddingTop - paddingBottom;

        // Draw horizontal grid lines (3 lines: bottom, middle, top)
        for (int i = 0; i <= 3; i++) {
            float y = paddingTop + (graphHeight / 3) * i;
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, linePaint);
        }

        // Draw bars
        float numBars = dataPoints.length;
        float barSpacing = graphWidth / numBars;
        float barWidth = barSpacing * 0.5f;

        for (int i = 0; i < numBars; i++) {
            float val = dataPoints[i];
            float valRatio = val / maxValue;
            if (valRatio > 1.0f) valRatio = 1.0f;

            float x = paddingLeft + (barSpacing * i) + (barSpacing / 2);
            float y = paddingTop + graphHeight - (graphHeight * valRatio);

            // Draw bar
            RectF rect = new RectF(x - barWidth / 2, y, x + barWidth / 2, paddingTop + graphHeight);
            canvas.drawRoundRect(rect, 10f, 10f, barPaint);

            // Draw value above bar
            canvas.drawText(String.valueOf((int) val), x, y - 10f, textPaint);

            // Draw label below bar
            if (labels != null && i < labels.length) {
                canvas.drawText(labels[i], x, height - 15f, textPaint);
            }
        }
    }
}
