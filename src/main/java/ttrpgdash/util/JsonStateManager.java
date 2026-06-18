package ttrpgdash.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import ttrpgdash.entity.CharacterEntity;
import ttrpgdash.entity.Entity;
import ttrpgdash.entity.PlayerEntity;
import ttrpgdash.music.MusicTrack;
import ttrpgdash.scene.SceneState;

/**
 * Handles reading and writing of data/state.json.
 *
 * Uses Gson with a custom deserialiser to handle the Entity polymorphism
 * (PlayerEntity vs CharacterEntity share the same list structure in JSON
 * but need to be instantiated as the correct subclass on load).
 *
 * Usage:
 *   SceneState state = JsonStateManager.load();   // on startup
 *   JsonStateManager.save(state);                // called automatically by SceneState mutators
 */
public class JsonStateManager {

    /** Default save path used when no scene-specific path is configured. */
    public static final String DEFAULT_STATE_FILE = "data/state.json";

    private static final String STATE_FILE = DEFAULT_STATE_FILE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Serialises the given SceneState to the default data/state.json path.
     */
    public static void save(SceneState state) {
        save(state, STATE_FILE);
    }

    /**
     * Serialises the given SceneState to the specified file path.
     * Creates parent directories if they do not exist.
     */
    public static void save(SceneState state, String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("mapImagePath", state.getMapImagePath());
            root.addProperty("mapWidthInFeet", state.getMapWidthInFeet());

            JsonArray playersArr = new JsonArray();
            for (PlayerEntity p : state.getPlayers()) {
                JsonObject obj = GSON.toJsonTree(p).getAsJsonObject();
                obj.addProperty("entityType", "player");
                playersArr.add(obj);
            }
            root.add("players", playersArr);

            JsonArray charsArr = new JsonArray();
            for (CharacterEntity c : state.getCharacters()) {
                JsonObject obj = GSON.toJsonTree(c).getAsJsonObject();
                obj.addProperty("entityType", "character");
                charsArr.add(obj);
            }
            root.add("characters", charsArr);

            root.add("musicTracks", GSON.toJsonTree(state.getMusicTracks()));

            Files.writeString(path, GSON.toJson(root));

        } catch (IOException e) {
            System.err.println("[JsonStateManager] Failed to save state: " + e.getMessage());
        }
    }

    /**
     * Deserialises SceneState from data/state.json, or returns a fresh state if missing.
     */
    public static SceneState load() {
        return load(STATE_FILE);
    }

    /**
     * Deserialises SceneState from the given file path, or returns a fresh state if missing.
     */
    public static SceneState load(String filePath) {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            System.out.println("[JsonStateManager] No file found at " + filePath + " — starting fresh.");
            return new SceneState();
        }

        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            SceneState state = new SceneState();

            setFieldDirectly(state, root);

            if (root.has("players")) {
                for (JsonElement el : root.getAsJsonArray("players")) {
                    PlayerEntity p = GSON.fromJson(el, PlayerEntity.class);
                    state.getPlayers().add(p);
                }
            }

            if (root.has("characters")) {
                for (JsonElement el : root.getAsJsonArray("characters")) {
                    CharacterEntity c = GSON.fromJson(el, CharacterEntity.class);
                    state.getCharacters().add(c);
                }
            }

            if (root.has("musicTracks")) {
                for (JsonElement el : root.getAsJsonArray("musicTracks")) {
                    state.getMusicTracks().add(GSON.fromJson(el, MusicTrack.class));
                }
            }

            for (Entity e : state.getAllEntities()) {
                e.setAvatarPath(FileHelper.normalizeToRelative(e.getAvatarPath()));
                e.setDetailsPath(FileHelper.normalizeToRelative(e.getDetailsPath()));
            }
            save(state, filePath);

            System.out.println("[JsonStateManager] Loaded state from " + filePath + ": "
                    + state.getPlayers().size() + " players, "
                    + state.getCharacters().size() + " characters.");
            return state;

        } catch (IOException | JsonParseException e) {
            System.err.println("[JsonStateManager] Failed to load state: " + e.getMessage());
            return new SceneState();
        }
    }

    /**
     * Sets mapImagePath and mapWidthInFeet directly on a fresh SceneState
     * during load — avoids triggering save() before the state is fully built.
     */
    private static void setFieldDirectly(SceneState state, JsonObject root) {
        try {
            var pathField = SceneState.class.getDeclaredField("mapImagePath");
            pathField.setAccessible(true);
            if (root.has("mapImagePath") && !root.get("mapImagePath").isJsonNull()) {
                pathField.set(state,
                        FileHelper.normalizeToRelative(root.get("mapImagePath").getAsString()));
            }

            var feetField = SceneState.class.getDeclaredField("mapWidthInFeet");
            feetField.setAccessible(true);
            if (root.has("mapWidthInFeet")) {
                feetField.set(state, root.get("mapWidthInFeet").getAsDouble());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.err.println("[JsonStateManager] Reflection error during load: " + e.getMessage());
        }
    }
}
