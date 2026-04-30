import javax.sound.sampled.*;

public class SoundEngine {
    private SourceDataLine line;
    private AudioFormat format;
    private static final int SAMPLE_RATE = 44100;

    /* Drum sample lengths */
    private static final int KICK_SAMPLES  = 2000;
    private static final int SNARE_SAMPLES = 1500;
    private static final int HAT_SAMPLES   = 500;

    /* Hard ceiling on per-note duration so a 64-step whole-note doesn't blow the buffer. */
    private static final int MAX_NOTE_SAMPLES = SAMPLE_RATE; // 1s

    public SoundEngine() {
        try {
            format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(format);

            line.open(format, SAMPLE_RATE) ;
            line.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    /** Convert a MIDI pitch (60 = middle C) to a frequency in Hz. */
    public static double midiToFreq(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    /**
     * Render and play a single audio "tick": any active drums plus any chord notes,
     * all summed into one buffer so they play SIMULTANEOUSLY.
     *
     * @param drums           length-3 array: [kick, snare, hat] active flags
     * @param chordPitches    MIDI pitches that should sound on this step
     * @param chordVelocities velocities (0-127) parallel to chordPitches
     * @param chordDurMs      per-note durations in ms (parallel to chordPitches)
     * @param drumGain        0..1, multiplier on drum amplitude (drum vol * master vol)
     * @param synthGain       0..1, multiplier on synth amplitude (synth vol * master vol)
     */
    public void playStep(boolean[] drums,
                         int[] chordPitches,
                         int[] chordVelocities,
                         int[] chordDurMs,
                         double drumGain,
                         double synthGain) {

        int bufLen = 0;
        if (drums.length > 0 && drums[0]) bufLen = Math.max(bufLen, KICK_SAMPLES);
        if (drums.length > 1 && drums[1]) bufLen = Math.max(bufLen, SNARE_SAMPLES);
        if (drums.length > 2 && drums[2]) bufLen = Math.max(bufLen, HAT_SAMPLES);
        for (int dur : chordDurMs) {
            int s = (int)(SAMPLE_RATE * (dur / 1000.0));
            if (s > MAX_NOTE_SAMPLES) s = MAX_NOTE_SAMPLES;
            if (s > bufLen) bufLen = s;
        }
        if (bufLen == 0) return;

        double[] mix = new double[bufLen];

        /* Drums, scaled by drum gain */
        if (drums.length > 0 && drums[0]) addKick(mix,  drumGain);
        if (drums.length > 1 && drums[1]) addSnare(mix, drumGain);
        if (drums.length > 2 && drums[2]) addHat(mix,   drumGain);

        // Chord notes. Each note honors its own velocity AND the synth gain.
        // We additionally divide by sqrt(N) so a 5-note chord doesn't sit 5x louder
        // than a single note (still leaves user-controlled gain in charge of clipping).
        double polyScale = (chordPitches.length > 0)
                ? 1.0 / Math.sqrt(chordPitches.length)
                : 1.0;
        for (int i = 0; i < chordPitches.length; i++) {
            int durMs = chordDurMs[i];
            int durSamples = (int)(SAMPLE_RATE * (durMs / 1000.0));
            if (durSamples > MAX_NOTE_SAMPLES) durSamples = MAX_NOTE_SAMPLES;
            double amp = 8000.0 * (chordVelocities[i] / 127.0) * synthGain * polyScale;
            addSynthNote(mix, midiToFreq(chordPitches[i]), durSamples, amp);
        }

        writeBuffer(mix);
    }

    /** Convenience: mono tone with default volume (e.g. for previews). */
    public void playChord(int[] midiPitches, int durationMs) {
        int[] vels = new int[midiPitches.length];
        int[] durs = new int[midiPitches.length];
        for (int i = 0; i < midiPitches.length; i++) { vels[i] = 100; durs[i] = durationMs; }
        playStep(new boolean[3], midiPitches, vels, durs, 1.0, 1.0);
    }

    /* Voice generation */
    private void addSynthNote(double[] mix, double freq, int durSamples, double amp) {
        int n = Math.min(mix.length, durSamples);
        for (int i = 0; i < n; i++) {
            double angle = i / (SAMPLE_RATE / freq) * 2.0 * Math.PI;
            double sample = (Math.sin(angle) > 0) ? amp : -amp; 
            double envelope = (double)(n - i) / n;              
            mix[i] += sample * envelope;
        }
    }

    private void addKick(double[] mix, double gain) {
        int n = Math.min(mix.length, KICK_SAMPLES);
        for (int i = 0; i < n; i++) {
            double freq = 150.0 * (1.0 - (double)i / n);
            double angle = i / (SAMPLE_RATE / freq) * 2.0 * Math.PI;
            mix[i] += 12000 * gain * Math.sin(angle);
        }
    }

    private void addSnare(double[] mix, double gain) {
        int n = Math.min(mix.length, SNARE_SAMPLES);
        for (int i = 0; i < n; i++) {
            double sample = Math.random() * 10000 - 5000;
            double envelope = (double)(n - i) / n;
            mix[i] += sample * envelope * gain;
        }
    }

    private void addHat(double[] mix, double gain) {
        int n = Math.min(mix.length, HAT_SAMPLES);
        for (int i = 0; i < n; i++) {
            double sample = Math.random() * 8000 - 4000;
            double envelope = Math.pow((double)(n - i) / n, 2);
            mix[i] += sample * envelope * gain;
        }
    }

    /* Buffer output */
    private void writeBuffer(double[] mix) {
        byte[] buf = new byte[mix.length * 2];
        for (int i = 0; i < mix.length; i++) {
            double v = mix[i];
            if (v >  32767) v =  32767;
            if (v < -32768) v = -32768;
            short s = (short) v;
            buf[i * 2]     = (byte)(s & 0xff);
            buf[i * 2 + 1] = (byte)((s >> 8) & 0xff);
        }
        line.write(buf, 0, buf.length);
    }
}