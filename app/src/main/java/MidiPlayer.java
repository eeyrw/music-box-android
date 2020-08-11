package com.company;

import java.util.concurrent.LinkedBlockingQueue;

public class MidiPlayer {
    public enum PlayerState {
        STOP, READY_TO_PLAY, PLAYING, READY_TO_PAUSE, PAUSE, READY_TO_STOP, READY_TO_PLAY_OR_PAUSE
    }

    public enum UIMessage {PLAY_KEY, STOP_KEY, PAUSE_KEY, PLAY_OR_PAUSE_KEY}

    ;

    MidiPlayer() {
        mUIMessageQueue = new LinkedBlockingQueue<UIMessage>();
        mState = PlayerState.STOP;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    processState();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private PlayerState mState;
    private PlayerState mLastState;
    private LinkedBlockingQueue<UIMessage> mUIMessageQueue;
    private Thread mThread;

    private void internalPlay() {

    }

    private void internalPause() {

    }

    private void internalResume() {

    }

    private void internalStop() {
    }

    private void processState() throws InterruptedException {
        UIMessage currentMessage = mUIMessageQueue.take();
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

    public void playOrPause() throws InterruptedException {
        mUIMessageQueue.put(UIMessage.PLAY_OR_PAUSE_KEY);
    }

    public void play() throws InterruptedException {
        mUIMessageQueue.put(UIMessage.PLAY_KEY);
    }

    public void stop() throws InterruptedException {
        mUIMessageQueue.put(UIMessage.STOP_KEY);
    }

    public void pause() throws InterruptedException {
        mUIMessageQueue.put(UIMessage.PAUSE_KEY);
    }
}
