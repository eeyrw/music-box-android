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


public class MidiPlayer {
    public void setTranspose(int transposeValue) {
        mTransposeValue = transposeValue;
    }

    public enum PlayerState {
        STOP, READY_TO_PLAY, PLAYING, READY_TO_PAUSE, PAUSE, READY_TO_STOP, READY_TO_PLAY_OR_PAUSE
    }

    public enum UIMessage {PLAY_KEY, STOP_KEY, PAUSE_KEY, PLAY_OR_PAUSE_KEY}

    ;

    public MidiPlayer() {
        mUIMessageQueue = new LinkedBlockingQueue<UIMessage>();
        mState = PlayerState.STOP;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true)
                        processState();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        mThread.start();
        mEngine = new MusicBoxEngine();
    }

    private final String TAG = com.yuan.music_box.MainActivity.class.toString();
    private PlayerState mState;
    private PlayerState mLastState;
    private LinkedBlockingQueue<UIMessage> mUIMessageQueue;
    private Thread mThread;
    private MidiProcessor mProcessor;
    public MusicBoxEngine mEngine;
    private String midiFilePath;
    private int mTransposeValue = 0;

    private void internalPlay() {
        mProcessor.start();
        mEngine.pause(false);
    }

    private void internalPause() {
        mProcessor.stop();
        mEngine.pause(true);
    }

    private void internalResume() {
        mProcessor.start();
        mEngine.pause(false);
    }

    private void internalStop() {
        mProcessor.reset();
        mEngine.pause(true);
    }

    private void processState() throws InterruptedException {
        UIMessage currentMessage = mUIMessageQueue.take();
        Log.d(TAG, "processState: New Messege " + currentMessage.toString());
        switch (mState) {
            case STOP:
                if (currentMessage == UIMessage.PLAY_KEY || currentMessage == UIMessage.PLAY_OR_PAUSE_KEY) {
                    internalPlay();
                    updateState(PlayerState.PLAYING);
                }
                break;
            case PLAYING:
                if (currentMessage == UIMessage.PAUSE_KEY || currentMessage == UIMessage.PLAY_OR_PAUSE_KEY) {
                    internalPause();
                    updateState(PlayerState.PAUSE);
                } else if (currentMessage == UIMessage.STOP_KEY) {
                    internalStop();
                    updateState(PlayerState.STOP);
                }
                break;
            case PAUSE:
                if (currentMessage == UIMessage.PLAY_KEY || currentMessage == UIMessage.PLAY_OR_PAUSE_KEY) {
                    internalResume();
                    updateState(PlayerState.PLAYING);
                } else if (currentMessage == UIMessage.STOP_KEY) {
                    internalStop();
                    updateState(PlayerState.STOP);
                }
                break;
        }
    }


    private void updateState(PlayerState state) {
        mLastState = mState;
        mState = state;
    }

    public void playOrPause() {
        try {
            mUIMessageQueue.put(UIMessage.PLAY_OR_PAUSE_KEY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void play() {
        try {
            mUIMessageQueue.put(UIMessage.PLAY_KEY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            mUIMessageQueue.put(UIMessage.STOP_KEY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        try {
            mUIMessageQueue.put(UIMessage.PAUSE_KEY);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void playMidiFile(InputStream input) {

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
