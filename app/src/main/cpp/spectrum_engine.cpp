#include <jni.h>
#include <cmath>
#include <thread>
#include <atomic>
#include <vector>
#include <cstring>
#include <chrono>

#include "LockFreeQueue.h"

#define SAMPLE_RATE 32000
#define AUDIO_BLOCK 1024
#define SPECTRUM_BANDS 128

// ======================
// Audio block
// ======================
struct AudioBlock {
    float samples[AUDIO_BLOCK];
};

// ======================
// Spectrum output
// ======================
struct SpectrumFrame {
    float bands[SPECTRUM_BANDS];
};

// ======================
// Lock-free queues
// ======================
static LockFreeQueue<AudioBlock, 2> gAudioQueue;
static LockFreeQueue<SpectrumFrame, 2> gSpectrumQueue;

// ======================
// Simple Biquad
// ======================
struct Biquad {
    float b0, b1, b2;
    float a1, a2;
    float z1 = 0.0f, z2 = 0.0f;

    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
};

// ======================
// Envelope follower
// ======================
struct Envelope {
    float value = 0.0f;
    float attack;
    float release;

    Envelope() = default;

    Envelope(float aMs, float rMs) {
        attack = std::exp(-1.0f / (aMs * 0.001f * SAMPLE_RATE));
        release = std::exp(-1.0f / (rMs * 0.001f * SAMPLE_RATE));
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


// ======================
// 四阶滤波器组（每频段两级二阶）
// ======================
struct MultiBiquad {
    Biquad stages[2]; // 两个二阶 → 四阶
    float process(float x) {
        float y = x;
        for (int i = 0; i < 2; i++)
            y = stages[i].process(y);
        return y;
    }
};

class SpectrumProcessor4th {
public:
    SpectrumProcessor4th() {
        initFilters();
        for (int i = 0; i < SPECTRUM_BANDS; i++)
            envelopes[i] = Envelope(2.0f, 5.0f); // attack/release 可调
    }

    const float *getFrequencies() const { return freqs; }

    void processBlock(const AudioBlock &blk) {
        for (int i = 0; i < AUDIO_BLOCK; i++) {
            float x = blk.samples[i];
            for (int b = 0; b < SPECTRUM_BANDS; b++) {
                float y = filters[b].process(x);
                accum[b] += envelopes[b].process(y);
            }
            sampleCount++;
            if (sampleCount >= frameSamples)
                emitFrame();
        }
    }

private:
    MultiBiquad filters[SPECTRUM_BANDS];
    Envelope envelopes[SPECTRUM_BANDS];
    float accum[SPECTRUM_BANDS]{};
    int sampleCount = 0;
    const int frameSamples = 1024;
    float freqs[SPECTRUM_BANDS]{};

    void emitFrame() {
        SpectrumFrame frame{};
        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            float v = accum[i] / frameSamples;
            frame.bands[i] = 20.0f * log10f(v + 1e-6f);
            accum[i] = 0.0f;
        }
        gSpectrumQueue.push(frame);
        sampleCount = 0;
    }

    void initFilters() {
        float fMin = 50.0f;
        float fMax = 12000.0f;
        float octaves = log2f(fMax / fMin);
        float bandsPerOctave = SPECTRUM_BANDS / octaves;

        for (int i = 0; i < SPECTRUM_BANDS; i++) {
            float t = (float) i / (SPECTRUM_BANDS - 1);
            float fc = fMin * powf(fMax / fMin, t);
            freqs[i] = fc;

            float Q = 1.0f / (powf(2.0f, 1.0f / bandsPerOctave) - 1.0f);

            // 两级二阶 → 四阶
            for (int stage = 0; stage < 2; stage++)
                makeBandpass(filters[i].stages[stage], fc, Q);
        }
    }

    void makeBandpass(Biquad &bq, float fc, float q) {
        float w0 = 2.0f * M_PI * fc / SAMPLE_RATE;
        float alpha = sinf(w0) / (2.0f * q);

        float b0 = alpha, b1 = 0.0f, b2 = -alpha;
        float a0 = 1.0f + alpha;
        float a1 = -2.0f * cosf(w0);
        float a2 = 1.0f - alpha;

        bq.b0 = b0 / a0;
        bq.b1 = b1 / a0;
        bq.b2 = b2 / a0;
        bq.a1 = a1 / a0;
        bq.a2 = a2 / a0;
    }
};

// ======================
// Worker thread
// ======================
static std::atomic<bool> gRunning{false};
static std::thread gWorker;

static void spectrumThread() {
    SpectrumProcessor4th proc;
    AudioBlock blk;
    while (gRunning.load()) {
        while (gAudioQueue.pop(blk)) {
            proc.processBlock(blk);
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
}

// ======================
// Audio callback entry
// ======================
extern "C" void pushAudio(const float *data) {
    AudioBlock blk;
    memcpy(blk.samples, data, sizeof(float) * AUDIO_BLOCK);
    gAudioQueue.push(blk);
}


// ======================
// JNI
// ======================
extern "C" JNIEXPORT void JNICALL

Java_com_yuan_midiplayer_SpectrumNative_start(JNIEnv *, jclass) {
    gRunning.store(true);
    gWorker = std::thread(spectrumThread);
}

extern "C" JNIEXPORT void JNICALL
Java_com_yuan_midiplayer_SpectrumNative_stop(JNIEnv *, jclass) {
    gRunning.store(false);
    if (gWorker.joinable())
        gWorker.join();
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_yuan_midiplayer_SpectrumNative_getSpectrum(JNIEnv *env, jclass) {
    SpectrumFrame frame;
    if (!gSpectrumQueue.pop(frame))
        return nullptr;

    jfloatArray arr = env->NewFloatArray(SPECTRUM_BANDS);
    env->SetFloatArrayRegion(arr, 0, SPECTRUM_BANDS, frame.bands);
    return arr;
}
