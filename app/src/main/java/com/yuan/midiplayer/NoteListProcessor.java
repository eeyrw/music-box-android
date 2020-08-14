package com.yuan.midiplayer;

import android.util.Log;

import com.pdrogfer.mididroid.MidiFile;

import java.io.InputStream;
import java.util.*;

public class NoteListProcessor {
    public int highestPitch;
    public int lowestPitch;
    public int validHighestPitch = 127;
    public int validLowestPitch = 0;
    public int recommHighestPitch = 120;
    public int recommLowestPitch = 45;
    public int suggestTranpose;
    public int offestToMidiPitch = 0;
    public HashMap<Integer, Integer> noteOccurTimesMap = new HashMap<>();
    public int centroidPitch = 0;
    private final String TAG = com.yuan.music_box.MainActivity.class.toString();

    private HashMap<Long, ArrayList<Integer>> tickNoteMap;
    private String pitchName[];


    public NoteListProcessor(MidiFile midi) {
        MidiHelper helper = new MidiHelper(midi);
        tickNoteMap = helper.getTickNoteMap();
        InitPitchName();
    }


    private void InitPitchName() {
        String pitchInOneOctave[] = new String[]{
                "C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"
        };

        pitchName = new String[129];
        // Midi pitch 12=C0,108=C8
        for (int i = 0; i != 127; ++i) {
            int octaveGroup = (i - 12) / 12;
            int pitch = Math.abs((i - 12) % 12);
            pitchName[i] = String.valueOf(octaveGroup) + ":" + pitchInOneOctave[pitch];
        }
        pitchName[128] = "Fine";
    }

    private void printAnalyzeResult() {
        Log.d(TAG, String.format("Highest pitch: %s", pitchName[highestPitch]));
        Log.d(TAG, String.format("Lowest pitch: %s", pitchName[lowestPitch]));
        Log.d(TAG, String.format("Centroid pitch: %s", pitchName[centroidPitch]));
        Log.d(TAG, String.format("Transpose suggestion: %d half-tone(s)", suggestTranpose));
    }

    void analyzeNoteMapByCentroid() {

        for (Map.Entry<Long, ArrayList<Integer>> entry : tickNoteMap.entrySet()) {
            ArrayList<Integer> noteList = entry.getValue();
            for (Integer note : noteList) {
                if (noteOccurTimesMap.containsKey(note)) {
                    noteOccurTimesMap.put(note, noteOccurTimesMap.get(note) + 1);
                } else {
                    noteOccurTimesMap.put(note, 1);
                }
            }
        }

        lowestPitch = Collections.min(noteOccurTimesMap.keySet());
        highestPitch = Collections.max(noteOccurTimesMap.keySet());

        int centroidSum1 = 0;
        int centroidSum2 = 0;

        for (Integer note : noteOccurTimesMap.keySet()) {
            Integer occurTimes = noteOccurTimesMap.get(note);
            centroidSum1 += note * occurTimes;
            centroidSum2 += occurTimes;
        }

        centroidPitch = centroidSum1 / centroidSum2;
        int centerOfSuggest = recommLowestPitch +
                (recommHighestPitch - recommLowestPitch) / 2;

        int wantedTranspose = centerOfSuggest - centroidPitch;

        int wantedHighestPitch = highestPitch + wantedTranspose;
        int wantedLowestPitch = lowestPitch + wantedTranspose;

        suggestTranpose = wantedTranspose;

        int offsetToValidHighestPitch = validHighestPitch - wantedHighestPitch;
        int offsetToValidLowestPitch = validLowestPitch - wantedLowestPitch;

        if (offsetToValidHighestPitch >= 0 && offsetToValidLowestPitch <= 0)
            suggestTranpose += 0;
        else if (offsetToValidHighestPitch < 0)
            suggestTranpose += offsetToValidHighestPitch; //keep the highest pitch by all means
        else if (offsetToValidLowestPitch >= 0) {
            if (Math.abs(offsetToValidHighestPitch) >= Math.abs(offsetToValidLowestPitch))
                suggestTranpose += offsetToValidLowestPitch;
            else
                suggestTranpose += offsetToValidHighestPitch;
        }

        printAnalyzeResult();
    }
}