package com.customview.graph;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import com.yuan.music_box.R;

public class WaveformView extends View {
    // ================== 属性 ==================
    private String mTitle = "Waveform";
    private int mBackgroundColor = Color.DKGRAY;
    private int mLineColor = Color.WHITE;

    // ================== 尺寸 ==================
    private int mWidth, mHeight;
    private float mCenterY;

    // ================== 数据 ==================
    private float[] mValueArray;

    // ================== 绘制缓存 ==================
    private Paint mWavePaint;
    private Paint mTextPaint;
    private Paint mGridPaint;

    private float[] mLinePoints;

    // ================== 构造 ==================
    public WaveformView(Context context) {
        super(context);
        init(null, 0);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr);
    }


    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }


    // ================== 初始化 ==================
    private void init(AttributeSet attrs, int defStyle) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(
                    attrs, R.styleable.WaveformView, defStyle, 0);
            mTitle = a.getString(R.styleable.WaveformView_title);
            mBackgroundColor = a.getColor(
                    R.styleable.WaveformView_backgroundColor, mBackgroundColor);
            mLineColor = a.getColor(
                    R.styleable.WaveformView_lineColor, mLineColor);
            a.recycle();
        }

        initPaints();
    }

    private void initPaints() {
        mWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mWavePaint.setStyle(Paint.Style.STROKE);
        mWavePaint.setStrokeWidth(3f);
        mWavePaint.setColor(mLineColor);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(mLineColor);

        mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setColor(mLineColor);
        mGridPaint.setAlpha(120);
        mGridPaint.setStrokeWidth(1f);
    }

    // ================== 尺寸变化 ==================
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        mCenterY = h * 0.5f;

        mTextPaint.setTextSize(h * 0.08f);
    }


    // ================== 绘制 ==================
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(mBackgroundColor);

        // 中心线
        canvas.drawLine(0, mCenterY, mWidth, mCenterY, mGridPaint);

        // 标题
        float m = mHeight * 0.05f;

        if (mTitle == null || mTitle.isEmpty()) return;

        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        int h = (int) (fm.bottom - fm.top);
        int w = (int) mTextPaint.measureText(mTitle);
        int base = (int) (-fm.ascent);

        canvas.drawText(mTitle, m, m + base, mTextPaint);


        drawWaveform(canvas);
    }

    // ================== 波形 ==================
    private void drawWaveform(Canvas canvas) {
        if (mValueArray == null || mValueArray.length < 2) return;

        int n = mValueArray.length;
        int count = (n - 1) * 4;

        if (mLinePoints == null || mLinePoints.length < count) {
            mLinePoints = new float[count];
        }

        float dx = (float) mWidth / (n - 1);
        float halfH = mHeight * 0.5f;

        int j = 0;
        for (int i = 0; i < n - 1; i++) {
            float v0 = clamp(mValueArray[i]);
            float v1 = clamp(mValueArray[i + 1]);

            mLinePoints[j++] = dx * i;
            mLinePoints[j++] = mCenterY - v0 * halfH;
            mLinePoints[j++] = dx * (i + 1);
            mLinePoints[j++] = mCenterY - v1 * halfH;
        }

        canvas.drawLines(mLinePoints, 0, j, mWavePaint);
    }

    // ================== 数据输入 ==================
    public void setValueArray(float[] values) {
        mValueArray = values;
        postInvalidateOnAnimation();
    }

    // ================== API ==================
    public void setTitle(String title) {
        mTitle = title;
        invalidate();
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        invalidate();
    }

    public void setLineColor(int color) {
        mLineColor = color;
        mWavePaint.setColor(color);
        mTextPaint.setColor(color);
        mGridPaint.setColor(color);
        invalidate();
    }
}
