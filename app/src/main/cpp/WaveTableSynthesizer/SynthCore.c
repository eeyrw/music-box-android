#include "SynthCore.h"
#include <stdint.h>
#include <stdio.h>
#include "WaveTable_Celesta_C5.h"
#include "WaveTable_Celesta_C6.h"
#include "EnvelopeTable.h"
#include <pthread.h>
#include <logging_macros.h>

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

void SynthInit(volatile Synthesizer *synth) {
    SoundUnit *soundUnits = synth->SoundUnitList;
    for (uint8_t i = 0; i < POLY_NUM; i++) {
        soundUnits[i].increment = 0;
        soundUnits[i].wavetablePos = 0;
        soundUnits[i].envelopeLevel = 0;
        soundUnits[i].envelopePos = 0;
        soundUnits[i].val = 0;
        soundUnits[i].waveTableAddress = (volatile int16_t *) WaveTable_Celesta_C5;
        soundUnits[i].waveTableLen = WAVETABLE_CELESTA_C5_LEN;
        soundUnits[i].waveTableLoopLen = WAVETABLE_CELESTA_C5_LOOP_LEN;
        soundUnits[i].waveTableAttackLen = WAVETABLE_CELESTA_C5_ATTACK_LEN;
    }
    synth->lastSoundUnit = 0;
}

//#ifdef RUN_TEST
void NoteOnC(volatile Synthesizer *synth, uint8_t note) {
    int rc;
    //disable_interrupts();
    rc = pthread_mutex_lock(&mutex);
    uint32_t lastSoundUnit = synth->lastSoundUnit;
    SoundUnit *soundUnits = synth->SoundUnitList;

    // LOGD("NoteOnC pthread_mutex_lock() %d", rc);

    soundUnits[lastSoundUnit].increment = WaveTable_Celesta_C5_Increment[note & 0x7F];
    soundUnits[lastSoundUnit].wavetablePos = 0;
    soundUnits[lastSoundUnit].waveTableAddress = (volatile int16_t *) WaveTable_Celesta_C5;
    soundUnits[lastSoundUnit].waveTableLen = WAVETABLE_CELESTA_C5_LEN;
    soundUnits[lastSoundUnit].waveTableLoopLen = WAVETABLE_CELESTA_C5_LOOP_LEN;
    soundUnits[lastSoundUnit].waveTableAttackLen = WAVETABLE_CELESTA_C5_ATTACK_LEN;
    soundUnits[lastSoundUnit].envelopeLevel = 255;
    soundUnits[lastSoundUnit].envelopePos = 0;
    //enable_interrupts();


    lastSoundUnit++;
    if (lastSoundUnit == POLY_NUM)
        lastSoundUnit = 0;

    synth->lastSoundUnit = lastSoundUnit;
    rc = pthread_mutex_unlock(&mutex);
    // LOGD("NoteOnC pthread_mutex_unlock() %d", rc);

}

void SynthC(volatile Synthesizer *synth) {
    int rc;
    rc = pthread_mutex_lock(&mutex);
    synth->mixOut = 0;
    int16_t *pWaveTable;
    uint32_t waveTablePosInt;
    SoundUnit *soundUnits = synth->SoundUnitList;

    // LOGD("SynthC pthread_mutex_lock() %d", rc);

    for (uint32_t i = 0; i < POLY_NUM; i++) {
        if (soundUnits[i].envelopeLevel != 0) {
            pWaveTable = (int16_t *) soundUnits[i].waveTableAddress;
            waveTablePosInt = (soundUnits[i].wavetablePos) >> 8;
            soundUnits[i].val =
                    ((int32_t) soundUnits[i].envelopeLevel) * pWaveTable[waveTablePosInt];
            soundUnits[i].sampleVal = pWaveTable[waveTablePosInt];
            uint32_t waveTablePos = soundUnits[i].increment +
                                    soundUnits[i].wavetablePos;

            if (waveTablePos >= soundUnits[i].waveTableLen << 8)
                waveTablePos -= soundUnits[i].waveTableLoopLen << 8;
            soundUnits[i].wavetablePos = waveTablePos;
            synth->mixOut += soundUnits[i].val;
        }
    }
    rc = pthread_mutex_unlock(&mutex);
    // LOGD("SynthC pthread_mutex_unlock() %d", rc);

}

void GenDecayEnvlopeC(volatile Synthesizer *synth) {
    int rc;
    rc = pthread_mutex_lock(&mutex);
    SoundUnit *soundUnits = synth->SoundUnitList;

    // LOGD("GenDecayEnvlopeC pthread_mutex_lock() %d", rc);
    for (uint32_t i = 0; i < POLY_NUM; i++) {
        if ((soundUnits[i].wavetablePos >> 8) >= soundUnits[i].waveTableAttackLen &&
            soundUnits[i].envelopePos < (sizeof(EnvelopeTable) - 1)) {
            soundUnits[i].envelopeLevel = EnvelopeTable[soundUnits[i].envelopePos];
            soundUnits[i].envelopePos += 1;
        }
    }
    rc = pthread_mutex_unlock(&mutex);
    // LOGD("GenDecayEnvlopeC pthread_mutex_unlock() %d", rc);

}

void SynthAsm(volatile Synthesizer *synth) {

}

void GenDecayEnvlopeAsm(volatile Synthesizer *synth) {

}
//#endif
