import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

/**
 * {@code PianoRollPanel} provides a graphical interface for sequencing MIDI notes.
 */
public class PianoRollPanel extends JPanel {
    public static final int CELL_W = 32;
    public static final int CELL_H = 18;

    public static final int HIGH_MIDI = 83; // B5
    public static final int LOW_MIDI  = 48; // C3
    public static final int NUM_KEYS  = HIGH_MIDI - LOW_MIDI + 1;

    private final Sequencer seq;
    private int playheadCol = -1;

    // Default velocity for newly-created notes (set by toolbar slider).
    private int defaultVelocity = 100;

    // Active drag state
    private Note draggingNote = null;
    private int draggingStartCol = -1;

    /**
     * Constructs a {@code PianoRollPanel} for sequencing.
     * * @param seq The {@link Sequencer} instance to be edited.
     */
    public PianoRollPanel(Sequencer seq) {
        this.seq = seq;
        setBackground(Color.WHITE);
        rebuild();

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int col = e.getX() / CELL_W;
                int row = e.getY() / CELL_H;
                if (col < 0 || col >= seq.numSteps || row < 0 || row >= NUM_KEYS) return;
                int pitch = HIGH_MIDI - row;

                Note existing = seq.findNoteCovering(pitch, col);
                if (existing != null) {
                    seq.removeNoteRef(existing);
                    draggingNote = null;
                } else {
                    draggingNote = seq.addNoteAndReturn(pitch, col, 1, defaultVelocity);
                    draggingStartCol = col;
                    seq.previewNote(pitch, defaultVelocity);  // hear what you just placed
                }
                repaint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (draggingNote == null) return;
                int col = e.getX() / CELL_W;
                col = Math.max(draggingStartCol, Math.min(col, seq.numSteps - 1));
                int newLen = col - draggingStartCol + 1;
                if (newLen != draggingNote.length) {
                    draggingNote.length = newLen;
                    repaint();
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                draggingNote = null;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    public void setDefaultVelocity(int v) {
        defaultVelocity = Math.max(1, Math.min(127, v));
    }

    /** Call after Sequencer.numSteps changes (or after Load). */
    public void rebuild() {
        setPreferredSize(new Dimension(seq.numSteps * CELL_W, NUM_KEYS * CELL_H));
        revalidate();
        repaint();
    }

    public void setPlayheadCol(int col) {
        if (col == playheadCol) return;
        playheadCol = col;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        int n = seq.numSteps;
        int w = n * CELL_W;
        int h = NUM_KEYS * CELL_H;

        // 1) Row backgrounds (white-key vs black-key shading).
        for (int row = 0; row < NUM_KEYS; row++) {
            int midi = HIGH_MIDI - row;
            g2.setColor(isBlackKey(midi) ? new Color(225, 225, 230) : Color.WHITE);
            g2.fillRect(0, row * CELL_H, w, CELL_H);
        }

        // 2) Beat-stripe shading every 4 steps.
        for (int col = 0; col < n; col += 8) {
            g2.setColor(new Color(0, 0, 0, 8));
            int stripeW = Math.min(4, n - col) * CELL_W;
            g2.fillRect(col * CELL_W, 0, stripeW, h);
        }

        // 3) Playhead column highlight.
        if (playheadCol >= 0 && playheadCol < n) {
            g2.setColor(new Color(255, 230, 100, 110));
            g2.fillRect(playheadCol * CELL_W, 0, CELL_W, h);
        }

        // 4) Vertical grid lines (heavier every 4 steps).
        for (int col = 0; col <= n; col++) {
            g2.setColor((col % 4 == 0) ? Color.DARK_GRAY : new Color(220, 220, 220));
            g2.drawLine(col * CELL_W, 0, col * CELL_W, h);
        }
        // 5) Horizontal grid lines (heavier on every C).
        for (int row = 0; row <= NUM_KEYS; row++) {
            int midi = HIGH_MIDI - row;
            boolean isC = (((midi % 12) + 12) % 12) == 0;
            g2.setColor(isC ? Color.DARK_GRAY : new Color(220, 220, 220));
            g2.drawLine(0, row * CELL_H, w, row * CELL_H);
        }

        // 6) Notes.
        synchronized (seq.synthNotes) {
            for (Note note : seq.synthNotes) {
                int row = HIGH_MIDI - note.pitch;
                if (row < 0 || row >= NUM_KEYS) continue;
                if (note.startStep >= n) continue;

                int x = note.startStep * CELL_W;
                int y = row * CELL_H;
                int wn = Math.min(note.length, n - note.startStep) * CELL_W;
                int hn = CELL_H;

                // Color: saturation/brightness encodes velocity.
                float velNorm = note.velocity / 127f;
                Color noteColor = new Color(
                    (int)(40 + (1 - velNorm) * 40),     // R
                    (int)(140 + velNorm * 60),          // G
                    (int)(180 + velNorm * 75)           // B
                );
                g2.setColor(noteColor);
                g2.fillRect(x + 1, y + 1, wn - 2, hn - 2);

                // Outline + small bar at the start to mark the attack.
                g2.setColor(new Color(20, 60, 90));
                g2.drawRect(x, y, wn, hn);
                g2.fillRect(x, y, 3, hn);
            }
        }
    }

    private static boolean isBlackKey(int midi) {
        int n = ((midi % 12) + 12) % 12;
        return n == 1 || n == 3 || n == 6 || n == 8 || n == 10;
    }
}