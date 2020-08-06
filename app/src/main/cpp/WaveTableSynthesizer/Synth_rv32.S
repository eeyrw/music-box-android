#include "riscv_encoding.h"
.section .data
lastLimiterGain:
.word 256
.section .text
.global SynthAsm
.global GenDecayEnvlopeAsm
.global NoteOnAsm

// typedef struct _SoundUnit
// {
// 	uint32_t wavetablePos;
// 	uint32_t waveTableAddress;
// 	uint32_t waveTableLen;
// 	uint32_t waveTableLoopLen;
// 	uint32_t waveTableAttackLen;
// 	uint32_t envelopePos;
// 	uint32_t increment;
// 	int32_t val;
// 	int32_t sampleVal;
// 	uint32_t envelopeLevel;
// }SoundUnit;

// typedef struct _Synthesizer
// {
//     SoundUnit SoundUnitList[POLY_NUM];
// 	int32_t mixOut;
//     uint32_t lastSoundUnit;
// }Synthesizer;

.equ pWavetablePos , 0
.equ pWaveTableAddress , 4
.equ pWaveTableLen , 8
.equ pWaveTableLoopLen , 12
.equ pWaveTableAttackLen , 16
.equ pEnvelopePos ,20
.equ pIncrement , 24
.equ pVal , 28
.equ pSampleVal , 32
.equ pEnvelopeLevel , 36
.equ SoundUnitSize,40


.equ ENVELOP_LEN,256
.equ POLY_NUM,32
.equ pMixOut,SoundUnitSize*POLY_NUM
.equ pLastSoundUnit,pMixOut+4

.equ WAVETABLE_CELESTA_C5_LEN,2608
.equ WAVETABLE_CELESTA_C5_ATTACK_LEN,1998
.equ WAVETABLE_CELESTA_C5_LOOP_LEN,610

.equ  WAVETABLE_CELESTA_C6_LEN, 1358
.equ  WAVETABLE_CELESTA_C6_ATTACK_LEN, 838
.equ  WAVETABLE_CELESTA_C6_LOOP_LEN, 520

.equ PWM_OUT1,0x40000438
.equ PWM_OUT2,0x4000043C

#define SPI1              (0x40000000U + 0x00003800U)  /*!< SPI base address                 */

#define SPI_DATA                  (SPI1 + 0x0CU)                   /*!< SPI data register */

#define APB2_BUS_BASE         0x40010000U        /*!< apb2 base address                */
#define GPIO_BASE             (APB2_BUS_BASE + 0x00000800U)  /*!< GPIO base address                */
#define GPIOA                     (GPIO_BASE + 0x00000000U)
#define GPIOA_BOP            (GPIOA + 0x10U)    /*!< GPIO port bit operation register */
#define GPIOA_BC 	 (GPIOA + 0x14U)    /*!< GPIO bit clear register */


.func SynthAsm
SynthAsm:
// push {r1-r2,r4-r7,lr}
#define pSoundUnit t1
#define loopIndex t2
#define mixOut t3

li t5,GPIOA_BOP
li t4,(0x01<<2)|(0x01<<4)
sw t4,(t5)

li loopIndex, POLY_NUM
mv mixOut, x0
mv pSoundUnit,a0

loopSynth:
	lw t4,pEnvelopeLevel(pSoundUnit)
	beqz t4,loopSynthEnd
    lw t5,pWaveTableAddress(pSoundUnit)
    lw t6,pWavetablePos(pSoundUnit)
    srli  t6,t6,8 //wavetablePos /= 256
    slli  t6,t6,1 //wavetablePos *= 2
	add t6,t5,t6  // Load signed 16bit sample to t6
    lh t6,(t6) //
	sw t6,pSampleVal(pSoundUnit)
	mul t4,t6,t4 //sample*envelope
    // srai t4,t4,8
	sw t4,pVal(pSoundUnit)
    add mixOut,t4,mixOut //mixOut+=sample*envelope
	
    lw t6,pWavetablePos(pSoundUnit)
    lw t5,pIncrement(pSoundUnit)
    add t6,t5,t6
	lw t5,pWaveTableLen(pSoundUnit)
    slli t5,t5,8 //pWaveTableLen*=256    
    bgeu t5,t6,wavePosUpdateEnd  //bgeu:">="
	lw t5,pWaveTableLoopLen(pSoundUnit)
    slli t5,t5,8 //waveTableLoopLen*=256
    sub t6,t6,t5
    wavePosUpdateEnd:
	sw t6,pWavetablePos(pSoundUnit)
