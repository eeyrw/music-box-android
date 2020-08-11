package com.yuan.midiplayer;

import android.os.Build;
import android.util.Log;

public class MusicBoxEngine {
    private static long mEngineHandle = 0;
    private final String TAG = com.yuan.music_box.MainActivity.class.toString();

    private static native long createNativeEngine(int[] cpuIds);

    private static native void deleteNativeEngine(long engineHandle);

    private static native void nativePause(long engineHandle, boolean isPause);

    private static native void nativeNoteOn(long engineHandle, int note);

    private static native void nativeSetDefaultStreamValues(int sampleRate, int framesPerBurst);

    private static native float[] nativeGetWaveformData(long engineHandle);

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    MusicBoxEngine() {
        mEngineHandle = createNativeEngine(getExclusiveCores());
    }

    public void noteOn(int note) {
        if (mEngineHandle != 0)
            nativeNoteOn(mEngineHandle, note);
    }

    public void pause(boolean isPause) {
        if (mEngineHandle != 0)
            nativePause(mEngineHandle, isPause);
    }

    public void releaseResource() {
        if (mEngineHandle != 0)
            deleteNativeEngine(mEngineHandle);
    }

    public float[] getWaveformData() {
        if (mEngineHandle != 0)
            return nativeGetWaveformData(mEngineHandle);
        else
            return new float[256];
    }

    // Obtain CPU cores which are reserved for the foreground app. The audio thread can be
    // bound to these cores to avoids the risk of it being migrated to slower or more contended
    // core(s).
    private int[] getExclusiveCores() {
        int[] exclusiveCores = {};

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "getExclusiveCores() not supported. Only available on API " +
                    Build.VERSION_CODES.N + "+");
        } else {
            try {
                exclusiveCores = android.os.Process.getExclusiveCores();
            } catch (RuntimeException e) {
                Log.w(TAG, "getExclusiveCores() is not supported on this device.");
            }
        }
        return exclusiveCores;
    }
}
