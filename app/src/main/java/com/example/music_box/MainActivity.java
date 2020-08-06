package com.example.music_box;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;


public class MainActivity extends AppCompatActivity {
    private final String TAG = com.example.music_box.MainActivity.class.toString();
    private static long mEngineHandle = 0;

    private native long startEngine(int[] cpuIds);
    private native void stopEngine(long engineHandle);
    private native void tap(long engineHandle, boolean isDown);

    private static native void native_setDefaultStreamValues(int sampleRate, int framesPerBurst);

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());
        setDefaultStreamValues(this);
    }

    @Override
    protected void onResume(){
        mEngineHandle = startEngine(getExclusiveCores());
        super.onResume();
    }


    @Override
    protected void onPause(){
        stopEngine(mEngineHandle);
        super.onPause();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_DOWN){
            tap(mEngineHandle, true);
        } else if (event.getAction() == MotionEvent.ACTION_UP){
            tap(mEngineHandle, false);
        }
        return super.onTouchEvent(event);
    }

    // Obtain CPU cores which are reserved for the foreground app. The audio thread can be
    // bound to these cores to avoids the risk of it being migrated to slower or more contended
    // core(s).
    private int[] getExclusiveCores(){
        int[] exclusiveCores = {};

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "getExclusiveCores() not supported. Only available on API " +
                    Build.VERSION_CODES.N + "+");
        } else {
            try {
                exclusiveCores = android.os.Process.getExclusiveCores();
            } catch (RuntimeException e){
                Log.w(TAG, "getExclusiveCores() is not supported on this device.");
            }
        }
        return exclusiveCores;
    }

    static void setDefaultStreamValues(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
            AudioManager myAudioMgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            String sampleRateStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            int defaultSampleRate = Integer.parseInt(sampleRateStr);
            String framesPerBurstStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            int defaultFramesPerBurst = Integer.parseInt(framesPerBurstStr);
            native_setDefaultStreamValues(defaultSampleRate, defaultFramesPerBurst);
        }
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}
