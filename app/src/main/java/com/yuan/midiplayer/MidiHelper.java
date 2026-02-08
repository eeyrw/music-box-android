package com.yuan.midiplayer;

import com.customview.graph.PianoRollView.NoteEvent;
import com.pgf.mididroid.MidiFile;
import com.pgf.mididroid.MidiTrack;
import com.pgf.mididroid.event.MidiEvent;
import com.pgf.mididroid.event.NoteOn;
import com.pgf.mididroid.event.NoteOff;
import com.pgf.mididroid.event.meta.Tempo;
import com.pgf.mididroid.event.meta.TimeSignature;
import com.pgf.mididroid.util.MetronomeTick;
import com.pgf.mididroid.util.MidiUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

    public List<NoteEvent> generateNoteEvents() {
        // 1. 确保 tickNoteMap 已经生成
        getTickNoteMap();

        List<NoteEvent> noteEvents = new ArrayList<>();

        // 临时存储激活的音符
        HashMap<Integer, NoteEvent> activeNotes = new HashMap<>();

        for (MidiTrack track : mMidiFile.getTracks()) {
            for (MidiEvent event : track.getEvents()) {
                if (event instanceof NoteOn) {
                    NoteOn no = (NoteOn) event;
                    int note = no.getNoteValue();
                    int channel = no.getChannel();
                    int velocity = no.getVelocity();
                    long absoluteMs = MidiUtil.ticksToMs(event.getTick(), mMPQN, mPPQ);
                    int key = note + (channel << 8);

                    if (channel == 0x09) continue; // 排除打击乐

                    if (velocity == 0) {
                        // NoteOn(0) → 视为 NoteOff
                        NoteEvent ne = activeNotes.remove(key);
                        if (ne != null) {
                            ne.durationMs = absoluteMs - ne.startTimeMs;
                            noteEvents.add(ne);
                        }
                    } else {
                        // 正常 NoteOn
                        NoteEvent ne = new NoteEvent();
                        ne.midiNote = note;
                        ne.startTimeMs = absoluteMs;
                        ne.velocity = velocity / 127f;
                        activeNotes.put(key, ne);
                    }

                } else if (event instanceof NoteOff) {
                    NoteOff noff = (NoteOff) event;
                    int note = noff.getNoteValue();
                    int channel = noff.getChannel();
                    long absoluteMs = MidiUtil.ticksToMs(event.getTick(), mMPQN, mPPQ);
                    int key = note + (channel << 8);

                    NoteEvent ne = activeNotes.remove(key);
                    if (ne != null) {
                        ne.durationMs = absoluteMs - ne.startTimeMs;
                        noteEvents.add(ne);
                    }
                }
            }
        }

        // 处理剩余没有 NoteOff 的音符，默认持续 200ms
        for (NoteEvent ne : activeNotes.values()) {
            ne.durationMs = 200;
            noteEvents.add(ne);
        }

        // 按开始时间排序
        noteEvents.sort((a, b) -> Long.compare(a.startTimeMs, b.startTimeMs));

        return noteEvents;
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