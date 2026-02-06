#pragma once
#include <cmath>
#include <vector>
#include <complex>
#include <algorithm>
#include <cstring>
#include "PinnedSnapshot.h"

/*
 * ===============================
 * 全局配置参数
 * ===============================
 */

// 音频采样率（Hz）
#define SAMPLE_RATE 32000

// 每次处理的音频 block 大小
// 同时也是 FFT 的长度
#define AUDIO_BLOCK 1024

// 频谱显示的频段数
#define SPECTRUM_BANDS 128

// 波形显示的采样点数
#define VISUAL_POINTS 256


// 抽帧间隔（MS）
#define EXTRACT_INTERVAL_MS 20

/*
 * ===============================
 * 音频块（来自音频回调）
 * ===============================
 */
struct AudioBlock {
    float samples[AUDIO_BLOCK];
};


/*
 * ===============================
 * 频谱输出帧（给 GUI 用）
 * 每一帧 = 一个完整频谱快照
 * ===============================
 */
struct SpectrumFrame {
    float bands[SPECTRUM_BANDS];
};


/*
 * ===============================
 * 波形输出帧（给 GUI 用）
 * ===============================
 */
struct WaveformFrame {
    float waveform[VISUAL_POINTS];
};


/*
 * ===============================
 * 二阶 IIR 滤波器（Direct Form I）
 * ===============================
 *
 * y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2]
 *        - a1*y[n-1] - a2*y[n-2]
 *
 * 这里用 z1/z2 保存历史状态
 */
struct Biquad {
    float b0, b1, b2;
    float a1, a2;

    // 延迟状态
    float z1 = 0.0f;
    float z2 = 0.0f;

    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
};


/*
 * ===============================
 * Envelope Follower（sample 域）
 * ===============================
 *
 * 用于能量包络提取：
 * - 上升快（attack）
 * - 下降慢（release）
 *
 * 注意：这是 sample 级的
 */
struct Envelope {
    float value = 0.0f;
    float attack;
    float release;

    Envelope() = default;

    // attack / release 以毫秒为单位
    Envelope(float attackMs, float releaseMs) {
        attack  = std::exp(-1.0f / (attackMs  * 0.001f * SAMPLE_RATE));
        release = std::exp(-1.0f / (releaseMs * 0.001f * SAMPLE_RATE));
    }

    inline float process(float x) {
        float v = std::fabs(x);
        if (v > value)
            value = attack * value + (1.0f - attack) * v;
        else
            value = release * value + (1.0f - release) * v;
        return value;
    }
};


/*
 * ===============================
 * Frame-domain Smoother（固定 dt 版本）
 * ===============================
 *
 * - dt 在构造时固定（秒）
 * - attack / release 用毫秒描述
 * - 运行期无 exp，开销极小
 *
 * 适合：
 * - 固定 20~30ms 的视觉线程
 * - 频谱 / VU / 能量显示
 */
struct FrameSmoother {
    float value = 0.0f;

    // 已经计算好的平滑系数
    float attackAlpha;
    float releaseAlpha;

    FrameSmoother() = default;

    /*
     * dtMs       : 帧间隔（毫秒），例如 20 / 30
     * attackMs   : 上升时间常数
     * releaseMs  : 下降时间常数
     */
    FrameSmoother(float dtMs,
                  float attackMs,
                  float releaseMs)
    {
        float dt = dtMs * 0.001f;

        // 防御：避免除零
        if (attackMs <= 0.0f) {
            attackAlpha = 1.0f;
        } else {
            float Ta = attackMs * 0.001f;
            attackAlpha = 1.0f - std::exp(-dt / Ta);
        }

        if (releaseMs <= 0.0f) {
            releaseAlpha = 1.0f;
        } else {
            float Tr = releaseMs * 0.001f;
            releaseAlpha = 1.0f - std::exp(-dt / Tr);
        }
    }

    inline float process(float x) {
        if (x > value)
            value += attackAlpha * (x - value);
        else
            value += releaseAlpha * (x - value);

        return value;
    }
};


/*
 * ===============================
 * 四阶带通滤波器
 * ===============================
 *
 * 两个二阶 biquad 串联
 * 用于 filterbank 频谱分析
 */
struct MultiBiquad {
    Biquad stages[2];

    float process(float x) {
        x = stages[0].process(x);
        x = stages[1].process(x);
        return x;
    }
};


/*
 * =========================================================
 * WaveformProcessor
 * =========================================================
 *
 * 将 AUDIO_BLOCK 下采样成 VISUAL_POINTS
 * 仅用于波形显示（不是频谱）
 */
class WaveformProcessor {
public:
    void processBlock(const AudioBlock& blk) {
        constexpr int step = AUDIO_BLOCK / VISUAL_POINTS;
        static_assert(AUDIO_BLOCK % VISUAL_POINTS == 0);

        auto* p = waveformSnapshot.beginWrite();
        if (!p) return;

        for (int i = 0; i < VISUAL_POINTS; i++) {
            float sum = 0.0f;
            for (int j = 0; j < step; j++)
                sum += blk.samples[i * step + j];

            p->waveform[i] = sum / step;
        }

        waveformSnapshot.endWrite(p);
    }

    PinnedSnapshot<WaveformFrame> waveformSnapshot;
};


/*
 * =========================================================
 * FrameSpectrumProcessor4th
 * =========================================================
 *
 * 频谱方案 1：
 * - 四阶带通 filterbank
 * - 每帧 RMS 能量
 * - frame smoothing（linear 域）
 */
