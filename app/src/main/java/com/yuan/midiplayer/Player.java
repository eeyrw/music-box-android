package com.yuan.midiplayer;

import android.util.Log;

import java.util.concurrent.LinkedBlockingQueue;


public class Player {

    private void processState() throws InterruptedException {
        UIMessage currentMessage = mUIMessageQueue.take();
        Log.d(TAG, "processState: New Messege " + currentMessage.toString());
        switch (mState) {
            case STOP:
                if (currentMessage == UIMessage.PLAY_KEY) {
                    internalPlay();
                    updateState(PlayerState.PLAYING);
                }
                break;
            case PLAYING:
            case RESUME:
                if (currentMessage == UIMessage.PAUSE_KEY) {
                    internalPause();
                    updateState(PlayerState.PAUSE);
                } else if (currentMessage == UIMessage.STOP_KEY) {
                    internalStop();
                    updateState(PlayerState.STOP);
                } else if (currentMessage == UIMessage.GO_TO_BACK) {
                    internalPause();
                    updateState(PlayerState.PAUSE_BY_OS);
                }
                break;
            case PAUSE:
                if (currentMessage == UIMessage.PLAY_KEY) {
                    internalResume();
                    updateState(PlayerState.RESUME);
                } else if (currentMessage == UIMessage.STOP_KEY) {
                    internalStop();
                    updateState(PlayerState.STOP);
                }
                break;
            case PAUSE_BY_OS:
                if (currentMessage == UIMessage.PLAY_KEY || currentMessage == UIMessage.RETURN_FROM_BACK) {
                    internalResume();
                    updateState(PlayerState.RESUME);
                } else if (currentMessage == UIMessage.STOP_KEY) {
                    internalStop();
                    updateState(PlayerState.STOP);
                }
                break;
        }
    }

    public enum UIMessage {PLAY_KEY, STOP_KEY, PAUSE_KEY, GO_TO_BACK, RETURN_FROM_BACK}

    ;

    public Player(PlayerEventListener listener) {
        mListener = listener;
        mRunning = true;
        mUIMessageQueue = new LinkedBlockingQueue<UIMessage>();
        mState = PlayerState.STOP;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (mRunning)
                        processState();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        mThread.start();
    }

    private final String TAG = com.yuan.music_box.MainActivity.class.toString();
    private PlayerState mState;
    private PlayerState mLastState;
    private LinkedBlockingQueue<UIMessage> mUIMessageQueue;
    private Thread mThread;
    private boolean mRunning;
    private PlayerEventListener mListener;


    protected void internalPlay() {

    }

    protected void internalPause() {

    }

    protected void internalResume() {

    }

    protected void internalStop() {

    }

    public enum PlayerState {
        STOP, PLAYING, PAUSE, RESUME, PAUSE_BY_OS,
    }


    private void updateState(PlayerState state) {
        mLastState = mState;
        mState = state;
        if (mLastState != mState)
            mListener.onPlayStateChange(mState);
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

    public void goToBack() {
        try {
            mUIMessageQueue.put(UIMessage.GO_TO_BACK);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void returnFromBack() {
        try {
            mUIMessageQueue.put(UIMessage.RETURN_FROM_BACK);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
