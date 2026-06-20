package ttrpgdash.log;

import ttrpgdash.map.MapController;
import ttrpgdash.scene.SceneController;

/**
 * Applies the reverse of a {@link LogEntry}, delegating to the appropriate controller.
 * Every case that exists in the log schema is handled here.
 */
public final class UndoHandler {

    private final MapController mapController;
    private final SceneController sceneController;

    /**
     * Creates the handler bound to the given controllers.
     */
    public UndoHandler(MapController mapController, SceneController sceneController) {
        this.mapController = mapController;
        this.sceneController = sceneController;
    }

    /**
     * Applies the logical reverse of the given log entry.
     */
    public void undo(LogEntry entry) {
        switch (entry.getEvent()) {

        case MOVE_PLAYER:
        case MOVE_CHARACTER:
            mapController.moveEntityTo(entry.get("Name"),
                    Double.parseDouble(entry.get("From X")),
                    Double.parseDouble(entry.get("From Y")));
            break;

        case ADD_STATUS_EFFECT:
        case REMOVE_STATUS_EFFECT:
            // ADD undo = remove; REMOVE undo = add — toggleStatus handles both
            sceneController.getActiveState()
                    .findByName(entry.get("Name"))
                    .ifPresent(e -> mapController.toggleStatus(e, entry.get("Status")));
            break;

        case PLACE_PLAYER:
        case PLACE_CHARACTER:
            mapController.removeEntityFromMap(entry.get("Name"));
            break;

        case REMOVE_FROM_MAP:
            mapController.placeEntityOnMap(entry.get("Name"),
                    Double.parseDouble(entry.get("X")),
                    Double.parseDouble(entry.get("Y")));
            mapController.restoreStatusEffects(entry.get("Name"),
                    entry.get("Status Effects"));
            break;

        case ADD_PLAYER:
        case ADD_CHARACTER:
            sceneController.removeEntityFromSession(entry.get("Name"));
            break;

        case REMOVE_PLAYER:
            sceneController.addEntityToSession(entry.get("Name"),
                    Double.parseDouble(entry.get("Size")), true);
            break;

        case REMOVE_CHARACTER:
            sceneController.addEntityToSession(entry.get("Name"),
                    Double.parseDouble(entry.get("Size")), false);
            break;

        case SWITCH_SCENE:
            sceneController.switchToScene(entry.get("Previous Scene ID"));
            break;

        case ADD_SCENE:
            sceneController.deleteScene(entry.get("Scene ID"));
            break;

        case DELETE_SCENE:
            sceneController.restoreScene(entry.get("Scene ID"), entry.get("Name"));
            break;

        case RENAME_SCENE:
            sceneController.renameScene(entry.get("Scene ID"), entry.get("Old Name"));
            break;

        case MOUNT:
            mapController.clearMount(entry.get("Rider"));
            break;

        case DISMOUNT:
            mapController.setMount(entry.get("Rider"), entry.get("Mount"));
            break;

        default:
            System.out.println("[UndoHandler] No undo defined for: " + entry.getEvent());
            break;
        }
    }
}
