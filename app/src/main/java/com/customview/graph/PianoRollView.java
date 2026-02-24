package com.customview.graph;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 简化版 PianoRollView
 * <p>
 * 1. 上方垂直卷帘显示 FallingNote
 * 2. 底部固定钢琴键盘（完善版，带 attack/release 动画和辉光效果）
 * 3. 网格显示
 * 4. 键盘高亮（渐入渐出动画）
 * 5. 所有渲染状态由外部传入 playbackTimeMs
 * 6. 支持设置当前时刻线在卷帘中的位置
 */
public class PianoRollView extends View {

    private final List<PianoKey> keys = new ArrayList<>();
    private final List<FallingNote> activeNotes = new ArrayList<>();
    private final List<NoteEvent> noteEvents = new ArrayList<>();

    private final PianoKey[] keyByMidi = new PianoKey[128];

    private final KeyboardRenderer keyboardRenderer;
    private final FallingNoteRenderer fallingRenderer;

    private float whiteKeyWidth = 40f;
    private float blackKeyWidth = 24f;
    private float keyHeight;
    private float blackKeyHeight;
    private float scrollX = 0f;
    private float lastX;
    private float maxScrollX = 0f;

    private int highlightColor = Color.YELLOW;
    private long attackTimeMs = 20; // 琴键按下渐入时间
    private long releaseTimeMs = 60; // 琴键松开渐出时间
    private int initialCenterMidi = 60;
    // 单位：半音，可正可负
    private int transposeSemitone = 0;
    private boolean needRebuildKeyState = false;
    // 时间轴标尺：每 1px 对应多少毫秒（系统级参数）
    private float msPerPx = 3.0f; // 例如 1px = 20ms
    int nextEventIndex = 0;


    // 当前时刻线配置
    private float currentTimeLineRatio = 1.0f; // 当前时刻线在瀑布帘中的位置比例 (0.0 = 顶部, 1.0 = 底部，默认底部)
    private boolean showCurrentTimeLine = true; // 是否显示当前时刻线
    private int currentTimeLineColor = Color.RED; // 当前时刻线的颜色
    private float currentTimeLineWidth = 3f; // 当前时刻线的宽度

    // 颜色配置
    private int fallingNoteColor = Color.YELLOW; // 下落音符的颜色
    private int gridColor = 0x22000000; // 网格颜色（半透明黑色）
    private int backgroundColor = Color.TRANSPARENT; // 背景颜色
    private int whiteKeyColor = Color.WHITE; // 白键颜色
    private int blackKeyColor = Color.BLACK; // 黑键颜色
    private int keyBorderColor = Color.LTGRAY; // 琴键边框颜色
    // ------------------- 外部接口 -------------------
    private long externalTimeMs = 0;
    private boolean isPlaying = false; // 播放状态
    private long lastUpdateTimeMs = 0; // 上次更新的系统时间
    private Choreographer.FrameCallback frameCallback; // VSync 回调

    public PianoRollView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // 初始化 128 个 MIDI 键
        for (int i = 0; i <= 127; i++) {
            PianoKey key = new PianoKey();
            key.midiNote = i;
            key.isBlack = isBlackKey(i);
            keys.add(key);

            keyByMidi[i] = key; // ⭐关键
        }

        keyboardRenderer = new KeyboardRenderer();
        fallingRenderer = new FallingNoteRenderer();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        keyHeight = h * 0.15f;
        blackKeyHeight = keyHeight * 0.6f;

        int whiteKeyCount = countWhiteKeys(0, 127);
        maxScrollX = whiteKeyCount * whiteKeyWidth - w;
        if (maxScrollX < 0) maxScrollX = 0;


