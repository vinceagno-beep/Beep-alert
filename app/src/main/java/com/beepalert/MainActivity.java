package com.beepalert;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Draggable vertical fader (level 0-10). Higher level = louder beep. */
public class MainActivity extends Activity {

    private static final int MAX_LEVEL = 10;
    private static final int SAMPLE_RATE = 44100;
    private static final double TONE_HZ = 880.0;

    private volatile int level = 5;
    private volatile boolean playing = false;
    private Thread audioThread;

    private TextView levelText;
    private Button toggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(64, 64, 64, 64);
        root.setBackgroundColor(Color.WHITE);

        levelText = new TextView(this);
        levelText.setTextSize(48);
        levelText.setTextColor(Color.BLACK);
        levelText.setGravity(Gravity.CENTER);
        root.addView(levelText);

        LevelView fader = new LevelView(this, MAX_LEVEL, level);
        fader.setOnLevelChangeListener(l -> {
            level = l;
            updateLabel();
        });
        float d = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (80 * d), (int) (320 * d));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, (int) (24 * d), 0, (int) (24 * d));
        root.addView(fader, lp);

        toggle = new Button(this);
        toggle.setText("Start");
        toggle.setOnClickListener(v -> {
            if (playing) stopSound(); else startSound();
        });
        root.addView(toggle);

        updateLabel();
        setContentView(root);
    }

    private void updateLabel() {
        levelText.setText("Level " + level);
    }

    private void startSound() {
        playing = true;
        toggle.setText("Stop");
        audioThread = new Thread(this::beepLoop);
        audioThread.start();
    }

    private void stopSound() {
        playing = false;
        toggle.setText("Start");
        if (audioThread != null) {
            try { audioThread.join(500); } catch (InterruptedException ignored) {}
            audioThread = null;
        }
    }

    /** Repeating beep: 200 ms tone, 300 ms silence. Amplitude follows the level. */
    private void beepLoop() {
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, 4096), AudioTrack.MODE_STREAM);
        track.play();

        int toneSamples = SAMPLE_RATE / 5;      // 200 ms
        int cycleSamples = SAMPLE_RATE / 2;     // 500 ms
        short[] buf = new short[1024];
        long n = 0;

        while (playing) {
            // Squared curve so each level step sounds like a clear increase.
            double v = (double) level / MAX_LEVEL;
            double amp = v * v * Short.MAX_VALUE;
            for (int i = 0; i < buf.length; i++, n++) {
                int pos = (int) (n % cycleSamples);
                buf[i] = pos < toneSamples
                        ? (short) (amp * Math.sin(2 * Math.PI * TONE_HZ * n / SAMPLE_RATE))
                        : 0;
            }
            track.write(buf, 0, buf.length);
        }
        track.stop();
        track.release();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSound();
    }
}
