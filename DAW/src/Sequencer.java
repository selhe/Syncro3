import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class Sequencer implements Runnable {
    public volatile int bpm = 120;
    private volatile boolean playing = false;
    public volatile int numSteps = 16;           

    public volatile int synthVol  = 80;
    public volatile int drumVol   = 80;
    public volatile int masterVol = 80;

    private int currentStep = 0;

    // Drum grid 
    public volatile boolean[][] trackData = new boolean[3][16];

    // Piano-roll synth track 
    public final List<Note> synthNotes = new ArrayList<>();

    private final SoundEngine soundEngine;
    private DAWGui gui;

    public Sequencer() {
        this.soundEngine = new SoundEngine();
    }

    public void setGui(DAWGui gui) { this.gui = gui; }

    public void setBPM(int newBpm) {
        if (newBpm >= 40 && newBpm <= 300) this.bpm = newBpm;
    }

    public void stop() {
        playing = false;
        currentStep = 0;
        if (gui != null) gui.updatePlayhead(-1);
    }

    public void start() {
        if (!playing) {
            playing = true;
            new Thread(this).start();
        }
    }

    // loop length

    /** Resize the song. Existing drum data and notes are preserved where possible. */
    public synchronized void setNumSteps(int n) {
        if (n <= 0) return;

        boolean[][] newData = new boolean[3][n];
        for (int row = 0; row < 3; row++) {
            int copyLen = Math.min(trackData[row].length, n);
            System.arraycopy(trackData[row], 0, newData[row], 0, copyLen);
        }
        trackData = newData;
        numSteps = n;

        // Drop / clamp synth notes that no longer fit.
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

    //piano roll note management

    /** Adds a note (rejecting exact (pitch, startStep) duplicates) and returns it. */
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

    /** Convenience overload */
    public Note addNoteAndReturn(int pitch, int startStep, int length) {
        return addNoteAndReturn(pitch, startStep, length, 100);
    }

    /** Old API kept for compatibility */
    public void addNote(int pitch, int startStep, int length) {
        addNoteAndReturn(pitch, startStep, length, 100);
    }

    public void removeNote(int pitch, int startStep) {
        synchronized (synthNotes) {
            synthNotes.removeIf(n -> n.pitch == pitch && n.startStep == startStep);
        }
    }

    public void removeNoteRef(Note target) {
        synchronized (synthNotes) {
            synthNotes.remove(target);
        }
    }

    public boolean hasNote(int pitch, int startStep) {
        synchronized (synthNotes) {
            for (Note n : synthNotes) {
                if (n.pitch == pitch && n.startStep == startStep) return true;
            }
            return false;
        }
    }

    /** Find any note that COVERS this (pitch, step), i.e. its length spans the cell. */
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

    public void clearNotes() {
        synchronized (synthNotes) {
            synthNotes.clear();
        }
    }

    // --- SAVE / LOAD ---

    public void saveTo(File file) throws IOException {
        SongData data = new SongData();
        data.bpm       = bpm;
        data.numSteps  = numSteps;
        // Deep copy of trackData so the snapshot can't be mutated under us.
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

    public void loadFrom(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            SongData data = (SongData) ois.readObject();
            // Apply atomically-ish: stop playback first.
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

    // --- PLAYBACK LOOP ---

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

    private void playStep(int step) {
        if (gui != null) gui.updatePlayhead(step);

        // Snapshot the (possibly-resized) drum array reference so we don't
        // race with setNumSteps mid-step.
        boolean[][] td = trackData;
        if (step >= td[0].length) return;

        boolean[] drums = new boolean[3];
        for (int row = 0; row < 3; row++) drums[row] = td[row][step];

        // Notes that START on this step trigger this tick.
        List<Note> starting;
        synchronized (synthNotes) {
            starting = new ArrayList<>();
            for (Note n : synthNotes) {
                if (n.startStep == step) starting.add(n);
            }
        }

        int stepDurMs = (60000 / bpm) / 4;
        int[] pitches = new int[starting.size()];
        int[] vels    = new int[starting.size()];
        int[] durs    = new int[starting.size()];
        for (int i = 0; i < starting.size(); i++) {
            Note n = starting.get(i);
            pitches[i] = n.pitch;
            vels[i]    = n.velocity;
            durs[i]    = n.length * stepDurMs;
        }

        double mv = masterVol / 100.0;
        double dGain = (drumVol  / 100.0) * mv;
        double sGain = (synthVol / 100.0) * mv;

        soundEngine.playStep(drums, pitches, vels, durs, dGain, sGain);
    }
}