        scrollToInitialMidi();
        computeKeyPositions();

    }

    private void computeKeyPositions() {
        float x = 0f;

        // 1️⃣ 先算白键 X
        for (PianoKey key : keys) {
            if (!key.isBlack) {
                key.drawX = x;
                x += whiteKeyWidth;
            }
        }

        // 2️⃣ 再算黑键 X（一次性）
        for (PianoKey key : keys) {
            if (key.isBlack) {
                int leftWhiteIndex = 0;
                for (PianoKey k : keys) {
                    if (!k.isBlack) {
                        if (k.midiNote < key.midiNote) {
                            leftWhiteIndex++;
                        } else {
                            break;
                        }
                    }
                }
                key.blackKeyX =
                        leftWhiteIndex * whiteKeyWidth
                                - whiteKeyWidth
                                + whiteKeyWidth * 0.65f;
            }
        }
    }

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

    @Override
    protected void onDraw(Canvas canvas) {
        // 绘制背景
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor);
        }

        canvas.save();
        canvas.translate(-scrollX, 0);
        float waterfallHeight = getHeight() - keyHeight;

        drawGrid(canvas, waterfallHeight);
        fallingRenderer.draw(canvas, activeNotes, keys, msPerPx, whiteKeyWidth, blackKeyWidth, fallingNoteColor, transposeSemitone, keyByMidi);


        // 绘制当前时刻线
        if (showCurrentTimeLine) {
            drawCurrentTimeLine(canvas, waterfallHeight);
        }

        canvas.translate(0, waterfallHeight);

        // currentTimeMs 由外部传入
        long currentTimeMs = externalTimeMs;
        boolean anyAlpha = keyboardRenderer.draw(canvas, keys, keyHeight, whiteKeyWidth, blackKeyWidth,
                blackKeyHeight, highlightColor, currentTimeMs,
                attackTimeMs, releaseTimeMs, scrollX, getWidth(),
                whiteKeyColor, blackKeyColor, keyBorderColor);

        canvas.restore();

        // 如果有键在动画中，继续刷新
        if (anyAlpha) {
            postInvalidateOnAnimation();
        }
    }

    private void drawGrid(Canvas canvas, float waterfallHeight) {
        Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);

        float x = 0;
        for (PianoKey key : keys) {
            if (!key.isBlack) {
                canvas.drawLine(x, 0, x, waterfallHeight, gridPaint);
                x += whiteKeyWidth;
            }
        }

        long gridStepMs = 500; // 例如 500ms 一条
        float step = gridStepMs / msPerPx;
        for (float y = 0; y < waterfallHeight; y += step) {
            canvas.drawLine(0, y, getWidth() + scrollX, y, gridPaint);
        }
    }

    /**
     * 绘制当前时刻线
     */
    private void drawCurrentTimeLine(Canvas canvas, float waterfallHeight) {
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(currentTimeLineColor);
        linePaint.setStrokeWidth(currentTimeLineWidth);
        linePaint.setStyle(Paint.Style.STROKE);

        float lineY = waterfallHeight * currentTimeLineRatio;
        float lineWidth = getWidth() + scrollX;

        // 当时刻线在底部（ratio = 1.0）时，绘制在瀑布区域和键盘的交界处
        // 这样音符落到线上就正好对应琴键被按下
        canvas.drawLine(0, lineY, lineWidth, lineY, linePaint);
    }

    /**
     * 外部每帧调用，传入播放时间
     */
    public void setPlaybackTime(long timeMs) {
        this.externalTimeMs = timeMs;
        this.lastUpdateTimeMs = System.currentTimeMillis();
        updateActiveNotes(timeMs);
        invalidate();
    }


    /**
     * 获取当前播放时间
     */
    public long getCurrentTime() {
        return externalTimeMs;
    }

    /**
     * 检查是否正在播放
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 开始播放（启动内部定时刷新）
     */
    public void startPlayback() {
        if (isPlaying) return;
        isPlaying = true;
        lastUpdateTimeMs = System.currentTimeMillis();
        scheduleUpdate();
    }

    /**
     * 暂停播放（停止内部定时刷新）
     */
    public void pausePlayback() {
        isPlaying = false;
        if (frameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }
        // 暂停时保持当前状态，不做任何清理
    }

    /**
     * 恢复播放（从暂停位置继续）
     */
    public void resumePlayback() {
        if (isPlaying) return;
        isPlaying = true;
        lastUpdateTimeMs = System.currentTimeMillis(); // 重置时间基准
        scheduleUpdate();
    }

    /**
     * 停止播放并重置到开始位置
     */
    public void stopPlayback() {
        pausePlayback();

        // 重置时间相关状态
        externalTimeMs = 0;
        lastUpdateTimeMs = 0;
        nextEventIndex = 0;

        // 清空当前的 activeNotes，准备重新加载
        activeNotes.clear();

        // 重置琴键状态
        resetAllKeys();

        // ✅ 重新从 0 时刻开始加载和计算状态
        updateActiveNotes(0);

        invalidate();
    }

    /**
     * 定时更新任务（使用 Choreographer 实现 VSync 同步）
     */
    private void scheduleUpdate() {
        if (!isPlaying) return;

        if (frameCallback == null) {
            frameCallback = new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (!isPlaying) return;

                    // 计算经过的时间
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastUpdateTimeMs;
                    lastUpdateTimeMs = now;

                    // 更新播放时间
                    externalTimeMs += elapsed;
                    updateActiveNotes(externalTimeMs);
                    invalidate();

                    // 继续下一帧（VSync 同步）
                    scheduleUpdate();
                }
            };
        }

        // 注册到 Choreographer，与屏幕刷新同步
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void loadNoteEvents(List<NoteEvent> events) {
        // 清空所有音符相关状态
        noteEvents.clear();
        activeNotes.clear();

        nextEventIndex = 0;


        // 深拷贝 NoteEvent，避免修改外部对象
        for (NoteEvent ev : events) {
            NoteEvent copy = new NoteEvent();
            copy.midiNote = ev.midiNote;
            copy.startTimeMs = ev.startTimeMs;
            copy.durationMs = ev.durationMs;
            copy.velocity = ev.velocity;
            noteEvents.add(copy);
        }

        // 重置所有琴键的状态
        resetAllKeys();

        // 重置播放时间（可选，根据需求）
        // externalTimeMs = 0;

        invalidate();
    }

    /**
     * 重置所有琴键的状态
     */
    private void resetAllKeys() {
        for (PianoKey key : keys) {
            key.velocity = 0f;
            key.lastPressedTime = -1;
            key.lastReleasedTime = -1;
        }
    }

    /**
     * 清空所有音符和状态（用于停止播放或重置）
     */
    public void clear() {
        pausePlayback(); // 停止内部刷新
        noteEvents.clear();
        activeNotes.clear();
        resetAllKeys();
        externalTimeMs = 0;
        lastUpdateTimeMs = 0;
        invalidate();
    }

    public void setTransposeSemitone(int semitone) {
        if (transposeSemitone != semitone) {
            transposeSemitone = semitone;
            needRebuildKeyState = true;
            invalidate();
        }
    }

    /**
     * 设置当前时刻线的颜色
     *
     * @param color 颜色值
     */
    public void setCurrentTimeLineColor(int color) {
        this.currentTimeLineColor = color;
        invalidate();
    }

    /**
     * 设置当前时刻线的宽度
     *
     * @param width 宽度（像素）
     */
    public void setCurrentTimeLineWidth(float width) {
        this.currentTimeLineWidth = width;
        invalidate();
    }

    /**
     * 设置是否显示当前时刻线
     *
     * @param show true 显示，false 隐藏
     */
    public void setShowCurrentTimeLine(boolean show) {
        this.showCurrentTimeLine = show;
        invalidate();
    }

    /**
     * 获取当前时刻线的位置比例
     *
     * @return 位置比例 (0.0-1.0)
     */
    public float getCurrentTimeLineRatio() {
        return currentTimeLineRatio;
    }

    /**
     * 设置当前时刻线在瀑布帘中的位置
     *
     * @param ratio 位置比例，0.0 表示顶部，1.0 表示底部，0.8 表示距离顶部 80% 的位置
     */
    public void setCurrentTimeLineRatio(float ratio) {
        this.currentTimeLineRatio = Math.max(0f, Math.min(1f, ratio));
        invalidate();
    }

    /**
     * 设置 attack 和 release 时间
     */
    public void setAttackRelease(long attack, long release) {
        this.attackTimeMs = attack;
        this.releaseTimeMs = release;
    }

    // ------------------- 颜色配置 getter/setter -------------------

    /**
     * 获取琴键高亮颜色
     */
    public int getHighlightColor() {
        return highlightColor;
    }

    /**
     * 设置琴键高亮颜色
     */
    public void setHighlightColor(int color) {
        this.highlightColor = color;
        invalidate();
    }

    /**
     * 获取下落音符颜色
     */
    public int getFallingNoteColor() {
        return fallingNoteColor;
    }

    /**
     * 设置下落音符颜色
     */
    public void setFallingNoteColor(int color) {
        this.fallingNoteColor = color;
        invalidate();
    }

    /**
     * 获取网格颜色
     */
    public int getGridColor() {
        return gridColor;
    }

    /**
     * 设置网格颜色
     */
    public void setGridColor(int color) {
        this.gridColor = color;
        invalidate();
    }

    /**
     * 获取背景颜色
     */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * 设置背景颜色
     */
    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        invalidate();
    }

    /**
     * 获取白键颜色
     */
    public int getWhiteKeyColor() {
        return whiteKeyColor;
    }

    /**
     * 设置白键颜色
     */
    public void setWhiteKeyColor(int color) {
        this.whiteKeyColor = color;
        invalidate();
    }

    /**
     * 获取黑键颜色
     */
    public int getBlackKeyColor() {
        return blackKeyColor;
    }

    /**
     * 设置黑键颜色
     */
    public void setBlackKeyColor(int color) {
        this.blackKeyColor = color;
        invalidate();
    }

    /**
     * 获取琴键边框颜色
     */
    public int getKeyBorderColor() {
        return keyBorderColor;
    }

    /**
     * 设置琴键边框颜色
     */
    public void setKeyBorderColor(int color) {
        this.keyBorderColor = color;
        invalidate();
    }

    private void updateActiveNotes(long currentTimeMs) {
        final float waterfallHeight = getHeight() - keyHeight;

        // 当前时刻线以上可提前显示的时间长度
        final long advanceTimeMs =
                (long) (currentTimeLineRatio * waterfallHeight * msPerPx);



        /* =========================
         * 1️⃣ 处理 transpose 后的键盘状态重建
         * ========================= */
        if (needRebuildKeyState) {
            // 清空所有键的状态
            for (PianoKey key : keys) {
                key.velocity = 0f;
                key.lastPressedTime = -1;
                key.lastReleasedTime = -1;  // ✅ 改为 -1
            }

            // 重新激活当前仍在 hold 的音符
            for (FallingNote fn : activeNotes) {
                long on = fn.startTimeMs;
                long off = fn.startTimeMs + fn.durationMs;

                if (on <= currentTimeMs && currentTimeMs < off) {
                    int displayNote = fn.midiNote + transposeSemitone;
                    if (displayNote >= 0 && displayNote <= 127) {
                        PianoKey key = keyByMidi[displayNote];
                        if (key != null) {
                            key.velocity = fn.velocity;
                            key.lastPressedTime = on;
                            key.lastReleasedTime = -1;  // ✅ 改为 -1（表示还在按下）
                        }
                    }
                }
            }

            needRebuildKeyState = false;
        }

        /* =========================
         * 2️⃣ 推进事件游标：把即将进入屏幕的 NoteEvent 变成 FallingNote
         * ========================= */
        while (nextEventIndex < noteEvents.size()) {
            NoteEvent ev = noteEvents.get(nextEventIndex);

            // 还没到预加载时间窗口，停
            if (ev.startTimeMs > currentTimeMs + advanceTimeMs) {
                break;
            }

            // 生成 FallingNote
            FallingNote fn = new FallingNote();
            fn.midiNote = ev.midiNote;
            fn.startTimeMs = ev.startTimeMs;
            fn.durationMs = ev.durationMs;
            fn.velocity = ev.velocity;
            fn.y = 0f;

            activeNotes.add(fn);
            nextEventIndex++;
        }

        /* =========================
         * 3️⃣ 更新 activeNotes：位置 + 键盘状态
         * ========================= */
        final float currentLineY = currentTimeLineRatio * waterfallHeight;

        Iterator<FallingNote> it = activeNotes.iterator();
        while (it.hasNext()) {
            FallingNote fn = it.next();

            // 时间差
            long dt = currentTimeMs - fn.startTimeMs;

            // 位置：时间向前，y 向下
            fn.y = currentLineY + dt / msPerPx;

            // 键盘映射（transpose 后）
            int displayNote = fn.midiNote + transposeSemitone;
            PianoKey key = null;
            if (displayNote >= 0 && displayNote <= 127) {
                key = keyByMidi[displayNote];
            }

            long noteOn = fn.startTimeMs;
            long noteOff = fn.startTimeMs + fn.durationMs;

// === note-on 区间 ===
            if (currentTimeMs >= noteOn && currentTimeMs < noteOff) {
                if (key != null) {
                    key.velocity = fn.velocity;

                    // 第一次进入 attack（避免重复设置）
                    if (key.lastPressedTime != noteOn) {
                        key.lastPressedTime = noteOn;
                        key.lastReleasedTime = -1;  // 确保在按下期间 release 时间无效
                    }
                }
            }
// === note-off 之后 ===
            else if (currentTimeMs >= noteOff) {
                if (key != null) {
                    // 只在第一次到达 note-off 时设置
                    if (key.lastReleasedTime != noteOff) {
                        key.lastReleasedTime = noteOff;
                    }
                }
            }

            // === 完全掉出屏幕底部，移除 ===
            float barHeight = fn.durationMs / msPerPx;
            if (fn.y - barHeight > waterfallHeight) {
                it.remove();
            }
        }
    }


    // ------------------- 工具 -------------------
    private boolean isBlackKey(int midiNote) {
        int mod = midiNote % 12;
        return mod == 1 || mod == 3 || mod == 6 || mod == 8 || mod == 10;
    }

    private int countWhiteKeys(int min, int max) {
        int c = 0;
        for (int i = min; i <= max; i++) if (!isBlackKey(i)) c++;
        return c;
    }

    private void scrollToInitialMidi() {
        int whiteIndex = 0;
        for (PianoKey key : keys) {
            if (!key.isBlack) {
                if (key.midiNote < initialCenterMidi) whiteIndex++;
                else break;
            }
        }
        float centerX = whiteIndex * whiteKeyWidth + whiteKeyWidth / 2f;
        scrollX = Math.max(0, Math.min(centerX - getWidth() / 2f, maxScrollX));
    }

    public void setInitialCenterMidi(int midi) {
        this.initialCenterMidi = midi;
        scrollToInitialMidi();
        invalidate();
    }

    // ------------------- 数据结构 -------------------
    public static class PianoKey {
        public int midiNote;
        public boolean isBlack;
        public float drawX;
        // 新增：缓存黑键 X
        public float blackKeyX = -1f;
        public float velocity;
        public long lastPressedTime = -1;  // 改为 -1
        public long lastReleasedTime = -1; // 改为 -1
    }

    public static class FallingNote {
        public int midiNote;
        public float velocity;
        public long startTimeMs;      // 音符开始时间
        public long durationMs;     // 音符持续时间
        public float y;             // 当前 Y 坐标
    }

    public static class NoteEvent {
        public int midiNote;
        public long startTimeMs;
        public long durationMs;     // 音符持续时间
        public float velocity;
    }
}