loopSynthEnd:

addi loopIndex,loopIndex,-1 // set n = n-1
addi pSoundUnit,pSoundUnit,SoundUnitSize
bnez loopIndex,loopSynth

srai mixOut,mixOut,8 // mixOut/=255 ( envelope)
li t5,150
mul mixOut,t5,mixOut //pre vol
srai mixOut,mixOut,8

//j limiterEnd

#define FIX_POINT_WEIGHT 256
#define GAIN_1_FIX FIX_POINT_WEIGHT
#define TIME_CONST_ATTACK_FIX 0
#define TIME_CONST_RELEASE_FIX 230
#define THRESHOLD (32768-1) // 2^16-1
#define THRESHOLD_FIX THRESHOLD*FIX_POINT_WEIGHT
beqz mixOut,limiterEnd
li t5,THRESHOLD
li t6,GAIN_1_FIX
mv t4,mixOut
bgtz mixOut,calcGainStart
neg t4,mixOut
calcGainStart:
bleu t4,t5,calcGainEnd
li t5,THRESHOLD_FIX
divu t5,t5,t4
mv t6,t5
mv t1,t6
calcGainEnd:

la t5,lastLimiterGain
lw t5,(t5)
bleu t5,t6,releaseSmooth
attackSmooth:
    li t4,GAIN_1_FIX-TIME_CONST_ATTACK_FIX
    mul t6,t4,t6
    li t4,TIME_CONST_ATTACK_FIX
    mul t5,t4,t5
    add t6,t5,t6
    srai t6,t6,8
    li t5,GPIOA_BC
li t4,0x01<<1
sw t4,(t5)

    j smoothEnd
releaseSmooth:
    li t4,GAIN_1_FIX-TIME_CONST_RELEASE_FIX
    mul t6,t4,t6
    li t4,TIME_CONST_RELEASE_FIX
    mul t5,t4,t5
    add t6,t5,t6
    srai t6,t6,8
    li t5,GPIOA_BOP
li t4,0x01<<1
sw t4,(t5)
smoothEnd:

bleu t6,t1,extraGainCorrectEnd
mv t6,t1
extraGainCorrectEnd:

la t5,lastLimiterGain
sw t6,(t5)
mul mixOut,t6,mixOut
srai mixOut,mixOut,8
limiterEnd:



// mixOut /=1<<6, 2^(10-1)<= mixOut <=2^(10-1)-1
//srai mixOut,mixOut,(8+1)
li t5,-32767
bge mixOut,t5,saturateLowerBoundSatisfied
mv mixOut,t5
saturateLowerBoundSatisfied:
li t5,32767
ble mixOut,t5,saturateEnd
mv mixOut,t5
saturateEnd:

// mixOut: [-512,511] -> [0,1023]
//add mixOut,mixOut,t5
//li t5,PWM_OUT1
//sh mixOut,(t5)
//li t5,PWM_OUT2
//sh mixOut,(t5)
li t5,SPI_DATA
sh mixOut,(t5)
mv pSoundUnit,a0
sw mixOut,pMixOut(pSoundUnit)


li t5,GPIOA_BC
li t4,(0x01<<2)|(0x01<<4)
sw t4,(t5)

ret
.endfunc

.func GenDecayEnvlopeAsm
GenDecayEnvlopeAsm:
//pSoundUnitGenEnv .req r0
//loopIndexGenEnv .req r4
//push {r1-r2,r4-r7,lr}
#define pSoundUnitGenEnv t1
#define loopIndexGenEnv t2

