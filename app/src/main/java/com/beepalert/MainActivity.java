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

/**
 * Draggable vertical fader (level 0-10).
 * Higher level = louder, faster and higher-pitched beep.
 * Level 10 = rapid two-tone alert. Sound starts automatically at level 0.
 */
public class MainActivity extends Activity {

    private static final int MAX_LEVEL = 10;
    private static final int SAMPLE_RATE = 44100;

    private volatile int level = 0;
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
        toggle.setOnClickListener(v -> {
            if (playing) stopSound(); else startSound();
        });
        root.addView(toggle);

        updateLabel();
        setContentView(root);
    }

    private void updateLabel() {
        levelText.setText(level == MAX_LEVEL ? "ALERT!" : "Level " + level);
        levelText.setTextColor(level == MAX_LEVEL ? Color.RED : Color.BLACK);
    }

    private void startSound() {
        if (playing) return;
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

    /**
     * Beep cycle per level (level 0 = silent):
     *   level 1: one beep every 1.0 s ... level 9: about 4 beeps per second.
     *   level 10: continuous fast two-tone alert.
     */
    private void beepLoop() {
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, 2048), AudioTrack.MODE_STREAM);
        track.play();

        short[] buf = new short[512];
        double phase = 0;
        int posInCycle = 0;
        int cycleLen = 1;
        int toneLen = 0;
        int cycleCount = 0;

        while (playing) {
            for (int i = 0; i < buf.length; i++) {
                if (posInCycle >= cycleLen) {
                    // Start a new beep cycle using the current level.
                    posInCycle = 0;
                    cycleCount++;
                    int lv = level;
                    if (lv == 0) {
                        cycleLen = SAMPLE_RATE / 20;   // silent, re-check level every 50 ms
                        toneLen = 0;
                    } else if (lv == MAX_LEVEL) {
                        cycleLen = SAMPLE_RATE * 125 / 1000;  // 125 ms, no gap
                        toneLen = cycleLen;
                    } else {
                        // 1000 ms at level 1 down to 240 ms at level 9
                        int ms = 1000 - (lv - 1) * 95;
                        cycleLen = SAMPLE_RATE * ms / 1000;
                        toneLen = cycleLen / 2;
                    }
                }

                int lv = level;
                double sample = 0;
                if (posInCycle < toneLen && lv > 0) {
                    double freq;
                    if (lv == MAX_LEVEL) {
                        freq = (cycleCount % 2 == 0) ? 1800 : 1200;  // two-tone alert
                    } else {
                        freq = 700 + lv * 70;   // 770 Hz .. 1330 Hz
                    }
                    phase += 2 * Math.PI * freq / SAMPLE_RATE;
                    if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                    double v = (double) lv / MAX_LEVEL;
                    sample = v * v * Short.MAX_VALUE * Math.sin(phase);
                }
                buf[i] = (short) sample;
                posInCycle++;
            }
            track.write(buf, 0, buf.length);
        }
        track.stop();
        track.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSound();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSound();
    }
}
