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

import ttrpgdash.model.CharacterEntity;
import ttrpgdash.model.GameState;
import ttrpgdash.model.PlayerEntity;

/**
 * Handles reading and writing of data/state.json.
 *
 * Uses Gson with a custom deserialiser to handle the Entity polymorphism
 * (PlayerEntity vs CharacterEntity share the same list structure in JSON
 * but need to be instantiated as the correct subclass on load).
 *
 * Usage:
 *   GameState state = JsonStateManager.load();   // on startup
 *   JsonStateManager.save(state);                // called automatically by GameState mutators
 */
public class JsonStateManager {

    private static final String STATE_FILE = "data/state.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // ── Save ──────────────────────────────────────────────────────────────────

    public static void save(GameState state) {
        try {
            Path path = Paths.get(STATE_FILE);
            Files.createDirectories(path.getParent());

            // Wrap into a raw structure so Gson serialises subclass fields correctly
            JsonObject root = new JsonObject();
            root.addProperty("mapImagePath", state.getMapImagePath());
            root.addProperty("mapWidthInFeet", state.getMapWidthInFeet());

            // Serialise players with type tag
            JsonArray playersArr = new JsonArray();
            for (PlayerEntity p : state.getPlayers()) {
                JsonObject obj = GSON.toJsonTree(p).getAsJsonObject();
                obj.addProperty("entityType", "player");
                playersArr.add(obj);
            }
            root.add("players", playersArr);

            // Serialise characters with type tag
            JsonArray charsArr = new JsonArray();
            for (CharacterEntity c : state.getCharacters()) {
                JsonObject obj = GSON.toJsonTree(c).getAsJsonObject();
                obj.addProperty("entityType", "character");
                charsArr.add(obj);
            }
            root.add("characters", charsArr);

            Files.writeString(path, GSON.toJson(root));

        } catch (IOException e) {
            System.err.println("[JsonStateManager] Failed to save state: " + e.getMessage());
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public static GameState load() {
        Path path = Paths.get(STATE_FILE);

        if (!Files.exists(path)) {
            System.out.println("[JsonStateManager] No state.json found — starting fresh.");
            return new GameState();
        }

        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            GameState state = new GameState();

            // Map config
            if (!root.get("mapImagePath").isJsonNull()) {
                state.getPlayers(); // init lists before setting path directly
                // Set without triggering save during load
                setFieldDirectly(state, root);
            } else {
                setFieldDirectly(state, root);
            }

            // Players
            if (root.has("players")) {
                for (JsonElement el : root.getAsJsonArray("players")) {
                    PlayerEntity p = GSON.fromJson(el, PlayerEntity.class);
                    state.getPlayers().add(p);
                }
            }

            // Characters
            if (root.has("characters")) {
                for (JsonElement el : root.getAsJsonArray("characters")) {
                    CharacterEntity c = GSON.fromJson(el, CharacterEntity.class);
                    state.getCharacters().add(c);
                }
            }

            System.out.println("[JsonStateManager] Loaded state: "
                    + state.getPlayers().size() + " players, "
                    + state.getCharacters().size() + " characters.");
            return state;

        } catch (IOException | JsonParseException e) {
            System.err.println("[JsonStateManager] Failed to load state: " + e.getMessage());
            return new GameState();
        }
    }

    /**
     * Sets mapImagePath and mapWidthInFeet directly on a fresh GameState
     * during load — avoids triggering save() before the state is fully built.
     */
    private static void setFieldDirectly(GameState state, JsonObject root) {
        try {
            var pathField = GameState.class.getDeclaredField("mapImagePath");
            pathField.setAccessible(true);
            if (!root.get("mapImagePath").isJsonNull()) {
                pathField.set(state, root.get("mapImagePath").getAsString());
            }

            var feetField = GameState.class.getDeclaredField("mapWidthInFeet");
            feetField.setAccessible(true);
            if (root.has("mapWidthInFeet")) {
                feetField.set(state, root.get("mapWidthInFeet").getAsDouble());
            }
        } catch (Exception e) {
            System.err.println("[JsonStateManager] Reflection error during load: " + e.getMessage());
        }
    }
}
