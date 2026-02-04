package com.customview.graph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class VuMeterView extends View {

    private static final float DB_FLOOR = -60f;
    private static final long PEAK_HOLD_MS = 600;
    private static final float PEAK_FALL_DB = 1.2f;

    private static final float[] DB_MARKS = {0f, -6f, -12f, -24f, -48f};
    private static final String DB_UNIT = "dBFS";
    private final Paint barPaint;
    private final Paint linePaint;
    private final Paint textPaint;
    private final Paint gridPaint;
    private float mRmsDb = DB_FLOOR;
    private float mPeakDb = DB_FLOOR;
    private float mPeakHoldDb = DB_FLOOR;
    private long mLastPeakTime;

    public VuMeterView(Context context) {
        this(context, null);
    }

    public VuMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(Color.WHITE);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(2f);
        linePaint.setColor(Color.WHITE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(28f);
        textPaint.setColor(Color.WHITE);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setAlpha(120);
        gridPaint.setColor(Color.WHITE);
    }

    /**
     * 设置 RMS 和 Peak 数据
     */
    public void setLevels(float rmsDb, float peakDb) {
        mRmsDb = rmsDb;
        mPeakDb = peakDb;

        long now = System.currentTimeMillis();
        if (peakDb >= mPeakHoldDb) {
            mPeakHoldDb = peakDb;
            mLastPeakTime = now;
        } else if (now - mLastPeakTime > PEAK_HOLD_MS) {
            mPeakHoldDb -= PEAK_FALL_DB;
            if (mPeakHoldDb < mPeakDb) {
                mPeakHoldDb = mPeakDb;
            }
        }

        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawColor(Color.DKGRAY);

        float textArea = 50f; // 数字显示区
        float tickArea = 20f; // 刻度线区
        float barLeft = textArea + tickArea;
        float barRight = w;

        Paint.FontMetrics fm = textPaint.getFontMetrics();

        // RMS 条
        float rmsY = dbToY(mRmsDb, h);
        canvas.drawRect(barLeft, rmsY, barRight, h, barPaint);

        // Peak Hold 线
        float peakY = dbToY(mPeakHoldDb, h);
        canvas.drawLine(barLeft, peakY, barRight, peakY, linePaint);

        // --- 刻度线 + 数字 ---
        for (int i = 0; i < DB_MARKS.length; i++) {
            float db = DB_MARKS[i];
            float y = dbToY(db, h);

            // 刻度线
            canvas.drawLine(textArea, y, textArea + tickArea, y, gridPaint);

            // 数字
            String label;
            if (db == 0f) {
                label = "0 " + DB_UNIT; // 顶部直接显示 0 dBFS
            } else {
                label = String.valueOf((int) db);
            }

            float textY = y - fm.top; // 顶部对齐
            canvas.drawText(label, 4, textY, textPaint);
        }
    }


    /**
     * dB → Y 坐标
     */
    private float dbToY(float db, int h) {
        float norm = (db - DB_FLOOR) / (0f - DB_FLOOR);
        norm = Math.max(0f, Math.min(1f, norm));
        return h * (1f - norm);
    }
}
