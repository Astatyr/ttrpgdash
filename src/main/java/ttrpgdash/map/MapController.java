package ttrpgdash.map;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.geometry.Point2D;
import javafx.stage.Stage;
import ttrpgdash.entity.Entity;
import ttrpgdash.entity.SidebarPanel;
import ttrpgdash.log.LogController;
import ttrpgdash.scene.SceneState;
import ttrpgdash.util.FileHelper;

/**
 * Owns all map interaction logic: file operations, token management, and coordinating
 * canvas refreshes. MapCanvas is treated as a dumb rendering surface.
 *
 * Dialogs (file chooser, width input) are handled by the UI layer; this controller
 * exposes single-purpose mutating methods that the UI calls after obtaining input.
 *
 * Event contract:
 *   onStateChanged   — fires after any mutation that requires a canvas/player refresh
 *   onTokenRightClick — fires when the user right-clicks a token; UI builds the menu
 *   onStatusMessage  — fires with human-readable status text after operations
 */
public final class MapController {

    private final Stage stage;
    private SceneState sceneState;
    private MapCanvas mapCanvas;
    private SidebarPanel sidebarPanel;

    private Runnable onStateChanged;
    private BiConsumer<Token, Point2D> onTokenRightClick;
    private Consumer<String> onStatusMessage;
    private LogController logController;

    /**
     * Creates the controller bound to the given stage (used for file chooser dialogs).
     */
    public MapController(Stage stage) {
        this.stage = stage;
    }

    /**
     * Attaches a new scene's state and its UI components. Called on initial load
     * and on every scene switch.
     */
    public void attachScene(SceneState state, MapCanvas canvas, SidebarPanel sidebar) {
        this.sceneState = state;
        this.mapCanvas = canvas;
        this.sidebarPanel = sidebar;

        canvas.setOnTokenRightClick((token, point) -> {
            if (onTokenRightClick != null) {
                onTokenRightClick.accept(token, point);
            }
        });
        canvas.setOnTokensChanged(() -> fireStateChanged());
        canvas.setOnTokenPlaced(token -> {
            if (logController != null) {
                logController.logPlaceToken(token.getEntity(),
                        token.getEntity().getXFraction(),
                        token.getEntity().getYFraction());
            }
        });
        canvas.setOnTokenMoved((token, coords) -> {
            if (logController != null) {
                logController.logMoveToken(token.getEntity(),
                        coords[0], coords[1], coords[2], coords[3]);
            }
        });

        sidebar.setOnEntitiesChanged(() -> {
            mapCanvas.syncTokens();
            fireStateChanged();
            fireStatus("Entities updated.");
        });
        sidebar.setOnPlaceEntity(entity -> {
            mapCanvas.setPendingEntity(entity);
            fireStatus("Click on the map to place: " + entity.getName());
        });
    }

    public MapCanvas getMapCanvas() {
        return mapCanvas;
    }

    public SidebarPanel getSidebarPanel() {
        return sidebarPanel;
    }

    public void setOnStateChanged(Runnable handler) {
        this.onStateChanged = handler;
    }

    public void setOnTokenRightClick(BiConsumer<Token, Point2D> handler) {
        this.onTokenRightClick = handler;
    }

    public void setOnStatusMessage(Consumer<String> handler) {
        this.onStatusMessage = handler;
    }

    public void setLogController(LogController lc) {
        this.logController = lc;
    }

    /**
     * Loads a map image from the given relative path and resets the view.
     */
    public void setMap(String relativePath, String displayName) {
        sceneState.setMapImagePath(relativePath);
        mapCanvas.loadMap(relativePath);
        if (logController != null) {
            logController.logAddMap(relativePath, sceneState.getMapWidthInFeet());
        }
        fireStateChanged();
        fireStatus("Map loaded: " + displayName);
    }

    /**
     * Updates the map's real-world width and recalculates token radii.
     */
    public void setMapWidth(double feet) {
        double oldFeet = sceneState.getMapWidthInFeet();
        sceneState.setMapWidthInFeet(feet);
        mapCanvas.onMapWidthChanged();
        if (logController != null) {
            logController.logChangeMapSize(oldFeet, feet);
        }
        fireStateChanged();
        fireStatus("Map width set to " + feet + " ft.");
    }

    /**
     * Fits the current map image to the canvas and resets zoom/pan.
     */
    public void fitMap() {
        mapCanvas.reloadFromState();
        fireStateChanged();
    }

    /**
     * Clears the map image and all token positions, keeping entities in the sidebar.
     */
    public void clearMap() {
        sceneState.clearMapOnly();
        mapCanvas.reloadFromState();
        fireStateChanged();
        fireStatus("Map cleared.");
    }

    /**
     * Resets all token positions to off-map without removing entities.
     */
    public void clearTokenPositions() {
        sceneState.clearMapPositions();
        mapCanvas.syncTokens();
        fireStateChanged();
        fireStatus("Token positions cleared.");
    }

