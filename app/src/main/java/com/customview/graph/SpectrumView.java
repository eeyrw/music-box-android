package com.customview.graph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {

    // 仅用于显示映射，不是 DSP
    private static final float DB_MIN = -80.0f;
    private static final float DB_MAX = 0.0f;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    // 当前帧数据（dBFS）
    private float[] bands = new float[0];

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        barPaint.setColor(Color.WHITE);
        barPaint.setStyle(Paint.Style.FILL);

        bgPaint.setColor(Color.DKGRAY);
    }

    /**
     * 推送一帧频谱数据（dBFS）
     * 可在任意线程调用
     */
    public void setSpectrum(float[] dbBands) {
        if (dbBands == null || dbBands.length == 0) return;

        synchronized (this) {
            // 直接整帧替换（不保留历史）
            bands = dbBands.clone();
        }

        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 背景
        canvas.drawRect(0, 0, w, h, bgPaint);

        float[] localBands;
        synchronized (this) {
            if (bands.length == 0) return;
            localBands = bands;
        }

        int n = localBands.length;
        float barWidth = (float) w / n;
        float gap = barWidth * 0.1f;
        float realBarWidth = barWidth - gap;

        for (int i = 0; i < n; i++) {
            float norm = dbToNorm(localBands[i]);
            float barHeight = norm * h;

            float left = i * barWidth + gap * 0.5f;
            float right = left + realBarWidth;
            float top = h - barHeight;

            canvas.drawRect(left, top, right, h, barPaint);
        }
    }

    /**
     * dBFS → [0,1]（仅用于 UI 映射）
     */
    private float dbToNorm(float db) {
        if (db <= DB_MIN) return 0.0f;
        if (db >= DB_MAX) return 1.0f;
        return (db - DB_MIN) / (DB_MAX - DB_MIN);
    }
}
