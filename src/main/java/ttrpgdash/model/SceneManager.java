package ttrpgdash.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runtime manager for the ordered list of scenes and which one is active.
 * Persisted as data/scenes.json (metadata only; full scene data is per-file).
 */
public class SceneManager {

    private List<SceneEntry> scenes;
    private String activeSceneId;

    /**
     * Creates an empty scene manager with no active scene.
     */
    public SceneManager() {
        this.scenes = new ArrayList<>();
        this.activeSceneId = null;
    }

    public List<SceneEntry> getScenes() {
        return scenes;
    }

    public String getActiveSceneId() {
        return activeSceneId;
    }

    public void setActiveSceneId(String id) {
        this.activeSceneId = id;
    }

    public Optional<SceneEntry> getActiveEntry() {
        return scenes.stream().filter(s -> s.getId().equals(activeSceneId)).findFirst();
    }

    public Optional<SceneEntry> getById(String id) {
        return scenes.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    /**
     * Appends a new scene entry to the list.
     */
    public void addScene(SceneEntry entry) {
        scenes.add(entry);
    }

    /**
     * Removes the scene with the given id. No-op if not found.
     */
    public void removeScene(String id) {
        scenes.removeIf(s -> s.getId().equals(id));
    }

    /**
     * Shifts a scene up (delta = -1) or down (delta = +1) in the ordered list
     * and updates all order fields accordingly.
     */
    public void moveScene(String id, int delta) {
        int idx = -1;
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i).getId().equals(id)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return;
        }
        int newIdx = Math.max(0, Math.min(scenes.size() - 1, idx + delta));
        if (newIdx == idx) {
            return;
        }
        SceneEntry entry = scenes.remove(idx);
        scenes.add(newIdx, entry);
        for (int i = 0; i < scenes.size(); i++) {
            scenes.get(i).setOrder(i);
        }
    }
}