class FrameSpectrumProcessor4th {
public:
    FrameSpectrumProcessor4th() {
        initFilters();

        // 每个频段一个 frame smoother
        for (int i = 0; i < SPECTRUM_BANDS; i++)
            smoothers[i] = FrameSmoother(EXTRACT_INTERVAL_MS,30.f, 120.f);
    }

    const float* getFrequencies() const { return freqs; }

    void processBlock(const AudioBlock& blk) {
        float energy[SPECTRUM_BANDS] = {};

        // sample 域能量累计
        for (int i = 0; i < AUDIO_BLOCK; i++) {
            float x = blk.samples[i];
            for (int b = 0; b < SPECTRUM_BANDS; b++) {
                float y = filters[b].process(x);
                energy[b] += y * y;
            }
        }

        auto* pFrame = spectrumSnapshot.beginWrite();
        if (!pFrame) return;

        for (int b = 0; b < SPECTRUM_BANDS; b++) {
            // RMS（linear）
            float rms = std::sqrt(energy[b] / AUDIO_BLOCK);

            // ⭐ frame 间平滑发生在这里
            float lin = smoothers[b].process(rms);

            // 最后才转 dB
            pFrame->bands[b] = 20.0f * std::log10f(lin + 1e-6f);
        }

        spectrumSnapshot.endWrite(pFrame);
    }

    PinnedSnapshot<SpectrumFrame> spectrumSnapshot;

private:
    MultiBiquad filters[SPECTRUM_BANDS];
    FrameSmoother smoothers[SPECTRUM_BANDS];
    float freqs[SPECTRUM_BANDS]{};

    void initFilters() {
        float fMin = 50.0f;
        float fMax = 12000.0f;
        float octaves = std::log2(fMax / fMin);
        float bandsPerOctave = SPECTRUM_BANDS / octaves;

        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            float t = (float)i / (SPECTRUM_BANDS - 1);
            float fc = fMin * std::pow(fMax / fMin, t);
            freqs[i] = fc;

            float Q = 1.0f / (std::pow(2.0f, 1.0f / bandsPerOctave) - 1.0f);
            for (int s = 0; s < 2; s++)
                makeBandpass(filters[i].stages[s], fc, Q);
        }
    }

    void makeBandpass(Biquad& bq, float fc, float q) {
        float w0 = 2.0f * M_PI * fc / SAMPLE_RATE;
        float alpha = std::sinf(w0) / (2.0f * q);

        float b0 = alpha;
        float b1 = 0.0f;
        float b2 = -alpha;
        float a0 = 1.0f + alpha;
        float a1 = -2.0f * std::cosf(w0);
        float a2 = 1.0f - alpha;

        bq.b0 = b0 / a0;
        bq.b1 = b1 / a0;
        bq.b2 = b2 / a0;
        bq.a1 = a1 / a0;
        bq.a2 = a2 / a0;
    }
};


/*
 * =========================================================
 * SpectrumProcessorFFT
 * =========================================================
 *
 * 频谱方案 2：
 * - Hann 窗
 * - FFT
 * - bin → band
 * - frame smoothing（linear 域）
 */
class SpectrumProcessorFFT {
public:
    SpectrumProcessorFFT() {
        N = AUDIO_BLOCK;

        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            float t = (float)i / SPECTRUM_BANDS;
            bandBins[i] = std::min((int)(t * (N / 2)), N / 2 - 1);
            smoothers[i] = FrameSmoother(EXTRACT_INTERVAL_MS,30.f, 120.f);
        }

        // Hann window
        window.resize(N);
        for (int i = 0; i < N; i++)
            window[i] = 0.5f * (1.0f - std::cos(2.0f * M_PI * i / (N - 1)));
    }

    void processBlock(const AudioBlock& blk) {
        std::vector<std::complex<float>> x(N);

        for (int i = 0; i < N; i++)
            x[i] = { blk.samples[i] * window[i], 0.0f };

        fftRecursive(x);

        auto* pFrame = spectrumSnapshot.beginWrite();
        if (!pFrame) return;

        for (int b = 0; b < SPECTRUM_BANDS; b++) {
            float mag = std::abs(x[bandBins[b]]) / N;
            float lin = smoothers[b].process(mag);
            pFrame->bands[b] = 20.0f * std::log10f(lin + 1e-6f);
        }

        spectrumSnapshot.endWrite(pFrame);
    }

    PinnedSnapshot<SpectrumFrame> spectrumSnapshot;

private:
    int N;
    int bandBins[SPECTRUM_BANDS];
    FrameSmoother smoothers[SPECTRUM_BANDS];
    std::vector<float> window;

    // 递归 radix-2 FFT
    void fftRecursive(std::vector<std::complex<float>>& a) {
        int n = a.size();
        if (n <= 1) return;

        std::vector<std::complex<float>> a0(n / 2), a1(n / 2);
        for (int i = 0; i < n / 2; i++) {
            a0[i] = a[i * 2];
            a1[i] = a[i * 2 + 1];
        }

        fftRecursive(a0);
        fftRecursive(a1);

        for (int k = 0; k < n / 2; k++) {
            std::complex<float> w(
                    std::cos(-2.0f * M_PI * k / n),
                    std::sin(-2.0f * M_PI * k / n)
            );
            auto t = w * a1[k];
            a[k] = a0[k] + t;
            a[k + n / 2] = a0[k] - t;
        }
    }
};
