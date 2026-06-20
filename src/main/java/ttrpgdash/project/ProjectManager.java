package ttrpgdash.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ttrpgdash.App;
import ttrpgdash.scene.SceneManager;

/**
 * Handles project save: bundles {@code data/}, {@code assets/}, and {@code logs/}
 * into a single {@code .ttrpg} file (a ZIP archive) alongside a {@code manifest.json}
 * describing the app version, save time, and active scene.
 */
public final class ProjectManager {

    /** File extension used for project archives. */
    public static final String FILE_EXTENSION = "ttrpg";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectManager() {}

    /**
     * Opens a save dialog and writes the current project to a {@code .ttrpg} archive.
     * No-op if the user cancels the dialog.
     *
     * @param sceneManager the live scene manager (for active scene ID in the manifest)
     * @param ownerStage   the parent window for the file chooser
     */
    public static void saveProject(SceneManager sceneManager, Stage ownerStage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Project");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("TTRPG Dash Project", "*." + FILE_EXTENSION));
        java.io.File result = chooser.showSaveDialog(ownerStage);
        if (result == null) {
            return;
        }

        Path target = result.toPath();
        if (!target.getFileName().toString().endsWith("." + FILE_EXTENSION)) {
            target = target.resolveSibling(target.getFileName() + "." + FILE_EXTENSION);
        }

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            writeManifest(zos, sceneManager);
            addDirectory(zos, Paths.get("data"), "data");
            addDirectory(zos, Paths.get("assets"), "assets");
            addDirectory(zos, Paths.get("logs"), "logs");
            System.out.println("[ProjectManager] Project saved to: " + target);
        } catch (IOException e) {
            System.err.println("[ProjectManager] Failed to save project: " + e.getMessage());
        }
    }

    private static void writeManifest(ZipOutputStream zos, SceneManager sceneManager)
            throws IOException {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("projectVersion", "1");
        manifest.addProperty("appVersion", App.VERSION);
        manifest.addProperty("savedAt", Instant.now().toString());
        manifest.addProperty("activeSceneId", sceneManager.getActiveSceneId());

        zos.putNextEntry(new ZipEntry("manifest.json"));
        zos.write(GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void addDirectory(ZipOutputStream zos, Path sourceDir, String zipRoot)
            throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            return;
        }
        try (var stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String entryName = zipRoot + "/"
                        + sourceDir.relativize(file).toString().replace('\\', '/');
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    System.err.println("[ProjectManager] Skipped " + entryName
                            + ": " + e.getMessage());
                }
            });
        }
    }
}
