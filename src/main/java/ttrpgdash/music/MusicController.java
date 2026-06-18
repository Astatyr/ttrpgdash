package ttrpgdash.music;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import ttrpgdash.util.FileHelper;

/**
 * Manages JavaFX MediaPlayer instances for a scene's music tracks.
 *
 * Each track gets its own MediaPlayer created on play and disposed on stop.
 * Multiple tracks can play simultaneously.
 *
 * NOTE: Audio output device selection is not yet implemented.
 * JavaFX MediaPlayer does not expose a device-selection API.
 * Future implementation would require javax.sound.sampled or a third-party library
 * (e.g. VLCJ) to enumerate and target specific output devices.
 */
public final class MusicController {

    private final Map<String, MediaPlayer> players = new HashMap<>();

    /**
     * Starts playback for the given track, stopping any existing player for the same id.
     */
    public void play(MusicTrack track) {
        stop(track.getId());
        if (!FileHelper.fileExists(track.getFilePath())) {
            System.err.println("[MusicController] File not found: " + track.getFilePath());
            return;
        }
        try {
            Media media = new Media(new File(track.getFilePath()).toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            player.setVolume(track.getVolume());
            player.setCycleCount(track.isLoop() ? MediaPlayer.INDEFINITE : 1);
            player.play();
            players.put(track.getId(), player);
        } catch (Exception e) {
            System.err.println("[MusicController] Failed to play "
                    + track.getFilePath() + ": " + e.getMessage());
        }
    }

    /**
     * Stops and disposes the player for the given track id.
     */
    public void stop(String trackId) {
        MediaPlayer player = players.remove(trackId);
        if (player != null) {
            player.stop();
            player.dispose();
        }
    }

    /**
     * Updates the volume of a currently playing track in real-time.
     */
    public void setVolume(String trackId, double volume) {
        MediaPlayer player = players.get(trackId);
        if (player != null) {
            player.setVolume(volume);
        }
    }

    /**
     * Updates the loop setting of a currently playing track in real-time.
     */
    public void setLoop(String trackId, boolean loop) {
        MediaPlayer player = players.get(trackId);
        if (player != null) {
            player.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
        }
    }

    /**
     * Returns true if the given track is currently playing.
     */
    public boolean isPlaying(String trackId) {
        MediaPlayer player = players.get(trackId);
        return player != null && player.getStatus() == MediaPlayer.Status.PLAYING;
    }

    /**
     * Stops and disposes all active players.
     */
    public void stopAll() {
        players.values().forEach(p -> {
            p.stop();
            p.dispose();
        });
        players.clear();
    }
}
