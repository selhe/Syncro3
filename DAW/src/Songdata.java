import java.io.Serializable;
import java.util.List;

/**
 * Serializable snapshot of an entire song.
 * Written/read by Sequencer.saveTo / loadFrom via ObjectOutputStream.
 */
public class SongData implements Serializable {
    private static final long serialVersionUID = 1L;

    public int bpm;
    public int numSteps;
    public boolean[][] trackData;   
    public List<Note> synthNotes;  

    public int synthVol;          
    public int drumVol;           
    public int masterVol;         
}