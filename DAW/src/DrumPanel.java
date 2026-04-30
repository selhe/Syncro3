import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

public class DrumPanel extends JPanel {
    public static final int CELL_W = 32;
    public static final int CELL_H = 30;

    private final Sequencer seq;
    private int playheadCol = -1;

    private static final Color[] ROW_COLORS = {
        new Color(220,  90,  90),  
        new Color( 90, 130, 220),  
        new Color( 90, 200, 130)   
    };

    public DrumPanel(Sequencer seq) {
        this.seq = seq;
        setBackground(Color.WHITE);
        rebuild();

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int col = e.getX() / CELL_W;
                int row = e.getY() / CELL_H;
                if (col < 0 || col >= seq.numSteps || row < 0 || row >= 3) return;
                seq.trackData[row][col] = !seq.trackData[row][col];
                repaint();
            }
        };
        addMouseListener(ma);
    }

    /** Call after Sequencer.numSteps changes (or after Load). */
    public void rebuild() {
        setPreferredSize(new Dimension(seq.numSteps * CELL_W, 3 * CELL_H));
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
        int n = seq.numSteps;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < n; col++) {
                int x = col * CELL_W;
                int y = row * CELL_H;

                // Background
                Color bg;
                if (col == playheadCol)        bg = new Color(255, 250, 200);
                else if ((col / 4) % 2 == 0)   bg = Color.WHITE;
                else                           bg = new Color(245, 245, 248);
                g.setColor(bg);
                g.fillRect(x, y, CELL_W, CELL_H);

                // Filled?
                if (seq.trackData[row][col]) {
                    g.setColor(ROW_COLORS[row]);
                    g.fillRect(x + 2, y + 2, CELL_W - 4, CELL_H - 4);
                }

                // Cell border + thicker every 4 steps.
                g.setColor(((col + 1) % 4 == 0) ? Color.DARK_GRAY : Color.LIGHT_GRAY);
                g.drawRect(x, y, CELL_W, CELL_H);
            }
        }
    }
}