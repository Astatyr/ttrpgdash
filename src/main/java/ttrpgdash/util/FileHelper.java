package ttrpgdash.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
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
    public static final String CHARACTERS_DIR = ASSETS_DIR + "/characters";
    public static final String MAPS_DIR = ASSETS_DIR + "/maps";
    public static final String MUSIC_DIR = ASSETS_DIR + "/music";

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
     * Loads a map image from the given file path.
     * Returns a pink-and-black checkerboard if the file is missing or fails to load,
     * so the canvas always has a visible placeholder rather than a blank black surface.
     */
    public static Image loadMapImage(String filePath) {
        if (filePath == null) {
            return checkerboardImage(512);
        }
        File f = new File(filePath);
        if (!f.exists() || !f.isFile()) {
            System.err.println("[FileHelper] Missing map image: " + filePath);
            return checkerboardImage(512);
        }
        try {
            Image img = new Image(f.toURI().toString());
            if (img.isError()) {
                return checkerboardImage(512);
            }
            return img;
        } catch (Exception e) {
            return checkerboardImage(512);
        }
    }

    /**
     * Generates a pink-and-black checkerboard {@link WritableImage} of the given size.
     * Used as a missing-texture placeholder for map images.
     */
    public static Image checkerboardImage(int size) {
        int tile = Math.max(1, size / 8);
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        Color pink = Color.color(1.0, 0.0, 0.5);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pw.setColor(x, y, ((x / tile) + (y / tile)) % 2 == 0 ? pink : Color.BLACK);
            }
        }
        return img;
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
        return folder + "/Avatar.png";
    }

    public static String detailsPathFor(String folder) {
        return folder + "/Details.png";
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
     * Opens a file chooser restricted to assets/music/ for selecting audio tracks.
     * Returns null if the user cancels or selects nothing.
     */
    public static File browseForMusic(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Music File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav")
        );
        File initial = safeDir(new File(MUSIC_DIR), new File(ASSETS_DIR));
        chooser.setInitialDirectory(initial);
        File file = chooser.showOpenDialog(owner);
        if (file == null || !file.exists()) {
            return null;
        }
        return file;
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
