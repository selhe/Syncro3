import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code Sequencer} class acts as the engine for the DAW, managing playback timing,
 * note data storage, and file I/O. 
 */
public class Sequencer implements Runnable {
    public volatile int bpm = 120;
    private volatile boolean playing = false;
    public volatile int numSteps = 16;        

    public volatile int synthVol  = 80;
    public volatile int drumVol   = 80;
    public volatile int masterVol = 80;

    private int currentStep = 0;

    public volatile boolean[][] trackData = new boolean[3][16];
    public final List<Note> synthNotes = new ArrayList<>();

    private final SoundEngine soundEngine;
    private DAWGui gui;

    /**
     * Initializes a new {@code Sequencer} with a default {@link SoundEngine}.
     */
    public Sequencer() {
        this.soundEngine = new SoundEngine();
    }

    /** @param gui The GUI instance to notify during playback. */
    public void setGui(DAWGui gui) { this.gui = gui; }

    /** @return The underlying {@link SoundEngine}. */
    public SoundEngine getSoundEngine() { return soundEngine; }

    /**
     * Sets the BPM within a safe range of 40 to 300.
     * @param newBpm Requested tempo.
     */
    public void setBPM(int newBpm) {
        if (newBpm >= 40 && newBpm <= 300) this.bpm = newBpm;
    }

    /**
     * Immediately stops playback, resets the playhead, and silences all active audio.
     */
    public void stop() {
        playing = false;
        currentStep = 0;
        soundEngine.stopAll();         
        if (gui != null) gui.updatePlayhead(-1);
    }

    /** * Plays a single pitch immediately without adding it to the sequence.
     * Used for  notes when a user clicks the piano roll.
     * * @param pitch MIDI note number.
     * @param velocity Velocity 0-127.
     */
    public void previewNote(int pitch, int velocity) {
        double mv = masterVol / 100.0;
        double sGain = (synthVol / 100.0) * mv;
        soundEngine.triggerSynth(pitch, 200, velocity, sGain);
    }

    /**
     * Starts the playback thread if it is not already running.
     */
    public void start() {
        if (!playing) {
            playing = true;
            new Thread(this).start();
        }
    }

    /** * Resizes the sequence length. 
     * @param n The new number of steps.
     */
    public synchronized void setNumSteps(int n) {
        if (n <= 0) return;

        boolean[][] newData = new boolean[3][n];
        for (int row = 0; row < 3; row++) {
            int copyLen = Math.min(trackData[row].length, n);
            System.arraycopy(trackData[row], 0, newData[row], 0, copyLen);
        }
        trackData = newData;
        numSteps = n;

        // Drop synth notes that no longer fit.
        synchronized (synthNotes) {
            synthNotes.removeIf(note -> note.startStep >= n);
            for (Note note : synthNotes) {
                if (note.startStep + note.length > n) {
                    note.length = Math.max(1, n - note.startStep);
                }
            }
        }
        if (currentStep >= n) currentStep = 0;
    }

    /** * Adds a note to the piano roll and returns the reference.
     * Prevents duplicate notes at the exact same pitch and start time.
     * * @return The newly created {@link Note} or the existing duplicate.
     */
    public Note addNoteAndReturn(int pitch, int startStep, int length, int velocity) {
        synchronized (synthNotes) {
            for (Note n : synthNotes) {
                if (n.pitch == pitch && n.startStep == startStep) return n;
            }
            Note n = new Note(pitch, startStep, length, velocity);
            synthNotes.add(n);
            return n;
        }
    }

    /** * Overload of {@link #addNoteAndReturn(int, int, int, int)} with default velocity of 100. 
     */
    public Note addNoteAndReturn(int pitch, int startStep, int length) {
        return addNoteAndReturn(pitch, startStep, length, 100);
    }

    /** * Legacy method for adding notes without returning a reference.
     */
    public void addNote(int pitch, int startStep, int length) {
        addNoteAndReturn(pitch, startStep, length, 100);
    }

    /**
     * Removes a note matching the specified coordinates.
     */
    public void removeNote(int pitch, int startStep) {
        synchronized (synthNotes) {
            synthNotes.removeIf(n -> n.pitch == pitch && n.startStep == startStep);
        }
    }

