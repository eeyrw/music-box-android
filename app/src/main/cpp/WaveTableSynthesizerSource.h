#ifndef MUSICBOX_SYNTH_H
#define MUSICBOX_SYNTH_H

#include <array>
#include <TappableAudioSource.h>
#include <MonoToStereo.h>
#include <Player.h>

class WaveTableSynthesizerSource : public IRenderableAudio {
public:
    float waveformData[256];
    int waveformDataPtr = 0;

    WaveTableSynthesizerSource(int32_t sampleRate, int32_t channelCount) {
        mChannelCount = channelCount;
        mSampleRate = sampleRate;
        PlayerInit(&player);
    }

    void noteOn(uint8_t note) {
        NoteOn(&player.mainSynthesizer, note);
    };

    void resetSynthesizer() {
        PlayerResetSynthesizer(&player);
    }

    // From IRenderableAudio
    void renderAudio(float *audioData, int32_t numFrames) override {
        for (int i = 0; i < numFrames; ++i) {
            Player32kProc(&player);
            audioData[i] = (float) (player.mainSynthesizer.mixOut >> 8) / (float) 32768 * 0.5;
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
};

#endif //MEGADRONE_SYNTH_H
