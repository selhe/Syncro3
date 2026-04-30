import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * This class serves as the primary Graphical User Interface for the 8-Bit Mini-DAW (synco3).
 * It manages the main application window, transport controls, mixers, and the integration 
 * of the drum and piano roll sequencers. I've added more specific documentation in these files
 * than usual. Hopefully it helps when programming other parts of this project!
 * 
 * This class acts as the user GUI for the Sequencer program,
 * ensuring that UI changes update the audio state and thread-safe 
 * playhead updates.
 * 
 * @author Selena He
 */
public class DAWGui {
    /** The underlying logic engine for audio playback and data management. */
    private final Sequencer sequencer;
    private JFrame mainFrame;

    private DrumPanel       drumPanel;
    private PianoRollPanel  pianoPanel;
    private PianoKeysLabelPanel pianoKeyLabels;

    /** Tool bar control. */
    private JSlider  bpmSlider;
    private JLabel   bpmLabel;
    private JComboBox<Integer> stepCombo;
    private JSlider  masterVolSlider, drumVolSlider, synthVolSlider, velocitySlider;
    private JLabel   masterVolLabel, drumVolLabel, synthVolLabel, velocityLabel;

    private boolean syncingControls = false;

    /**
     * Constructs a new {@code DAWGui} and initializes all visual components.
     * * @param sequencer The {@link Sequencer} instance to be controlled by this GUI.
     */
    public DAWGui(Sequencer sequencer) {
        this.sequencer = sequencer;
        sequencer.setGui(this);

        mainFrame = new JFrame("8-Bit Mini-DAW");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());

        mainFrame.add(createToolbar(), BorderLayout.NORTH);

        drumPanel  = new DrumPanel(sequencer);
        pianoPanel = new PianoRollPanel(sequencer);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Drums",      buildDrumTab());
        tabs.addTab("Piano Roll", buildPianoTab());
        mainFrame.add(tabs, BorderLayout.CENTER);

