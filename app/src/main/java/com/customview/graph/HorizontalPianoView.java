package com.customview.graph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 可滑动的水平钢琴 View
 */
public class HorizontalPianoView extends View {

    private static final int MIN_MIDI = 0;
    private static final int MAX_MIDI = 127;

    // 键尺寸
    private float whiteKeyWidth = 40f;
    private float blackKeyWidth = 24f;
    private float keyHeight = 300f;
    private float blackKeyHeight = keyHeight * 0.6f;

    // 滑动
    private float scrollX = 0f;       // 当前偏移
    private float lastX;               // 上次触摸 X
    private float maxScrollX = 0f;

    // 外部可设置的初始 MIDI，可选默认中央 C
    private int initialCenterMidi = 60;

    // 绘制 Paint
    private Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // noteOn/highlight
    private boolean noteOnOnlyMode = false;

    private long attackTimeMs = 20;   // ms

    private long releaseTimeMs = 100;  // ms

    // 高亮颜色，可外部设置
    private int highlightColor = Color.YELLOW;

    // 所有键
    private List<PianoKey> keys = new ArrayList<>();

    public HorizontalPianoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        whitePaint.setStyle(Paint.Style.FILL);
        blackPaint.setStyle(Paint.Style.FILL);

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(highlightColor);
        glowPaint.setAlpha(128);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setColor(Color.LTGRAY); // 淡灰色边线
        edgePaint.setStrokeWidth(2f);

        // 初始化键盘
        for (int i = MIN_MIDI; i <= MAX_MIDI; i++) {
            PianoKey key = new PianoKey();
            key.midiNote = i;
            key.isBlack = isBlackKey(i);
            keys.add(key);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 根据 View 高度动态计算白键高度（保留上下 padding 可视情况）
        keyHeight = h * 0.95f; // 留一点顶部空间
        blackKeyHeight = keyHeight * 0.6f;

        int whiteKeyCount = countWhiteKeys(MIN_MIDI, MAX_MIDI);
        maxScrollX = whiteKeyCount * whiteKeyWidth - w;
        if (maxScrollX < 0) maxScrollX = 0;

        // 计算初始滚动位置，使 initialCenterMidi 居中
        scrollToInitialMidi();
    }

