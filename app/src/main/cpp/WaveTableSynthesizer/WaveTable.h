#ifndef __WAVETABLE__
#define __WAVETABLE__
// Sample name: CP-80 EP C4
// Sample's base frequency: 262.0848114426004 Hz
// Sample's sample rate: 32000 Hz
#define WAVETABLE_LEN 27503
#define WAVETABLE_ATTACK_LEN 25426
#define WAVETABLE_LOOP_LEN 2077
#define WAVETABLE_ACTUAL_LEN 27504

#ifndef __ASSEMBLER__

#include <stdint.h>

extern const int16_t WaveTable[WAVETABLE_ACTUAL_LEN];
extern const uint16_t WaveTable_Increment[];
#else
.extern	WaveTable
.extern WaveTable_Increment
#endif

#endif