public final class Drums {

    private static final int SAMPLE_RATE = 44100;

    public SoundEngine.Voice newKick (double gain) { return new KickVoice (gain); }
    public SoundEngine.Voice newSnare(double gain) { return new SnareVoice(gain); }
    public SoundEngine.Voice newHat  (double gain) { return new HatVoice  (gain); }

    /* Needs LPF */
    private static final class KickVoice extends SoundEngine.Voice {
        private final double gain;
        private double angle1, angle2, angle3;
        KickVoice(double gain) {
            super((int)(SAMPLE_RATE * 0.4));   
            this.gain = gain;
        }
        @Override public void render(double[] mix) {
            int n = Math.min(mix.length, total - played);
            for (int i = 0; i < n; i++) {
                int idx = played + i;
                double t = (double) idx / total;
                double freq = 45.0 + 115.0 * (1.0 - Math.min(t * 4.0, 1.0));
                angle1 += (freq        / SAMPLE_RATE) * 2.0 * Math.PI;
                angle2 += (freq * 2.3  / SAMPLE_RATE) * 2.0 * Math.PI;
                angle3 += (freq * 3.7  / SAMPLE_RATE) * 2.0 * Math.PI;
                double attack = Math.min(t / 0.015, 1.0);
                double click  = (t < 0.005) ? (Math.random() * 2 - 1) * (1.0 - t / 0.005) : 0;
                double e1 = Math.pow(1.0 - t, 0.6);
                double e2 = Math.pow(1.0 - t, 3.0);
                double e3 = Math.pow(1.0 - t, 5.0);
                mix[i] += 18000 * gain * attack
                       * (Math.sin(angle1) * e1
                        + Math.sin(angle2) * e2 * 0.25
                        + Math.sin(angle3) * e3 * 0.15
                        + click * 0.003);
            }
            played += n;
        }
    }

    //HPF? Fiddle with variance some more
    private static final class SnareVoice extends SoundEngine.Voice {
        private final double gain;
        SnareVoice(double gain) {
            super(1500);                 
            this.gain = gain;
        }
        @Override public void render(double[] mix) {
            int n = Math.min(mix.length, total - played);
            for (int i = 0; i < n; i++) {
                int idx = played + i;
                double s1 = Math.random() * 14000 - 7000;
                double s2 = Math.random() * 14000 - 6500;
                double s3 = Math.random() * 14000 - 7500;
                double e  = (double)(total - idx) / total;
                mix[i] += (s1 * e + s2 * e * 0.5 + s3 * e * 0.3) * gain;
            }
            played += n;
        }
    }


    //HPF? Fiddle with variance & equations some more.
    private static final class HatVoice extends SoundEngine.Voice {
        private final double gain;
        HatVoice(double gain) {
            super(500);                    
            this.gain = gain;
        }
        @Override public void render(double[] mix) {
            int n = Math.min(mix.length, total - played);
            for (int i = 0; i < n; i++) {
                int idx = played + i;
                double s1 = Math.random() * 10000 - 5000;
                double s2 = Math.random() * 10000 - 4500;
                double s3 = Math.random() * 10000 - 5500;
                double base = (double)(total - idx) / total;
                double e1 = Math.pow(base, 2);
                double e2 = Math.pow(base, 2.5);
                double e3 = Math.pow(base, 3);
                mix[i] += (s1 * e1 + s2 * e2 + s3 * e3) * gain;
            }
            played += n;
        }
    }
}