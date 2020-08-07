#ifndef MUSICBOX_ENGINE_H
#define MUSICBOX_ENGINE_H

#include <oboe/Oboe.h>
#include <vector>

#include <DefaultAudioStreamCallback.h>
#include <TappableAudioSource.h>
#include <IRestartable.h>

using namespace oboe;

class MusicBoxEngine : public IRestartable
{

public:
    MusicBoxEngine(std::vector<int> cpuIds);

    virtual ~MusicBoxEngine() = default;

    void tap(bool isDown);
    void noteOn(uint8_t note);

    // from IRestartable
    virtual void restart() override;

private:
    oboe::ManagedStream mStream;
    std::shared_ptr<TappableAudioSource> mAudioSource;
    std::unique_ptr<DefaultAudioStreamCallback> mCallback;

    oboe::Result createPlaybackStream();
    void createCallback(std::vector<int> cpuIds);
    void start();
};

#endif //MUSICBOX_ENGINE_H
