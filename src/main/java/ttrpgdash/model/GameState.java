package ttrpgdash.model;

import ttrpgdash.util.JsonStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for the entire session.
 *
 * All changes to entities, map config, or positions go through GameState.
 * After every mutation, call save() — or use the mutating helpers below
 * which call it automatically.
 *
 * GameState is loaded once at startup by JsonStateManager and kept in memory.
 * The UI reads from and writes to this object; it never touches state.json directly.
 */
public class GameState {

    // ── Map configuration ─────────────────────────────────────────────────────

    /** Absolute path to the currently loaded map image. Null = no map loaded. */
    private String mapImagePath;

    /**
     * The real-world width of the map image in feet.
     * Used to convert token sizes (in feet) to pixel radii on the canvas.
     * Default: 100 ft.
     */
    private double mapWidthInFeet;

    // ── Entity lists ──────────────────────────────────────────────────────────

    /** All player characters in this session. */
    private List<PlayerEntity> players;

    /** All NPCs, enemies, and creatures in this session. */
    private List<CharacterEntity> characters;

    // ── Constructor (used by Gson on deserialisation too) ─────────────────────

    public GameState() {
        this.mapImagePath = null;
        this.mapWidthInFeet = 100.0;
        this.players = new ArrayList<>();
        this.characters = new ArrayList<>();
    }

    // ── Map config helpers ────────────────────────────────────────────────────

    public String getMapImagePath()                    { return mapImagePath; }
    public void   setMapImagePath(String path)         { this.mapImagePath = path; save(); }

    public double getMapWidthInFeet()                  { return mapWidthInFeet; }
    public void   setMapWidthInFeet(double feet)       { this.mapWidthInFeet = feet; save(); }

    // ── Player helpers ────────────────────────────────────────────────────────

    public List<PlayerEntity> getPlayers()             { return players; }

    public void addPlayer(PlayerEntity player) {
        players.add(player);
        save();
    }

    public void removePlayer(String id) {
        players.removeIf(p -> p.getId().equals(id));
        save();
    }

    // ── Character (NPC) helpers ───────────────────────────────────────────────

    public List<CharacterEntity> getCharacters()       { return characters; }

    public void addCharacter(CharacterEntity character) {
        characters.add(character);
        save();
    }

    public void removeCharacter(String id) {
        characters.removeIf(c -> c.getId().equals(id));
        save();
    }

    // ── Unified entity lookup ─────────────────────────────────────────────────

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
     * Notifies GameState that an entity's data changed (position, status, etc.)
     * so it can persist to disk. Call this after mutating any entity field directly.
     */
    public void entityChanged() {
        save();
    }

    // ── Clear all ─────────────────────────────────────────────────────────────

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

    // ── Persistence ───────────────────────────────────────────────────────────

    /** Saves the current state to data/state.json. Called automatically by all mutators. */
    public void save() {
        JsonStateManager.save(this);
    }
}
