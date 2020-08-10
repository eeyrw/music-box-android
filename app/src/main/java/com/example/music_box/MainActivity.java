package com.example.music_box;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;

import com.customview.graph.LineGraphView;
import com.pdrogfer.mididroid.MidiFile;
import com.pdrogfer.mididroid.event.MidiEvent;
import com.pdrogfer.mididroid.event.NoteOn;
import com.pdrogfer.mididroid.event.meta.Tempo;
import com.pdrogfer.mididroid.examples.EventPrinter;
import com.pdrogfer.mididroid.util.MidiEventListener;
import com.pdrogfer.mididroid.util.MidiProcessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class MainActivity extends AppCompatActivity {
    private final String TAG = com.example.music_box.MainActivity.class.toString();
    private static long mEngineHandle = 0;
    private String midiFilePath;
    private MidiProcessor processor;
    private int transposeValue = 0;

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
            // System.out.println(mLabel + " received event: " + event);
            final int note = ev.getNoteValue();
            int velocity = ev.getVelocity();
            int channel = ev.getChannel();
            if (velocity != 0 && channel != 9) {
                final int noteTranspose = note + transposeValue;
                if (noteTranspose >= 0 && noteTranspose <= 127) {
                    noteOn(mEngineHandle, noteTranspose);
                    Log.d(TAG, String.format("onMidiEvent: %d", noteTranspose));
                    runOnUiThread(new Runnable() {
                        public void run() {
                            final TextView tvNoteNum = findViewById(R.id.tvNoteNum);
                            tvNoteNum.setText(String.format("N: %d", noteTranspose));
                        }
                    });
                }

            }


        }

        @Override
        public void onStop(boolean finished) {
            if (finished) {
                Log.d(TAG, "onMidiEvent: Stop");
                pause(mEngineHandle, true);
                runOnUiThread(new Runnable() {
                    public void run() {
                        final TextView tvPlayStatus = findViewById(R.id.tvPlayStatus);
                        tvPlayStatus.setText("Stop");
                    }
                });
            } else {
                // System.out.println(mLabel + " paused");
            }
        }

    }


    private void printMidiFile(String midiFilePath) {

        try {
            InputStream input = getAssets().open(midiFilePath);
            MidiFile midi = new MidiFile(input);
            // Create a new MidiProcessor:
            MidiEventPlayer ep = new MidiEventPlayer("sd");
            processor = new MidiProcessor(midi);

            processor.registerEventListener(ep, NoteOn.class);

// Start the processor:
            processor.start();
        } catch (IOException e) {
            System.err.println(e);
        }

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
                processor.stop();
                tvPlayStatus.setText("Stop");
            }
        });

        Button btnPause = findViewById(R.id.btnPause);
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause(mEngineHandle, true);
                processor.stop();
                tvPlayStatus.setText("Pause");
            }
        });

        Button btnMoveon = findViewById(R.id.btnMoveon);
        btnMoveon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause(mEngineHandle, false);
                processor.start();
                tvPlayStatus.setText("Playing...");
            }
        });

        Button btnStartMidi = findViewById(R.id.btnStartMidi);
        btnStartMidi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause(mEngineHandle, false);
                tvPlayStatus.setText("Midi Playing...");
                printMidiFile(midiFilePath);
            }
        });

        Button btnChooseMidi = findViewById(R.id.btnChooseMidi);
        btnChooseMidi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                startActivityForResult(intent, 0);//此处的requestCode应与下面结果处理函中调用的requestCode一致
            }
        });

        SeekBar sbTranspose = findViewById(R.id.sbTranspose);
        sbTranspose.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                transposeValue = progress - 24;
                TextView tvTransposeValue = findViewById(R.id.tvTransposeValue);
                tvTransposeValue.setText(String.format("Transpose: %d half-tone", transposeValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        LineGraphView mLineGraphView = (LineGraphView) findViewById(R.id.lineGraphView);
        float[] aaa = new float[128];
        mLineGraphView.setValueArray(aaa);

        tv.setText(stringFromJNI());
        setDefaultStreamValues(this);
        mEngineHandle = createEngine(getExclusiveCores());
        Intent intent = new Intent(MainActivity.this, FileListActivity.class);
        startActivityForResult(intent, 0);//此处的requestCode应与下面结果处理函中调用的requestCode一致


    }

    //结果处理函数，当从secondActivity中返回时调用此函数
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            if (bundle != null)
                midiFilePath = bundle.getString("filePath");
            TextView tvFileName = findViewById(R.id.tvFileName);
            tvFileName.setText(midiFilePath);
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }


    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
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
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            noteOn(mEngineHandle, 46);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            // noteOn(mEngineHandle, false);
        }
        return super.onTouchEvent(event);
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

    static void setDefaultStreamValues(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
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
