public final class Drums{

    private static final int SAMPLE_RATE = 44100;

    private int played = 0;
    private int total = 2400;

    //Needs LPF, but good enough for right now.
    public void kick(double[] mix, double gain){
        final int numSamples = (int)(SAMPLE_RATE * 0.4);
        double angle1 = 0;
        double angle2 = 0;
        double angle3 = 0;

        int n = Math.min(mix.length, numSamples);

       for (int i = 0; i < n; i++) {
            int idx = played + i;
            double t = (double)idx / total;
            double freq = 45.0 + 115.0 * (1.0 - Math.min(t * 4.0, 1.0));
            angle1 += (freq / SAMPLE_RATE) * 2.0 * Math.PI;
            angle2 += ((freq * 2.3) / SAMPLE_RATE) * 2.0 * Math.PI;
            angle3 += ((freq * 3.7) / SAMPLE_RATE) * 2.0 * Math.PI;
            double click = 0;
            double attack = Math.min(t / 0.015, 1.0);
            if(t < 0.005) click = (Math.random() * 2 - 1) * (1.0 - t / 0.005);
            double envelope1 = Math.pow(1.0 - t, 0.6);
            double envelope2 = Math.pow(1.0 - t, 3.0);
            double envelope3 = Math.pow(1.0 - t, 5.0);
            mix[i] += 18000 * gain * attack * ((Math.sin(angle1) * envelope1)
                    + (Math.sin(angle2) * envelope2 * 0.25)
                    + (Math.sin(angle3) * envelope3 * 0.15) 
                    + (click * 0.003));
        }
        played += n;
    }

    //HPF? Fiddle with variance some more
    public void snare(double[] mix, double gain){
        int n = Math.min(mix.length, total - played);
        for (int i = 0; i < n; i++) {
            int idx = played + i;
            double sample1 = Math.random() * 14000 - 7000;
            double sample2 = Math.random() * 14000 - 6500;
            double sample3 = Math.random() * 14000 - 7500;        
            double envelope1 = (double)(total - idx) / total;
            double envelope2 = ((double)(total - idx) / total) * 0.5;
            double envelope3 = ((double)(total - idx) / total) * 0.3;
            mix[i] += ((sample1 * envelope1) + (sample2 * envelope2) + (sample3 * envelope3)) * gain;
        }
        played += n;
    }

    //HPF? Fiddle with variance & equations some more.
    public void hat(double[] mix, double gain){
        int n = Math.min(mix.length, total - played);
        for (int i = 0; i < n; i++) {
            int idx = played + i;
            double sample1 = Math.random() * 10000 - 5000;
            double sample2 = Math.random() * 10000 - 4500;
            double sample3 = Math.random() * 10000 - 5500;        
            double envelope1 = Math.pow((double)(total - idx) / total, 2);
            double envelope2 = Math.pow((double)(total - idx) / total, 2.5);
            double envelope3 = Math.pow((double)(total - idx) / total, 3);
            mix[i] += ((sample1 * envelope1) + (sample2 * envelope2) + (sample3 * envelope3)) * gain;
        }
        played += n;
    }
}