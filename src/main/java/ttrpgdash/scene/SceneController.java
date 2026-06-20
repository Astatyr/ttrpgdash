package ttrpgdash.scene;

import java.util.function.Consumer;

import ttrpgdash.entity.CharacterEntity;
import ttrpgdash.entity.PlayerEntity;
import ttrpgdash.log.LogController;
import ttrpgdash.music.MusicController;
import ttrpgdash.util.FileHelper;

/**
 * Owns the scene lifecycle: loading, switching, persisting, and modifying scenes.
 *
 * MainWindow creates this controller and registers callbacks to react to changes.
 * The controller never calls back into UI components directly — it only fires events.
 *
 * Event contract:
 *   onSceneChanged    — fired when the active scene switches (new SceneState provided)
 *   onSceneListChanged — fired when scene metadata changes (add, delete, rename, reorder)
 *   onStateChanged    — fired for full in-place state resets (e.g. clearAll)
 *   onEntitiesChanged — fired when only the entity list changes (lighter than onStateChanged)
 */
public final class SceneController {

    private final SceneManager sceneManager;
    private final MusicController musicController;
    private SceneState activeState;

    private Consumer<SceneState> onSceneChanged;
    private Runnable onSceneListChanged;
    private Runnable onStateChanged;
    private Runnable onEntitiesChanged;
    private LogController logController;

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

    /** Fired for full in-place resets (e.g. clearAll — map path may change). */
    public void setOnStateChanged(Runnable handler) {
        this.onStateChanged = handler;
    }

    /** Fired when only the entity list changes; avoids the zoom-reset caused by reloadFromState. */
    public void setOnEntitiesChanged(Runnable handler) {
        this.onEntitiesChanged = handler;
    }

    public void setLogController(LogController lc) {
        this.logController = lc;
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
        if (logController != null) {
            logController.onSceneSwitched(sceneId);
        }
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
        if (logController != null) {
            logController.logAddScene(id, "New Scene");
        }
        switchToScene(id);
    }

    /**
     * Deletes the given scene. If it was active, switches to the first remaining scene.
     * No-op if only one scene exists.
     */
    public void deleteScene(String id) {
        if (sceneManager.getScenes().size() <= 1) {
            return;
        }
        boolean wasActive = id.equals(sceneManager.getActiveSceneId());
        String sceneName = sceneManager.getById(id).map(SceneEntry::getName).orElse("Unknown");
        if (logController != null) {
            logController.logDeleteScene(id, sceneName);
        }
        sceneManager.removeScene(id);
        SceneStateManager.saveMaster(sceneManager);
        // TODO: delete data/scenes/{id}.json from disk (see TODO file — Scene cleanup)
        if (wasActive) {
            switchToScene(sceneManager.getScenes().get(0).getId());
        } else {
            fireListChanged();
        }
    }

    /**
     * Moves a scene up or down in the ordered list and persists the master.
     */
    public void moveScene(String id, int delta) {
        sceneManager.moveScene(id, delta);
        SceneStateManager.saveMaster(sceneManager);
        fireListChanged();
    }

    /**
     * Renames a scene. Used by undo/redo and by the UI (which handles logging separately).
     */
    public void renameScene(String sceneId, String newName) {
        sceneManager.getById(sceneId).ifPresent(e -> e.setName(newName));
        SceneStateManager.saveMaster(sceneManager);
        fireListChanged();
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

    /**
     * Re-adds an entity to the active session. Avatar and details paths are
     * derived from the entity name via {@link FileHelper#CHARACTERS_DIR}.
     * Used by undo of Remove Player/Character and redo of Add Player/Character.
     */
    public void addEntityToSession(String name, double size, boolean isPlayer) {
        String id = FileHelper.generateId(name);
        String avatarPath = FileHelper.CHARACTERS_DIR + "/" + name + "/Avatar.png";
        String detailsPath = FileHelper.CHARACTERS_DIR + "/" + name + "/Details.png";
        if (isPlayer) {
            PlayerEntity p = new PlayerEntity(id, name, size);
            if (FileHelper.fileExists(avatarPath)) {
                p.setAvatarPath(avatarPath);
            }
            if (FileHelper.fileExists(detailsPath)) {
                p.setDetailsPath(detailsPath);
            }
            activeState.addPlayer(p);
        } else {
            CharacterEntity c = new CharacterEntity(id, name, size);
            if (FileHelper.fileExists(avatarPath)) {
                c.setAvatarPath(avatarPath);
            }
            if (FileHelper.fileExists(detailsPath)) {
                c.setDetailsPath(detailsPath);
            }
            activeState.addCharacter(c);
        }
        if (onEntitiesChanged != null) {
            onEntitiesChanged.run();
        }
    }

    /**
     * Removes the entity with the given name from the session entirely (sidebar + map).
     * Used by undo of Add Player/Character and redo of Remove.
     */
    public void removeEntityFromSession(String name) {
        activeState.findByName(name).ifPresent(e -> {
            e.setOnMap(false);
            e.setMountedOnId(null);
            if ("player".equals(e.getEntityType())) {
                activeState.removePlayer(e.getId());
            } else {
                activeState.removeCharacter(e.getId());
            }
        });
        if (onEntitiesChanged != null) {
            onEntitiesChanged.run();
        }
    }

    /**
     * Re-adds a deleted scene entry to the manager so it appears in the list again.
     * The scene JSON remains on disk (undo is a pointer; files are not deleted).
     * Used by undo of Delete Scene.
     */
    public void restoreScene(String sceneId, String sceneName) {
        if (sceneManager.getById(sceneId).isPresent()) {
            return;
        }
        int order = sceneManager.getScenes().size();
        sceneManager.addScene(new SceneEntry(sceneId, sceneName, order));
        SceneStateManager.saveMaster(sceneManager);
        fireListChanged();
    }

    /**
     * Re-adds a scene that was previously undone. Loads existing JSON if on disk.
     * Used by redo of Add Scene.
     */
    public void addSceneWithId(String sceneId, String sceneName) {
        if (sceneManager.getById(sceneId).isPresent()) {
            return;
        }
        int order = sceneManager.getScenes().size();
        sceneManager.addScene(new SceneEntry(sceneId, sceneName, order));
        SceneStateManager.saveMaster(sceneManager);
        switchToScene(sceneId);
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