    /**
     * Removes a specific note object from the sequence.
     */
    public void removeNoteRef(Note target) {
        synchronized (synthNotes) {
            synthNotes.remove(target);
        }
    }

    /** Checks if a note exists at the exact pitch and start step. */
    public boolean hasNote(int pitch, int startStep) {
        synchronized (synthNotes) {
            for (Note n : synthNotes) {
                if (n.pitch == pitch && n.startStep == startStep) return true;
            }
            return false;
        }
    }

    /** * Searches for any note that occupies a specific pitch and time step.
     * This considers the note's duration (length), not just its start position.
     * * @return The {@link Note} covering the cell, or {@code null} if empty.
     */
    public Note findNoteCovering(int pitch, int step) {
        synchronized (synthNotes) {
            for (Note n : synthNotes) {
                if (n.pitch == pitch
                    && n.startStep <= step
                    && step < n.startStep + n.length) {
                    return n;
                }
            }
            return null;
        }
    }

    /** Clears all notes from the piano roll. */
    public void clearNotes() {
        synchronized (synthNotes) {
            synthNotes.clear();
        }
    }

    /**
     * Serializes the current project state
     * to a file using {@link ObjectOutputStream}.
     * * @param file The destination file.
     * @throws IOException If writing fails.
     */
    public void saveTo(File file) throws IOException {
        SongData data = new SongData();
        data.bpm       = bpm;
        data.numSteps  = numSteps;
        
        boolean[][] td = new boolean[3][numSteps];
        for (int r = 0; r < 3; r++) {
            System.arraycopy(trackData[r], 0, td[r], 0, Math.min(trackData[r].length, numSteps));
        }
        data.trackData = td;
        synchronized (synthNotes) {
            data.synthNotes = new ArrayList<>(synthNotes);
        }
        data.synthVol  = synthVol;
        data.drumVol   = drumVol;
        data.masterVol = masterVol;

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(data);
        }
    }

    /**
     * Loads a project state from a file. Playback is momentarily stopped during 
     * the loading process to ensure thread safety while swapping data structures.
     * * @param file The source file.
     * @throws IOException If reading fails.
     * @throws ClassNotFoundException If the file contains an invalid object type.
     */
    public void loadFrom(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            SongData data = (SongData) ois.readObject();
            boolean wasPlaying = playing;
            stop();

            this.bpm       = data.bpm;
            this.numSteps  = data.numSteps;
            this.trackData = data.trackData;
            this.synthVol  = data.synthVol;
            this.drumVol   = data.drumVol;
            this.masterVol = data.masterVol;
            synchronized (synthNotes) {
                synthNotes.clear();
                synthNotes.addAll(data.synthNotes);
            }

            if (wasPlaying) start();
        }
    }

    /**
     * The core playback loop. Calculates the timing for a 16th-note grid based 
     * on the current BPM and invokes {@link #playStep(int)} repeatedly.
     */
    @Override
    public void run() {
        while (playing) {
            long sleepTime = (60000 / bpm) / 4; // 16th-note interval

            playStep(currentStep);
            currentStep = (currentStep + 1) % Math.max(1, numSteps);

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Executes the audio triggers for a specific time step.
     * @param step The step index to play.
     */
    private void playStep(int step) {
        if (gui != null) gui.updatePlayhead(step);
        boolean[][] td = trackData;
        if (step >= td[0].length) return;

        double mv    = masterVol / 100.0;
        double dGain = (drumVol  / 100.0) * mv;
        double sGain = (synthVol / 100.0) * mv;

        if (td[0][step]) soundEngine.triggerKick (dGain);
        if (td[1][step]) soundEngine.triggerSnare(dGain);
        if (td[2][step]) soundEngine.triggerHat  (dGain);

        List<Note> starting;
        synchronized (synthNotes) {
            starting = new ArrayList<>();
            for (Note n : synthNotes) {
                if (n.startStep == step) starting.add(n);
            }
        }
        int polyN = starting.size();
        double polyScale = (polyN > 0) ? 1.0 / Math.sqrt(polyN) : 1.0;

        int stepDurMs = (60000 / bpm) / 4;
        for (Note n : starting) {
            soundEngine.triggerSynth(n.pitch,
                                     n.length * stepDurMs,
                                     n.velocity,
                                     sGain * polyScale);
        }
    }
}