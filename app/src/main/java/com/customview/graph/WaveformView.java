package com.customview.graph;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.yuan.music_box.R;

public class WaveformView extends View {

    // ===== 可配置属性 =====
    private String mTitle = "Waveform";
    private int mBackgroundColor = Color.DKGRAY;
    private int mLineColor = Color.WHITE;

    // ===== 尺寸 =====
    private int mWidth;
    private int mHeight;
    private float mCenterY;

    // ===== 数据 =====
    private float[] mValueArray;

    // ===== 绘制缓存 =====
    private Paint mLinePaint;
    private Paint mTextPaint;

    private float[] mLinePoints;

    private Bitmap mTitleBitmap;
    private Canvas mTitleCanvas;

    // ===== 构造 =====
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
        if (v > 1f) return 1f;
        if (v < -1f) return -1f;
        return v;
    }

    // ===== 初始化 =====
    private void init(AttributeSet attrs, int defStyle) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(
                    attrs, R.styleable.WaveformView, defStyle, 0);

            mTitle = a.getString(R.styleable.WaveformView_title);
            mBackgroundColor = a.getColor(
                    R.styleable.WaveformView_backgroundColor,
                    mBackgroundColor);
            mLineColor = a.getColor(
                    R.styleable.WaveformView_lineColor,
                    mLineColor);
            a.recycle();
        }

        initPaints();
    }

    private void initPaints() {
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(3f);
        mLinePaint.setColor(mLineColor);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setColor(mLineColor);
    }

    // ===== 尺寸变化 =====
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        mCenterY = h * 0.5f;

        mTextPaint.setTextSize(h * 0.06f);
        rebuildTitleBitmap();
    }

    // ===== 标题 Bitmap =====
    private void rebuildTitleBitmap() {
        if (mTitle == null || mTitle.isEmpty()) {
            if (mTitleBitmap != null) {
                mTitleBitmap.recycle();
                mTitleBitmap = null;
                mTitleCanvas = null;
            }
            return;
        }

        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        int textHeight = (int) (fm.bottom - fm.top);
        int textWidth = (int) mTextPaint.measureText(mTitle);
        int baseline = (int) (-fm.ascent);

        if (textWidth <= 0 || textHeight <= 0) return;

        if (mTitleBitmap != null) {
            mTitleBitmap.recycle();
        }

        mTitleBitmap = Bitmap.createBitmap(
                textWidth,
                textHeight,
                Bitmap.Config.ARGB_8888
        );
        mTitleCanvas = new Canvas(mTitleBitmap);
        mTitleCanvas.drawColor(Color.TRANSPARENT);
        mTitleCanvas.drawText(mTitle, 0, baseline, mTextPaint);
    }

    // ===== 绘制 =====
    @Override
    protected void onDraw(Canvas canvas) {
        // 背景
        canvas.drawColor(mBackgroundColor);

        // 中心线
        canvas.drawLine(
                0, mCenterY,
                mWidth, mCenterY,
                mLinePaint
        );

        // 标题
        if (mTitleBitmap != null) {
            float margin = mHeight * 0.05f;
            canvas.drawBitmap(mTitleBitmap, margin, margin, null);
        }

        // 波形
        if (mValueArray == null || mValueArray.length < 2) return;

        int n = mValueArray.length;
        int pointCount = (n - 1) * 4;

        if (mLinePoints == null || mLinePoints.length < pointCount) {
            mLinePoints = new float[pointCount];
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

        canvas.drawLines(mLinePoints, 0, j, mLinePaint);
    }

    // ===== API（按你要求，不管 shadow）=====

    public void setTitle(String title) {
        mTitle = title;
        rebuildTitleBitmap();
        invalidate();
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        invalidate();
    }

    public void setLineColor(int color) {
        mLineColor = color;
        mLinePaint.setColor(color);
        mTextPaint.setColor(color);
        rebuildTitleBitmap();
        invalidate();
    }

    public void setValueArray(float[] values) {
        // 假定调用者保证线程安全 & 生命周期
        mValueArray = values;
        postInvalidateOnAnimation();
    }
}
