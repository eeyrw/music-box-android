#pragma once
#include <cmath>
#include <vector>
#include <complex>
#include <algorithm>
#include <cstring>
#include "PinnedSnapshot.h"

/*
 * =========================================================
 * 全局配置
 * =========================================================
 */

#define SAMPLE_RATE        32000
#define AUDIO_BLOCK        1024
#define SPECTRUM_BANDS     128
#define VISUAL_POINTS      256
#define EXTRACT_INTERVAL_MS 20

/*
 * =========================================================
 * 音频与可视化数据结构
 * =========================================================
 */

struct AudioBlock {
    float samples[AUDIO_BLOCK];
};

struct SpectrumFrame {
    float bands[SPECTRUM_BANDS];
};

struct WaveformFrame {
    float waveform[VISUAL_POINTS];
};

/*
 * =========================================================
 * 二阶 IIR 滤波器（Direct Form I）
 * =========================================================
 *
 * ⚠️ 有状态，历史样本 z1/z2 必须能 reset
 */
struct Biquad {
    float b0{}, b1{}, b2{};
    float a1{}, a2{};

    float z1 = 0.0f;
    float z2 = 0.0f;

    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    // ⭐ reset 清空滤波器历史状态
    inline void reset() {
        z1 = 0.0f;
        z2 = 0.0f;
    }
};

/*
 * =========================================================
 * 四阶带通滤波器 = 两个 biquad 串联
 * =========================================================
 */
struct MultiBiquad {
    Biquad stages[2];

    inline float process(float x) {
        x = stages[0].process(x);
        x = stages[1].process(x);
        return x;
    }

    // ⭐ reset 清空两个二阶滤波器历史状态
    inline void reset() {
        stages[0].reset();
        stages[1].reset();
    }
};

/*
 * =========================================================
 * 包络跟随器（sample 域）
 * =========================================================
 */
struct Envelope {
    float value = 0.0f;
    float attack{};
    float release{};

    Envelope() = default;

    Envelope(float attackMs, float releaseMs) {
        attack = std::exp(-1.0f / (attackMs * 0.001f * SAMPLE_RATE));
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

    // ⭐ reset = 清零包络历史
    inline void reset(float v = 0.0f) {
        value = v;
    }
};

/*
 * =========================================================
 * Frame-domain 平滑器（固定 dt）
 * =========================================================
 */
struct FrameSmoother {
    float value = 0.0f;
    float attackAlpha{};
    float releaseAlpha{};

    FrameSmoother() = default;

    FrameSmoother(float dtMs, float attackMs, float releaseMs) {
        float dt = dtMs * 0.001f;

        if (attackMs <= 0.0f)
            attackAlpha = 1.0f;
        else
            attackAlpha = 1.0f - std::exp(-dt / (attackMs * 0.001f));

        if (releaseMs <= 0.0f)
            releaseAlpha = 1.0f;
        else
            releaseAlpha = 1.0f - std::exp(-dt / (releaseMs * 0.001f));
    }

    inline float process(float x) {
        if (x > value)
            value += attackAlpha * (x - value);
        else
            value += releaseAlpha * (x - value);
        return value;
    }

    // ⭐ reset 清空跨帧历史
    inline void reset(float v = 0.0f) {
        value = v;
    }
};

/*
 * =========================================================
 * 波形处理器（触发 + 采样）
 * =========================================================
 */
class WaveformProcessor {
public:
    float triggerLevel = 0.1f;
    float minAvailableRatio = 0.5f;

    void processBlock(const AudioBlock &blk) {
        int triggerPos = findTrigger(blk);
        int available = AUDIO_BLOCK - triggerPos;
        int minAvailable = (int) (AUDIO_BLOCK * minAvailableRatio);

        if (available < minAvailable)
            return;

        float step = (float) available / VISUAL_POINTS;

        auto *p = waveformSnapshot.beginWrite();
        if (!p) return;

        for (int i = 0; i < VISUAL_POINTS; i++) {
            int idx = triggerPos + (int) (i * step);
            if (idx >= AUDIO_BLOCK)
                idx = AUDIO_BLOCK - 1;
            p->waveform[i] = blk.samples[idx];
        }

        waveformSnapshot.endWrite(p);
    }

    // ⭐ reset 清空 waveform 输出，避免残影
    void reset() {
        if (auto *p = waveformSnapshot.beginWrite()) {
            std::memset(p->waveform, 0, sizeof(p->waveform));
            waveformSnapshot.endWrite(p);
        }
    }

    PinnedSnapshot<WaveformFrame> waveformSnapshot;

private:
    int findTrigger(const AudioBlock &blk) {
        for (int i = 1; i < AUDIO_BLOCK; i++) {
            if (blk.samples[i - 1] < triggerLevel &&
                blk.samples[i] >= triggerLevel)
                return i;
        }
        return 0;
    }
};


/*
 * =========================================================
 * VuMeterProcessor
 * =========================================================
 *
 * - 计算 RMS / Peak / PeakHold（dBFS）
 * - 复用 FrameSmoother 做平滑
 * - GUI 只读取 PinnedSnapshot
 * - 支持 reset
 */
struct VuLevel {
    float rmsDb = -60.0f;
    float peakDb = -60.0f;
    float peakHoldDb = -60.0f;
};

class VuMeterProcessor {
public:
    VuMeterProcessor() {
        rmsSmoother = FrameSmoother(EXTRACT_INTERVAL_MS, 10.f, 50.f);
        peakSmoother = FrameSmoother(EXTRACT_INTERVAL_MS, 10.f, 50.f);
    }