    // ----------------------------
    // 手指滑动
    // ----------------------------
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = lastX - event.getX();
                scrollX += dx;
                scrollX = Math.max(0, Math.min(scrollX, maxScrollX));
                lastX = event.getX();
                invalidate();
                break;
        }
        return true;
    }

    // ----------------------------
    // 绘制键盘
    // ----------------------------
    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();

        canvas.save();
        canvas.translate(-scrollX, 0); // 平移画布，键盘随 scrollX 移动

        float x = 0f;
        // 白键
        for (PianoKey key : keys) {
            if (!key.isBlack) {
                float alpha = computeAlpha(key, now); // 获取动画透明度
                whitePaint.setColor(Color.WHITE);
                whitePaint.setAlpha((int) (255 * (1 - alpha)));
                canvas.drawRect(x, 0, x + whiteKeyWidth, keyHeight, whitePaint);

                // 高亮动画
                if (alpha > 0) {
                    whitePaint.setColor(highlightColor); // 使用可设置高亮色
                    whitePaint.setAlpha((int) (255 * alpha));
                    canvas.drawRect(x, 0, x + whiteKeyWidth, keyHeight, whitePaint);
                }

                // 白键边线
                canvas.drawRect(x, 0, x + whiteKeyWidth, keyHeight, edgePaint);

                key.drawX = x;
                x += whiteKeyWidth;
            }
        }


        // 绘制可视范围内 C 音标签
        drawWhiteKeyLabels(canvas);

        // 黑键
        for (PianoKey key : keys) {
            if (key.isBlack) {
                float pos = computeBlackKeyX(key.midiNote);
                key.drawX = pos; // ← 修复左右 glow 的关键
                float alpha = computeAlpha(key, now);

                blackPaint.setColor(Color.BLACK);
                blackPaint.setAlpha((int) (255 * (1 - alpha)));
                canvas.drawRect(pos, 0, pos + blackKeyWidth, blackKeyHeight, blackPaint);

                if (alpha > 0) {
                    blackPaint.setColor(highlightColor);
                    blackPaint.setAlpha((int) (255 * alpha));
                    canvas.drawRect(pos, 0, pos + blackKeyWidth, blackKeyHeight, blackPaint);
                }
            }
        }

        // 左右辉光
        drawGlow(canvas);

        canvas.restore();

        postInvalidateOnAnimation();
    }

    private void drawGlow(Canvas canvas) {
        float viewStart = scrollX;
        float viewEnd = scrollX + getWidth();
        float leftAlpha = 0f;
        float rightAlpha = 0f;
        long now = System.currentTimeMillis();

        for (PianoKey key : keys) {
            float alpha = computeAlpha(key, now);
            if (alpha <= 0) continue; // 没有高亮就忽略

            // 完全在左边不可见
            if (key.drawX + whiteKeyWidth <= viewStart) {
                leftAlpha = Math.max(leftAlpha, alpha);
            }
            // 完全在右边不可见
            if (key.drawX >= viewEnd) {
                rightAlpha = Math.max(rightAlpha, alpha);
            }
        }

        // 绘制左侧渐变
        if (leftAlpha > 0) {
            LinearGradient leftGradient = new LinearGradient(
                    viewStart, 0, viewStart + 50, 0,
                    new int[]{adjustAlpha(highlightColor, leftAlpha), Color.TRANSPARENT},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
            glowPaint.setShader(leftGradient);
            canvas.drawRect(viewStart, 0, viewStart + 50, keyHeight, glowPaint);
            glowPaint.setShader(null);
        }

        // 绘制右侧渐变
        if (rightAlpha > 0) {
            LinearGradient rightGradient = new LinearGradient(
                    viewEnd - 50, 0, viewEnd, 0,
                    new int[]{Color.TRANSPARENT, adjustAlpha(highlightColor, rightAlpha)},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
            glowPaint.setShader(rightGradient);
            canvas.drawRect(viewEnd - 50, 0, viewEnd, keyHeight, glowPaint);
            glowPaint.setShader(null);
        }
    }


    /**
     * 调整颜色透明度
     */
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.min(255, Math.max(0, (int) (Color.alpha(color) * factor)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }


    /**
     * 绘制可视范围内的 C 音白键标签
     */
    private void drawWhiteKeyLabels(Canvas canvas) {
        float viewStartX = scrollX;
        float viewEndX = scrollX + getWidth();

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        float textY = keyHeight - 10; // 白键底部，向上偏移10px

        for (PianoKey key : keys) {
            if (!key.isBlack && key.drawX + whiteKeyWidth >= viewStartX && key.drawX <= viewEndX) {
                int noteIndex = key.midiNote % 12;
                if (noteIndex == 0) { // 只绘制 C 音
                    String noteName = midiToNoteName(key.midiNote);
                    canvas.drawText(noteName, key.drawX + whiteKeyWidth / 2, textY, textPaint);
                }
            }
        }
    }


    /**
     * MIDI 转音名，例如 60 -> C4
     */
    private String midiToNoteName(int midiNote) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = (midiNote / 12) - 1;
        int noteIndex = midiNote % 12;
        return names[noteIndex] + octave;
    }


    /**
     * 计算 scrollX 使得指定 MIDI 音居中
     */
    private void scrollToInitialMidi() {
        int whiteIndex = 0;
        for (PianoKey key : keys) {
            if (!key.isBlack) {
                if (key.midiNote < initialCenterMidi) {
                    whiteIndex++;
                } else {
                    break;
                }
            }
        }
        float centerX = whiteIndex * whiteKeyWidth + whiteKeyWidth / 2;
        scrollX = centerX - getWidth() / 2f;
        // 限制 scrollX 范围
        scrollX = Math.max(0, Math.min(scrollX, maxScrollX));
        invalidate();
    }

    /**
     * 按下一个 MIDI 音
     *
     * @param midiNote MIDI 音号
     * @param velocity 音量/力度 (0~1)
     */
    public void noteOn(int midiNote, float velocity) {
        long now = System.currentTimeMillis();
        for (PianoKey key : keys) {
            if (key.midiNote == midiNote) {
                key.velocity = velocity;
                key.lastPressedTime = now; // 更新按下时间
                // 如果是 noteOnOnly 模式，直接把 lastReleasedTime 设置到 attack+release 后
                if (noteOnOnlyMode) {
                    key.lastReleasedTime = now + releaseTimeMs;
                }
                invalidate(); // 触发重绘
                break;
            }
        }
    }


    /**
     * 松开一个 MIDI 音
     *
     * @param midiNote MIDI 音号
     */
    public void noteOff(int midiNote) {
        if (noteOnOnlyMode) return; // noteOnOnly 模式不处理松开
        long now = System.currentTimeMillis();
        for (PianoKey key : keys) {
            if (key.midiNote == midiNote) {
                key.lastReleasedTime = now; // 更新松开时间
                invalidate(); // 触发重绘
                break;
            }
        }
    }

    private float computeAlpha(PianoKey key, long now) {
        if (key.lastPressedTime > key.lastReleasedTime) {
            // attack
            float t = now - key.lastPressedTime;
            float alpha = t / (float) attackTimeMs;  // 线性增加
            return Math.min(1f, alpha);
        } else if (key.lastReleasedTime > key.lastPressedTime) {
            // release
            float t = now - key.lastReleasedTime;
            float alpha = 1f - t / (float) releaseTimeMs; // 线性衰减
            return Math.max(0f, alpha);
        }
        return 0f;
    }


    private float computeBlackKeyX(int midiNote) {
        int leftWhiteIndex = 0;
        for (PianoKey key : keys) {
            if (!key.isBlack) {
                if (key.midiNote < midiNote) leftWhiteIndex++;
                else break;
            }
        }
        float leftX = leftWhiteIndex * whiteKeyWidth;
        return leftX - whiteKeyWidth + whiteKeyWidth * 0.65f;
    }

    private boolean isBlackKey(int midiNote) {
        int mod = midiNote % 12;
        return mod == 1 || mod == 3 || mod == 6 || mod == 8 || mod == 10;
    }

    private int countWhiteKeys(int min, int max) {
        int c = 0;
        for (int i = min; i <= max; i++) {
            if (!isBlackKey(i)) c++;
        }
        return c;
    }

    // ----------------------------
    // setter
    // ----------------------------
    public void setNoteOnOnlyMode(boolean mode) {
        this.noteOnOnlyMode = mode;
    }

    public void setAttackRelease(long a, long r) {
        this.attackTimeMs = a;
        this.releaseTimeMs = r;
    }

// ----------------------------
// getter / setter
// ----------------------------

    /**
     * 获取高亮颜色
     */
    public int getHighlightColor() {
        return highlightColor;
    }

    /**
     * 设置高亮颜色
     *
     * @param color 高亮颜色，例如 Color.YELLOW
     */
    public void setHighlightColor(int color) {
        this.highlightColor = color;
        glowPaint.setColor(color);
        invalidate();
    }

    /**
     * 获取初始中心 MIDI
     */
    public int getInitialCenterMidi() {
        return initialCenterMidi;
    }

    /**
     * 设置初始可视化中心 MIDI
     *
     * @param midiNote MIDI 音号，例如中央C 60
     */
    public void setInitialCenterMidi(int midiNote) {
        this.initialCenterMidi = midiNote;
        scrollToInitialMidi();
    }
}

class PianoKey {
    int midiNote;
    boolean isBlack;
    float velocity;
    long lastPressedTime;
    long lastReleasedTime;
    float drawX;
}