        mainFrame.pack();
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }

    /**
     * Builds the control toolbar containing transport buttons, 
     * BPM settings, step counts, and volume mixers.
     * * @return A {@link JComponent} containing the organized toolbar rows.
     */
    private JComponent createToolbar() {
        /* Implementation details for Row 1 (Transport), Row 2 (Timing), */ 
        /* Row 3 (Volumes), and Row 4 (Velocity) */ 
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton playBtn  = new JButton("Play");
        JButton stopBtn  = new JButton("Stop");
        JButton clearBtn = new JButton("Clear All");
        JButton saveBtn  = new JButton("Save...");
        JButton loadBtn  = new JButton("Load...");
        playBtn.addActionListener(e  -> sequencer.start());
        stopBtn.addActionListener(e  -> sequencer.stop());
        clearBtn.addActionListener(e -> clearAll());
        saveBtn.addActionListener(e  -> doSave());
        loadBtn.addActionListener(e  -> doLoad());
        row1.add(playBtn);
        row1.add(stopBtn);
        row1.add(clearBtn);
        row1.add(saveBtn);
        row1.add(loadBtn);

        /* BPM + step count */
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bpmSlider = new JSlider(JSlider.HORIZONTAL, 40, 300, sequencer.bpm);
        bpmSlider.setPreferredSize(new Dimension(220, 28));
        bpmLabel  = new JLabel("BPM: " + sequencer.bpm);
        bpmSlider.addChangeListener(e -> {
            if (syncingControls) return;
            int v = bpmSlider.getValue();
            sequencer.setBPM(v);
            bpmLabel.setText("BPM: " + v);
        });
        row2.add(new JLabel("Tempo:"));
        row2.add(bpmSlider);
        row2.add(bpmLabel);

        row2.add(Box.createHorizontalStrut(20));
        stepCombo = new JComboBox<>(new Integer[]{16, 32, 64});
        stepCombo.setSelectedItem(sequencer.numSteps);
        stepCombo.addActionListener(e -> {
            if (syncingControls) return;
            int n = (Integer) stepCombo.getSelectedItem();
            sequencer.setNumSteps(n);
            drumPanel.rebuild();
            pianoPanel.rebuild();
            pianoKeyLabels.repaint();
        });
        row2.add(new JLabel("Steps:"));
        row2.add(stepCombo);

        /* Volume Sliders */
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        masterVolSlider = makeVolSlider(sequencer.masterVol);
        masterVolLabel  = new JLabel("Master: " + sequencer.masterVol);
        masterVolSlider.addChangeListener(e -> {
            if (syncingControls) return;
            sequencer.masterVol = masterVolSlider.getValue();
            masterVolLabel.setText("Master: " + sequencer.masterVol);
        });
        drumVolSlider = makeVolSlider(sequencer.drumVol);
        drumVolLabel  = new JLabel("Drums: " + sequencer.drumVol);
        drumVolSlider.addChangeListener(e -> {
            if (syncingControls) return;
            sequencer.drumVol = drumVolSlider.getValue();
            drumVolLabel.setText("Drums: " + sequencer.drumVol);
        });
        synthVolSlider = makeVolSlider(sequencer.synthVol);
        synthVolLabel  = new JLabel("Synth: " + sequencer.synthVol);
        synthVolSlider.addChangeListener(e -> {
            if (syncingControls) return;
            sequencer.synthVol = synthVolSlider.getValue();
            synthVolLabel.setText("Synth: " + sequencer.synthVol);
        });
        row3.add(masterVolLabel); row3.add(masterVolSlider);
        row3.add(drumVolLabel);   row3.add(drumVolSlider);
        row3.add(synthVolLabel);  row3.add(synthVolSlider);

        /* Velocity for piano roll notes */
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        velocitySlider = new JSlider(JSlider.HORIZONTAL, 1, 127, 100);
        velocitySlider.setPreferredSize(new Dimension(220, 28));
        velocityLabel  = new JLabel("New-note velocity: 100");
        velocitySlider.addChangeListener(e -> {
            if (syncingControls) return;
            int v = velocitySlider.getValue();
            velocityLabel.setText("New-note velocity: " + v);
            if (pianoPanel != null) pianoPanel.setDefaultVelocity(v);
        });
        row4.add(velocityLabel);
        row4.add(velocitySlider);

        root.add(row1);
        root.add(row2);
        root.add(row3);
        root.add(row4);
        return root;
    }

    /**
     * A helper method to create a standardized horizontal volume slider.
     * * @param initial The initial volume value to set the slider to.
     * @return A configured {@link JSlider} with 140x28 pixels.
     */
    private static JSlider makeVolSlider(int initial) {
        JSlider s = new JSlider(JSlider.HORIZONTAL, 0, 100, initial);
        s.setPreferredSize(new Dimension(140, 28));
        return s;
    }

    /**
     * Constructs the Drum Machine tab component.
     * The scroll pane is configured to scroll horizontally by increments matching the 
     * DrumPanel.CELL_W to maintain grid alignment.
     * 
     * * @return A {@link JComponent} containing the initialized drum sequencer interface.
     */
    private JComponent buildDrumTab() {
        JPanel root = new JPanel(new BorderLayout());

        /* Row labels (Kick / Snare / Hat) pinned next to the grid. */
        JPanel labels = new JPanel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        String[] names = {"Kick", "Snare", "Hat"};
        for (String n : names) {
            JLabel l = new JLabel(" " + n + " ");
            l.setOpaque(true);
            l.setBackground(Color.WHITE);
            l.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            l.setPreferredSize(new Dimension(60, DrumPanel.CELL_H));
            l.setMinimumSize(new Dimension(60, DrumPanel.CELL_H));
            l.setMaximumSize(new Dimension(60, DrumPanel.CELL_H));
            labels.add(l);
        }
        labels.add(Box.createVerticalGlue());

        JScrollPane drumScroll = new JScrollPane(drumPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        drumScroll.setPreferredSize(new Dimension(540, 3 * DrumPanel.CELL_H + 25));
        drumScroll.getHorizontalScrollBar().setUnitIncrement(DrumPanel.CELL_W);

        root.add(labels, BorderLayout.WEST);
        root.add(drumScroll, BorderLayout.CENTER);
        return root;
    }

    /**
     * Constructs the Piano Roll tab component.
     * * @return A {@link JComponent} containing the piano roll interface.
     */
    private JComponent buildPianoTab() {
        pianoKeyLabels = new PianoKeysLabelPanel();

        JScrollPane pianoScroll = new JScrollPane(pianoPanel);
        pianoScroll.setRowHeaderView(pianoKeyLabels);
        pianoScroll.setPreferredSize(new Dimension(580, 420));
        pianoScroll.getVerticalScrollBar().setUnitIncrement(PianoRollPanel.CELL_H);
        pianoScroll.getHorizontalScrollBar().setUnitIncrement(PianoRollPanel.CELL_W);
        return pianoScroll;
    }

    /**
     * Synchronizes the playhead position across both the drum and piano panels.
     * This method is thread-safe and can be called from the sequencer's audio thread.
     * * @param currentStep The current tick/step index to highlight in the UI.
     */
    public void updatePlayhead(int currentStep) {
        SwingUtilities.invokeLater(() -> {
            drumPanel.setPlayheadCol(currentStep);
            pianoPanel.setPlayheadCol(currentStep);
        });
    }

    /**
     * Resets the sequencer data and clears all visual notes in the grids.
     */
    private void clearAll() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < sequencer.numSteps; col++) {
                sequencer.trackData[row][col] = false;
            }
        }
        sequencer.clearNotes();
        drumPanel.repaint();
        pianoPanel.repaint();
    }

    /**
     * Configures and returns a {@link JFileChooser} specifically for Sync3 song files.
     * * @return A configured {@link JFileChooser} ready for use in Open or Save files.
     */
    private static JFileChooser makeChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Sync3 song files (*.sync3)", "sync3"));
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    /**
     * Opens a save dialog to export the current session as a {@code .sync3} file.
     */
    private void doSave() {
        JFileChooser chooser = makeChooser();
        if (chooser.showSaveDialog(mainFrame) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".sync3")) {
            f = new File(f.getAbsolutePath() + ".sync3");
        }
        try {
            sequencer.saveTo(f);
            JOptionPane.showMessageDialog(mainFrame, "Saved: " + f.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                "Save failed: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Opens an open dialog to import a {@code .sync3} file and updates the UI state.
     */
    private void doLoad() {
        JFileChooser chooser = makeChooser();
        if (chooser.showOpenDialog(mainFrame) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        try {
            sequencer.loadFrom(f);
            syncControlsFromSequencer();
            drumPanel.rebuild();
            pianoPanel.rebuild();
            pianoKeyLabels.repaint();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                "Load failed: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Updates all sliders and labels to reflect the current state of the {@link Sequencer}.
     * Sets {@link #syncingControls} to true during execution to prevent feedback loops.
     */
    private void syncControlsFromSequencer() {
        syncingControls = true;
        try {
            bpmSlider.setValue(sequencer.bpm);
            bpmLabel.setText("BPM: " + sequencer.bpm);

            stepCombo.setSelectedItem(sequencer.numSteps);

            masterVolSlider.setValue(sequencer.masterVol);
            masterVolLabel.setText("Master: " + sequencer.masterVol);
            drumVolSlider.setValue(sequencer.drumVol);
            drumVolLabel.setText("Drums: " + sequencer.drumVol);
            synthVolSlider.setValue(sequencer.synthVol);
            synthVolLabel.setText("Synth: " + sequencer.synthVol);
        } finally {
            syncingControls = false;
        }
    }

    /**
     * A specialized panel used as a row header to display MIDI note names 
     * and piano key visuals for the piano roll.
     */
    private static class PianoKeysLabelPanel extends JPanel {
        private static final String[] NOTE_NAMES =
            {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
        PianoKeysLabelPanel() {
            setPreferredSize(new Dimension(50,
                PianoRollPanel.NUM_KEYS * PianoRollPanel.CELL_H));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setFont(g2.getFont().deriveFont(10f));
            int w = getWidth();

            for (int row = 0; row < PianoRollPanel.NUM_KEYS; row++) {
                int midi = PianoRollPanel.HIGH_MIDI - row;
                boolean black = isBlackKey(midi);

                g2.setColor(black ? Color.DARK_GRAY : Color.WHITE);
                g2.fillRect(0, row * PianoRollPanel.CELL_H, w, PianoRollPanel.CELL_H);

                g2.setColor(black ? Color.WHITE : Color.BLACK);
                g2.drawString(noteName(midi),
                    4, row * PianoRollPanel.CELL_H + PianoRollPanel.CELL_H - 4);

                g2.setColor(Color.GRAY);
                g2.drawLine(0, row * PianoRollPanel.CELL_H, w, row * PianoRollPanel.CELL_H);
            }
        }
        /**
         * Determines if a MIDI note corresponds to a black key on a piano.
         * * @param midi The MIDI note number.
         * @return {@code true} if the note is a sharp/flat key.
         */
        private static boolean isBlackKey(int midi) {
            int n = ((midi % 12) + 12) % 12;
            return n == 1 || n == 3 || n == 6 || n == 8 || n == 10;
        }
        /**
         * Converts a MIDI note number into a readable string.
         * * @param midi The MIDI note number.
         * @return The note name and octave as a {@link String}.
         */
        private static String noteName(int midi) {
            int octave = midi / 12 - 1;
            return NOTE_NAMES[((midi % 12) + 12) % 12] + octave;
        }
    }
}