    /**
     * Toggles the given status effect on the entity and triggers a repaint.
     */
    public void toggleStatus(Entity entity, String status) {
        boolean adding = !entity.getStatusEffects().contains(status);
        if (adding) {
            entity.addStatusEffect(status);
        } else {
            entity.removeStatusEffect(status);
        }
        sceneState.entityChanged();
        mapCanvas.repaint();
        if (logController != null) {
            if (adding) {
                logController.logAddStatusEffect(entity.getName(), status);
            } else {
                logController.logRemoveStatusEffect(entity.getName(), status);
            }
        }
        fireStateChanged();
    }

    /**
     * Removes the given token from the map and disarms the sidebar card.
     */
    public void removeToken(Token token) {
        if (logController != null) {
            logController.logRemoveFromMap(token.getEntity());
        }
        mapCanvas.removeToken(token);
        sidebarPanel.disarmCard();
        fireStateChanged();
    }

    /**
     * Arms the canvas to place the given entity on the next left-click.
     */
    public void setPendingEntity(Entity entity) {
        mapCanvas.setPendingEntity(entity);
    }

    /**
     * Moves the first entity matching the given name to the specified map-fraction
     * coordinates. Used by the undo handler to reverse a token move.
     */
    public void moveEntityTo(String entityName, double xFraction, double yFraction) {
        sceneState.findByName(entityName).ifPresent(e -> {
            e.setXFraction(xFraction);
            e.setYFraction(yFraction);
            sceneState.entityChanged();
            mapCanvas.syncTokens();
            fireStateChanged();
        });
    }

    /**
     * Places the entity with the given name onto the map at the stored fraction coordinates.
     * Used by undo of Remove From Map.
     */
    public void placeEntityOnMap(String name, double xFraction, double yFraction) {
        sceneState.findByName(name).ifPresent(e -> {
            e.setOnMap(true);
            e.setXFraction(xFraction);
            e.setYFraction(yFraction);
            sceneState.entityChanged();
            mapCanvas.syncTokens();
            fireStateChanged();
        });
    }

    /**
     * Removes the entity with the given name from the map without removing it from the session.
     * Used by undo of Place and redo of Remove From Map.
     */
    public void removeEntityFromMap(String name) {
        sceneState.findByName(name).ifPresent(e -> {
            e.setOnMap(false);
            e.setMountedOnId(null);
            sceneState.entityChanged();
            mapCanvas.syncTokens();
            fireStateChanged();
        });
    }

    /**
     * Sets the rider's mount to the entity matching mountName.
     * Used by undo of Dismount and redo of Mount.
     */
    public void setMount(String riderName, String mountName) {
        sceneState.findByName(mountName).ifPresent(mount ->
                sceneState.findByName(riderName).ifPresent(rider -> {
                    rider.setMountedOnId(mount.getId());
                    sceneState.entityChanged();
                    mapCanvas.syncTokens();
                    fireStateChanged();
                }));
    }

    /**
     * Clears the rider's mount.
     * Used by undo of Mount and redo of Dismount.
     */
    public void clearMount(String riderName) {
        sceneState.findByName(riderName).ifPresent(rider -> {
            rider.setMountedOnId(null);
            sceneState.entityChanged();
            mapCanvas.syncTokens();
            fireStateChanged();
        });
    }

    /**
     * Parses a comma-separated status-effects string from the log and restores it onto
     * the entity, replacing whatever effects it currently has.
     */
    public void restoreStatusEffects(String entityName, String statusEffectsStr) {
        sceneState.findByName(entityName).ifPresent(e -> {
            e.getStatusEffects().clear();
            if (statusEffectsStr != null && !"none".equals(statusEffectsStr)
                    && !statusEffectsStr.isBlank()) {
                for (String s : statusEffectsStr.split(",\\s*")) {
                    if (!s.isBlank()) {
                        e.addStatusEffect(s.trim());
                    }
                }
            }
            sceneState.entityChanged();
            mapCanvas.repaint();
            fireStateChanged();
        });
    }

    /**
     * Sets whether entity nameboxes are rendered.
     */
    public void setNamesVisible(boolean visible) {
        mapCanvas.setNamesVisible(visible);
    }

    /**
     * Sets whether status-effect icons are rendered.
     */
    public void setStatusVisible(boolean visible) {
        mapCanvas.setStatusVisible(visible);
    }

    /**
     * Opens a file chooser for a map image and loads the selected file.
     * No-op if the user cancels.
     */
    public void browseAndLoadMap() {
        File file = FileHelper.browseForMap(stage);
        if (file == null) {
            return;
        }
        setMap(FileHelper.toRelativePath(file), file.getName());
    }

    private void fireStateChanged() {
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    private void fireStatus(String message) {
        if (onStatusMessage != null) {
            onStatusMessage.accept(message);
        }
    }
}
