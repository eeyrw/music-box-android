#ifndef MUSICBOX_SYNTH_H
#define MUSICBOX_SYNTH_H

#include <array>
#include <TappableAudioSource.h>
#include <MonoToStereo.h>
#include <Player.h>
#include "LockFreeQueue.h"
#include "PinnedSnapshot.h"
#include "AudioVisualCalc.h"

#include <cstdint>

struct SynthEvent {
    enum Type : uint8_t {
        NoteOn,
        NoteOff
    } type;

    uint8_t note;
};





extern "C" void pushAudio(const float *data);


class WaveTableSynthesizerSource : public IRenderableAudio {
public:

    PinnedSnapshot<AudioBlock> visualInputSnapshot;

    WaveTableSynthesizerSource(int32_t sampleRate, int32_t channelCount){
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


        for (int i = 0; i < numFrames; ++i) {
            while (eventQueue.pop(evt)) {
                if (evt.type == SynthEvent::NoteOn) {
                    NoteOn(&player.mainSynthesizer, evt.note);
                }
            }
            Player32kProc(&player);
            audioData[i] = (float) (player.mainSynthesizer.mixOut >> 8) / (float) 32768 * 0.5;

            // ======== 频谱 tap（新增） ========
            pcmSamples[rawPCMTapPtr++] = audioData[i];
            if (rawPCMTapPtr == 1024) {
                auto* block = visualInputSnapshot.beginWrite();
                memcpy(block->samples,pcmSamples,sizeof(block->samples));
                visualInputSnapshot.endWrite(block);
                rawPCMTapPtr = 0;
            }
            // =================================
            PlayerProcess(&player);
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
    LockFreeQueue<SynthEvent, 32> eventQueue;
    float pcmSamples[1024];
    int rawPCMTapPtr = 0;
};

#endif //MEGADRONE_SYNTH_H
