package com.yuan.midiplayer;

import android.util.Log;
import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.util.MidiEventListener;
import com.pdrogfer.mididroid.util.MidiProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;


public class MidiPlayer extends Player {
    private final String TAG = com.yuan.music_box.MainActivity.class.toString();
    public MusicBoxEngine mEngine;
    private MidiProcessor mProcessor;
    private String midiFilePath;
    private int mTransposeValue = 0;
    private MidiPlayerEventListener mListener;
    private Timer waveformPollTimer = new Timer();
    private TimerTask waveformPollTask;


    public MidiPlayer(MidiPlayerEventListener listener) {
        super(listener);
        mListener = listener;
        mEngine = new MusicBoxEngine();
    }

    public void setTranspose(int transposeValue) {
        mTransposeValue = transposeValue;
    }

    @Override
    protected void internalPlay() {
        if (mProcessor != null)
            mProcessor.start();
        if (mEngine != null)
            mEngine.pause(false);
        startInternalTimer();
    }

    @Override
    protected void internalPause() {
        if (mProcessor != null)
            mProcessor.stop();
        if (mEngine != null)
            mEngine.pause(true);
        stopInternalTimer();
    }

    @Override
    protected void internalResume() {
        if (mProcessor != null)
            mProcessor.start();
        if (mEngine != null)
            mEngine.pause(false);
        startInternalTimer();
    }

    @Override
    protected void internalStop() {
        if (mProcessor != null)
            mProcessor.reset();
        if (mEngine != null) {
            mEngine.pause(true);
            mEngine.resetSynthesizer();
        }
        stopInternalTimer();
    }

    void stopInternalTimer() {
        if (waveformPollTimer != null) {
            waveformPollTimer.cancel();
            waveformPollTimer = null;
        }
        if (waveformPollTask != null) {
            waveformPollTask.cancel();
            waveformPollTask = null;
        }
    }

    void startInternalTimer() {
        if (waveformPollTimer == null) {
            waveformPollTimer = new Timer();
        }

        if (waveformPollTask == null) {
            waveformPollTask = new TimerTask() {
                @Override
                public void run() {
                    mListener.onWaveformChange(mEngine.getWaveformData());
                }
            };
        }

        if (waveformPollTimer != null && waveformPollTask != null)
            waveformPollTimer.schedule(waveformPollTask, 0, 50);
    }

    public void playMidiFile(InputStream input) {
        try {
            // File input = new File(midiFilePath);
            MidiFile midi = new MidiFile(input);
            NoteListProcessor np = new NoteListProcessor(midi);
            np.recommHighestPitch = 72; //C5 in midi number
            np.recommLowestPitch = 72; //C5 in midi number
            np.analyzeNoteMapByCentroid();
            mListener.onSuggestTransposeChange(np.suggestTranpose);
            mTransposeValue = np.suggestTranpose;
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
        internalStop();
        if (mProcessor != null)
            mProcessor.reset();
        if (mEngine != null)
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
                    //Log.d(TAG, String.format("onMidiEvent: %d", noteTranspose));
                }
            }
        }

        @Override
        public void onStop(boolean finished) {
            if (finished) {
                Log.d(TAG, "onMidiEvent: Stop");
                stop();
            }
        }

    }
}
