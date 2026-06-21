package ttrpgdash.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import ttrpgdash.entity.CharacterEntity;
import ttrpgdash.entity.PlayerEntity;
import ttrpgdash.music.MusicTrack;
import ttrpgdash.scene.ScenePersistenceException;
import ttrpgdash.scene.SceneState;

/**
 * Handles reading and writing of scene state JSON files.
 *
 * Uses Gson with a custom deserialiser to handle the Entity polymorphism
 * (PlayerEntity vs CharacterEntity share the same list structure in JSON
 * but need to be instantiated as the correct subclass on load).
 *
 * Usage:
 *   SceneState state = JsonStateManager.load(path);   // on startup / scene switch
 *   JsonStateManager.save(state, path);               // called automatically by SceneState mutators
 */
public final class JsonStateManager {

    /** Default save path used when no scene-specific path is configured. */
    public static final String DEFAULT_STATE_FILE = "data/state.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

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
     * Deserialises SceneState from the given path, or returns a fresh state if the file is
     * missing. If the file exists but is corrupted, logs and returns a fresh state.
     * Use {@link #loadOrThrow(String)} when callers need to distinguish corruption from absence.
     */
    public static SceneState load(String filePath) {
        try {
            return loadOrThrow(filePath);
        } catch (ScenePersistenceException e) {
            System.err.println("[JsonStateManager] " + e.getMessage() + " — starting fresh.");
            return new SceneState();
        }
    }

    /**
     * Deserialises SceneState from the given path, or returns a fresh state if the file is
     * missing. Throws {@link ScenePersistenceException} if the file exists but cannot be parsed,
     * allowing callers to distinguish a corrupted file from a legitimately absent one.
     */
    public static SceneState loadOrThrow(String filePath) throws ScenePersistenceException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            return new SceneState();
        }

        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            String mapPath = null;
            if (root.has("mapImagePath") && !root.get("mapImagePath").isJsonNull()) {
                mapPath = FileHelper.normalizeToRelative(root.get("mapImagePath").getAsString());
            }
            double mapWidthInFeet = root.has("mapWidthInFeet")
                    ? root.get("mapWidthInFeet").getAsDouble() : 100.0;

            List<PlayerEntity> players = new ArrayList<>();
            if (root.has("players")) {
                for (JsonElement el : root.getAsJsonArray("players")) {
                    PlayerEntity p = GSON.fromJson(el, PlayerEntity.class);
                    p.setAvatarPath(FileHelper.normalizeToRelative(p.getAvatarPath()));
                    p.setDetailsPath(FileHelper.normalizeToRelative(p.getDetailsPath()));
                    players.add(p);
                }
            }

            List<CharacterEntity> characters = new ArrayList<>();
            if (root.has("characters")) {
                for (JsonElement el : root.getAsJsonArray("characters")) {
                    CharacterEntity c = GSON.fromJson(el, CharacterEntity.class);
                    c.setAvatarPath(FileHelper.normalizeToRelative(c.getAvatarPath()));
                    c.setDetailsPath(FileHelper.normalizeToRelative(c.getDetailsPath()));
                    characters.add(c);
                }
            }

            List<MusicTrack> musicTracks = new ArrayList<>();
            if (root.has("musicTracks")) {
                for (JsonElement el : root.getAsJsonArray("musicTracks")) {
                    musicTracks.add(GSON.fromJson(el, MusicTrack.class));
                }
            }

            SceneState state = SceneState.fromLoad(mapPath, mapWidthInFeet,
                    players, characters, musicTracks);

            System.out.println("[JsonStateManager] Loaded state from " + filePath + ": "
                    + players.size() + " players, "
                    + characters.size() + " characters.");
            return state;

        } catch (JsonParseException e) {
            throw new ScenePersistenceException(
                    "Scene file is corrupted at '" + filePath + "': " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ScenePersistenceException(
                    "Failed to read scene file '" + filePath + "': " + e.getMessage(), e);
        }
    }
}
