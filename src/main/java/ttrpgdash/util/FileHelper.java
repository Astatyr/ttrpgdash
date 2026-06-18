package ttrpgdash.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.scene.image.Image;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Utility class for file operations:
 * - Safe native file/folder dialogs
 * - Robust image loading with fallback
 * - Safe asset path handling
 */
public class FileHelper {

    public static final String ASSETS_DIR = "assets";
    public static final String CHARACTERS_DIR = ASSETS_DIR + File.separator + "characters";
    public static final String MAPS_DIR = ASSETS_DIR + File.separator + "maps";
    public static final String MUSIC_DIR = ASSETS_DIR + File.separator + "music";

    private static File safeDir(File dir, File fallback) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            return dir.getAbsoluteFile();
        }
        if (fallback != null && fallback.exists() && fallback.isDirectory()) {
            return fallback.getAbsoluteFile();
        }
        return new File(System.getProperty("user.home"));
    }

    /**
     * Opens a file chooser dialog for selecting a PNG image.
     */
    public static File browseForImage(Stage owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Images", "*.png")
        );

        File initial = safeDir(
                new File(ASSETS_DIR),
                new File(System.getProperty("user.home"))
        );

        chooser.setInitialDirectory(initial);

        File file = chooser.showOpenDialog(owner);
        if (file == null || !file.exists()) {
            return null;
        }

        return file;
    }

    /**
     * Opens a file chooser dialog for selecting a map PNG.
     */
    public static File browseForMap(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Map Image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Images", "*.png")
        );

        File initial = safeDir(
                new File(MAPS_DIR),
                new File(ASSETS_DIR)
        );

        chooser.setInitialDirectory(initial);

        File file = chooser.showOpenDialog(owner);
        if (file == null || !file.exists()) {
            return null;
        }

        return file;
    }

    /**
     * Opens a directory chooser dialog for selecting a character folder.
     */
    public static File browseForCharacterFolder(Stage owner) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Character Folder");

        File initial = safeDir(
                new File(CHARACTERS_DIR),
                new File(ASSETS_DIR)
        );

        chooser.setInitialDirectory(initial);

        File dir = chooser.showDialog(owner);

        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }

        return dir;
    }

    /**
     * Loads an image from the given file path, returning a default avatar on failure.
     */
    public static Image loadImage(String filePath) {
        if (filePath == null) {
            return loadDefaultAvatar();
        }

        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            System.err.println("[FileHelper] Missing image: " + filePath);
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
     * Returns a 1x1 transparent PNG as a fallback avatar image.
     */
    public static Image loadDefaultAvatar() {
        String dataUri =
                "data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwADhQGAWjR9awAAAABJRU5ErkJggg==";

        try {
            return new Image(dataUri);
        } catch (Exception e) {
            return null;
        }
    }

    public static String avatarPathFor(String folder) {
        return folder + File.separator + "Avatar.png";
    }

    public static String detailsPathFor(String folder) {
        return folder + File.separator + "Details.png";
    }

    public static boolean fileExists(String path) {
        return path != null && new File(path).exists();
    }

    /**
     * Generates a unique ID for an entity based on its name and current time.
     */
    public static String generateId(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        return base + "_" + Long.toHexString(System.currentTimeMillis());
    }

    /**
     * Normalises a path string to a relative path.
     * If the path is already relative, it is returned unchanged.
     * Useful for sanitising paths loaded from persisted state.
     */
    public static String normalizeToRelative(String path) {
        if (path == null) {
            return null;
        }
        File f = new File(path);
        if (!f.isAbsolute()) {
            return path;
        }
        return toRelativePath(f);
    }

    /**
     * Converts an absolute file path to a path relative to the working directory,
     * using forward slashes. Falls back to the absolute path if relativisation fails
     * (e.g. different drive on Windows).
     */
    public static String toRelativePath(File file) {
        try {
            Path workDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            Path relative = workDir.relativize(file.toPath().toAbsolutePath());
            return relative.toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }
}
