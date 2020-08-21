#ifndef __SYNTH_CORE_H__
#define __SYNTH_CORE_H__

#include <stdint.h>

#define POLY_NUM 32

#define NoteOn NoteOnC
#define GenDecayEnvlope GenDecayEnvlopeC
#define Synth SynthC

#if defined(__aarch64__) || defined(__x86_64__)
#define ADDRESS_TYPE uint64_t
#else
#define ADDRESS_TYPE uint32_t
#endif


#ifdef __cplusplus
extern "C" {
#endif

typedef struct _SoundUnit {
    uint32_t volatile wavetablePos;
    ADDRESS_TYPE volatile waveTableAddress;
    uint32_t volatile waveTableLen;
    uint32_t volatile waveTableLoopLen;
    uint32_t volatile waveTableAttackLen;
    uint32_t volatile envelopePos;
    uint32_t volatile increment;
    int32_t volatile val;
    int32_t volatile sampleVal;
    uint32_t volatile envelopeLevel;
} SoundUnit;


typedef struct _Synthesizer {
    SoundUnit SoundUnitList[POLY_NUM];
    int32_t mixOut;
    uint32_t lastSoundUnit;
} Synthesizer;

typedef struct _SampleInfo {
    uint8_t sampleCoverlowerPitch;
    uint8_t sampleCoverupperPitch;
    uint8_t sampleBasePitch;
    uint8_t reserved;
    uint32_t sampleAddr;
    uint32_t sampleLen;
    uint32_t sampleLoopStart;
    uint32_t sampleLoopLen;
} SampleInfo;

typedef struct _InstrumentInfo {
    uint8_t instrumentId;
    uint8_t sampleNum;
    ADDRESS_TYPE sampleBaseAddr;
    SampleInfo *samples;
} InstrumentInfo;


extern void SynthInit(volatile Synthesizer *synth);

//#ifdef RUN_TEST
extern void NoteOnC(volatile Synthesizer *synth, uint8_t note);

extern void SynthC(volatile Synthesizer *synth);

extern void GenDecayEnvlopeC(volatile Synthesizer *synth);
//#endif

extern void NoteOnAsm(volatile Synthesizer *synth, uint8_t note);

extern void GenDecayEnvlopeAsm(volatile Synthesizer *synth);

extern void SynthAsm(volatile Synthesizer *synth);

#ifdef __cplusplus
} //end extern "C"
#endif

#endif
