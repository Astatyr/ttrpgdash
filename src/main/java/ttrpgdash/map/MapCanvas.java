package ttrpgdash.map;

import java.util.function.Consumer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import ttrpgdash.model.Entity;
import ttrpgdash.model.GameState;
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
public class MapCanvas extends Pane {

    // ── Canvas ────────────────────────────────────────────────────────────────

    private final Canvas canvas;
    private final GraphicsContext gc;

    // ── Map image ─────────────────────────────────────────────────────────────

    private Image mapImage = null;

    // ── Transform state ───────────────────────────────────────────────────────

    /** Current zoom level. 1.0 = image fits the pane. */
    private double scale = 1.0;
    private static final double SCALE_MIN  = 0.1;
    private static final double SCALE_MAX  = 10.0;
    private static final double SCALE_STEP = 0.1;

    /** Canvas translation: where the top-left of the map image sits on the canvas. */
    private double offsetX = 0;
    private double offsetY = 0;

    // ── Pan drag state ────────────────────────────────────────────────────────

    private double panStartX, panStartY;
    private double panOriginX, panOriginY;
    private boolean panning = false;

    // ── Token layer ───────────────────────────────────────────────────────────

    private final TokenLayer tokenLayer;

    // ── Callbacks (set by MainWindow) ─────────────────────────────────────────

    /** Called when a token is right-clicked — passes the selected token for context menu. */
    private Consumer<Token> onTokenRightClick;

    // ── GameState ─────────────────────────────────────────────────────────────

    private final GameState gameState;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MapCanvas(GameState gameState) {
        this.gameState   = gameState;
        this.tokenLayer  = new TokenLayer(gameState);

        // Canvas fills the pane
        canvas = new Canvas();
        getChildren().add(canvas);

        // Bind canvas size to pane size
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(e  -> redraw());
        canvas.heightProperty().addListener(e -> redraw());

        gc = canvas.getGraphicsContext2D();

        setupMouseHandlers();
    }

    // ── Map loading ───────────────────────────────────────────────────────────

    /**
     * Loads a new map image from the given path and resets zoom/pan to fit.
     */
    public void loadMap(String imagePath) {
        mapImage = FileHelper.loadImage(imagePath);
        if (mapImage != null && !mapImage.isError()) {
            fitMapToPane();
        }
        redraw();
    }

    /**
     * Reloads the map from the path stored in GameState (called on startup).
     */
    public void reloadFromState() {
        String path = gameState.getMapImagePath();
        if (path != null) loadMap(path);
        tokenLayer.syncFromGameState();
        redraw();
    }

    /** Scales and centres the map image to fit the current pane size. */
    private void fitMapToPane() {
        if (mapImage == null) return;
        double paneW = getWidth()  > 0 ? getWidth()  : 800;
        double paneH = getHeight() > 0 ? getHeight() : 600;
        double scaleX = paneW / mapImage.getWidth();
        double scaleY = paneH / mapImage.getHeight();
        scale = Math.min(scaleX, scaleY);
        offsetX = (paneW - mapImage.getWidth()  * scale) / 2.0;
        offsetY = (paneH - mapImage.getHeight() * scale) / 2.0;
        notifyTokenLayerOfScale();
    }

    // ── Pending token placement ───────────────────────────────────────────────

    /**
     * Arms the canvas to place the given entity on the next left-click.
     * Called by SidebarPanel when the user selects an entity to place.
     */
    public void setPendingEntity(Entity entity) {
        tokenLayer.setPendingEntity(entity);
    }

    // ── Mouse handlers ────────────────────────────────────────────────────────

