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

        List<NoteEvent> noteEvents = new ArrayList<>();
        HashMap<Integer, NoteEvent> activeNotes = new HashMap<>();

        // 1. 合并所有 Track 的事件
        List<MidiEvent> allEvents = new ArrayList<>();
        for (MidiTrack track : mMidiFile.getTracks()) {
            allEvents.addAll(track.getEvents());
        }

        // 2. 按 tick 排序（这是正确处理 Tempo 的关键）
        allEvents.sort((a, b) -> Long.compare(a.getTick(), b.getTick()));

        // 3. 时间推进状态
        int currentMPQN = Tempo.DEFAULT_MPQN;
        long lastTick = 0;
        double currentMs = 0;

        // 如果你后面要用 Metronome，这里状态是对的
        mMetronome = new MetronomeTick(new TimeSignature(), mPPQ);

        // 4. 顺序扫描事件
        for (MidiEvent event : allEvents) {

            long eventTick = event.getTick();
            long deltaTick = eventTick - lastTick;

            if (deltaTick > 0) {
                // 使用“当前 Tempo”推进时间
                currentMs += ((double) (deltaTick * currentMPQN) / mPPQ) / 1000;
                lastTick = eventTick;
            }

            // ---- Tempo 变化 ----
            if (event instanceof Tempo) {
                currentMPQN = ((Tempo) event).getMpqn();
                continue;
            }

            // ---- 拍号变化（这里不直接影响时间，但影响节拍）----
            if (event instanceof TimeSignature) {
                mMetronome.setTimeSignature((TimeSignature) event);
                continue;
            }

            // ---- NoteOn ----
            if (event instanceof NoteOn) {
                NoteOn no = (NoteOn) event;
                int channel = no.getChannel();
                if (channel == 0x09) continue; // 排除打击乐

                int note = no.getNoteValue();
                int velocity = no.getVelocity();
                int key = note + (channel << 8);

                if (velocity == 0) {
                    // NoteOn velocity=0 -> NoteOff
                    NoteEvent ne = activeNotes.remove(key);
                    if (ne != null) {
                        ne.durationMs = (long) currentMs - ne.startTimeMs;
                        noteEvents.add(ne);
                    }
                } else {
                    NoteEvent ne = new NoteEvent();
                    ne.midiNote = note;
                    ne.startTimeMs = (long) currentMs;
                    ne.velocity = velocity / 127f;
                    activeNotes.put(key, ne);
                }
                continue;
            }

            // ---- NoteOff ----
            if (event instanceof NoteOff) {
                NoteOff off = (NoteOff) event;
                int key = off.getNoteValue() + (off.getChannel() << 8);

                NoteEvent ne = activeNotes.remove(key);
                if (ne != null) {
                    ne.durationMs = (long) currentMs - ne.startTimeMs;
                    noteEvents.add(ne);
                }
            }
        }

        // 5. 清理没有 NoteOff 的音符（兜底）
        for (NoteEvent ne : activeNotes.values()) {
            ne.durationMs = 200;
            noteEvents.add(ne);
        }

        // 6. 按开始时间排序（安全起见）
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