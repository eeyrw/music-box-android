package com.yuan.midiplayer;

public class SpectrumNative {
    static {
        System.loadLibrary("native-lib");
    }

    public static native void start();

    public static native void stop();

    public static native float[] getSpectrum();

    public void spectStart() {
        start();
    }

    public void spectStop() {
        stop();
    }

    public float[] spectGetSpectrum() {
        return getSpectrum();
    }

}