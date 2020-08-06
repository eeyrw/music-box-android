#ifndef MUSICBOX_SYNTH_H
#define MUSICBOX_SYNTH_H

#include <array>
#include <TappableAudioSource.h>

#include <Oscillator.h>
#include <Mixer.h>
#include <MonoToStereo.h>

constexpr int kNumOscillators = 50;
constexpr float kOscBaseFrequency = 116.0;
constexpr float kOscDivisor = 33;
constexpr float kOscAmplitude = 0.009;


class WaveTableSynthesizerSource : public TappableAudioSource {
public:

    Synth(int32_t sampleRate, int32_t channelCount) :
    TappableAudioSource(sampleRate, channelCount) {
        for (int i = 0; i < kNumOscillators; ++i) {
            mOscs[i].setSampleRate(mSampleRate);
            mOscs[i].setFrequency(kOscBaseFrequency + (static_cast<float>(i) / kOscDivisor));
            mOscs[i].setAmplitude(kOscAmplitude);
            mMixer.addTrack(&mOscs[i]);
        }
        if (mChannelCount == oboe::ChannelCount::Stereo) {
            mOutputStage =  &mConverter;
        } else {
            mOutputStage = &mMixer;
        }
    }

    void tap(bool isOn) override {
        for (auto &osc : mOscs) osc.setWaveOn(isOn);
    };

    // From IRenderableAudio
    void renderAudio(float *audioData, int32_t numFrames) override {
        mOutputStage->renderAudio(audioData, numFrames);
    };

    virtual ~Synth() {
    }
private:
    // Rendering objects
    std::array<Oscillator, kNumOscillators> mOscs;
    Mixer mMixer;
    MonoToStereo mConverter = MonoToStereo(&mMixer);
    IRenderableAudio *mOutputStage; // This will point to either the mixer or converter, so it needs to be raw
};


#endif //MEGADRONE_SYNTH_H
