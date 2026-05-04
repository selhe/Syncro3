public final class Drums{

    private static final int SAMPLE_RATE = 44100;

    //UNTESTED. May swap back to void & take in double[] param to alter.
    public double[] kick(double gain, double duration){
        final int numSamples = (int)(SAMPLE_RATE * duration);
        int played = 0;
        double[] samples = new double[numSamples];
        double startFreq = 160.0;
        double endFreq = 0.0;
        double freq = 0;
        double t = 0;
        double amp = gain;

        for(int i = 0; i < numSamples; i++){
            t = (played + 1) / numSamples;
            freq = startFreq * Math.pow(endFreq / startFreq, t / duration);
            amp = Math.pow(0.001, t / duration);
            samples[i] = Math.sin(2 * Math.PI * freq * t) * (amp * gain);
        }

        return samples;
    }

    public void snare(){

    }

    public void hat(){

    }
}