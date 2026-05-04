import java.awt.*;
import javax.swing.*;

public class SynthPanel extends JPanel {
    private final SoundEngine engine;

    private final JComboBox<Synth.Waveform> waveBox;
    private final JSlider unisonSlider, detuneSlider;
    private final JSlider attackSlider, decaySlider, releaseSlider, sustainSlider;

    public SynthPanel(SoundEngine engine) {
        this.engine = engine;
        Synth s = engine.getSynth();

        setLayout(new GridLayout(0, 2, 6, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        waveBox      = new JComboBox<>(Synth.Waveform.values());
        waveBox.setSelectedItem(s.getWaveform());

        unisonSlider = slider(1, 7,  s.getUnisonVoices());
        detuneSlider = slider(0, 50, (int) s.getDetuneCents());

        attackSlider  = slider(0, 500,  s.getAttackMs());
        decaySlider   = slider(0, 1000, s.getDecayMs());
        releaseSlider = slider(0, 1000, s.getReleaseMs());
        sustainSlider = slider(0, 100,  (int)(s.getSustainLevel() * 100));

        add(new JLabel("Waveform")); add(waveBox);
        add(new JLabel("Unison voices")); add(unisonSlider);
        add(new JLabel("Detune (cents)")); add(detuneSlider);
        add(new JLabel("Attack (ms)"));  add(attackSlider);
        add(new JLabel("Decay (ms)"));   add(decaySlider);
        add(new JLabel("Sustain (%)"));  add(sustainSlider);
        add(new JLabel("Release (ms)")); add(releaseSlider);

        Runnable apply = this::rebuildSynth;
        waveBox.addActionListener(e -> apply.run());
        for (JSlider sl : new JSlider[]{unisonSlider, detuneSlider,
                attackSlider, decaySlider, releaseSlider, sustainSlider}) {
            sl.addChangeListener(e -> { if (!sl.getValueIsAdjusting()) apply.run(); });
        }
    }

    private static JSlider slider(int min, int max, int val) {
        JSlider s = new JSlider(min, max, Math.min(max, Math.max(min, val)));
        s.setPreferredSize(new Dimension(220, 28));
        return s;
    }

    private void rebuildSynth() {
        Synth s = new Synth(
            (Synth.Waveform) waveBox.getSelectedItem(),
            44100,
            unisonSlider.getValue(),
            detuneSlider.getValue(),
            attackSlider.getValue(),
            decaySlider.getValue(),
            releaseSlider.getValue(),
            sustainSlider.getValue() / 100.0
        );
        engine.setSynth(s);
    }
}