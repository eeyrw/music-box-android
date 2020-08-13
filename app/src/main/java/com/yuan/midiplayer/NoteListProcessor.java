package com.company;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
    public HashMap<Integer, Integer> noteOccurTimesMap;
    public int centroidPitch = 0;

    private HashMap<Integer, ArrayList<Integer>> tickNoteMap;
    private HashMap<Integer, ArrayList<Integer>> tickNoteMapTransposed;
    private String pitchName[];
    private OutputStreamWriter defaultOutput;


    public NoteListProcessor(String midifilePath, OutputStream outputStream) {
        defaultOutput = new OutputStreamWriter(outputStream);
/*        MidiHelper helper = MidiHelper(midifilePath);

        helper.getTickNoteMap(tickNoteMap);*/
        InitPitchName();
    }

    private void InitPitchName() {
        String pitchInOneOctave[] = new String[]{
                "C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"
        };

        // Midi pitch 12=C0,108=C8
        for (int i = 0; i != 127; ++i) {
            int octaveGroup = (i - 12) / 12;
            int pitch = Math.abs((i - 12) % 12);
            pitchName[i] = String.valueOf(octaveGroup) + ":" + pitchInOneOctave[pitch];
        }
        pitchName[128] = "Fine";
    }

    private void printAnalyzeResult() {
/*        ( * defaultOutput) <<"Song duration: " << midiDuration << " s" << endl;
        ( * defaultOutput) <<"Note pitch v.s. occur times table: " << endl;
        TablePrinter tp (defaultOutput);
        tp.AddColumn("Note pitch", 12);
        tp.AddColumn("Occur times", 12);
        tp.AddColumn("Is in range", 12);
        tp.PrintHeader();
        for (auto & noteNumMapItem :noteOccurTimesMap)
        {
            tp << pitchName[noteNumMapItem.first] << noteNumMapItem.second;
            int offestToVailidHighestPitch = validHighestPitch - noteNumMapItem.first;
            int offestToVailidLowestPitch = validLowestPitch - noteNumMapItem.first;

            if (offestToVailidHighestPitch >= 0 && offestToVailidLowestPitch <= 0)
                tp << "YES";
            else
                tp << "NO";
        }
        tp.PrintFooter();
        ( * defaultOutput) <<"Highest pitch: " << pitchName[highestPitch] << endl
                << "Lowest pitch: " << pitchName[lowestPitch] << endl;
        ( * defaultOutput) <<"Centroid pitch: " << pitchName[centroidPitch] << endl;
        ( * defaultOutput) <<"Transpose suggestion: " << suggestTranpose << " half note" << endl;
        if (useExternTransposeParam)
            ( * defaultOutput) <<"External transpose: " << externTransposeParam << " half note" << endl;*/
    }

    void analyzeNoteMapByCentroid() {
        tickNoteMap.forEach((tick, noteList) -> {
            noteList.forEach(note -> {
                noteOccurTimesMap.merge(note, 1, (k, v) -> v + 1);
            });
        });

        lowestPitch = Collections.max(noteOccurTimesMap.keySet());
        highestPitch = Collections.min(noteOccurTimesMap.keySet());

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