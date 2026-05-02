public final class Synth {
	public enum Waveform { // Do we need more?
		SQUARE,
		SAW,
		TRIANGLE
	}

	private final Waveform waveform;
	private final int sampleRate;
	private final int unisonVoices;
	private final double detuneSpreadCents;
	private final int attackSamples;
	private final int decaySamples;
	private final int releaseSamples;
	private final double sustainLevel;

	public Synth() {
		this(Waveform.SQUARE, 44100, 2, 6.0, 12, 40, 80, 0.72);
	}

	public Synth(Waveform waveform,
				 int sampleRate,
				 int unisonVoices,
				 double detuneSpreadCents,
				 int attackMs,
				 int decayMs,
				 int releaseMs,
				 double sustainLevel) {
		this.waveform = waveform;
		this.sampleRate = sampleRate;
		this.unisonVoices = Math.max(1, unisonVoices);
		this.detuneSpreadCents = Math.max(0.0, detuneSpreadCents);
		this.attackSamples = msToSamples(attackMs);
		this.decaySamples = msToSamples(decayMs);
		this.releaseSamples = msToSamples(releaseMs);
		this.sustainLevel = Math.max(0.0, Math.min(1.0, sustainLevel));
	}

	public SoundEngine.Voice note(double frequencyHz, int totalSamples, double peakAmplitude) {
		return new NoteVoice(frequencyHz, Math.max(1, totalSamples), peakAmplitude);
	}

	private int msToSamples(int ms) {
		if (ms <= 0) return 1;
		return Math.max(1, (int) (sampleRate * (ms / 1000.0)));
	}

	// Extracted SoundEngine.Voice implementation for a synth note, with unison and ADSR envelope.
	private final class NoteVoice extends SoundEngine.Voice {
		private final double peakAmplitude;
		private final double[] phases;
		private final double[] increments;

		private NoteVoice(double frequencyHz, int totalSamples, double peakAmplitude) {
			super(totalSamples);
			this.peakAmplitude = peakAmplitude;
			this.phases = new double[unisonVoices];
			this.increments = new double[unisonVoices];

			double center = (unisonVoices - 1) / 2.0;
			for (int i = 0; i < unisonVoices; i++) {
				double spread = (center == 0.0) ? 0.0 : (i - center) / center;
				double detuneRatio = Math.pow(2.0, (detuneSpreadCents * spread) / 1200.0);
				increments[i] = (frequencyHz * detuneRatio) / sampleRate;
				phases[i] = i / (double) unisonVoices;
			}
		}

		public void render(double[] mix) {
			int n = Math.min(mix.length, total - played);
			for (int i = 0; i < n; i++) {
				int idx = played + i;
				double envelope = envelopeAt(idx);
				double sample = 0.0;

				for (int v = 0; v < phases.length; v++) {
					sample += oscillator(phases[v]);
					phases[v] += increments[v];
					if (phases[v] >= 1.0) phases[v] -= 1.0;
				}

				sample /= phases.length;
				mix[i] += sample * peakAmplitude * envelope;
			}
			played += n;
		}

		private double envelopeAt(int sampleIndex) {
			int attack = Math.min(attackSamples, total);
			int remaining = total - attack;
			int decay = Math.min(decaySamples, Math.max(0, remaining));
			remaining -= decay;
			int release = Math.min(releaseSamples, Math.max(0, remaining));
			int sustain = Math.max(0, remaining - release);

			if (sampleIndex < attack) {
				return sampleIndex / (double) Math.max(1, attack);
			}

			sampleIndex -= attack;
			if (sampleIndex < decay) {
				double t = sampleIndex / (double) Math.max(1, decay);
				return 1.0 - (1.0 - sustainLevel) * t;
			}

			sampleIndex -= decay;
			if (sampleIndex < sustain) {
				return sustainLevel;
			}

			sampleIndex -= sustain;
			if (release <= 0) {
				return 0.0;
			}

			double t = sampleIndex / (double) Math.max(1, release);
			return sustainLevel * Math.max(0.0, 1.0 - t);
		}

		private double oscillator(double phase) {
			phase -= Math.floor(phase);
			switch (waveform) {
				case SAW:
					return 2.0 * phase - 1.0;
				case TRIANGLE:
					return 1.0 - 4.0 * Math.abs(phase - 0.5);
				case SQUARE:
				default:
					return (phase < 0.5) ? 1.0 : -1.0;
			}
		}
	}
}
