/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef WAVETABLESYNTHESIZER_H_H
#define WAVETABLESYNTHESIZER_H_H

#include <cstdint>
#include <atomic>
#include <math.h>
#include <memory>
#include <IRenderableAudio.h>
#include <Player.h>

class WaveTableSynthesizer : public IRenderableAudio
{

public:
    WaveTableSynthesizer()
    {
        PlayerInit(&player);
        PlayerPlay(&player);
    }

    ~WaveTableSynthesizer() = default;

    void setWaveOn(bool isWaveOn)
    {
        mIsWaveOn.store(isWaveOn);
    };

    void setSampleRate(int32_t sampleRate)
    {
        mSampleRate = sampleRate;
    };

    // From IRenderableAudio
    void renderAudio(int16_t *audioData, int32_t numFrames) override
    {
        for (int i = 0; i < numFrames; ++i)
        {
            Player32kProc(&player);
            audioData[i] = (int16_t)(player.mainSynthesizer.mixOut >> 8);
            PlayerProcess(&player);
        }
    };

private:
    std::atomic<bool> mIsWaveOn{false};
    int32_t mSampleRate = kDefaultSampleRate;
    Player player;
};

#endif //WAVETABLESYNTHESIZER_H_H