// ------------------- KeyboardRenderer -------------------
class KeyboardRenderer {

    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public KeyboardRenderer() {
        whitePaint.setStyle(Paint.Style.FILL);
        blackPaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(2f);
        edgePaint.setColor(Color.LTGRAY);

        glowPaint.setStyle(Paint.Style.FILL);

        labelPaint.setColor(Color.DKGRAY);
        labelPaint.setTextSize(24f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public boolean draw(Canvas canvas, List<PianoRollView.PianoKey> keys,
                        float keyHeight, float whiteKeyWidth, float blackKeyWidth,
                        float blackKeyHeight, int highlightColor, long currentTimeMs,
                        long attackTimeMs, long releaseTimeMs, float scrollX, int viewWidth,
                        int whiteKeyColor, int blackKeyColor, int keyBorderColor) {

        boolean anyAlpha = false;
        float x = 0f;

        // 更新边框颜色
        edgePaint.setColor(keyBorderColor);

        // 绘制白键
        for (PianoRollView.PianoKey key : keys) {
            if (!key.isBlack) {
                key.drawX = x;
                float alpha = computeAlpha(key, currentTimeMs, attackTimeMs, releaseTimeMs);

                whitePaint.setColor(whiteKeyColor);
                whitePaint.setAlpha((int) (255 * (1 - alpha)));
                canvas.drawRect(x, 0, x + whiteKeyWidth, keyHeight, whitePaint);

                // 高亮动画
                if (alpha > 0) {
                    whitePaint.setColor(highlightColor);
                    whitePaint.setAlpha((int) (255 * alpha));
                    canvas.drawRect(x, 0, x + whiteKeyWidth, keyHeight, whitePaint);
                    anyAlpha = true;
                }

                canvas.drawRect(x, 0, x + whiteKeyWidth, keyHeight, edgePaint);

                // 绘制 C 音标签
                if (key.midiNote % 12 == 0) {
                    canvas.drawText(midiToNoteName(key.midiNote), x + whiteKeyWidth / 2, keyHeight - 4, labelPaint);
                }

                x += whiteKeyWidth;
            }
        }

        // 绘制黑键
        for (PianoRollView.PianoKey key : keys) {
            if (key.isBlack) {
                float pos = key.blackKeyX;
                key.drawX = pos;
                float alpha = computeAlpha(key, currentTimeMs, attackTimeMs, releaseTimeMs);

                blackPaint.setColor(blackKeyColor);
                blackPaint.setAlpha((int) (255 * (1 - alpha)));
                canvas.drawRect(pos, 0, pos + blackKeyWidth, blackKeyHeight, blackPaint);

                if (alpha > 0) {
                    blackPaint.setColor(highlightColor);
                    blackPaint.setAlpha((int) (255 * alpha));
                    canvas.drawRect(pos, 0, pos + blackKeyWidth, blackKeyHeight, blackPaint);
                    anyAlpha = true;
                }
            }
        }

        // 绘制左右辉光效果
        drawGlow(canvas, keys, highlightColor, currentTimeMs, attackTimeMs, releaseTimeMs,
                scrollX, viewWidth, keyHeight, whiteKeyWidth);

        return anyAlpha;
    }

    /**
     * 绘制左右边缘的辉光效果（当不可见的键被高亮时）
     */
    private void drawGlow(Canvas canvas, List<PianoRollView.PianoKey> keys, int highlightColor,
                          long currentTimeMs, long attackTimeMs, long releaseTimeMs,
                          float scrollX, int viewWidth, float keyHeight, float whiteKeyWidth) {
        float viewStart = scrollX;
        float viewEnd = scrollX + viewWidth;
        float leftAlpha = 0f;
        float rightAlpha = 0f;

        for (PianoRollView.PianoKey key : keys) {
            float alpha = computeAlpha(key, currentTimeMs, attackTimeMs, releaseTimeMs);
            if (alpha <= 0) continue;

            float keyWidth = key.isBlack ? 24f : whiteKeyWidth;
            // 完全在左边不可见
            if (key.drawX + keyWidth <= viewStart) {
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
     * 计算键的当前透明度（attack/release 动画）
     */
    private float computeAlpha(PianoRollView.PianoKey key, long now, long attackTimeMs, long releaseTimeMs) {
        if (key.lastPressedTime < 0) return 0f;

        if (key.lastReleasedTime < 0 || key.lastPressedTime > key.lastReleasedTime) {
            // attack 阶段
            float t = now - key.lastPressedTime;
            float alpha = t / (float) attackTimeMs;
            return Math.min(1f, alpha);
        } else if (key.lastReleasedTime > key.lastPressedTime) {
            // release 阶段
            float t = now - key.lastReleasedTime;
            float alpha = 1f - t / (float) releaseTimeMs;
            return Math.max(0f, alpha);
        }
        return 0f;
    }


    private String midiToNoteName(int midiNote) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = (midiNote / 12) - 1;
        return names[midiNote % 12] + octave;
    }
}

// ------------------- FallingNoteRenderer (Horse Centered Edition) -------------------
class FallingNoteRenderer {

    private static final String HORSE = "🐎";

    private final Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FallingNoteRenderer() {
        emojiPaint.setTextAlign(Paint.Align.LEFT);
        emojiPaint.setTextSize(64f);
    }

    public void draw(Canvas canvas,
                     List<PianoRollView.FallingNote> notes,
                     List<PianoRollView.PianoKey> keys,
                     float msPerPx,
                     float whiteKeyWidth,
                     float blackKeyWidth,
                     int noteColor,
                     int transposeSemitone,
                     PianoRollView.PianoKey[] keyByMidi) {

        for (PianoRollView.FallingNote fn : notes) {

            float x = 0f;
            boolean isBlack = false;
            int displayNote = fn.midiNote + transposeSemitone;

            if (displayNote >= 0 && displayNote <= 127) {
                PianoRollView.PianoKey key = keyByMidi[displayNote];
                if (key != null) {
                    x = key.drawX;
                    isBlack = key.isBlack;
                }
            }

            float keyWidth = isBlack ? blackKeyWidth : whiteKeyWidth;

            // 根据 velocity 调整大小
            float sizeScale = 0.7f + fn.velocity * 0.6f;
            float textSize = keyWidth * sizeScale;

            emojiPaint.setTextSize(textSize);
            // 获取文字宽度（用于水平居中）
            float textWidth = emojiPaint.measureText(HORSE);

            // 获取字体 metrics（用于垂直对齐）
            Paint.FontMetrics fm = emojiPaint.getFontMetrics();

            // 🎯 水平居中
            float centerX = x + keyWidth * 0.5f;
            float drawX = centerX - textWidth * 0.5f;

            // 🎯 垂直底部对齐（马蹄踩线）
            float baseline = fn.y - fm.descent;

            // 🐎 轻微左右摆动
            float sway = (float) Math.sin(fn.y * 0.02f) * keyWidth * 0.15f;

            canvas.drawText(
                    HORSE,
                    drawX + sway,
                    baseline,
                    emojiPaint
            );
        }
    }
}