    /*
     * 处理每个音频 block
     */
    void processBlock(const AudioBlock &blk) {
        float sumSq = 0.0f;
        float peak = 0.0f;

        for (int i = 0; i < AUDIO_BLOCK; i++) {
            float s = blk.samples[i];
            sumSq += s * s;
            peak = std::max(peak, std::fabs(s));
        }

        // 线性 RMS / Peak 平滑
        float rmsLin = rmsSmoother.process(std::sqrt(sumSq / AUDIO_BLOCK));
        float peakLin = peakSmoother.process(peak);

        float rmsDb = linearToDb(rmsLin);
        float peakDb = linearToDb(peakLin);

        uint64_t nowMs = getTimeMs();

        // Peak Hold
        if (peakDb >= peakHoldDb) {
            peakHoldDb = peakDb;
            lastPeakTimeMs = nowMs;
        } else if (nowMs - lastPeakTimeMs > PEAK_HOLD_MS) {
            peakHoldDb -= PEAK_FALL_DB;
            if (peakHoldDb < peakDb)
                peakHoldDb = peakDb;
        }

        // 写入 snapshot，GUI 直接读取
        auto *pFrame = snapshot.beginWrite();
        if (!pFrame) return;

        pFrame->rmsDb = rmsDb;
        pFrame->peakDb = peakDb;
        pFrame->peakHoldDb = peakHoldDb;

        snapshot.endWrite(pFrame);
    }

    /*
     * 重置所有状态
     */
    void reset() {
        rmsSmoother.reset(0.0f);
        peakSmoother.reset(0.0f);
        peakHoldDb = DB_FLOOR;
        lastPeakTimeMs = 0;

        if (auto *pFrame = snapshot.beginWrite()) {
            std::memset(pFrame, 0, sizeof(VuLevel));
            snapshot.endWrite(pFrame);
        }
    }

    PinnedSnapshot<VuLevel> snapshot;

private:
    FrameSmoother rmsSmoother;
    FrameSmoother peakSmoother;

    float peakHoldDb = DB_FLOOR;
    uint64_t lastPeakTimeMs = 0;

    static constexpr float DB_FLOOR = -60.0f;
    static constexpr long PEAK_HOLD_MS = 600;   // ms
    static constexpr float PEAK_FALL_DB = 1.2f;

    inline float linearToDb(float x) {
        return 20.0f * std::log10(std::max(1e-6f, x));
    }

    // 简单时间获取函数，可根据实际平台改
    inline uint64_t getTimeMs() {
        // C++11 chrono
        using namespace std::chrono;
        return duration_cast<milliseconds>(
                steady_clock::now().time_since_epoch()
        ).count();
    }
};


/*
 * =========================================================
 * FrameSpectrumProcessor4th（四阶 filterbank + RMS + frame smoother）
 * =========================================================
 */
class FrameSpectrumProcessor4th {
public:
    FrameSpectrumProcessor4th() {
        initFilters();
        for (int i = 0; i < SPECTRUM_BANDS; i++)
            smoothers[i] = FrameSmoother(EXTRACT_INTERVAL_MS, 20.f, 100.f);
    }

    const float *getFrequencies() const { return freqs; }

    void processBlock(const AudioBlock& blk) {
        float energy[SPECTRUM_BANDS] = {};

        // 样本级能量累加
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
            float rms = std::sqrt(energy[b] / AUDIO_BLOCK);
            float lin = smoothers[b].process(rms);
            pFrame->bands[b] = 20.0f * std::log10f(lin + 1e-6f);
        }

        spectrumSnapshot.endWrite(pFrame);
    }

    // ⭐ reset = 清空滤波历史 + 平滑历史 + GUI 输出
    void reset() {
        for (int b = 0; b < SPECTRUM_BANDS; b++) {
            filters[b].reset();
            smoothers[b].reset(0.0f);
        }

        if (auto *pFrame = spectrumSnapshot.beginWrite()) {
            std::memset(pFrame->bands, 0, sizeof(pFrame->bands));
            spectrumSnapshot.endWrite(pFrame);
        }
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
 * SpectrumProcessorFFT（Hann window + FFT + frame smoother）
 * =========================================================
 */
class SpectrumProcessorFFT {
public:
    SpectrumProcessorFFT() {
        N = AUDIO_BLOCK;

        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            float t = (float)i / SPECTRUM_BANDS;
            bandBins[i] = std::min((int) (t * (N / 2)), N / 2 - 1);
            smoothers[i] = FrameSmoother(EXTRACT_INTERVAL_MS, 30.f, 120.f);
        }

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

    // ⭐ reset = 清空 frame smoother 历史 + GUI 输出
    void reset() {
        for (int b = 0; b < SPECTRUM_BANDS; b++)
            smoothers[b].reset(0.0f);

        if (auto *pFrame = spectrumSnapshot.beginWrite()) {
            std::memset(pFrame->bands, 0, sizeof(pFrame->bands));
            spectrumSnapshot.endWrite(pFrame);
        }
    }

    PinnedSnapshot<SpectrumFrame> spectrumSnapshot;

private:
    int N{};
    int bandBins[SPECTRUM_BANDS]{};
    FrameSmoother smoothers[SPECTRUM_BANDS];
    std::vector<float> window;

    void fftRecursive(std::vector<std::complex<float>> &a) {
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
