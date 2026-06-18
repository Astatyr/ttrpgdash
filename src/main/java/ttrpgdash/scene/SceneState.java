package ttrpgdash.scene;

import java.util.ArrayList;
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
public class SceneState {

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
    private transient String savePath = JsonStateManager.DEFAULT_STATE_FILE;

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

    public List<PlayerEntity> getPlayers() {
        return players;
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

    public List<CharacterEntity> getCharacters() {
        return characters;
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

    public List<MusicTrack> getMusicTracks() {
        return musicTracks;
    }

    /**
     * Configures the file path this SceneState saves to.
     * Set by SceneStateManager after loading a scene so saves go to the right file.
     */
    public void setSavePath(String path) {
        this.savePath = path;
    }

    /** Saves the current state to its configured path. Called automatically by all mutators. */
    public void save() {
        JsonStateManager.save(this, savePath);
    }
}
