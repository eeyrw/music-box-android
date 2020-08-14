package com.yuan.midiplayer;

public interface MidiPlayerEventListener extends PlayerEventListener {
    void onSuggestTransposeChange(int transpose);

    void onWaveformChange(float[] waveform);
}
