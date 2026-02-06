package com.yuan.midiplayer;

import com.customview.graph.VuLevel;

public interface MidiPlayerEventListener extends PlayerEventListener {
    void onSuggestTransposeChange(int transpose);

    void onVisualChangeChange(float[] waveform, float[] spectrum, VuLevel vuLevel);
}
