package ttrpgdash.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import ttrpgdash.entity.CharacterEntity;
import ttrpgdash.entity.Entity;
import ttrpgdash.entity.PlayerEntity;
import ttrpgdash.music.MusicTrack;
import ttrpgdash.util.JsonStateManager;

/**
 * Single source of truth for the entire session.
 *
 * All changes to entities, map config, or positions go through sceneState.
 * After every mutation, call save() — or use the mutating helpers below
 * which call it automatically.
 *
 * SceneState is loaded once at startup by JsonStateManager and kept in memory.
 * The UI reads from and writes to this object; it never touches state.json directly.
 */
public final class SceneState {

    /** Absolute path to the currently loaded map image. Null = no map loaded. */
    private String mapImagePath;

    /**
     * The real-world width of the map image in feet.
     * Used to convert token sizes (in feet) to pixel radii on the canvas.
     * Default: 100 ft.
     */
    private double mapWidthInFeet;

    /** All player characters in this session. */
    private final List<PlayerEntity> players;

    /** All NPCs, enemies, and creatures in this session. */
    private final List<CharacterEntity> characters;

    /** Music tracks for this scene. */
    private final List<MusicTrack> musicTracks;

    /**
     * Runtime save path — set by SceneStateManager so each scene writes to its own file.
     * Transient so Gson does not serialise it.
     */
    private transient String savePath;

    /** When true, all save() calls are no-ops. Set for replay states. */
    private transient boolean saveEnabled = true;

    /**
     * Creates a fresh SceneState with default values.
     */
    public SceneState() {
        this.mapImagePath = null;
        this.mapWidthInFeet = 100.0;
        this.players = new ArrayList<>();
        this.characters = new ArrayList<>();
        this.musicTracks = new ArrayList<>();
    }

    /**
     * Factory method for JsonStateManager: builds a fully populated SceneState
     * from already-normalised load data without triggering any save() calls.
     */
    public static SceneState fromLoad(String mapPath, double mapWidthInFeet,
                                      List<PlayerEntity> players,
                                      List<CharacterEntity> characters,
                                      List<MusicTrack> musicTracks) {
        SceneState s = new SceneState();
        s.mapImagePath = mapPath;
        s.mapWidthInFeet = mapWidthInFeet;
        s.players.addAll(players);
        s.characters.addAll(characters);
        s.musicTracks.addAll(musicTracks);
        return s;
    }

    public String getMapImagePath() {
        return mapImagePath;
    }

    public void setMapImagePath(String path) {
        this.mapImagePath = path;
        save();
    }

    public double getMapWidthInFeet() {
        return mapWidthInFeet;
    }

    public void setMapWidthInFeet(double feet) {
        this.mapWidthInFeet = feet;
        save();
    }

    /** Returns an unmodifiable view of the players list. Use addPlayer/removePlayer to mutate. */
    public List<PlayerEntity> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    /**
     * Adds a player to the session and persists the change.
     */
    public void addPlayer(PlayerEntity player) {
        players.add(player);
        save();
    }

    /**
     * Removes a player by ID and persists the change.
     */
    public void removePlayer(String id) {
        players.removeIf(p -> p.getId().equals(id));
        save();
    }

    /** Returns an unmodifiable view of the characters list. Use addCharacter/removeCharacter to mutate. */
    public List<CharacterEntity> getCharacters() {
        return Collections.unmodifiableList(characters);
    }

    /**
     * Adds a character (NPC) to the session and persists the change.
     */
    public void addCharacter(CharacterEntity character) {
        characters.add(character);
        save();
    }

    /**
     * Removes a character by ID and persists the change.
     */
    public void removeCharacter(String id) {
        characters.removeIf(c -> c.getId().equals(id));
        save();
    }

    /**
     * Returns all entities (players + characters) as a flat list.
     * Useful for collision detection, token rendering, and clearing the map.
     */
    public List<Entity> getAllEntities() {
        List<Entity> all = new ArrayList<>();
        all.addAll(players);
        all.addAll(characters);
        return all;
    }

    /**
     * Finds any entity by display name regardless of type.
     * Returns the first match — names are not guaranteed unique.
     */
    public Optional<Entity> findByName(String name) {
        return getAllEntities().stream()
                .filter(e -> e.getName().equals(name))
                .findFirst();
    }

    /**
     * Finds any entity by ID regardless of type.
     * Returns an Optional — always check isPresent() before using.
     */
    public Optional<Entity> findById(String id) {
        return getAllEntities().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst();
    }

    /**
     * Notifies SceneState that an entity's data changed (position, status, etc.)
     * so it can persist to disk. Call this after mutating any entity field directly.
     */
    public void entityChanged() {
        save();
    }

    /**
     * Clears the map image and removes all tokens from the map,
     * but keeps the player and character list intact.
     */
    public void clearMapOnly() {
        mapImagePath = null;
        getAllEntities().forEach(e -> {
            e.setOnMap(false);
            e.setXFraction(0.0);
            e.setYFraction(0.0);
            e.setMountedOnId(null);
        });
        save();
    }

    /**
     * Removes all entities and resets the map. Used by Options → Clear All.
     */
    public void clearAll() {
        players.clear();
        characters.clear();
        mapImagePath = null;
        mapWidthInFeet = 100.0;
        save();
    }

    /**
     * Removes all entities from the map (resets onMap + position)
     * without deleting them from the sidebar.
     */
    public void clearMapPositions() {
        getAllEntities().forEach(e -> {
            e.setOnMap(false);
            e.setXFraction(0.0);
            e.setYFraction(0.0);
            e.setMountedOnId(null);
        });
        save();
    }

    /** Returns an unmodifiable view of the music tracks list. Use addMusicTrack/removeMusicTrack to mutate. */
    public List<MusicTrack> getMusicTracks() {
        return Collections.unmodifiableList(musicTracks);
    }

    /**
     * Adds a music track to the scene and persists the change.
     */
    public void addMusicTrack(MusicTrack track) {
        musicTracks.add(track);
        save();
    }

    /**
     * Removes a music track from the scene and persists the change.
     */
    public void removeMusicTrack(MusicTrack track) {
        musicTracks.remove(track);
        save();
    }

    /**
     * Configures the file path this SceneState saves to.
     * Set by SceneStateManager after loading a scene so saves go to the right file.
     */
    public void setSavePath(String path) {
        this.savePath = path;
    }

    /**
     * Disables all future save() calls on this instance.
     * Used by replay states that must never write to disk.
     */
    public void disableSave() {
        this.saveEnabled = false;
    }

    /** Saves the current state to its configured path. Called automatically by all mutators. */
    public void save() {
        if (!saveEnabled) {
            return;
        }
        if (savePath == null) {
            System.err.println("[SceneState] save() called but savePath is not set — skipping.");
            return;
        }
        JsonStateManager.save(this, savePath);
    }
}
