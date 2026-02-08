package com.yuan.music_box;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.customview.graph.AudioMeterView;
import com.customview.graph.PianoRollView;
import com.customview.graph.TransposeSliderView;
import com.customview.graph.VuLevel;
import com.yuan.midiplayer.MidiPlayer;
import com.yuan.midiplayer.MidiPlayerEventListener;
import com.yuan.midiplayer.Player;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private final String TAG = com.yuan.music_box.MainActivity.class.toString();
    private MidiPlayer midiPlayer;
    private String midiFilePath;

    private PianoRollView pianoRollView;

    private AudioMeterView meterView;

    private TransposeSliderView transposeSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        meterView = findViewById(R.id.audio_meter);

        pianoRollView = findViewById(R.id.pianoRollView);
        // 设置 noteOn only 模式
        //pianoView.setNoteOnOnlyMode(true);

        // 设置 attack/release 时间
        pianoRollView.setAttackRelease(100, 300);

        pianoRollView.setHighlightColor(getResources().getColor(R.color.colorPrimary));

        pianoRollView.setFallingNoteColor(getResources().getColor(R.color.colorPrimary));

        transposeSlider = findViewById(R.id.transposeSlider);

        final TextView tvPlayStatus = findViewById(R.id.tvPlayStatus);

        ImageButton btnStop = findViewById(R.id.btnStop);

        final TextView tvFileName = findViewById(R.id.tvFileName);
        tvFileName.setSelected(true);
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                midiPlayer.stop();
            }
        });

        ImageButton btnPause = findViewById(R.id.btnPause);
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                midiPlayer.pause();
            }
        });

        ImageButton btnPlay = findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                midiPlayer.play();
            }
        });

        Button btnChooseSampleMidi = findViewById(R.id.btnChooseSampleMidi);
        btnChooseSampleMidi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                midiPlayer.pause();
                Intent intent = new Intent(MainActivity.this, FileListActivity.class);
                startActivityForResult(intent, 0);//此处的requestCode应与下面结果处理函中调用的requestCode一致
            }
        });

        Button btnChooseMidi = findViewById(R.id.btnChooseMidi);
        btnChooseMidi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                // intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/mid"});
                intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 2);
            }
        });

        Button btnScanQRCode = findViewById(R.id.btnScanQRCode);
        btnScanQRCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                midiPlayer.pause();
                Intent intent = new Intent(MainActivity.this, ContinuousCaptureActivity.class);
                startActivityForResult(intent, 3);//此处的requestCode应与下面结果处理函中调用的requestCode一致
            }
        });


        // 监听移调变化
        transposeSlider.setOnSemitoneChangeListener(semitone -> {

            TextView tvTransposeValue = findViewById(R.id.tvTransposeValue);
            tvTransposeValue.setText(String.format("Transpose: %d half-tone", semitone));

            midiPlayer.setTranspose(semitone);
        });


        Intent intent = new Intent(MainActivity.this, FileListActivity.class);
        startActivityForResult(intent, 0);//此处的requestCode应与下面结果处理函中调用的requestCode一致

        midiPlayer = new MidiPlayer(new MidiPlayerEventListener() {
            @Override
            public void onPlayStateChange(Player.PlayerState state) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {


                        tvPlayStatus.setText(state.toString());

                        switch (state) {
                            case PLAYING:
                                // 开始播放
                                pianoRollView.resumePlayback();
                                break;
                            case PAUSE:
                            case PAUSE_BY_OS:
                                pianoRollView.pausePlayback();
                                break;
                            case STOP:
                                pianoRollView.stopPlayback();
                                break;
                        }
                    }
                });
            }

            @Override
            public void onSuggestTransposeChange(int transpose) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        transposeSlider.setSemitone(transpose);
                        TextView tvTransposeValue = findViewById(R.id.tvTransposeValue);
                        tvTransposeValue.setText(String.format("Transpose: %d half-tone", transpose));
                    }
                });
            }

            @Override
            public void onVisualChangeChange(float[] waveform, float[] spectrum, VuLevel vuLevel) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        meterView.pushAudioFrame(waveform, spectrum, vuLevel);
                    }
                });
            }

            @Override
            public void onNoteOn(int note, long ms) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        //pianoView.noteOn(note, 1.0f);
                        pianoRollView.setPlaybackTime(ms);
                    }
                });
            }

            @Override
            public void onGetNoteList(List<PianoRollView.NoteEvent> noteList) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        //pianoView.noteOn(note, 1.0f);
                        pianoRollView.loadNoteEvents(noteList);
                        pianoRollView.setPlaybackTime(0);  // 从头开始
                        pianoRollView.startPlayback();      // 启动内部刷新
                    }
                });
            }

        });
    }

    //结果处理函数，当从secondActivity中返回时调用此函数
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            if (bundle != null) {
                midiFilePath = bundle.getString("filePath");
                TextView tvFileName = findViewById(R.id.tvFileName);
                tvFileName.setText(midiFilePath);
                try {
                    InputStream input = getAssets().open(midiFilePath);
                    midiPlayer.stop();
                    midiPlayer.playMidiFile(input);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == 2 && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            Uri uri = data.getData();//得到uri，后面就是将uri转化成file的过程。
            midiFilePath = uri.getPath();
            Toast.makeText(MainActivity.this, midiFilePath, Toast.LENGTH_SHORT).show();
            TextView tvFileName = findViewById(R.id.tvFileName);
            tvFileName.setText(midiFilePath);
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                midiPlayer.stop();
                midiPlayer.playMidiFile(inputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        } else if (requestCode == 3 && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            if (bundle != null) {
                byte[] a = bundle.getByteArray("songContent");
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < a.length; j++) {
                    sb.append(String.format("%02X ", a[j] & 0xFF));
                }
                Log.d(TAG, sb.toString());
                Log.d(TAG, String.format("DataLen %d", a.length));
            }
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        midiPlayer.returnFromBack();
    }


    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        midiPlayer.goToBack();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        //  deleteEngine(mEngineHandle);
        midiPlayer.releaseResource();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        // midiPlayer.releaseResource();
        super.onBackPressed();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // midiPlayer.mEngine.noteOn(45);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            // noteOn(mEngineHandle, false);
        }
        return super.onTouchEvent(event);
    }


    static void setDefaultStreamValues(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            AudioManager myAudioMgr = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            String sampleRateStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            int defaultSampleRate = Integer.parseInt(sampleRateStr);
            String framesPerBurstStr = myAudioMgr.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            int defaultFramesPerBurst = Integer.parseInt(framesPerBurstStr);
        }
    }
}
