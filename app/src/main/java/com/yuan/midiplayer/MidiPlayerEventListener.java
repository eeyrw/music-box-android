package com.yuan.midiplayer;

import com.customview.graph.PianoRollView;
import com.customview.graph.VuLevel;

import java.util.List;

public interface MidiPlayerEventListener extends PlayerEventListener {
    void onSuggestTransposeChange(int transpose);

    void onGetNoteList(List<PianoRollView.NoteEvent> noteList);

    void onNoteOn(int note, long ms);

    void onVisualChangeChange(float[] waveform, float[] spectrum, VuLevel vuLevel);
}
