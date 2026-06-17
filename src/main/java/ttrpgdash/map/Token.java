package ttrpgdash.map;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import ttrpgdash.model.Entity;
import ttrpgdash.util.FileHelper;

/**
 * Visual representation of a single Entity on the map canvas.
 *
 * A Token knows:
 *   - Which Entity it represents
 *   - Its current pixel position on the canvas (centre point)
 *   - Its pixel radius (derived from entity size in feet + map scale)
 *
 * Token does NOT extend any JavaFX node — it is drawn manually by TokenLayer
 * onto the MapCanvas GraphicsContext. This gives us full control over layering,
 * especially for mounted/riding entities.
 */
public class Token {

    private final Entity entity;

    private double cx;
    private double cy;

    /** Radius in canvas pixels. Recalculated whenever map scale changes. */
    private double radius;

    private Image avatarImage;

    private boolean selected;

    /**
     * Creates a token for the given entity at the specified canvas position and radius.
     */
    public Token(Entity entity, double cx, double cy, double radius) {
        this.entity = entity;
        this.cx = cx;
        this.cy = cy;
        this.radius = radius;
        this.selected = false;
        reloadAvatar();
    }

    /** Reloads the avatar image from disk. Call after the entity's avatarPath changes. */
    public void reloadAvatar() {
        avatarImage = FileHelper.loadImage(entity.getAvatarPath());
    }

    /**
     * Draws this token onto the given GraphicsContext.
     * Call order matters — TokenLayer draws mounts first, then riders.
     */
    public void draw(GraphicsContext gc) {
        double x = cx - radius;
        double y = cy - radius;
        double diameter = radius * 2;

        // Clip to circle
        gc.save();
        gc.beginPath();
        gc.arc(cx, cy, radius, radius, 0, 360);
        gc.clip();

        // Avatar image or white fill
        if (avatarImage != null && !avatarImage.isError()) {
            gc.drawImage(avatarImage, x, y, diameter, diameter);
        } else {
            gc.setFill(Color.WHITE);
            gc.fillOval(x, y, diameter, diameter);
        }

        gc.restore();

        // Circle border — colour indicates type and selection
        if (selected) {
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(3);
        } else if ("player".equals(entity.getEntityType())) {
            gc.setStroke(Color.CORNFLOWERBLUE);
            gc.setLineWidth(2);
        } else {
            gc.setStroke(Color.TOMATO);
            gc.setLineWidth(2);
        }
        gc.strokeOval(x, y, diameter, diameter);

        // Name label below the token
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", Math.max(10, radius * 0.5)));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(entity.getName(), cx, cy + radius + 14);

        // Status effect dots above the token (up to 4 shown)
        drawStatusDots(gc);

        // Mounted indicator — small "M" badge if this entity is riding someone
        if (entity.getMountedOnId() != null) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("Arial", 10));
            gc.fillText("M", cx + radius - 4, cy - radius + 12);
        }
    }

    /** Draws small coloured dots above the token for each active status effect. */
    private void drawStatusDots(GraphicsContext gc) {
        var effects = entity.getStatusEffects();
        if (effects.isEmpty()) {
            return;
        }

        int maxDots = Math.min(effects.size(), 4);
        double dotRadius = Math.max(4, radius * 0.18);
        double spacing = dotRadius * 2.5;
        double startX = cx - (spacing * (maxDots - 1)) / 2.0;
        double dotY = cy - radius - dotRadius - 4;

        for (int i = 0; i < maxDots; i++) {
            Color dotColor = statusColor(effects.get(i));
            gc.setFill(dotColor);
            gc.fillOval(startX + i * spacing - dotRadius, dotY - dotRadius,
                    dotRadius * 2, dotRadius * 2);
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(0.5);
            gc.strokeOval(startX + i * spacing - dotRadius, dotY - dotRadius,
                    dotRadius * 2, dotRadius * 2);
        }
    }

    /** Maps a status effect name to a colour for the dot indicator. */
    private Color statusColor(String effect) {
        return switch (effect.toLowerCase()) {
        case "poisoned" -> Color.LIMEGREEN;
        case "stunned" -> Color.ORANGE;
        case "burning" -> Color.ORANGERED;
        case "frozen" -> Color.DEEPSKYBLUE;
        case "bleeding" -> Color.CRIMSON;
        case "cursed" -> Color.MEDIUMPURPLE;
        case "invisible" -> Color.LIGHTGRAY;
        case "blessed" -> Color.GOLD;
        case "exhausted" -> Color.SADDLEBROWN;
        default -> Color.WHITE;
        };
    }

    /**
     * Returns true if the given canvas point is inside this token's circle.
     * Used for click detection.
     */
    public boolean contains(double px, double py) {
        double dx = px - cx;
        double dy = py - cy;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    /**
     * Returns true if this token overlaps with another token.
     * Used for collision detection when placing a new token.
     * Overlap = distance between centres < sum of radii.
     */
    public boolean overlaps(Token other) {
        double dx = this.cx - other.cx;
        double dy = this.cy - other.cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        return dist < (this.radius + other.radius);
    }

    public Entity getEntity() {
        return entity;
    }

    public double getCx() {
        return cx;
    }

    public void setCx(double cx) {
        this.cx = cx;
    }

    public double getCy() {
        return cy;
    }

    public void setCy(double cy) {
        this.cy = cy;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double r) {
        this.radius = r;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean s) {
        this.selected = s;
    }
}
