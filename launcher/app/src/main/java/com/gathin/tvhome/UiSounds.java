package com.gathin.tvhome;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Short UI sounds for the home styles, synthesised once into static AudioTracks: no sound files, no decoding at
 * play time. A tick for each move, a soft thunk at an edge, a rising whoosh when an app opens, a pop for menus.
 */
final class UiSounds {
    private static final int RATE = 22050;
    private final AudioTrack tick, thunk, whoosh, pop;
    private boolean enabled;

    UiSounds(boolean enabled) {
        this.enabled = enabled;
        tick = enabled ? track(tone(2400, 2400, 0.035f, 0.10f, false)) : null;
        thunk = enabled ? track(tone(160, 90, 0.12f, 0.30f, false)) : null;
        whoosh = enabled ? track(mix(tone(320, 1400, 0.45f, 0.10f, true), tone(640, 2000, 0.5f, 0.04f, true))) : null;
        pop = enabled ? track(tone(900, 1500, 0.08f, 0.12f, false)) : null;
    }

    void tick() { play(tick); }
    void thunk() { play(thunk); }
    void whoosh() { play(whoosh); }
    void pop() { play(pop); }

    void release() {
        enabled = false;
        for (AudioTrack t : new AudioTrack[]{tick, thunk, whoosh, pop}) {
            if (t != null) t.release();
        }
    }

    private void play(AudioTrack t) {
        if (!enabled || t == null || t.getState() != AudioTrack.STATE_INITIALIZED) return;
        try {
            if (t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) t.stop();
            t.reloadStaticData();
            t.play();
        } catch (IllegalStateException ignored) {
            // A track the system took back (audio focus, low memory): stay silent rather than fail a key press.
        }
    }

    /** A sine sweep from f0 to f1 Hz with an exponential decay; swell ramps in over the first third. */
    private static short[] tone(float f0, float f1, float seconds, float gain, boolean swell) {
        int n = Math.round(RATE * seconds);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 * Math.pow(f1 / f0, t);
            phase += 2 * Math.PI * f / RATE;
            float env = (float) Math.exp(-5 * t);
            if (swell) env *= Math.min(1f, t * 3f);
            // A 2 ms attack, so the start does not click.
            env *= Math.min(1f, i / (RATE * 0.002f));
            out[i] = (short) (Math.sin(phase) * env * gain * Short.MAX_VALUE);
        }
        return out;
    }

    private static short[] mix(short[] a, short[] b) {
        short[] out = new short[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++) {
            int v = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return out;
    }

    private static AudioTrack track(short[] pcm) {
        try {
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.length * 2)
                    .build();
            t.write(pcm, 0, pcm.length);
            return t;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
