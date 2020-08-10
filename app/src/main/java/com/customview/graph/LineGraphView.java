package com.customview.graph;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class LineGraphView extends SurfaceView implements
        SurfaceHolder.Callback, OnGestureListener {

    private SurfaceHolder mSfHolder;
    private SurfaceUpdateThread mSfUpdateThread;
    private boolean mDrawThreadFlag;
    private int mSurfaceHeight;
    private int mSurfaceWidth;
    private float[] mValueArray;
    private int mBackColor;
    private int mBarColor;
    private GestureDetector mGestureDetector;

    private String mExtraText;

    private Object token = null;

    public int getBackColor() {
        return mBackColor;
    }

    public void setBackColor(int color) {
        mBackColor = color;
    }

    public String getExtraText() {
        return mExtraText;
    }

    public void setExtraText(String text) {
        mExtraText = text;
    }

    private void initDefaultParam(Context context) {
        mSfHolder = getHolder();
        mSfHolder.addCallback(this);
        mDrawThreadFlag = false;
        token = new Object();
        mGestureDetector = new GestureDetector(context, this);

        mBackColor = Color.GRAY;
        mBarColor = Color.WHITE;
        mExtraText = "BarGraphView";
    }

    public LineGraphView(Context context, AttributeSet attrs) {

        super(context, attrs);
        initDefaultParam(context);

    }

    public synchronized void setValueArray(float[] array) {
        mValueArray = array;
        synchronized (token) {
            token.notify();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder mSfHolder, int format, int width,
                               int height) {
        mSurfaceHeight = height;
        mSurfaceWidth = width;

    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        mDrawThreadFlag = true;
        mSfUpdateThread = new SurfaceUpdateThread();
        mSfUpdateThread.start();

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        mDrawThreadFlag = false;

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {
        return false;
    }

    class SurfaceUpdateThread extends Thread {
        @Override
        public synchronized void run() {
            SurfaceHolder runholder = mSfHolder;
            Canvas canvas = null;

            Paint linePaint = new Paint();
            Paint textPaint = new Paint();
            linePaint.setColor(mBarColor);

            textPaint.setAntiAlias(true);
            textPaint.setColor(mBarColor);
            textPaint.setStyle(Style.FILL);
            textPaint.setTextSize(mSurfaceHeight * 0.06f);

            FontMetrics fm = new FontMetrics();

            textPaint.getFontMetrics(fm);

            int textHeight = (int) (fm.bottom - fm.top);
            int textWidth = (int) textPaint.measureText(mExtraText);
            int textBaseline = (int) (-fm.ascent);

            Bitmap textBitmap = Bitmap.createBitmap(textWidth, textHeight, Bitmap.Config.ARGB_8888);

            Canvas textCanvas = new Canvas(textBitmap);
            textCanvas.drawColor(Color.TRANSPARENT);
            textCanvas.drawText(mExtraText, 0, textBaseline, textPaint);

            while (mDrawThreadFlag) {

//				synchronized (token) {
//					try {
//						 token.wait();
//					} catch (InterruptedException e) {
//						e.printStackTrace();
//					}
//				}

                try {
                    if (mValueArray != null) {
                        int sampleNum = mValueArray.length;

                        float barWidth = (float) mSurfaceWidth / sampleNum;
                        canvas = runholder.lockCanvas();
                        if (canvas != null) {
                            canvas.drawColor(mBackColor);// 这里是绘制背景
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
                            linePaint.setStrokeWidth(2.5f);
                            canvas.drawLines(points, linePaint);
                        }
                    }
                } catch (Exception e) {
                } finally {
                    if (canvas != null)
                        runholder.unlockCanvasAndPost(canvas);
                }

            }
        }
    }
}