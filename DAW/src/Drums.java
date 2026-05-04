public final class Drums{

    private static final int SAMPLE_RATE = 44100;

    private int played = 0;
    private int total = 2400;

    //UNTESTED. May swap back to void & take in double[] param to alter.
    public void kick(double[] mix, double gain){
        final int numSamples = (int)(SAMPLE_RATE * 0.5);
        double angle1 = 0;
        double angle2 = 0;
        double angle3 = 0;

        int n = Math.min(mix.length, numSamples);

       for (int i = 0; i < n; i++) {
            int idx = played + i;
            double t = (double)idx / total;
            double freq = 45.0 + 115.0 * (1.0 - Math.min(t * 4.0, 1.0));
            angle1 += (freq / SAMPLE_RATE) * 2.0 * Math.PI;
            angle2 += ((freq * 2) / SAMPLE_RATE) * 2.0 * Math.PI;
            angle3 += ((freq * 3) / SAMPLE_RATE) * 2.0 * Math.PI;
            double click = 0;
            if(t < 0.005) click = (Math.random() * 2 - 1) * (1.0 - t / 0.005);
            double envelope1 = Math.pow(1.0 - t, 0.6);
            double envelope2 = Math.pow(1.0 - t, 1.6);
            double envelope3 = Math.pow(1.0 - t, 2.6);
            mix[i] += 18000 * gain * (Math.sin(angle1) * envelope1)
                    + (Math.sin(angle2) * envelope2 * 0.5)
                    + (Math.sin(angle3) * envelope3 * 0.25) 
                    + (click * 0.3);
        }
        played += n;
    }

    public void snare(){

    }

    public void hat(){

    }
}