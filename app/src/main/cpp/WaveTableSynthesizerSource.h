#ifndef MUSICBOX_SYNTH_H
#define MUSICBOX_SYNTH_H

#include <array>
#include <TappableAudioSource.h>

#include <Oscillator.h>
#include <Mixer.h>
#include <MonoToStereo.h>

#include "WaveTableSynthesizer.h"

class WaveTableSynthesizerSource : public TappableAudioSource
{
public:
    WaveTableSynthesizerSource(int32_t sampleRate, int32_t channelCount) : TappableAudioSource(sampleRate, channelCount)
    {

        if (mChannelCount == oboe::ChannelCount::Stereo)
        {
            mOutputStage = &mConverter;
        }
    }

    void tap(bool isOn) override{

    };

    // From IRenderableAudio
    void renderAudio(float *audioData, int32_t numFrames) override
    {
        mOutputStage->renderAudio(audioData, numFrames);
    };

    virtual ~WaveTableSynthesizerSource()
    {
    }

private:
    // Rendering objects
    WaveTableSynthesizer synthesizer;
    MonoToStereo mConverter = MonoToStereo(&synthesizer);
    IRenderableAudio *mOutputStage; // This will point to either the mixer or converter, so it needs to be raw
};

#endif //MEGADRONE_SYNTH_H
