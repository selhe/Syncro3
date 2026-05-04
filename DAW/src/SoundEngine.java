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
    private final Drums drums = new Drums();

    private static final double KICK_DUR_SEC  = 2400.0 / SAMPLE_RATE;  // ~54 ms
    private static final double SNARE_DUR_SEC = 1500.0 / SAMPLE_RATE;  // ~34 ms
    private static final double HAT_DUR_SEC   = 500.0  / SAMPLE_RATE;  // ~11 ms

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

    public void trigger(Voice v) {
        pending.offer(v);
    }

    public void triggerSynth(int midi, int durMs, int velocity, double gain) {

        int durSamples = Math.min(SAMPLE_RATE * 4, (int)(SAMPLE_RATE * (durMs / 1000.0)));
        if (durSamples < 1) durSamples = 1;
        double amp = 8000.0 * (velocity / 127.0) * gain;
        trigger(synth.note(midiToFreq(midi), durSamples, amp));
    }

    public void triggerKick (double gain) { trigger(drums.newKick (gain)); }
    public void triggerSnare(double gain) { trigger(drums.newSnare(gain)); }
    public void triggerHat  (double gain) { trigger(drums.newHat  (gain)); }
    
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

            Voice incoming;
            while ((incoming = pending.poll()) != null) active.add(incoming);

            for (int i = 0; i < BUF_SAMPLES; i++) mix[i] = 0;
            for (int i = 0; i < active.size(); i++) active.get(i).render(mix);
            active.removeIf(Voice::isFinished);

            for (int i = 0; i < BUF_SAMPLES; i++) {
                double s = mix[i];
                if (s >  32767) s =  32767;
                if (s < -32768) s = -32768;
                short v = (short) s;
                out[i * 2]     = (byte)( v        & 0xff);
                out[i * 2 + 1] = (byte)((v >> 8)  & 0xff);
            }

            line.write(out, 0, out.length);
        }
    }

    public static abstract class Voice {
        protected int played = 0;
        protected final int total;
        protected Voice(int total) { this.total = total; }
        public boolean isFinished() { return played >= total; }
        public abstract void render(double[] mix);
    }

    /** Plays back a pre-rendered sample buffer (e.g. from Drums.java). */
    public static class SampleVoice extends Voice {
        private final double[] buffer;
        public SampleVoice(double[] buffer) {
            super(buffer.length);
            this.buffer = buffer;
        }
        @Override public void render(double[] mix) {
            int n = Math.min(mix.length, total - played);
            for (int i = 0; i < n; i++) {
                mix[i] += buffer[played + i];
            }
            played += n;
        }
    }
}