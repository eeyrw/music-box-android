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

/**
 * TODO: document your custom view class.
 */
public class WaveformView extends View {
    private String mTitle = "Waveform";
    private int mBackgroundColor = Color.DKGRAY;
    private int mLineColor = Color.WHITE;
    private int mSurfaceHeight;
    private int mSurfaceWidth;
    private float[] mValueArray;

    public WaveformView(Context context) {
        super(context);
        init(null, 0);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String mTitle) {
        this.mTitle = mTitle;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public void setBackgroundColor(int mBackgroundColor) {
        this.mBackgroundColor = mBackgroundColor;
        postInvalidate();
    }

    public int getLineColor() {
        return mLineColor;
    }

    public void setLineColor(int mLineColor) {
        this.mLineColor = mLineColor;
        postInvalidate();
    }

    public float[] getValueArray() {
        return mValueArray;
    }

    public void setValueArray(float[] mValueArray) {
        this.mValueArray = mValueArray;
        invalidate();
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.WaveformView, defStyle, 0);

        mTitle = a.getString(
                R.styleable.WaveformView_title);
        mBackgroundColor = a.getColor(
                R.styleable.WaveformView_backgroundColor,
                mBackgroundColor);
        mLineColor = a.getColor(
                R.styleable.WaveformView_lineColor,
                mLineColor);
        a.recycle();

        mValueArray = new float[256];

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mSurfaceHeight = h;
        mSurfaceWidth = w;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint linePaint = new Paint();
        Paint textPaint = new Paint();
        linePaint.setColor(mLineColor);

        textPaint.setAntiAlias(true);
        textPaint.setColor(mLineColor);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(mSurfaceHeight * 0.06f);

        Paint.FontMetrics fm = new Paint.FontMetrics();

        textPaint.getFontMetrics(fm);

        int textHeight = (int) (fm.bottom - fm.top);
        int textWidth = (int) textPaint.measureText(mTitle);
        int textBaseline = (int) (-fm.ascent);

        Bitmap textBitmap = Bitmap.createBitmap(textWidth, textHeight, Bitmap.Config.ARGB_8888);

        Canvas textCanvas = new Canvas(textBitmap);
        textCanvas.drawColor(Color.TRANSPARENT);
        textCanvas.drawText(mTitle, 0, textBaseline, textPaint);


        if (mValueArray != null) {
            int sampleNum = mValueArray.length;

            float barWidth = (float) mSurfaceWidth / sampleNum;
            canvas.drawColor(mBackgroundColor);// 这里是绘制背景
            linePaint.setStrokeWidth(1f);

            canvas.drawLine(0, mSurfaceHeight / 2,
                    mSurfaceWidth, mSurfaceHeight / 2,
                    linePaint);

            // canvas.drawRect(left, top, right, bottom, paint)
            canvas.drawBitmap(textBitmap,
                    mSurfaceHeight * 0.05f,
                    mSurfaceHeight * 0.05f, null);

            float[] points = new float[sampleNum * 4];

            for (int i = 0, j = 0; i < sampleNum - 1; i++) {
                points[j] = barWidth * i;
                points[j + 1] = mSurfaceHeight / 2f
                        - mValueArray[i] * mSurfaceHeight / 2f;
                points[j + 2] = barWidth * (i + 1);
                points[j + 3] = mSurfaceHeight / 2f
                        - mValueArray[i + 1] * mSurfaceHeight
                        / 2f;
                j += 4;
            }
            linePaint.setStrokeWidth(3f);
            canvas.drawLines(points, linePaint);

        }
    }

}
