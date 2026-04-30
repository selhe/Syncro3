import java.io.Serializable;

public class Note implements Serializable {
    private static final long serialVersionUID = 1L;

    public int pitch;      // 0-127 (MIDI standard, 60 = middle C)
    public int startStep;  // When the note starts (0..numSteps-1)
    public int length;     // How many steps it lasts (1+)
    public int velocity;   // 0-127 (how loud / hard the note is hit)

    /** Default-velocity constructor (100 is a comfortable mezzo-forte). */
    public Note(int pitch, int startStep, int length) {
        this(pitch, startStep, length, 100);
    }

    public Note(int pitch, int startStep, int length, int velocity) {
        this.pitch = pitch;
        this.startStep = startStep;
        this.length = Math.max(1, length);
        this.velocity = clampVel(velocity);
    }

    public void setVelocity(int v) { this.velocity = clampVel(v); }

    private static int clampVel(int v) {
        if (v < 0)   return 0;
        if (v > 127) return 127;
        return v;
    }

    @Override
    public String toString() {
        return "Note(pitch=" + pitch + ", start=" + startStep
             + ", len=" + length + ", vel=" + velocity + ")";
    }
}