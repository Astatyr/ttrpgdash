package ttrpgdash.scene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ttrpgdash.util.JsonStateManager;


/**
 * Handles persistence for the scene system.
 *
 * Layout:
 *   data/scenes.json        — master list (scene names, order, active id)
 *   data/scenes/{id}.json   — full SceneState + music tracks per scene
 */
public class SceneStateManager {

    private static final String SCENES_DIR = "data/scenes";
    private static final String MASTER_FILE = "data/scenes.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SceneStateManager() {}

    /**
     * Saves the master scenes.json (scene list + active id).
     */
    public static void saveMaster(SceneManager manager) {
        try {
            Path path = Paths.get(MASTER_FILE);
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("activeSceneId", manager.getActiveSceneId());
            root.add("scenes", GSON.toJsonTree(manager.getScenes()));
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            System.err.println("[SceneStateManager] Failed to save master: " + e.getMessage());
        }
    }

    /**
     * Loads the SceneManager from data/scenes.json.
     * If no file exists, migrates data/state.json into a default first scene.
     */
    public static SceneManager loadMaster() {
        Path path = Paths.get(MASTER_FILE);
        if (!Files.exists(path)) {
            return createDefaultSceneManager();
        }
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            SceneManager manager = new SceneManager();
            if (root.has("activeSceneId") && !root.get("activeSceneId").isJsonNull()) {
                manager.setActiveSceneId(root.get("activeSceneId").getAsString());
            }
            if (root.has("scenes")) {
                for (JsonElement el : root.getAsJsonArray("scenes")) {
                    manager.addScene(GSON.fromJson(el, SceneEntry.class));
                }
            }
            // Guard: if activeSceneId is missing but scenes exist, default to the first scene
            if (manager.getActiveSceneId() == null && !manager.getScenes().isEmpty()) {
                manager.setActiveSceneId(manager.getScenes().get(0).getId());
            }
            return manager;
        } catch (IOException e) {
            System.err.println("[SceneStateManager] Failed to load master: " + e.getMessage());
            return createDefaultSceneManager();
        }
    }

    /**
     * Loads the SceneState for the given scene id and sets its save path.
     */
    public static SceneState loadScene(String sceneId) {
        String path = scenePath(sceneId);
        SceneState state = JsonStateManager.load(path);
        state.setSavePath(path);
        return state;
    }

    /**
     * Creates a fresh SceneState for a new scene and configures its save path.
     */
    public static SceneState createNewScene(String sceneId) {
        SceneState state = new SceneState();
        state.setSavePath(scenePath(sceneId));
        return state;
    }

    /**
     * Returns the file path for a given scene id.
     */
    public static String scenePath(String sceneId) {
        return SCENES_DIR + "/" + sceneId + ".json";
    }

    /**
     * Deletes any {@code data/scenes/{id}.json} file whose ID does not appear
     * in the master scene list. Called once at startup after {@link #loadMaster()}.
     */
    public static void pruneOrphanedSceneFiles(SceneManager manager) {
        Set<String> validIds = manager.getScenes().stream()
                .map(SceneEntry::getId)
                .collect(Collectors.toSet());
        Path dir = Paths.get(SCENES_DIR);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                  .forEach(p -> {
                      String fname = p.getFileName().toString();
                      String id = fname.substring(0, fname.length() - 5);
                      if (!validIds.contains(id)) {
                          deleteOrphanedScene(p);
                      }
                  });
        } catch (IOException e) {
            System.err.println("[SceneStateManager] Failed to scan scenes dir: " + e.getMessage());
        }
    }

    private static void deleteOrphanedScene(Path sceneFile) {
        try {
            Files.deleteIfExists(sceneFile);
            System.out.println("[SceneStateManager] Pruned orphaned scene: " + sceneFile.getFileName());
        } catch (IOException e) {
            System.err.println("[SceneStateManager] Failed to prune " + sceneFile.getFileName()
                    + ": " + e.getMessage());
        }
    }

    private static SceneManager createDefaultSceneManager() {
        String sceneId = newId("scene1");
        SceneEntry entry = new SceneEntry(sceneId, "Scene 1", 0);
        SceneManager manager = new SceneManager();
        manager.addScene(entry);
        manager.setActiveSceneId(sceneId);

        // Migrate existing state.json if present, otherwise start fresh
        SceneState state = Files.exists(Paths.get(JsonStateManager.DEFAULT_STATE_FILE))
                ? JsonStateManager.load(JsonStateManager.DEFAULT_STATE_FILE)
                : new SceneState();
        state.setSavePath(scenePath(sceneId));
        JsonStateManager.save(state, scenePath(sceneId));

        // Remove the legacy single-scene file now that migration is complete
        try {
            Files.deleteIfExists(Paths.get(JsonStateManager.DEFAULT_STATE_FILE));
        } catch (IOException e) {
            System.err.println("[SceneStateManager] Could not delete legacy state.json: " + e.getMessage());
        }

        saveMaster(manager);
        return manager;
    }

    private static String newId(String base) {
        return base + "_" + Long.toHexString(System.currentTimeMillis());
    }
}
