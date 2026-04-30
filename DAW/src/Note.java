import java.io.Serializable;
/**
 * The {@code Note} class represents a single MIDI-style musical note within the sequencer.
 */
public class Note implements Serializable {
    /** * Identifier for versioning of the serialized data for loading process. */
    private static final long serialVersionUID = 1L;

    public int pitch; 
    /** The starting step index within the sequencer grid. */     
    public int startStep; 
    public int length;     
    public int velocity;   

    /** * Constructs a {@code Note} with a default velocity of 100.
     * * @param pitch     The MIDI note number.
     * @param startStep The step index where the note begins.
     * @param length    The number of steps the note spans.
     */
    public Note(int pitch, int startStep, int length) {
        this(pitch, startStep, length, 100);
    }

    /** * Constructs a {@code Note} with specified properties.
     * * @param pitch     The MIDI note number.
     * @param startStep The step index where the note begins.
     * @param length    The number of steps the note spans.
     * @param velocity  The intensity of the note.
     */
    public Note(int pitch, int startStep, int length, int velocity) {
        this.pitch = pitch;
        this.startStep = startStep;
        this.length = Math.max(1, length);
        this.velocity = clampVel(velocity);
    }

    /**
     * Updates the velocity of the note.
     * * @param v The new velocity value.
     */
    public void setVelocity(int v) { this.velocity = clampVel(v); }

    /**
     * Utility method to constrain velocity values between 0 and 127.
     * * @param v The input velocity.
     * @return An integer between 0 and 127.
     */
    private static int clampVel(int v) {
        if (v < 0)   return 0;
        if (v > 127) return 127;
        return v;
    }

    /**
     * Returns a string representation of the note.
     * * @return A formatted string containing pitch, start step, length, and velocity.
     */
    @Override
    public String toString() {
        return "Note(pitch=" + pitch + ", start=" + startStep
             + ", len=" + length + ", vel=" + velocity + ")";
    }
}