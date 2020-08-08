package com.example.music_box;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;


public class MainActivity extends AppCompatActivity {
    private final String TAG = com.example.music_box.MainActivity.class.toString();
    private static long mEngineHandle = 0;

    private native long createEngine(int[] cpuIds);
    private native void deleteEngine(long engineHandle);
    private native void tap(long engineHandle, boolean isDown);

    private native void pause(long engineHandle, boolean isPause);

    private native void noteOn(long engineHandle, int note);

    private static native void native_setDefaultStreamValues(int sampleRate, int framesPerBurst);

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);
        final TextView tvPlayStatus = findViewById(R.id.tvPlayStatus);
        Button btn = findViewById(R.id.button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(mEngineHandle, true);
                tvPlayStatus.setText("Playing...");
            }
        });

        Button bt2 = findViewById(R.id.button2);
        bt2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tap(mEngineHandle, false);
                tvPlayStatus.setText("Stop");
            }
        });

        Button btnPause = findViewById(R.id.btnPause);
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause(mEngineHandle, true);
                tvPlayStatus.setText("Pause");
            }
        });

        Button btnMoveon = findViewById(R.id.btnMoveon);
        btnMoveon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause(mEngineHandle, false);
                tvPlayStatus.setText("Playing...");
            }
        });

        tv.setText(stringFromJNI());
        setDefaultStreamValues(this);
        mEngineHandle = createEngine(getExclusiveCores());
    }

    @Override
    protected void onResume(){
        Log.d(TAG, "onResume");
        super.onResume();
    }


    @Override
    protected void onPause(){
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy(){
        Log.d(TAG, "onDestroy");
       //  deleteEngine(mEngineHandle);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        deleteEngine(mEngineHandle);
        super.onBackPressed();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN){
           noteOn(mEngineHandle, 46);
        } else if (event.getAction() == MotionEvent.ACTION_UP){
           // noteOn(mEngineHandle, false);
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
