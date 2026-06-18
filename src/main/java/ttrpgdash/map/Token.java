package ttrpgdash.map;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Namebox layout cached on first draw — based on entity size (constant),
     * so it never needs recomputing.
     */
    private double cachedBoxW = -1;
    private double cachedBoxH = -1;
    private double cachedFontSize = -1;
    private List<String> cachedLines = null;

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

    private void ensureNameboxCached() {
        if (cachedLines != null) {
            return;
        }

        double sizeInFeet = entity.getSizeInFeet();
        cachedBoxW = Math.min(Math.max(60, sizeInFeet * 14), 180);
        double baseBoxH = Math.min(Math.max(20, sizeInFeet * 4.5), 45);

        String name = entity.getName();
        double textAreaW = cachedBoxW * 0.8;
        double startFontSize = Math.max(7, baseBoxH * 0.42);

        // Try to fit in a single line at the natural font size
        Text measurer = new Text(name);
        measurer.setFont(loadCinzel(startFontSize));

        if (measurer.getBoundsInLocal().getWidth() <= textAreaW) {
            cachedFontSize = startFontSize;
            cachedBoxH = baseBoxH;
            cachedLines = List.of(name);
            return;
        }

        // Try a 2-line split at word boundaries, preferring balanced line lengths
        String[] words = name.split(" ");
        if (words.length > 1) {
            double bestScore = Double.MAX_VALUE;
            List<String> bestSplit = null;

            for (int split = 1; split < words.length; split++) {
                String line1 = String.join(" ", Arrays.copyOfRange(words, 0, split));
                String line2 = String.join(" ", Arrays.copyOfRange(words, split, words.length));
                Text m1 = new Text(line1);
                m1.setFont(loadCinzel(startFontSize));
                Text m2 = new Text(line2);
                m2.setFont(loadCinzel(startFontSize));
                double w1 = m1.getBoundsInLocal().getWidth();
                double w2 = m2.getBoundsInLocal().getWidth();
                if (w1 <= textAreaW && w2 <= textAreaW) {
                    double score = Math.abs(w1 - w2);
                    if (score < bestScore) {
                        bestScore = score;
                        bestSplit = List.of(line1, line2);
                    }
                }
            }

            if (bestSplit != null) {
                cachedFontSize = startFontSize;
                cachedBoxH = baseBoxH * 1.9;
                cachedLines = bestSplit;
                return;
            }
        }

        // No valid word split — shrink font until it fits on one line
        double fontSize = startFontSize;
        while (fontSize > 7) {
            measurer.setFont(loadCinzel(fontSize));
            if (measurer.getBoundsInLocal().getWidth() <= textAreaW) {
                break;
            }
            fontSize -= 0.5;
        }
        cachedFontSize = fontSize;
        cachedBoxH = baseBoxH;
        cachedLines = List.of(name);
    }

    private void drawNameBox(GraphicsContext gc) {
        ensureNameboxCached();

        double boxX = cx - cachedBoxW / 2.0;
        double boxY = cy + radius * 0.6;

        Image box = getNameboxImage();
        if (box != null && !box.isError()) {
            gc.drawImage(box, boxX, boxY, cachedBoxW, cachedBoxH);
        }

        gc.setFont(loadCinzel(cachedFontSize));
        gc.setFill(Color.BLACK);
        gc.setTextAlign(TextAlignment.CENTER);

        if (cachedLines.size() == 1) {
            gc.fillText(cachedLines.get(0), cx, boxY + cachedBoxH * 0.65, cachedBoxW * 0.8);
        } else {
            gc.fillText(cachedLines.get(0), cx, boxY + cachedBoxH * 0.35, cachedBoxW * 0.8);
            gc.fillText(cachedLines.get(1), cx, boxY + cachedBoxH * 0.70, cachedBoxW * 0.8);
        }
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
