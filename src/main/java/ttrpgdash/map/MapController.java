package ttrpgdash.map;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.geometry.Point2D;
import javafx.stage.Stage;
import ttrpgdash.entity.Entity;
import ttrpgdash.entity.SidebarPanel;
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
public class MapController {

    private final Stage stage;
    private SceneState sceneState;
    private MapCanvas mapCanvas;
    private SidebarPanel sidebarPanel;

    private Runnable onStateChanged;
    private BiConsumer<Token, Point2D> onTokenRightClick;
    private Consumer<String> onStatusMessage;

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

    /**
     * Loads a map image from the given relative path and resets the view.
     */
    public void setMap(String relativePath, String displayName) {
        sceneState.setMapImagePath(relativePath);
        mapCanvas.loadMap(relativePath);
        fireStateChanged();
        fireStatus("Map loaded: " + displayName);
    }

    /**
     * Updates the map's real-world width and recalculates token radii.
     */
    public void setMapWidth(double feet) {
        sceneState.setMapWidthInFeet(feet);
        mapCanvas.onMapWidthChanged();
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
        if (entity.getStatusEffects().contains(status)) {
            entity.removeStatusEffect(status);
        } else {
            entity.addStatusEffect(status);
        }
        sceneState.entityChanged();
        mapCanvas.repaint();
        fireStateChanged();
    }

    /**
     * Removes the given token from the map and disarms the sidebar card.
     */
    public void removeToken(Token token) {
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
     * Sets whether entity nameboxes are rendered.
     */
    public void setNamesVisible(boolean visible) {
        mapCanvas.setNamesVisible(visible);
        fireStateChanged();
    }

    /**
     * Sets whether status-effect icons are rendered.
     */
    public void setStatusVisible(boolean visible) {
        mapCanvas.setStatusVisible(visible);
        fireStateChanged();
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
