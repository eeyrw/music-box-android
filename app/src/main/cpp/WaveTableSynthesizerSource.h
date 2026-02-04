#ifndef MUSICBOX_SYNTH_H
#define MUSICBOX_SYNTH_H

#include <array>
#include <TappableAudioSource.h>
#include <MonoToStereo.h>
#include <Player.h>
#include "LockFreeQueue.h"

#include <cstdint>

struct SynthEvent {
    enum Type : uint8_t {
        NoteOn,
        NoteOff
    } type;

    uint8_t note;
};


static constexpr size_t kEventQueueSize = 256;

static constexpr int kSpectrumBlock = 1024;
static float spectrumTap[kSpectrumBlock];
static int spectrumTapPtr = 0;
extern "C" void pushAudio(const float *data);


class WaveTableSynthesizerSource : public IRenderableAudio {
public:
    float waveformData[256];
    int waveformDataPtr = 0;

    WaveTableSynthesizerSource(int32_t sampleRate, int32_t channelCount) {
        mChannelCount = channelCount;
        mSampleRate = sampleRate;
        PlayerInit(&player);
    }

    // 主线程或 MIDI 线程调用
    void postNoteOn(uint8_t note) {
        SynthEvent evt{SynthEvent::NoteOn, note};
        eventQueue.push(evt);
    }

    void noteOn(uint8_t note) {
        NoteOn(&player.mainSynthesizer, note);
    };

    void resetSynthesizer() {
        PlayerResetSynthesizer(&player);
    }

    // From IRenderableAudio
    void renderAudio(float *audioData, int32_t numFrames) override {

        SynthEvent evt;
        while (eventQueue.pop(evt)) {
            if (evt.type == SynthEvent::NoteOn) {
                NoteOn(&player.mainSynthesizer, evt.note);
            }
        }

        for (int i = 0; i < numFrames; ++i) {
            Player32kProc(&player);
            audioData[i] = (float) (player.mainSynthesizer.mixOut >> 8) / (float) 32768 * 0.5;


            // ======== 频谱 tap（新增） ========
            spectrumTap[spectrumTapPtr++] = audioData[i];
            if (spectrumTapPtr == kSpectrumBlock) {
                pushAudio(spectrumTap);   // 🚀 非阻塞
                spectrumTapPtr = 0;
            }
            // =================================

            PlayerProcess(&player);
            if (i % 4 == 0) {
                if (waveformDataPtr < sizeof(waveformData)) {
                    waveformData[waveformDataPtr] = audioData[i];
                    waveformDataPtr++;
                } else {
                    waveformDataPtr = 0;
                }
            }

        }


        constexpr int kChannelCountStereo = 2;
        // We assume that audioData has sufficient frames to hold the stereo output, so copy each
        // frame in the input to the output twice, working our way backwards through the input array
        // e.g. 123 => 112233
        if (mChannelCount == oboe::ChannelCount::Stereo) {
            for (int i = numFrames - 1; i >= 0; --i) {
                audioData[i * kChannelCountStereo] = audioData[i];
                audioData[i * kChannelCountStereo + 1] = audioData[i];
            }
        }
    };

    virtual ~WaveTableSynthesizerSource() {
    }

private:
    // Rendering objects
    int mChannelCount;
    int mSampleRate;
    Player player;
    LockFreeQueue<SynthEvent, kEventQueueSize> eventQueue;
};

#endif //MEGADRONE_SYNTH_H
