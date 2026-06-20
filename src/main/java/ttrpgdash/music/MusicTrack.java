package ttrpgdash.music;

/**
 * Represents a single audio track belonging to a scene.
 * Volume and loop are persisted; playback state is runtime-only.
 */
public class MusicTrack {

    private final String id;
    private final String name;
    private final String filePath;
    private double volume;
    private boolean loop;

    /**
     * Creates a new music track with default volume 1.0 and loop disabled.
     */
    public MusicTrack(String id, String name, String filePath) {
        this.id = id;
        this.name = name;
        this.filePath = filePath;
        this.volume = 1.0;
        this.loop = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFilePath() {
        return filePath;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }
}
