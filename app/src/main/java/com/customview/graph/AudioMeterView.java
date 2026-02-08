package com.customview.graph;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.yuan.music_box.R;

public class AudioMeterView extends ViewGroup {

    private final WaveformView waveformView;
    private final SpectrumView spectrumView;
    private final VuMeterView vuMeterView;

    private final float vuRatio = 0.12f; // 右侧宽度占比

    public AudioMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);

        waveformView = new WaveformView(context);
        spectrumView = new SpectrumView(context);
        vuMeterView = new VuMeterView(context);

        vuMeterView.setLineColor(getResources().getColor(R.color.colorPrimary));

        addView(waveformView);
        addView(spectrumView);
        addView(vuMeterView);
    }

    /**
     * 推送音频帧数据
     */
    public void setAudioVisualData(float[] pcm, float[] spectrumDb, VuLevel vuLevel) {
        waveformView.setValueArray(pcm);
        spectrumView.setSpectrum(spectrumDb);
        vuMeterView.setVuLevel(vuLevel);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);

        int vuW = (int) (w * vuRatio);
        int leftW = w - vuW;
        int halfH = (int) (h * 0.7);

        measureChild(
                waveformView,
                MeasureSpec.makeMeasureSpec(leftW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(halfH, MeasureSpec.EXACTLY)
        );

        measureChild(
                spectrumView,
                MeasureSpec.makeMeasureSpec(leftW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h - halfH, MeasureSpec.EXACTLY)
        );

        measureChild(
                vuMeterView,
                MeasureSpec.makeMeasureSpec(vuW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        );

        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;

        int vuW = (int) (w * vuRatio);
        int leftW = w - vuW;
        int halfH = (int) (h * 0.7);

        waveformView.layout(0, 0, leftW, halfH);
        spectrumView.layout(0, halfH, leftW, h);
        vuMeterView.layout(leftW, 0, w, h);
    }

    // 对外暴露子 View（可选）
    public WaveformView getWaveformView() {
        return waveformView;
    }

    public SpectrumView getSpectrumView() {
        return spectrumView;
    }

    public VuMeterView getVuMeterView() {
        return vuMeterView;
    }
}
