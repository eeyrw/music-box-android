#ifndef MUSICBOX_ENGINE_H
#define MUSICBOX_ENGINE_H

#include <oboe/Oboe.h>
#include <vector>

#include <DefaultAudioStreamCallback.h>
#include <TappableAudioSource.h>
#include <IRestartable.h>
#include "WaveTableSynthesizerSource.h"

using namespace oboe;

class MusicBoxEngine : public IRestartable
{

public:
    MusicBoxEngine(std::vector<int> cpuIds);

    virtual ~MusicBoxEngine() = default;

    void resetSynthesizer();

    void noteOn(uint8_t note);

    void pause(bool isPause);

    void readWaveformData(const float *data);

    // from IRestartable
    virtual void restart() override;

private:
    oboe::ManagedStream mStream;
    std::shared_ptr<WaveTableSynthesizerSource> mAudioSource;
    std::unique_ptr<DefaultAudioStreamCallback> mCallback;

    oboe::Result createPlaybackStream();
    void createCallback(std::vector<int> cpuIds);
    void start();
};

#endif //MUSICBOX_ENGINE_H
