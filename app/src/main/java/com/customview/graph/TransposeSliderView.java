package com.customview.graph;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TransposeSliderView extends View {

    /* =======================
     * 配置
     * ======================= */

    private int minSemitone = -24;
    private int maxSemitone = 24;

    private int semitone = 0; // 当前值

    /* =======================
     * 几何
     * ======================= */

    private float trackLeft;
    private float trackRight;
    private float trackY;

    private float thumbX;
    private float thumbWidth;
    private float thumbHeight;

    /* =======================
     * 状态
     * ======================= */

    private boolean dragging = false;

    /* =======================
     * 画笔
     * ======================= */

    private Paint trackPaint;
    private Paint tickPaint;
    private Paint textPaint;
    private Paint thumbPaint;
    private Paint shadowPaint;

    /* =======================
     * 回调
     * ======================= */
    private OnSemitoneChangeListener listener;

    public TransposeSliderView(Context c) {
        super(c);
        init();
    }

    public TransposeSliderView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    /* =======================
     * 构造
     * ======================= */

    public TransposeSliderView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init();
    }

    public void setOnSemitoneChangeListener(OnSemitoneChangeListener l) {
        listener = l;
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(Color.DKGRAY);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(Color.LTGRAY);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.LTGRAY);
        textPaint.setTextSize(dp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.GRAY);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        thumbWidth = dp(28);
        thumbHeight = dp(20);

        trackLeft = getPaddingLeft() + thumbWidth / 2f;
        trackRight = w - getPaddingRight() - thumbWidth / 2f;
        trackY = h * 0.5f;

        updateThumbFromSemitone();
    }

    /* =======================
     * 尺寸
     * ======================= */

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        drawTrack(c);
        drawTicks(c);
        drawThumb(c);
    }

    /* =======================
     * 绘制
     * ======================= */

    private void drawTrack(Canvas c) {
        c.drawRoundRect(
                trackLeft,
                trackY - dp(3),
                trackRight,
                trackY + dp(3),
                dp(3),
                dp(3),
                trackPaint
        );
    }

    private void drawTicks(Canvas c) {
        int total = maxSemitone - minSemitone;
        float step = (trackRight - trackLeft) / total;

        for (int i = minSemitone; i <= maxSemitone; i++) {
            float x = trackLeft + (i - minSemitone) * step;

            boolean isZero = (i == 0);
            boolean isOctave = (i % 12 == 0);

            tickPaint.setStrokeWidth(isZero ? dp(3) : dp(1.5f));
            float h = isZero ? dp(14) : isOctave ? dp(10) : dp(6);

            c.drawLine(x, trackY - h, x, trackY + h, tickPaint);

            if (isOctave || isZero) {
                String label = (i > 0 ? "+" : "") + i;
                c.drawText(label, x, trackY + h + dp(12), textPaint);
            }
        }
    }

    private void drawThumb(Canvas c) {
        RectF r = new RectF(
                thumbX - thumbWidth / 2f,
                trackY - thumbHeight / 2f,
                thumbX + thumbWidth / 2f,
                trackY + thumbHeight / 2f
        );

        float radius = dp(4);

        // 阴影
        shadowPaint.setShadowLayer(dp(6), 0, dp(3), Color.BLACK);
        c.drawRoundRect(r, radius, radius, shadowPaint);
        shadowPaint.clearShadowLayer();

        // 主体
        LinearGradient grad = new LinearGradient(
                r.left, r.top, r.left, r.bottom,
                new int[]{
                        Color.LTGRAY,
                        Color.GRAY,
                        Color.DKGRAY
                },
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        thumbPaint.setShader(grad);
        c.drawRoundRect(r, radius, radius, thumbPaint);
        thumbPaint.setShader(null);

        // 中央刻线
        thumbPaint.setColor(Color.argb(140, 0, 0, 0));
        c.drawLine(
                thumbX,
                r.top + dp(3),
                thumbX,
                r.bottom - dp(3),
                thumbPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();

        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (hitThumb(x, e.getY())) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    thumbX = clamp(x, trackLeft, trackRight);
                    updateSemitoneFromThumb();
                    invalidate();
                    notifyChange();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                updateThumbFromSemitone(); // 强制吸附
                invalidate();
                break;
        }
        return super.onTouchEvent(e);
    }

    /* =======================
     * 触摸
     * ======================= */

    private boolean hitThumb(float x, float y) {
        return x >= thumbX - thumbWidth / 2f &&
                x <= thumbX + thumbWidth / 2f &&
                y >= trackY - thumbHeight / 2f &&
                y <= trackY + thumbHeight / 2f;
    }

    private void updateSemitoneFromThumb() {
        float t = (thumbX - trackLeft) / (trackRight - trackLeft);
        int total = maxSemitone - minSemitone;
        semitone = Math.round(minSemitone + t * total);
    }

    /* =======================
     * 映射
     * ======================= */

    private void updateThumbFromSemitone() {
        int total = maxSemitone - minSemitone;
        float t = (float) (semitone - minSemitone) / total;
        thumbX = trackLeft + t * (trackRight - trackLeft);
    }

    public int getSemitone() {
        return semitone;
    }

    /* =======================
     * API
     * ======================= */

    public void setSemitone(int s) {
        semitone = clamp(s, minSemitone, maxSemitone);
        updateThumbFromSemitone();
        invalidate();
        notifyChange();
    }

    private void notifyChange() {
        if (listener != null) {
            listener.onSemitoneChanged(semitone);
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /* =======================
     * 工具
     * ======================= */

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public interface OnSemitoneChangeListener {
        void onSemitoneChanged(int semitone);
    }
}