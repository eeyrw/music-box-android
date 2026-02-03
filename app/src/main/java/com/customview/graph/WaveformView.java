package com.customview.graph;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import com.yuan.music_box.R;

public class WaveformView extends View {

    // ================== 常量 ==================
    private static final float LEVEL_BAR_RATIO = 0.06f;   // 电平条占总宽度比例
    private static final float DB_FLOOR = -50f;

    private static final long PEAK_HOLD_MS = 600;
    private static final float PEAK_FALL_DB = 1.2f;

    private static final float[] DB_MARKS = {
            0f, -6f, -12f, -24f, -48f
    };

    private static final String DB_UNIT = "dBFS";


    // ================== 属性 ==================
    private String mTitle = "Waveform";
    private int mBackgroundColor = Color.DKGRAY;
    private int mLineColor = Color.WHITE;

    // ================== 尺寸 ==================
    private int mWidth, mHeight;
    private float mCenterY;

    private float mWaveformWidth;
    private float mScaleWidth;
    private float mLevelBarWidth;

    private float mDbLabelWidth;

    // ================== 数据 ==================
    private float[] mValueArray;

    private float mRmsDb = DB_FLOOR;
    private float mPeakDb = DB_FLOOR;
    private float mPeakHoldDb = DB_FLOOR;
    private long mLastPeakTime;

    // ================== 绘制缓存 ==================
    private Paint mWavePaint;
    private Paint mLevelPaint;
    private Paint mTextPaint;
    private Paint mGridPaint;

    private float[] mLinePoints;

    private Bitmap mTitleBitmap;
    private Canvas mTitleCanvas;

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

    private static float linToDb(float v) {
        if (v <= 0f) return DB_FLOOR;
        return Math.max(20f * (float) Math.log10(v), DB_FLOOR);
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
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

        mLevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLevelPaint.setStyle(Paint.Style.FILL);
        mLevelPaint.setColor(mLineColor);

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

        mTextPaint.setTextSize(h * 0.045f);
        computeDbLabelWidth();

        mLevelBarWidth = w * LEVEL_BAR_RATIO;
        mScaleWidth = mDbLabelWidth + 8f;
        mWaveformWidth = w - mScaleWidth - mLevelBarWidth;

        rebuildTitleBitmap();
    }

    // ================== dB 标签宽度 ==================
    private void computeDbLabelWidth() {
        float max = 0f;
        for (float db : DB_MARKS) {
            String s = (db == 0f) ? "0" : String.format("-%d", (int) -db);
            max = Math.max(max, mTextPaint.measureText(s));
        }
        mDbLabelWidth = max + mTextPaint.getTextSize() * 0.6f;
    }

    // ================== 标题 Bitmap ==================
    private void rebuildTitleBitmap() {
        if (mTitle == null || mTitle.isEmpty()) return;

        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        int h = (int) (fm.bottom - fm.top);
        int w = (int) mTextPaint.measureText(mTitle);
        int base = (int) (-fm.ascent);

        if (mTitleBitmap != null) mTitleBitmap.recycle();

        mTitleBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        mTitleCanvas = new Canvas(mTitleBitmap);
        mTitleCanvas.drawColor(Color.TRANSPARENT);
        mTitleCanvas.drawText(mTitle, 0, base, mTextPaint);
    }

    // ================== 绘制 ==================
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(mBackgroundColor);

        // 中心线
        canvas.drawLine(0, mCenterY, mWaveformWidth, mCenterY, mGridPaint);

        // 标题
        if (mTitleBitmap != null) {
            float m = mHeight * 0.05f;
            canvas.drawBitmap(mTitleBitmap, m, m, null);
        }

        drawWaveform(canvas);
        drawLevelMeter(canvas);
    }

    // ================== 波形 ==================
    private void drawWaveform(Canvas canvas) {
        if (mValueArray == null || mValueArray.length < 2) return;

        int n = mValueArray.length;
        int count = (n - 1) * 4;

        if (mLinePoints == null || mLinePoints.length < count) {
            mLinePoints = new float[count];
        }

        float dx = mWaveformWidth / (n - 1);
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

    // ================== 电平条 + 刻度 ==================
    private void drawLevelMeter(Canvas canvas) {
        float scaleLeft = mWaveformWidth;
        float barLeft = mWaveformWidth + mScaleWidth;
        float barRight = mWidth;

        // RMS 电平条
        float rmsY = dbToY(mRmsDb);
        canvas.drawRect(barLeft, rmsY, barRight, mHeight, mLevelPaint);

        // Peak Hold 线
        float peakY = dbToY(mPeakHoldDb);
        canvas.drawLine(barLeft, peakY, barRight, peakY, mWavePaint);

        // dBFS 单位标识
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float unitY = -fm.top + 2; // 顶部留一点呼吸感

        float unitX = mWaveformWidth
                + (mScaleWidth - mTextPaint.measureText(DB_UNIT)) * 0.5f;

        canvas.drawText(DB_UNIT, unitX, unitY, mTextPaint);

        // dB 刻度
        for (float db : DB_MARKS) {
            float y = dbToY(db);

            canvas.drawLine(
                    scaleLeft,
                    y,
                    barLeft - 4,
                    y,
                    mGridPaint
            );

            String label = (db == 0f) ? "0" : String.format("-%d", (int) -db);
            float tw = mTextPaint.measureText(label);

            canvas.drawText(
                    label,
                    barLeft - 6 - tw,
                    y - 4,
                    mTextPaint
            );
        }
    }

    // ================== 数据输入 ==================
    public void setValueArray(float[] values) {
        mValueArray = values;
        computeLevels(values);
        postInvalidateOnAnimation();
    }

    private void computeLevels(float[] v) {
        float sum = 0f;
        float peak = 0f;

        for (float x : v) {
            float a = Math.abs(x);
            peak = Math.max(peak, a);
            sum += x * x;
        }

        float rms = (float) Math.sqrt(sum / v.length);

        mRmsDb = linToDb(rms);
        mPeakDb = linToDb(peak);

        long now = System.currentTimeMillis();
        if (mPeakDb >= mPeakHoldDb) {
            mPeakHoldDb = mPeakDb;
            mLastPeakTime = now;
        } else if (now - mLastPeakTime > PEAK_HOLD_MS) {
            mPeakHoldDb -= PEAK_FALL_DB;
            if (mPeakHoldDb < mPeakDb) {
                mPeakHoldDb = mPeakDb;
            }
        }
    }

    // ================== 工具 ==================
    private float dbToY(float db) {
        float norm = (db - DB_FLOOR) / (0f - DB_FLOOR);
        norm = clamp01(norm);
        return mHeight * (1f - norm);
    }

    // ================== API ==================
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
        mWavePaint.setColor(color);
        mLevelPaint.setColor(color);
        mTextPaint.setColor(color);
        mGridPaint.setColor(color);
        rebuildTitleBitmap();
        invalidate();
    }
}
