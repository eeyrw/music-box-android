package com.yuan.midiplayer;

import android.util.Log;
import java.util.concurrent.LinkedBlockingQueue;


public class Player {

    public enum PlayerState {
        STOP, PLAYING, PAUSE
    }

    public enum UIMessage {PLAY_KEY, STOP_KEY, PAUSE_KEY}

    ;

    public Player() {
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


    protected void internalPlay() {

    }

    protected void internalPause() {

    }

    protected void internalResume() {

    }

    protected void internalStop() {

    }

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
                if (currentMessage == UIMessage.PAUSE_KEY) {
                    internalPause();
                    updateState(PlayerState.PAUSE);
                } else if (currentMessage == UIMessage.STOP_KEY) {
                    internalStop();
                    updateState(PlayerState.STOP);
                }
                break;
            case PAUSE:
                if (currentMessage == UIMessage.PLAY_KEY) {
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
}
