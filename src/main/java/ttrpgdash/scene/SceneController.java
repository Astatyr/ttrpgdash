package ttrpgdash.scene;

import java.util.function.Consumer;

import ttrpgdash.music.MusicController;

/**
 * Owns the scene lifecycle: loading, switching, persisting, and modifying scenes.
 *
 * MainWindow creates this controller and registers callbacks to react to changes.
 * The controller never calls back into UI components directly — it only fires events.
 *
 * Event contract:
 *   onSceneChanged    — fired when the active scene switches (new SceneState provided)
 *   onSceneListChanged — fired when scene metadata changes (add, delete, rename, reorder)
 */
public class SceneController {

    private final SceneManager sceneManager;
    private final MusicController musicController;
    private SceneState activeState;

    private Consumer<SceneState> onSceneChanged;
    private Runnable onSceneListChanged;
    private Runnable onStateChanged;

    /**
     * Creates the controller, loading the active scene from disk.
     */
    public SceneController(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.musicController = new MusicController();
        this.activeState = SceneStateManager.loadScene(sceneManager.getActiveSceneId());
    }

    public SceneState getActiveState() {
        return activeState;
    }

    public SceneManager getSceneManager() {
        return sceneManager;
    }

    public MusicController getMusicController() {
        return musicController;
    }

    public String getActiveSceneName() {
        return sceneManager.getActiveEntry().map(SceneEntry::getName).orElse("Unknown");
    }

    public void setOnSceneChanged(Consumer<SceneState> handler) {
        this.onSceneChanged = handler;
    }

    public void setOnSceneListChanged(Runnable handler) {
        this.onSceneListChanged = handler;
    }

    /** Fired when the active scene's content changes in-place (e.g. clearAll). */
    public void setOnStateChanged(Runnable handler) {
        this.onStateChanged = handler;
    }

    /**
     * Wires the given ScenePanel's action callbacks to this controller.
     * Also exposes {@code onMusicChanged} so MusicPanel saves through the active state.
     */
    public void attachScenePanel(ScenePanel panel) {
        panel.setOnSceneSwitch(this::switchToScene);
        panel.setOnSceneAdd(this::addScene);
        panel.setOnSceneMove((id, delta) -> {
            sceneManager.moveScene(id, delta);
            SceneStateManager.saveMaster(sceneManager);
            fireListChanged();
        });
        panel.setOnSceneRename((id, name) -> {
            sceneManager.getById(id).ifPresent(e -> e.setName(name));
            SceneStateManager.saveMaster(sceneManager);
            fireListChanged();
        });
        panel.setOnSceneDelete(this::deleteScene);
        panel.setOnMusicChanged(() -> activeState.entityChanged());
    }

    /**
     * Switches to the given scene id, saving the current scene first.
     */
    public void switchToScene(String sceneId) {
        if (sceneId.equals(sceneManager.getActiveSceneId())) {
            return;
        }
        activeState.save();
        musicController.stopAll();
        activeState = SceneStateManager.loadScene(sceneId);
        sceneManager.setActiveSceneId(sceneId);
        SceneStateManager.saveMaster(sceneManager);
        fireSceneChanged();
        fireListChanged();
    }

    /**
     * Creates a new blank scene and switches to it.
     */
    public void addScene() {
        String id = "scene_" + Long.toHexString(System.currentTimeMillis());
        int order = sceneManager.getScenes().size();
        sceneManager.addScene(new SceneEntry(id, "New Scene", order));
        SceneState newState = SceneStateManager.createNewScene(id);
        newState.save();
        SceneStateManager.saveMaster(sceneManager);
        switchToScene(id);
    }

    /**
     * Deletes the given scene. If it was active, switches to the first remaining scene.
     * No-op if only one scene exists.
     */
    /**
     * Deletes the given scene. If it was active, switches to the first remaining scene.
     * No-op if only one scene exists.
     */
    public void deleteScene(String id) {
        if (sceneManager.getScenes().size() <= 1) {
            return;
        }
        boolean wasActive = id.equals(sceneManager.getActiveSceneId());
        sceneManager.removeScene(id);
        SceneStateManager.saveMaster(sceneManager);
        if (wasActive) {
            // Do NOT call setActiveSceneId before switchToScene — switchToScene
            // checks sceneManager.getActiveSceneId() to guard against no-op switches.
            // Setting it here would make that check pass and abort the switch.
            switchToScene(sceneManager.getScenes().get(0).getId());
        } else {
            fireListChanged();
        }
    }

    /**
     * Clears all entities and resets the map for the active scene.
     */
    public void clearAll() {
        activeState.clearAll();
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    private void fireSceneChanged() {
        if (onSceneChanged != null) {
            onSceneChanged.accept(activeState);
        }
    }

    private void fireListChanged() {
        if (onSceneListChanged != null) {
            onSceneListChanged.run();
        }
    }
}
