package ttrpgdash.map;

import java.util.function.BiConsumer;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import ttrpgdash.entity.Entity;
import ttrpgdash.scene.SceneState;
import ttrpgdash.util.FileHelper;

/**
 * The main interactive map surface.
 *
 * Wraps a JavaFX Canvas inside a resizable Pane.
 * Supports:
 *   - Loading and displaying a map image (PNG)
 *   - Smooth zoom (scroll wheel) and pan (right-click drag)
 *   - Token placement via TokenLayer
 *   - Left-click token selection with context menu callback
 *   - Right-click drag for panning
 *
 * The canvas uses a transform (offsetX, offsetY, scale) to position the map.
 * All token coordinates are stored as map-image fractions and converted to
 * canvas pixels on each redraw.
 */
public final class MapCanvas extends Pane {

    private static final double SCALE_MIN = 0.1;
    private static final double SCALE_MAX = 10.0;
    private static final double SCALE_STEP = 0.1;

    private final Canvas canvas;
    private final GraphicsContext gc;

    private Image mapImage;

    /** Current zoom level. 1.0 = image fits the pane. */
    private double scale = 1.0;

    /** Canvas translation: where the top-left of the map image sits on the canvas. */
    private double offsetX = 0;
    private double offsetY = 0;

    private double panStartX;
    private double panStartY;
    private double panOriginX;
    private double panOriginY;
    private boolean panning = false;

    private final TokenLayer tokenLayer;

    /** Called when a token is right-clicked — passes token and screen coordinates. */
    private BiConsumer<Token, Point2D> onTokenRightClick;

    /** Called when a token is placed on or moved on the map. */
    private Runnable onTokensChanged;

    private boolean panMoved = false;

    private final SceneState sceneState;

    /** When true, token interaction (drag, placement, context menu) is disabled. */
    private final boolean readOnly;

    private boolean namesVisible = true;
    private boolean statusVisible = true;

    private Image playerMapBg;
    private boolean playerMapBgLoaded = false;

    /**
     * Creates an editable map canvas bound to the given game state.
     */
    public MapCanvas(SceneState sceneState) {
        this(sceneState, false);
    }

    /**
     * Creates the map canvas bound to the given game state.
     * Pass {@code readOnly = true} for a display-only view that allows pan/zoom
     * but disables token drag, placement, and the context menu.
     */
    public MapCanvas(SceneState sceneState, boolean readOnly) {
        this.sceneState = sceneState;
        this.readOnly = readOnly;
        this.tokenLayer = new TokenLayer(sceneState);

        // Canvas fills the pane
        canvas = new Canvas();
        getChildren().add(canvas);

        // Bind canvas size to pane size
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(e -> redraw());
        canvas.heightProperty().addListener(e -> redraw());

        gc = canvas.getGraphicsContext2D();

        setupMouseHandlers();
    }

    /**
     * Loads a new map image from the given path and resets zoom/pan to fit.
     */
    public void loadMap(String imagePath) {
        mapImage = FileHelper.loadMapImage(imagePath);
        if (mapImage != null && !mapImage.isError()) {
            fitMapToPane();
        }
        redraw();
    }

    /**
     * Reloads the map from the path stored in SceneState (called on startup).
     */
    public void reloadFromState() {
        String path = sceneState.getMapImagePath();
        if (path != null) {
            loadMap(path);
        } else {
            mapImage = null;
        }
        tokenLayer.syncFromGameState();
        redraw();
    }

    /** Scales and centres the map image to fit the current pane size. */
    private void fitMapToPane() {
        if (mapImage == null) {
            return;
        }
        double paneW = getWidth() > 0 ? getWidth() : 800;
        double paneH = getHeight() > 0 ? getHeight() : 600;
        double scaleX = paneW / mapImage.getWidth();
        double scaleY = paneH / mapImage.getHeight();
        scale = Math.min(scaleX, scaleY);
        offsetX = (paneW - mapImage.getWidth() * scale) / 2.0;
        offsetY = (paneH - mapImage.getHeight() * scale) / 2.0;
        notifyTokenLayerOfScale();
    }

    /**
     * Arms the canvas to place the given entity on the next left-click.
     * Called by SidebarPanel when the user selects an entity to place.
     */
    public void setPendingEntity(Entity entity) {
        tokenLayer.setPendingEntity(entity);
    }

    private void setupMouseHandlers() {

        if (!readOnly) {
            canvas.setOnMouseClicked(e -> {
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                boolean handled = tokenLayer.handleClick(e.getX(), e.getY());
                if (handled && onTokensChanged != null) {
                    onTokensChanged.run();
                }
                if (!handled && !tokenLayer.hasPendingEntity()) {
                    tokenLayer.deselectAll();
                }
                redraw();
            });
        }

        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                panning = true;
                panMoved = false;
                panStartX = e.getX();
                panStartY = e.getY();
                panOriginX = offsetX;
                panOriginY = offsetY;
            } else if (e.getButton() == MouseButton.PRIMARY && !readOnly) {
                tokenLayer.handleDragStart(e.getX(), e.getY());
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (panning) {
                panMoved = true;
                offsetX = panOriginX + (e.getX() - panStartX);
                offsetY = panOriginY + (e.getY() - panStartY);
                notifyTokenLayerOfScale();
                redraw();
            } else if (!readOnly && tokenLayer.isDragging()) {
                tokenLayer.handleDragMove(e.getX(), e.getY());
                if (onTokensChanged != null) {
                    onTokensChanged.run();
                }
                redraw();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (panning) {
                panning = false;
            } else if (!readOnly && tokenLayer.isDragging()) {
                tokenLayer.handleDragEnd(e.getX(), e.getY());
                if (onTokensChanged != null) {
                    onTokensChanged.run();
                }
                redraw();
            }
            if (!readOnly && e.getButton() == MouseButton.SECONDARY && !panMoved) {
                Token hit = tokenLayer.hitTest(e.getX(), e.getY());
                if (hit != null && onTokenRightClick != null) {
                    onTokenRightClick.accept(hit, new Point2D(e.getScreenX(), e.getScreenY()));
                }
            }
        });

