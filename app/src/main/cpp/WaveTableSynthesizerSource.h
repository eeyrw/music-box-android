#ifndef MUSICBOX_SYNTH_H
#define MUSICBOX_SYNTH_H

#include <array>
#include <TappableAudioSource.h>
#include <MonoToStereo.h>

class WaveTableSynthesizerSource : public TappableAudioSource
{
public:
    WaveTableSynthesizerSource(int32_t sampleRate, int32_t channelCount) : TappableAudioSource(sampleRate, channelCount)
    {
        PlayerInit(&player);
    }

    void tap(bool isOn) override
    {
        if (isOn)
            PlayerPlay(&player);
        else
            PlayerInit(&player);
    };

    void noteOn(uint8_t note)
    {
        NoteOn(&player,note);
    };

    // From IRenderableAudio
    void renderAudio(float *audioData, int32_t numFrames) override
    {
        for (int i = 0; i < numFrames; ++i)
        {
            Player32kProc(&player);
            audioData[i] = (int16_t)(player.mainSynthesizer.mixOut >> 8);
            PlayerProcess(&player);
        }

        constexpr int kChannelCountStereo = 2;
        // We assume that audioData has sufficient frames to hold the stereo output, so copy each
        // frame in the input to the output twice, working our way backwards through the input array
        // e.g. 123 => 112233
        if (mChannelCount == oboe::ChannelCount::Stereo)
        {
            for (int i = numFrames - 1; i >= 0; --i)
            {
                audioData[i * kChannelCountStereo] = audioData[i];
                audioData[i * kChannelCountStereo + 1] = audioData[i];
            }
        }
    };

    virtual ~WaveTableSynthesizerSource()
    {
    }

private:
    // Rendering objects
    int32_t mSampleRate = kDefaultSampleRate;
    Player player;
};

#endif //MEGADRONE_SYNTH_H
