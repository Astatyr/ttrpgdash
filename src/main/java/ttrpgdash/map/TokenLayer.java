package ttrpgdash.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.canvas.GraphicsContext;
import ttrpgdash.entity.Entity;
import ttrpgdash.scene.SceneState;

/**
 * Manages the collection of Token objects drawn on the map canvas.
 *
 * Responsibilities:
 *   - Keeping a Token for every on-map Entity in SceneState
 *   - Hit-testing clicks to find which token was clicked
 *   - Handling drag moves (updating token position)
 *   - Collision detection on drop (block placement or mount)
 *   - Converting between canvas pixels and map-fraction coordinates
 *   - Rendering all tokens in correct Z-order (mounts below riders)
 *
 * TokenLayer is owned by MapCanvas and called during canvas redraws and
 * mouse event handling.
 */
public class TokenLayer {

    /** All currently visible tokens, keyed by entity ID. */
    private final Map<String, Token> tokens = new LinkedHashMap<>();

    /** SceneState reference — read for entity data, written for position persistence. */
    private final SceneState sceneState;

    /** Pixel width of the map image as currently rendered on canvas. */
    private double mapPixelWidth = 1;
    /** Pixel height of the map image as currently rendered on canvas. */
    private double mapPixelHeight = 1;

    /** Canvas-space offset of the map image top-left corner. */
    private double mapOffsetX = 0;
    private double mapOffsetY = 0;

    private Token draggedToken = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private double dragOriginalXFraction = 0;
    private double dragOriginalYFraction = 0;

    private Token selectedToken = null;

    private Entity pendingEntity = null;

    public TokenLayer(SceneState sceneState) {
        this.sceneState = sceneState;
    }

    /**
     * Called by MapCanvas whenever the rendered map dimensions change
     * (image loaded, zoom changed, window resized).
     * Repositions all existing tokens to match the new scale.
     */
    public void updateMapScale(double pixelWidth, double pixelHeight,
                               double offsetX, double offsetY) {
        this.mapPixelWidth = pixelWidth;
        this.mapPixelHeight = pixelHeight;
        this.mapOffsetX = offsetX;
        this.mapOffsetY = offsetY;

        // Recompute canvas positions for all existing tokens from their stored fractions
        for (Token token : tokens.values()) {
            Entity e = token.getEntity();
            double newCx = mapOffsetX + e.getXFraction() * mapPixelWidth;
            double newCy = mapOffsetY + e.getYFraction() * mapPixelHeight;
            double newR = feetToPixels(e.getSizeInFeet()) / 2.0;
            token.setCx(newCx);
            token.setCy(newCy);
            token.setRadius(newR);
        }
    }

    /**
     * Rebuilds the token map from sceneState.
     * Call this after adding/removing entities in the sidebar,
     * or after loading a saved state.
     */
    public void syncFromGameState() {
        tokens.clear();
        for (Entity e : sceneState.getAllEntities()) {
            if (e.isOnMap()) {
                double cx = mapOffsetX + e.getXFraction() * mapPixelWidth;
                double cy = mapOffsetY + e.getYFraction() * mapPixelHeight;
                double r = feetToPixels(e.getSizeInFeet()) / 2.0;
                tokens.put(e.getId(), new Token(e, cx, cy, r));
            }
        }
    }

    /**
     * Sets an entity as "pending placement" — the next click on the map
     * will attempt to place this entity's token there.
     * Pass null to cancel pending placement.
     */
    public void setPendingEntity(Entity entity) {
        this.pendingEntity = entity;
    }

    public Entity getPendingEntity() {
        return pendingEntity;
    }

    public boolean hasPendingEntity() {
        return pendingEntity != null;
    }

    /**
     * Handles a map click at canvas coordinates (x, y).
     *
     * If an entity is pending placement:
     *   - Check for overlap with existing tokens
     *   - If clear: place the token, update SceneState
     *   - If overlapping: cancel placement (return false)
     *
     * If no pending entity:
     *   - Check if click hit a token
     *   - If yes: select it, return true (caller shows context menu)
     *
     * Returns true if a token was placed or selected.
     */
    public boolean handleClick(double x, double y) {
        if (pendingEntity != null) {
            return tryPlaceToken(pendingEntity, x, y);
        }

        // Hit-test existing tokens (topmost / riders first — reverse order)
        List<Token> ordered = new ArrayList<>(tokens.values());
        Collections.reverse(ordered);
        for (Token t : ordered) {
            if (t.contains(x, y)) {
                selectToken(t);
                return true;
            }
        }
        // Clicked empty space — deselect
        deselectAll();
        return false;
    }

    /**
     * Starts a drag on whichever token is under (x, y).
     * Returns true if a draggable token was found.
     */
    public boolean handleDragStart(double x, double y) {
        // Find topmost token — if a rider is on top, must move rider first
        List<Token> ordered = new ArrayList<>(tokens.values());
        Collections.reverse(ordered);
        for (Token t : ordered) {
            if (t.contains(x, y)) {
                // Check if another token is riding this one — if so, skip
                if (hasRider(t)) {
                    continue;
                }
                draggedToken = t;
                dragOffsetX = x - t.getCx();
                dragOffsetY = y - t.getCy();
                dragOriginalXFraction = t.getEntity().getXFraction();
                dragOriginalYFraction = t.getEntity().getYFraction();
                return true;
            }
        }
        return false;
    }

    /**
     * Updates the dragged token position as the mouse moves.
     */
    public void handleDragMove(double x, double y) {
        if (draggedToken == null) {
            return;
        }
        draggedToken.setCx(x - dragOffsetX);
        draggedToken.setCy(y - dragOffsetY);
        // Update entity fractions in memory so the player view can read the live position.
        // Does not call sceneState.entityChanged() — no disk write until drop.
        persistTokenPosition(draggedToken);
    }

