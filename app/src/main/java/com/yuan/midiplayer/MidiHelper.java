package com.yuan.midiplayer;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.MidiTrack;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.event.meta.Tempo;
import com.pdrogfer.mididroid.event.meta.TimeSignature;
import com.pdrogfer.mididroid.util.MetronomeTick;
import com.pdrogfer.mididroid.util.MidiUtil;

public class MidiHelper {
    private MidiFile mMidiFile;
    private int mMPQN;
    private int mPPQ;
    private HashMap<Long, ArrayList<Integer>> tickNoteMap = new HashMap<>();
    private int tickPerSecond = 120;
    private final String TAG = com.yuan.music_box.MainActivity.class.toString();

    private MetronomeTick mMetronome;

    public MidiHelper(MidiFile midiFile) {
        mMidiFile = midiFile;
        mMPQN = Tempo.DEFAULT_MPQN;
        mPPQ = mMidiFile.getResolution();

        mMetronome = new MetronomeTick(new TimeSignature(), mPPQ);
    }


    public HashMap<Long, ArrayList<Integer>> getTickNoteMap() {

        for (MidiTrack midiTrack : mMidiFile.getTracks()) {
            for (MidiEvent trackEvent : midiTrack.getEvents()) {
                dispatch(trackEvent);
            }
        }
        return tickNoteMap;
    }

    protected void dispatch(MidiEvent event) {
        // Tempo and Time Signature events are always needed by the processor
        if (event.getClass().equals(Tempo.class)) {
            mMPQN = ((Tempo) event).getMpqn();
        } else if (event.getClass().equals(TimeSignature.class)) {

            boolean shouldDispatch = mMetronome.getBeatNumber() != 1;
            mMetronome.setTimeSignature((TimeSignature) event);

            if (shouldDispatch) {
                dispatch(mMetronome);
            }
        } else if (event.getClass().equals(NoteOn.class)) {
            long absoluteMs = MidiUtil.ticksToMs(event.getTick(), mMPQN, mPPQ);
            long tick = absoluteMs * tickPerSecond / 1000;
            Integer note = ((NoteOn) event).getNoteValue();
            int channel = ((NoteOn) event).getChannel();
            int velocity = ((NoteOn) event).getVelocity();
            if (channel != 0x09 && velocity != 0) {
                if (tickNoteMap.containsKey(Long.valueOf(tick))) {
                    tickNoteMap.get(Long.valueOf(tick)).add(note);
                } else {
                    tickNoteMap.put(Long.valueOf(tick), new ArrayList<Integer>() {{
                        add(note);
                    }});
                }
            }
        }

    }
}