    private void setupMouseHandlers() {

        // ── Left click ────────────────────────────────────────────────────────
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            boolean handled = tokenLayer.handleClick(e.getX(), e.getY());
            if (!handled && !tokenLayer.hasPendingEntity()) {
                tokenLayer.deselectAll();
            }
            redraw();
        });

        // ── Right click ───────────────────────────────────────────────────────
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                // Pan start
                panning    = true;
                panStartX  = e.getX();
                panStartY  = e.getY();
                panOriginX = offsetX;
                panOriginY = offsetY;
            } else if (e.getButton() == MouseButton.PRIMARY) {
                // Drag start
                tokenLayer.handleDragStart(e.getX(), e.getY());
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (panning) {
                offsetX = panOriginX + (e.getX() - panStartX);
                offsetY = panOriginY + (e.getY() - panStartY);
                notifyTokenLayerOfScale();
                redraw();
            } else if (tokenLayer.isDragging()) {
                tokenLayer.handleDragMove(e.getX(), e.getY());
                redraw();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (panning) {
                panning = false;
            } else if (tokenLayer.isDragging()) {
                tokenLayer.handleDragEnd(e.getX(), e.getY());
                redraw();
            }
            // Right-click on a selected token → context menu
            if (e.getButton() == MouseButton.SECONDARY && !panning) {
                Token sel = tokenLayer.getSelectedToken();
                if (sel != null && onTokenRightClick != null) {
                    onTokenRightClick.accept(sel);
                }
            }
        });

        // ── Scroll to zoom ────────────────────────────────────────────────────
        canvas.setOnScroll(this::handleScroll);
    }

    private void handleScroll(ScrollEvent e) {
        double delta     = e.getDeltaY() > 0 ? SCALE_STEP : -SCALE_STEP;
        double newScale  = Math.clamp(scale + delta, SCALE_MIN, SCALE_MAX);
        double mouseX    = e.getX();
        double mouseY    = e.getY();

        // Zoom toward the mouse cursor
        double scaleRatio = newScale / scale;
        offsetX = mouseX - (mouseX - offsetX) * scaleRatio;
        offsetY = mouseY - (mouseY - offsetY) * scaleRatio;
        scale   = newScale;

        notifyTokenLayerOfScale();
        redraw();
    }

    // ── Scale notification ────────────────────────────────────────────────────

    /** Tells the TokenLayer the current rendered map size and position. */
    private void notifyTokenLayerOfScale() {
        if (mapImage == null) return;
        double imgW = mapImage.getWidth()  * scale;
        double imgH = mapImage.getHeight() * scale;
        tokenLayer.updateMapScale(imgW, imgH, offsetX, offsetY);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    private void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.clearRect(0, 0, w, h);

        // Background
        gc.setFill(Color.rgb(20, 20, 30));
        gc.fillRect(0, 0, w, h);

        if (mapImage != null && !mapImage.isError()) {
            double imgW = mapImage.getWidth()  * scale;
            double imgH = mapImage.getHeight() * scale;
            gc.drawImage(mapImage, offsetX, offsetY, imgW, imgH);
        } else {
            // No map loaded — placeholder text
            gc.setFill(Color.rgb(60, 60, 80));
            gc.setFont(Font.font("Arial", 16));
            gc.fillText("No map loaded — use the menu to browse for a map PNG", w / 2 - 200, h / 2);
        }

        // Tokens
        tokenLayer.draw(gc);

        // Pending placement cursor hint
        if (tokenLayer.hasPendingEntity()) {
            gc.setFill(Color.color(1, 1, 0, 0.3));
            gc.setFont(Font.font("Arial", 13));
            gc.fillText("Click on the map to place: " + tokenLayer.getPendingEntity().getName(),
                    10, canvas.getHeight() - 12);
        }
    }

    // ── Map width config ──────────────────────────────────────────────────────

    /**
     * Called when the DM changes the map width in feet.
     * Triggers a token radius recalculation.
     */
    public void onMapWidthChanged() {
        notifyTokenLayerOfScale();
        redraw();
    }

    // ── Token removal (from context menu) ────────────────────────────────────

    public void removeSelectedToken() {
        Token sel = tokenLayer.getSelectedToken();
        if (sel != null) {
            tokenLayer.removeToken(sel.getEntity().getId());
            redraw();
        }
    }

    // ── Sync after sidebar changes ────────────────────────────────────────────

    /** Call this after adding or removing entities from the sidebar. */
    public void syncTokens() {
        tokenLayer.syncFromGameState();
        redraw();
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public void setOnTokenRightClick(Consumer<Token> handler) {
        this.onTokenRightClick = handler;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public TokenLayer getTokenLayer() { return tokenLayer; }
}
