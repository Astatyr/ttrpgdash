package ttrpgdash.log;

import ttrpgdash.map.MapController;
import ttrpgdash.scene.SceneController;

/**
 * Re-applies a {@link LogEntry} in the forward direction (redo).
 * Mirrors {@link UndoHandler}: where undo reverses an action, redo re-executes it.
 */
public final class RedoHandler {

    private final MapController mapController;
    private final SceneController sceneController;

    /**
     * Creates the handler bound to the given controllers.
     */
    public RedoHandler(MapController mapController, SceneController sceneController) {
        this.mapController = mapController;
        this.sceneController = sceneController;
    }

    /**
     * Re-applies the original action described by the log entry.
     */
    public void apply(LogEntry entry) {
        switch (entry.getEvent()) {

        case MOVE_PLAYER:
        case MOVE_CHARACTER:
            mapController.moveEntityTo(entry.get("Name"),
                    Double.parseDouble(entry.get("X")),
                    Double.parseDouble(entry.get("Y")));
            break;

        case ADD_STATUS_EFFECT:
        case REMOVE_STATUS_EFFECT:
            // ADD redo = add; REMOVE redo = remove — toggleStatus handles both
            sceneController.getActiveState()
                    .findByName(entry.get("Name"))
                    .ifPresent(e -> mapController.toggleStatus(e, entry.get("Status")));
            break;

        case PLACE_PLAYER:
        case PLACE_CHARACTER:
            mapController.placeEntityOnMap(entry.get("Name"),
                    Double.parseDouble(entry.get("X")),
                    Double.parseDouble(entry.get("Y")));
            break;

        case REMOVE_FROM_MAP:
            mapController.removeEntityFromMap(entry.get("Name"));
            break;

        case ADD_PLAYER:
            sceneController.addEntityToSession(entry.get("Name"),
                    Double.parseDouble(entry.get("Size")), true);
            break;

        case ADD_CHARACTER:
            sceneController.addEntityToSession(entry.get("Name"),
                    Double.parseDouble(entry.get("Size")), false);
            break;

        case REMOVE_PLAYER:
        case REMOVE_CHARACTER:
            sceneController.removeEntityFromSession(entry.get("Name"));
            break;

        case SWITCH_SCENE:
            sceneController.switchToScene(entry.get("Scene ID"));
            break;

        case ADD_SCENE:
            sceneController.addSceneWithId(entry.get("Scene ID"), entry.get("Name"));
            break;

        case DELETE_SCENE:
            sceneController.deleteScene(entry.get("Scene ID"));
            break;

        case RENAME_SCENE:
            sceneController.renameScene(entry.get("Scene ID"), entry.get("New Name"));
            break;

        case MOUNT:
            mapController.setMount(entry.get("Rider"), entry.get("Mount"));
            break;

        case DISMOUNT:
            mapController.clearMount(entry.get("Rider"));
            break;

        default:
            System.out.println("[RedoHandler] No redo defined for: " + entry.getEvent());
            break;
        }
    }
}