    /**
     * Finalises a drag drop at canvas coordinates (x, y).
     *
     * Checks for overlap with other tokens:
     *   - If overlapping a single other token: mount on it
     *   - If overlapping multiple tokens: reject, snap back to last valid position
     *   - If clear: update SceneState position
     */
    public void handleDragEnd(double x, double y) {
        if (draggedToken == null) {
            return;
        }

        double finalX = x - dragOffsetX;
        double finalY = y - dragOffsetY;
        draggedToken.setCx(finalX);
        draggedToken.setCy(finalY);

        // Check for overlaps with other tokens
        List<Token> overlapping = getOverlappingTokens(draggedToken);

        if (overlapping.isEmpty()) {
            // Free space — update entity position in SceneState
            persistTokenPosition(draggedToken);
            // Unmount if was mounted
            draggedToken.getEntity().setMountedOnId(null);

        } else if (overlapping.size() == 1) {
            // Exactly one overlap — mount on it
            Entity mount = overlapping.get(0).getEntity();
            draggedToken.getEntity().setMountedOnId(mount.getId());
            // Position on top of mount centre
            draggedToken.setCx(overlapping.get(0).getCx());
            draggedToken.setCy(overlapping.get(0).getCy());
            persistTokenPosition(draggedToken);

        } else {
            // Multiple overlaps — restore original position
            draggedToken.getEntity().setXFraction(dragOriginalXFraction);
            draggedToken.getEntity().setYFraction(dragOriginalYFraction);
            draggedToken.setCx(mapOffsetX + dragOriginalXFraction * mapPixelWidth);
            draggedToken.setCy(mapOffsetY + dragOriginalYFraction * mapPixelHeight);
        }

        sceneState.entityChanged();
        draggedToken = null;
    }

    private boolean tryPlaceToken(Entity entity, double x, double y) {
        double r = feetToPixels(entity.getSizeInFeet()) / 2.0;
        Token candidate = new Token(entity, x, y, r);

        // Check collision with existing tokens
        for (Token existing : tokens.values()) {
            if (candidate.overlaps(existing)) {
                pendingEntity = null;
                return false; // Blocked — cancel placement
            }
        }

        // Place it
        entity.setOnMap(true);
        entity.setXFraction((x - mapOffsetX) / mapPixelWidth);
        entity.setYFraction((y - mapOffsetY) / mapPixelHeight);
        tokens.put(entity.getId(), candidate);
        pendingEntity = null;
        sceneState.entityChanged();
        return true;
    }

    /**
     * Removes the token for the given entity from the map
     * (does NOT remove the entity from SceneState/sidebar).
     */
    public void removeToken(String entityId) {
        tokens.remove(entityId);
        sceneState.findById(entityId).ifPresent(e -> {
            e.setOnMap(false);
            e.setMountedOnId(null);
        });
        sceneState.entityChanged();
    }

    private void selectToken(Token t) {
        deselectAll();
        t.setSelected(true);
        selectedToken = t;
    }

    /**
     * Deselects all tokens and clears the selection state.
     */
    public void deselectAll() {
        tokens.values().forEach(t -> t.setSelected(false));
        selectedToken = null;
    }

    public Token getSelectedToken() {
        return selectedToken;
    }

    /**
     * Converts a size in feet to canvas pixels using the current map scale.
     * pixels = (feet / mapWidthInFeet) * mapPixelWidth
     */
    private double feetToPixels(double feet) {
        if (sceneState.getMapWidthInFeet() <= 0 || mapPixelWidth <= 0) {
            return 30;
        }
        return (feet / sceneState.getMapWidthInFeet()) * mapPixelWidth;
    }

    /** Returns all tokens that overlap the given token (excluding itself). */
    private List<Token> getOverlappingTokens(Token source) {
        List<Token> result = new ArrayList<>();
        for (Token t : tokens.values()) {
            if (t != source && source.overlaps(t)) {
                result.add(t);
            }
        }
        return result;
    }

    /** Returns true if any other token is mounted on the given token. */
    private boolean hasRider(Token mount) {
        String mountId = mount.getEntity().getId();
        return tokens.values().stream()
                .anyMatch(t -> mountId.equals(t.getEntity().getMountedOnId()));
    }

    /** Stores the token's current canvas position back into its entity as fractions. */
    private void persistTokenPosition(Token token) {
        Entity e = token.getEntity();
        e.setXFraction((token.getCx() - mapOffsetX) / mapPixelWidth);
        e.setYFraction((token.getCy() - mapOffsetY) / mapPixelHeight);
    }

    /**
     * Draws all tokens onto the given GraphicsContext.
     * Z-order: mounts drawn first, then riders on top.
     * Pass {@code false} to suppress nameboxes or status icons.
     */
    public void draw(GraphicsContext gc, boolean showNames, boolean showStatus) {
        for (Token t : tokens.values()) {
            if (t.getEntity().getMountedOnId() == null) {
                t.draw(gc, showNames, showStatus);
            }
        }
        for (Token t : tokens.values()) {
            if (t.getEntity().getMountedOnId() != null) {
                t.draw(gc, showNames, showStatus);
            }
        }
    }

    /** Returns the token for a given entity ID, or null. */
    public Token getToken(String entityId) {
        return tokens.get(entityId);
    }

    /**
     * Returns the topmost token at the given canvas coordinates, or null if none.
     * Does not select or modify any state.
     */
    public Token hitTest(double x, double y) {
        List<Token> ordered = new ArrayList<>(tokens.values());
        Collections.reverse(ordered);
        for (Token t : ordered) {
            if (t.contains(x, y)) {
                return t;
            }
        }
        return null;
    }

    public boolean isDragging() {
        return draggedToken != null;
    }
}
