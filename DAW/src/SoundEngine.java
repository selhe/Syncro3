import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.sampled.*;

/**
 * Real-time polyphonic mixer.
 *
 * Architecture: a single dedicated thread owns the audio output and continuously
 * renders short buffers (BUF_SAMPLES) sample-by-sample, summing all currently-
 * sounding {@link Voice}s into the mix. The Sequencer (or GUI) calls
 * {@link #trigger(Voice)} or one of the {@code triggerXxx()} helpers — those
 * calls are non-blocking and just push the new voice onto a concurrent queue.
 *
 * This decoupling fixes two problems the old "render-and-block-per-step"
 * design had: (1) long notes no longer back-pressure the sequencer thread,
 * so tempo stays stable when notes overlap; (2) the sequencer thread can
 * sleep to its own metronome without ever waiting on audio I/O, so the
 * visual playhead stays tight to the beat.
 */
public class SoundEngine {
    private static final int SAMPLE_RATE     = 44100;
    private static final int BUF_SAMPLES     = 512;   
    private static final int LINE_BUF_BYTES  = 4096; 

    private SourceDataLine line;
    private volatile Synth synth = new Synth();
    public void  setSynth(Synth s) { if (s != null) this.synth = s; }
    public Synth getSynth(){ return synth; }
    private final ConcurrentLinkedQueue<Voice> pending = new ConcurrentLinkedQueue<>();
    private final List<Voice> active = new ArrayList<>();   
    private volatile boolean stopRequested = false;
    private volatile boolean running = true;
    private final Thread mixerThread;

    public SoundEngine() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, LINE_BUF_BYTES);
            line.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        mixerThread = new Thread(this::mixerLoop, "SoundEngine-Mixer");
        mixerThread.setDaemon(true);
        mixerThread.start();
    }

    public static double midiToFreq(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    /** Fire-and-forget: queue this voice into the live mix. */
    public void trigger(Voice v) {
        pending.offer(v);
    }

    public void triggerSynth(int midi, int durMs, int velocity, double gain) {
        // Cap at 4s of synth sustain so a held whole-note at 40 BPM doesn't
        // sit in the active list forever.
        int durSamples = Math.min(SAMPLE_RATE * 4, (int)(SAMPLE_RATE * (durMs / 1000.0)));
        if (durSamples < 1) durSamples = 1;
        double amp = 8000.0 * (velocity / 127.0) * gain;
        trigger(synth.note(midiToFreq(midi), durSamples, amp));
    }

    public void triggerKick(double gain)  { trigger(new KickVoice(gain));  }
    public void triggerSnare(double gain) { trigger(new SnareVoice(gain)); }
    public void triggerHat(double gain)   { trigger(new HatVoice(gain));   }

    public void stopAll() { stopRequested = true; }

    private void mixerLoop() {
        double[] mix = new double[BUF_SAMPLES];
        byte[]   out = new byte[BUF_SAMPLES * 2];

        while (running) {
            if (stopRequested) {
                active.clear();
                pending.clear();
                if (line != null) line.flush();
                stopRequested = false;
            }

            // 1) Drain any newly-triggered voices into the active list.
            Voice incoming;
            while ((incoming = pending.poll()) != null) active.add(incoming);

            // 2) Render BUF_SAMPLES of audio (zero, then sum each voice).
            for (int i = 0; i < BUF_SAMPLES; i++) mix[i] = 0;
            for (int i = 0; i < active.size(); i++) active.get(i).render(mix);
            active.removeIf(Voice::isFinished);

            // 3) Convert doubles -> 16-bit PCM (with clipping) and write.
            for (int i = 0; i < BUF_SAMPLES; i++) {
                double s = mix[i];
                if (s >  32767) s =  32767;
                if (s < -32768) s = -32768;
                short v = (short) s;
                out[i * 2]     = (byte)( v        & 0xff);
                out[i * 2 + 1] = (byte)((v >> 8)  & 0xff);
            }
            // line.write blocks when the line buffer is full; this is what
            // throttles the mixer to real time. Exactly what we want.
            line.write(out, 0, out.length);
        }
    }

    public static abstract class Voice {
        protected int played = 0;
        protected final int total;
        protected Voice(int total) { this.total = total; }
        public boolean isFinished() { return played >= total; }
        /** Add this voice's contribution for the next mix.length samples. */
        public abstract void render(double[] mix);
    }

    /**
     * Kick: 160Hz->45Hz pitch sweep with an explicit power-curve envelope.
     * The 45Hz floor matters: laptop speakers roll off below ~50Hz, so the
     * old 150->0Hz sweep dropped most of its tail into inaudible territory.
     */
    public static class KickVoice extends Voice {
        private final double gain;
        public KickVoice(double gain) { super(2400); this.gain = gain; }
        @Override public void render(double[] mix) {
            int n = Math.min(mix.length, total - played);
            for (int i = 0; i < n; i++) {
                int idx = played + i;
                double t = (double)idx / total;
                double freq = 45.0 + 115.0 * (1.0 - t);              // 160Hz -> 45Hz
                double angle = idx / (SAMPLE_RATE / freq) * 2.0 * Math.PI;
                double envelope = Math.pow(1.0 - t, 0.6);            // punchy attack
                mix[i] += 18000 * gain * Math.sin(angle) * envelope;
            }
            played += n;
        }
    }

    /** Snare: white noise with linear decay. */
    public static class SnareVoice extends Voice {
        private final double gain;
        public SnareVoice(double gain) { super(1500); this.gain = gain; }
        @Override public void render(double[] mix) {
            int n = Math.min(mix.length, total - played);
            for (int i = 0; i < n; i++) {
                int idx = played + i;
                double sample = Math.random() * 14000 - 7000;        // wider noise range
                double envelope = (double)(total - idx) / total;
                mix[i] += sample * envelope * gain;
            }
            played += n;
        }
    }

    /** Hi-hat: white noise with sharp (squared) decay. */
    public static class HatVoice extends Voice {
        private final double gain;
        public HatVoice(double gain) { super(500); this.gain = gain; }
        @Override public void render(double[] mix) {
            int n = Math.min(mix.length, total - played);
            for (int i = 0; i < n; i++) {
                int idx = played + i;
                double sample = Math.random() * 10000 - 5000;        // wider noise range
                double envelope = Math.pow((double)(total - idx) / total, 2);
                mix[i] += sample * envelope * gain;
            }
            played += n;
        }
    }
}