mv pSoundUnitGenEnv,a0
li loopIndexGenEnv,POLY_NUM
loopGenDecayEnvlope:
// void GenDecayEnvlopeC(Synthesizer* synth)
// {
//     SoundUnit* soundUnits = synth->SoundUnitList//
// 	for (uint32_t i = 0// i < POLY_NUM// i++)
// 	{
// 		if((soundUnits[i].wavetablePos>>8) >=soundUnits[i].waveTableAttackLen &&
// 				soundUnits[i].envelopePos <sizeof(EnvelopeTable)-1)
// 		{
// 			soundUnits[i].envelopeLevel = EnvelopeTable[soundUnits[i].envelopePos]//
// 			soundUnits[i].envelopePos += 1//
// 		}
// 	}
// }
	lw t5,pWavetablePos(pSoundUnitGenEnv)
	lw t6,pWaveTableAttackLen(pSoundUnitGenEnv)
	slli t6,t6,8
    bltu t5,t6,conditionEnd // blt:"<"
	lw t5,pEnvelopePos(pSoundUnitGenEnv)
    li t6,(ENVELOP_LEN-1)
    bgeu t5,t6,conditionEnd // bhs Higher or same (unsigned >= )
	la t6,EnvelopeTable
	add t6,t5,t6
	lbu t6,(t6)   // Load envelope to r6
	sw t6,pEnvelopeLevel(pSoundUnitGenEnv)
    addi t5,t5,1
	sw t5,pEnvelopePos(pSoundUnitGenEnv)
    conditionEnd:
addi loopIndexGenEnv,loopIndexGenEnv,-1 // set n = n-1
addi pSoundUnitGenEnv,pSoundUnitGenEnv,SoundUnitSize
bnez loopIndexGenEnv,loopGenDecayEnvlope
//pop {r1-r2,r4-r7,pc}
ret
.endfunc

.func NoteOnAsm
NoteOnAsm:
#define pSynth t4
#define note a1
//push {r1-r2,r4-r7,lr}
mv pSynth,a0
// void NoteOnC(Synthesizer* synth,uint8_t note)
// {
// 	uint8_t lastSoundUnit = synth->lastSoundUnit//
// 	SoundUnit* soundUnits = synth->SoundUnitList//

// 	//disable_interrupts()//
// 	soundUnits[lastSoundUnit].increment = WaveTable_Celesta_C5_Increment[note&0x7F]//
// 	soundUnits[lastSoundUnit].wavetablePos = 0//
// 	soundUnits[lastSoundUnit].waveTableAddress = (uint32_t)WaveTable_Celesta_C5//
// 	soundUnits[lastSoundUnit].waveTableLen = WAVETABLE_CELESTA_C5_LEN//
// 	soundUnits[lastSoundUnit].waveTableLoopLen = WAVETABLE_CELESTA_C5_LOOP_LEN//
// 	soundUnits[lastSoundUnit].waveTableAttackLen = WAVETABLE_CELESTA_C5_ATTACK_LEN//
// 	//enable_interrupts()//

// 	lastSoundUnit++//
// 	if (lastSoundUnit== POLY_NUM)
// 		lastSoundUnit = 0//

//     synth->lastSoundUnit=lastSoundUnit//
// }

lw t5,pLastSoundUnit(pSynth)

li t6,SoundUnitSize
mul t5,t5,t6
add pSynth,pSynth,t5
li t5,0x7F
and note,note,t5
slli note,note,1
la t5,WaveTable_Celesta_C5_Increment
add t5,t5,note
lhu t5,(t5)
csrc CSR_MSTATUS, MSTATUS_MIE              // Disable all interrupt
sw t5,pIncrement(pSynth)
mv t5,x0
sw t5,pWavetablePos(pSynth)
la t5,WaveTable_Celesta_C5
sw t5,pWaveTableAddress(pSynth)
li t5,WAVETABLE_CELESTA_C5_LEN
sw t5,pWaveTableLen(pSynth)
li t5,WAVETABLE_CELESTA_C5_LOOP_LEN
sw t5,pWaveTableLoopLen(pSynth)
li t5,WAVETABLE_CELESTA_C5_ATTACK_LEN
sw t5,pWaveTableAttackLen(pSynth)
sw x0,pEnvelopePos(pSynth)
li t5,255
sw t5,pEnvelopeLevel(pSynth)
csrs CSR_MSTATUS, MSTATUS_MIE              // Enable all interrupt
mv pSynth,a0

lw t5,pLastSoundUnit(pSynth)

addi t5,t5,1
addi t6,t5,-POLY_NUM
bnez t6,updateLastSoundUnitEnd
mv t5,x0
updateLastSoundUnitEnd:
sw t5,pLastSoundUnit(pSynth)

//pop {r1-r2,r4-r7,pc}
ret
.endfunc