        canvas.setOnScroll(this::handleScroll);
    }

    private void handleScroll(ScrollEvent e) {
        double delta = e.getDeltaY() > 0 ? SCALE_STEP : -SCALE_STEP;
        double newScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, scale + delta));
        double mouseX = e.getX();
        double mouseY = e.getY();

        // Zoom toward the mouse cursor
        double scaleRatio = newScale / scale;
        offsetX = mouseX - (mouseX - offsetX) * scaleRatio;
        offsetY = mouseY - (mouseY - offsetY) * scaleRatio;
        scale = newScale;

        notifyTokenLayerOfScale();
        redraw();
    }

    private Image getPlayerMapBg() {
        if (!playerMapBgLoaded) {
            playerMapBgLoaded = true;
            String path = "assets/playermapbg.png";
            if (FileHelper.fileExists(path)) {
                playerMapBg = FileHelper.loadImage(path);
            }
        }
        return playerMapBg;
    }

    /** Tells the TokenLayer the current rendered map size and position. */
    private void notifyTokenLayerOfScale() {
        if (mapImage == null) {
            return;
        }
        double imgW = mapImage.getWidth() * scale;
        double imgH = mapImage.getHeight() * scale;
        tokenLayer.updateMapScale(imgW, imgH, offsetX, offsetY);
    }

    private void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.clearRect(0, 0, w, h);

        // Background
        if (readOnly) {
            Image bg = getPlayerMapBg();
            if (bg != null && !bg.isError()) {
                gc.drawImage(bg, 0, 0, w, h);
            } else {
                gc.setFill(Color.rgb(20, 20, 30));
                gc.fillRect(0, 0, w, h);
            }
        } else {
            gc.setFill(Color.rgb(20, 20, 30));
            gc.fillRect(0, 0, w, h);
        }

        if (mapImage != null && !mapImage.isError()) {
            double imgW = mapImage.getWidth() * scale;
            double imgH = mapImage.getHeight() * scale;
            gc.drawImage(mapImage, offsetX, offsetY, imgW, imgH);
        } else {
            // No map loaded — placeholder text
            gc.setFill(Color.rgb(60, 60, 80));
            gc.setFont(Font.font("Arial", 16));
            gc.fillText("No map loaded — use the menu to browse for a map PNG", w / 2 - 200, h / 2);
        }

        // Tokens
        tokenLayer.draw(gc, namesVisible, statusVisible);

        // Pending placement cursor hint
        if (tokenLayer.hasPendingEntity()) {
            gc.setFill(Color.color(1, 1, 0, 0.3));
            gc.setFont(Font.font("Arial", 13));
            gc.fillText("Click on the map to place: " + tokenLayer.getPendingEntity().getName(),
                    10, canvas.getHeight() - 12);
        }
    }

    /**
     * Called when the DM changes the map width in feet.
     * Triggers a token radius recalculation.
     */
    public void onMapWidthChanged() {
        notifyTokenLayerOfScale();
        redraw();
    }

    /**
     * Removes the given token from the map.
     */
    public void removeToken(Token token) {
        tokenLayer.removeToken(token.getEntity().getId());
        redraw();
    }

    /**
     * Removes the currently selected token from the map. No-op if nothing is selected.
     */
    public void removeSelectedToken() {
        Token sel = tokenLayer.getSelectedToken();
        if (sel != null) {
            removeToken(sel);
        }
    }

    /** Triggers a redraw without rebuilding token state. Use after in-place entity changes. */
    public void repaint() {
        redraw();
    }

    /** Call this after adding or removing entities from the sidebar. */
    public void syncTokens() {
        tokenLayer.syncFromGameState();
        redraw();
    }

    public void setNamesVisible(boolean visible) {
        this.namesVisible = visible;
        redraw();
    }

    public boolean isNamesVisible() {
        return namesVisible;
    }

    public void setStatusVisible(boolean visible) {
        this.statusVisible = visible;
        redraw();
    }

    public boolean isStatusVisible() {
        return statusVisible;
    }

    public void setOnTokenRightClick(BiConsumer<Token, Point2D> handler) {
        this.onTokenRightClick = handler;
    }

    public void setOnTokensChanged(Runnable handler) {
        this.onTokensChanged = handler;
    }

    public void setOnTokenPlaced(java.util.function.Consumer<Token> handler) {
        tokenLayer.setOnTokenPlaced(handler);
    }

    public void setOnTokenMoved(java.util.function.BiConsumer<Token, double[]> handler) {
        tokenLayer.setOnTokenMoved(handler);
    }

    public TokenLayer getTokenLayer() {
        return tokenLayer;
    }
}
