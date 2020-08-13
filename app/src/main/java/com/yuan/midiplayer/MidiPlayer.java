package com.yuan.midiplayer;

import android.util.Log;
import android.widget.TextView;

import com.yuan.music_box.R;
import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.util.MidiEventListener;
import com.pdrogfer.mididroid.util.MidiProcessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.LinkedBlockingQueue;


public class MidiPlayer extends Player {
    private final String TAG = com.yuan.music_box.MainActivity.class.toString();
    public MusicBoxEngine mEngine;
    private MidiProcessor mProcessor;
    private String midiFilePath;
    private int mTransposeValue = 0;

    public MidiPlayer() {
        super();
        mEngine = new MusicBoxEngine();
    }

    public void setTranspose(int transposeValue) {
        mTransposeValue = transposeValue;
    }

    @Override
    protected void internalPlay() {
        mProcessor.start();
        mEngine.pause(false);
    }

    @Override
    protected void internalPause() {
        mProcessor.stop();
        mEngine.pause(true);
    }

    @Override
    protected void internalResume() {
        mProcessor.start();
        mEngine.pause(false);
    }

    @Override
    protected void internalStop() {
        mProcessor.reset();
        mEngine.pause(true);
    }

    public void playMidiFile(InputStream input) {

        NoteListProcessor np = new NoteListProcessor(input);
        np.analyzeNoteMapByCentroid();
        try {
            // File input = new File(midiFilePath);
            MidiFile midi = new MidiFile(input);
            // Create a new MidiProcessor:
            MidiEventPlayer ep = new MidiEventPlayer("sd");
            mProcessor = new MidiProcessor(midi);
            mProcessor.registerEventListener(ep, NoteOn.class);
            play();
            // Start the processor:
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    public void releaseResource() {
        mProcessor.reset();
        mEngine.releaseResource();
    }

    // This class will print any event it receives to the console
    public class MidiEventPlayer implements MidiEventListener {
        private String mLabel;

        public MidiEventPlayer(String label) {
            mLabel = label;
        }

        @Override
        public void onStart(boolean fromBeginning) {
            if (fromBeginning) {
                // System.out.println(mLabel + " Started!");
            } else {
                // System.out.println(mLabel + " resumed");
            }
        }

        @Override
        public void onEvent(MidiEvent event, long ms) {
            NoteOn ev = (NoteOn) event;
            final int note = ev.getNoteValue();
            int velocity = ev.getVelocity();
            int channel = ev.getChannel();
            if (velocity != 0 && channel != 9) {
                final int noteTranspose = note + mTransposeValue;
                if (noteTranspose >= 0 && noteTranspose <= 127) {
                    mEngine.noteOn(noteTranspose);
                    // Log.d(TAG, String.format("onMidiEvent: %d", noteTranspose));
                }
            }
        }

        @Override
        public void onStop(boolean finished) {
            if (finished) {
                Log.d(TAG, "onMidiEvent: Stop");
                mEngine.pause(true);
            }
        }

    }
}
