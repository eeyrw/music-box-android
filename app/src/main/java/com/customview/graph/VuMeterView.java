package com.customview.graph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * VuMeterView
 * <p>
 * - 纯显示控件
 * - 数据由外部传入 VuLevel
 * - 不做任何 RMS/Peak 平滑或 PeakHold 计算
 */
public class VuMeterView extends View {

    private static final float DB_FLOOR = -60f;
    private static final float[] DB_MARKS = {0f, -6f, -12f, -24f, -48f};
    private static final String DB_UNIT = "dBFS";

    // 绘制对象
    private final Paint barPaint;
    private final Paint linePaint;
    private final Paint textPaint;
    private final Paint gridPaint;

    // 最新 VuLevel 数据
    private VuLevel vuLevel = new VuLevel();

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
     * 外部调用，传入最新 VuLevel
     */
    public void setVuLevel(VuLevel level) {
        if (level != null) {
            this.vuLevel = level;
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawColor(Color.DKGRAY);

        float textArea = 50f; // 数字区域宽度
        float tickArea = 20f; // 刻度线区域宽度
        float barLeft = textArea + tickArea;
        float barRight = w;

        Paint.FontMetrics fm = textPaint.getFontMetrics();

        // --- 绘制 RMS 条 ---
        float rmsY = dbToY(vuLevel.rmsDb, h);
        canvas.drawRect(barLeft, rmsY, barRight, h, barPaint);

        // --- 绘制 PeakHold 线 ---
        float peakY = dbToY(vuLevel.peakHoldDb, h);
        canvas.drawLine(barLeft, peakY, barRight, peakY, linePaint);

        // --- 绘制刻度线和数字 ---
        for (float db : DB_MARKS) {
            float y = dbToY(db, h);

            // 刻度线
            canvas.drawLine(textArea, y, textArea + tickArea, y, gridPaint);

            // 数字
            String label = db == 0f ? "0 " + DB_UNIT : String.valueOf((int) db);
            float textY = y - fm.top; // 顶部对齐
            canvas.drawText(label, 4, textY, textPaint);
        }
    }

    /**
     * dB 转 Y 坐标
     */
    private float dbToY(float db, int height) {
        float norm = (db - DB_FLOOR) / (0f - DB_FLOOR);
        norm = Math.max(0f, Math.min(1f, norm));
        return height * (1f - norm);
    }
}
