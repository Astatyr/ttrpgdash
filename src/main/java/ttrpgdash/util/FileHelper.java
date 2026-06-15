package ttrpgdash.util;

import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Utility class for file operations:
 * - Opening native file browser dialogs
 * - Loading images from paths (with fallback to default avatar)
 * - Resolving asset paths within the project structure
 *
 * All methods are static — no instantiation needed.
 */
public class FileHelper {

    // ── Asset paths ───────────────────────────────────────────────────────────

    public static final String ASSETS_DIR       = "assets";
    public static final String CHARACTERS_DIR   = ASSETS_DIR + "/characters";
    public static final String MAPS_DIR         = ASSETS_DIR + "/maps";
    public static final String MUSIC_DIR        = ASSETS_DIR + "/music";

    // ── File choosers ─────────────────────────────────────────────────────────

    /**
     * Opens a native file dialog filtered to PNG images.
     * Returns the chosen File, or null if the user cancelled.
     */
    public static File browseForImage(Stage owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Images", "*.png", "*.PNG")
        );
        // Default to assets/ if it exists, otherwise home directory
        File assetsDir = new File(ASSETS_DIR);
        chooser.setInitialDirectory(assetsDir.exists() ? assetsDir : new File(System.getProperty("user.home")));
        return chooser.showOpenDialog(owner);
    }

    /**
     * Opens a native file dialog for selecting a map image (PNG).
     * Defaults to assets/maps/.
     */
    public static File browseForMap(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Map Image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Images", "*.png", "*.PNG")
        );
        File mapsDir = new File(MAPS_DIR);
        chooser.setInitialDirectory(mapsDir.exists() ? mapsDir : new File(ASSETS_DIR));
        return chooser.showOpenDialog(owner);
    }

    /**
     * Opens a native file dialog for selecting a character folder.
     * Defaults to assets/characters/.
     */
    public static File browseForCharacterFolder(Stage owner) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Character Folder");
        File charsDir = new File(CHARACTERS_DIR);
        chooser.setInitialDirectory(charsDir.exists() ? charsDir : new File(ASSETS_DIR));
        return chooser.showDialog(owner);
    }

    // ── Image loading ─────────────────────────────────────────────────────────

    /**
     * Loads a JavaFX Image from an absolute or relative file path.
     * Returns the default white avatar if the file doesn't exist or fails to load.
     */
    public static Image loadImage(String filePath) {
        if (filePath == null) return loadDefaultAvatar();
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("[FileHelper] Image not found: " + filePath);
            return loadDefaultAvatar();
        }
        try {
            return new Image(file.toURI().toString());
        } catch (Exception e) {
            System.err.println("[FileHelper] Failed to load image: " + filePath);
            return loadDefaultAvatar();
        }
    }

    /**
     * Returns a plain white 64x64 Image used as the fallback avatar.
     * Generated programmatically — no external file dependency.
     */
    public static Image loadDefaultAvatar() {
        // 1x1 white pixel encoded as a data URI — JavaFX scales it up
        String dataUri = "data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwADhQGAWjR9awAAAABJRU5ErkJggg==";
        try {
            return new Image(dataUri);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Path helpers ──────────────────────────────────────────────────────────

    /**
     * Builds the expected Avatar.png path for a character folder.
     * e.g. "assets/characters/Aria/Avatar.png"
     */
    public static String avatarPathFor(String characterFolderPath) {
        return characterFolderPath + File.separator + "Avatar.png";
    }

    /**
     * Builds the expected Details.png path for a character folder.
     * e.g. "assets/characters/Aria/Details.png"
     */
    public static String detailsPathFor(String characterFolderPath) {
        return characterFolderPath + File.separator + "Details.png";
    }

    /**
     * Returns true if a file exists at the given path.
     */
    public static boolean fileExists(String path) {
        return path != null && new File(path).exists();
    }

    /**
     * Generates a simple unique ID: lowercase name + timestamp suffix.
     * Used when creating new entities.
     */
    public static String generateId(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        return base + "_" + Long.toHexString(System.currentTimeMillis());
    }
}
