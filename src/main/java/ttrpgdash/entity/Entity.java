package ttrpgdash.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for anything that can appear on the map as a token.
 * Both PlayerEntity (PCs) and CharacterEntity (NPCs/enemies/creatures)
 * extend this. All shared fields live here.
 */
public abstract class Entity {

    /** Unique ID, generated once on creation (used as folder name key). */
    private final String id;

    /** Display name shown on the token and sidebar. */
    private String name;

    /**
     * Path to the Avatar.png inside the entity's character folder.
     * Null means use the default white circle.
     */
    private String avatarPath;

    /**
     * Path to the Details.png inside the entity's character folder.
     * Null means no details image available.
     */
    private String detailsPath;

    /**
     * Token diameter in D&D feet (e.g. 5 = medium, 10 = large, 15 = huge).
     * Converted to pixels on the canvas using the map's feet-per-pixel ratio.
     */
    private double sizeInFeet;

    /**
     * Centre position of the token in map-image pixel coordinates.
     * Stored as a fraction (0.0-1.0) of the map image dimensions so positions
     * survive zoom changes and window resizes.
     */
    private double xFraction;
    private double yFraction;

    /** Whether this entity is currently placed on the map. */
    private boolean onMap;

    /**
     * ID of the entity this one is mounted on, or null if not riding.
     * Mounted entities share the same map position as their mount.
     */
    private String mountedOnId;

    /**
     * List of active status effect keys (e.g. "poisoned", "stunned").
     * Visual cues are determined by the rendering layer based on these strings.
     */
    private List<String> statusEffects;

    /**
     * Creates a new entity with the given ID, name, and size.
     */
    public Entity(String id, String name, double sizeInFeet) {
        this.id = id;
        this.name = name;
        this.sizeInFeet = sizeInFeet;
        this.avatarPath = null;
        this.detailsPath = null;
        this.xFraction = 0.0;
        this.yFraction = 0.0;
        this.onMap = false;
        this.mountedOnId = null;
        this.statusEffects = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getDetailsPath() {
        return detailsPath;
    }

    public void setDetailsPath(String path) {
        this.detailsPath = path;
    }

    public double getSizeInFeet() {
        return sizeInFeet;
    }

    public void setSizeInFeet(double sizeInFeet) {
        this.sizeInFeet = sizeInFeet;
    }

    public double getXFraction() {
        return xFraction;
    }

    public void setXFraction(double x) {
        this.xFraction = Math.max(0.0, Math.min(1.0, x));
    }

    public double getYFraction() {
        return yFraction;
    }

    public void setYFraction(double y) {
        this.yFraction = Math.max(0.0, Math.min(1.0, y));
    }

    public boolean isOnMap() {
        return onMap;
    }

    public void setOnMap(boolean onMap) {
        this.onMap = onMap;
    }

    public String getMountedOnId() {
        return mountedOnId;
    }

    public void setMountedOnId(String id) {
        this.mountedOnId = id;
    }

    public List<String> getStatusEffects() {
        return statusEffects;
    }

    public void setStatusEffects(List<String> fx) {
        this.statusEffects = fx;
    }

    /**
     * Adds a status effect if it is valid and not already present.
     */
    public void addStatusEffect(String effect) {
        if (!StatusEffect.isValid(effect)) {
            return;
        }

        if (!statusEffects.contains(effect)) {
            statusEffects.add(effect);
        }
    }

    public void removeStatusEffect(String effect) {
        statusEffects.remove(effect);
    }

    /** Returns "player" or "character" — used for serialisation type tagging. */
    public abstract String getEntityType();
}
