package ttrpgdash.map;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import ttrpgdash.model.Entity;
import ttrpgdash.model.StatusEffect;
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
public final class Token {

    private static final Map<String, Image> STATUS_ICONS = new HashMap<>();

    private static Image nameboxImage;
    private static boolean nameboxLoaded = false;
    private static Font cinzelFont;
    private static boolean cinzelLoaded = false;

    private final Entity entity;

    private double cx;
    private double cy;

    /** Radius in canvas pixels. Recalculated whenever map scale changes. */
    private double radius;

    private Image avatarImage;

    private boolean selected;

    /** Cached to avoid re-measuring text on every frame; invalidated when radius changes. */
    private double cachedNameFontRadius = -1;
    private double cachedNameFontSize = 10;

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

        gc.save();
        gc.beginPath();
        gc.arc(cx, cy, radius, radius, 0, 360);
        gc.clip();

        if (avatarImage != null && !avatarImage.isError()) {
            gc.drawImage(avatarImage, x, y, diameter, diameter);
        } else {
            gc.setFill(Color.WHITE);
            gc.fillOval(x, y, diameter, diameter);
        }

        gc.restore();

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

        drawNameBox(gc);
        drawStatusIcons(gc);

        if (entity.getMountedOnId() != null) {
            gc.setFill(Color.GOLD);
            gc.setFont(Font.font("Arial", 10));
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText("M", cx + radius - 4, cy - radius + 12);
        }
    }

    private void drawNameBox(GraphicsContext gc) {
        double boxW = Math.max(60, radius * 2.25);
        double boxH = Math.max(24, radius * 0.75);
        double boxX = cx - boxW / 2.0;
        double boxY = cy + radius * 0.6;

        Image box = getNameboxImage();
        if (box != null && !box.isError()) {
            gc.drawImage(box, boxX, boxY, boxW, boxH);
        }

        double fontSize = getNameFontSize(boxW, boxH);
        gc.setFont(loadCinzel(fontSize));
        gc.setFill(Color.BLACK);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(entity.getName(), cx, boxY + boxH * 0.65, boxW * 0.8);
    }

    /** Returns the fitting font size for the name, recomputed only when radius changes. */
    private double getNameFontSize(double boxW, double boxH) {
        if (radius == cachedNameFontRadius) {
            return cachedNameFontSize;
        }
        cachedNameFontRadius = radius;
        double textAreaW = boxW * 0.8;
        double fontSize = Math.max(7, boxH * 0.45);
        while (fontSize > 7) {
            Text measurer = new Text(entity.getName());
            measurer.setFont(loadCinzel(fontSize));
            if (measurer.getBoundsInLocal().getWidth() <= textAreaW) {
                break;
            }
            fontSize -= 0.5;
        }
        cachedNameFontSize = fontSize;
        return fontSize;
    }

    /** Draws status effect icons in a row above the token, up to 4 with a +N badge if more. */
    private void drawStatusIcons(GraphicsContext gc) {
        var effects = entity.getStatusEffects();
        if (effects.isEmpty()) {
            return;
        }

        int shown = Math.min(effects.size(), 4);
        int overflow = effects.size() - shown;
        double iconSize = Math.max(14, radius * 0.5);
        double gap = 3;
        double step = iconSize + gap;
        double startX = cx - (shown * step - gap) / 2.0;
        double iconY = cy - radius - iconSize - 4;

        for (int i = 0; i < shown; i++) {
            String effect = effects.get(i);
            Image icon = loadStatusIcon(effect);
            double x = startX + i * step;
            if (icon != null && !icon.isError()) {
                gc.drawImage(icon, x, iconY, iconSize, iconSize);
            } else {
                gc.setFill(statusColor(effect));
                gc.fillOval(x, iconY, iconSize, iconSize);
                gc.setStroke(Color.BLACK);
                gc.setLineWidth(0.5);
                gc.strokeOval(x, iconY, iconSize, iconSize);
            }
        }

        if (overflow > 0) {
            double badgeX = startX + shown * step;
            gc.setTextAlign(TextAlignment.LEFT);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", Math.max(9, iconSize * 0.65)));
            gc.fillText("+" + overflow, badgeX, iconY + iconSize * 0.85);
        }
    }

    private static Image getNameboxImage() {
        if (!nameboxLoaded) {
            nameboxLoaded = true;
            String path = "assets/namebox.png";
            if (FileHelper.fileExists(path)) {
                nameboxImage = FileHelper.loadImage(path);
            }
        }
        return nameboxImage;
    }

    private static Font loadCinzel(double size) {
        if (!cinzelLoaded) {
            cinzelLoaded = true;
            String fontPath = "assets/fonts/Cinzel-Regular.ttf";
            if (FileHelper.fileExists(fontPath)) {
                Font loaded = Font.loadFont(
                        new File(fontPath).toURI().toString(), size);
                if (loaded != null) {
                    cinzelFont = loaded;
                }
            }
        }
        if (cinzelFont != null) {
            return Font.font(cinzelFont.getFamily(), size);
        }
        return Font.font("Georgia", size);
    }

    /**
     * Returns the cached PNG for the given status effect, or null if the file is missing.
     * Missing entries are cached so the filesystem is only checked once per effect.
     */
    private static Image loadStatusIcon(String effect) {
        if (STATUS_ICONS.containsKey(effect)) {
            return STATUS_ICONS.get(effect);
        }
        String path = StatusEffect.iconPath(effect);
        Image icon = FileHelper.fileExists(path) ? FileHelper.loadImage(path) : null;
        STATUS_ICONS.put(effect, icon);
        return icon;
    }

    /** Fallback colour used when no PNG is found for a status effect. */
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
