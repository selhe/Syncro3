import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class RunApp {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        Sequencer engine = new Sequencer();

        /* Default Drum beat */
        engine.trackData[0][0]  = true;  
        engine.trackData[0][4]  = true;
        engine.trackData[0][8]  = true;
        engine.trackData[0][12] = true;
        engine.trackData[2][2]  = true;   
        engine.trackData[2][6]  = true;
        engine.trackData[2][10] = true;
        engine.trackData[2][14] = true;

        /* Default chords */
        engine.addNoteAndReturn(60, 0, 4, 110); 
        engine.addNoteAndReturn(64, 0, 4, 110); 
        engine.addNoteAndReturn(67, 0, 4, 110); 
        
        engine.addNoteAndReturn(65, 8, 4, 70);  
        engine.addNoteAndReturn(69, 8, 4, 70);  
        engine.addNoteAndReturn(72, 8, 4, 70);   

        SwingUtilities.invokeLater(() -> new DAWGui(engine));
    }
}