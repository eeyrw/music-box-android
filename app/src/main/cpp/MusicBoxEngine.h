#ifndef MUSICBOX_ENGINE_H
#define MUSICBOX_ENGINE_H

#include <oboe/Oboe.h>
#include <vector>

#include <DefaultAudioStreamCallback.h>
#include <TappableAudioSource.h>
#include <IRestartable.h>
#include "WaveTableSynthesizerSource.h"

#include <thread>
#include <atomic>

using namespace oboe;

class MusicBoxEngine : public IRestartable
{

public:
    MusicBoxEngine(std::vector<int> cpuIds);

    virtual ~MusicBoxEngine() = default;

    void resetSynthesizer();

    void noteOn(uint8_t note);

    void pause(bool isPause);

    // from IRestartable
    virtual void restart() override;

    void readWaveformData(const float *data);
    void readSpectrumData(const float *data);

    FrameSpectrumProcessor4th spectrumProcessor;
    WaveformProcessor waveformProcessor;

private:
    oboe::ManagedStream mStream;
    std::shared_ptr<WaveTableSynthesizerSource> mAudioSource;
    std::unique_ptr<DefaultAudioStreamCallback> mCallback;

    std::atomic<bool> visualCalcRunning{false};
    std::thread visualCalcWorker;


    oboe::Result createPlaybackStream();
    void createCallback(std::vector<int> cpuIds);
    void start();


    void runVisualCalc();
    void stopVisualCalc();
    void visualCalcThread();
};

#endif //MUSICBOX_ENGINE_H
