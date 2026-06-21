package ttrpgdash.project;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import ttrpgdash.App;
import ttrpgdash.scene.SceneManager;

/**
 * Handles project save and load: bundles {@code data/}, {@code assets/}, and {@code logs/}
 * into a single {@code .ttrpg} file (a ZIP archive) alongside a {@code manifest.json}.
 *
 * Saving runs on a background thread via {@link Task} so the FX thread remains responsive
 * and a modal progress dialog can update in real time.
 */
public final class ProjectManager {

    /** File extension used for project archives. */
    public static final String FILE_EXTENSION = "ttrpg";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectManager() {}

    /**
     * Opens a save dialog, then shows a modal progress window while bundling the project
     * into a {@code .ttrpg} archive on a background thread.
     * No-op if the user cancels the dialog.
     */
    public static void saveProject(SceneManager sceneManager, Stage ownerStage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Project");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("TTRPG Dash Project", "*." + FILE_EXTENSION));
        File savesDir = new File("saves");
        savesDir.mkdirs();
        chooser.setInitialDirectory(savesDir);
        File result = chooser.showSaveDialog(ownerStage);
        if (result == null) {
            return;
        }

        Path target = result.toPath();
        if (!target.getFileName().toString().endsWith("." + FILE_EXTENSION)) {
            target = target.resolveSibling(target.getFileName() + "." + FILE_EXTENSION);
        }

        final Path finalTarget = target;
        Task<Void> task = buildSaveTask(sceneManager, finalTarget);

        Stage progress = buildProgressDialog(task, ownerStage, "Saving Project…", true);

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progress.close();
            System.out.println("[ProjectManager] Project saved to: " + finalTarget);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            progress.close();
            Throwable ex = task.getException();
            System.err.println("[ProjectManager] Save failed: "
                    + (ex != null ? ex.getMessage() : "unknown error"));
        }));
        task.setOnCancelled(e -> Platform.runLater(() -> {
            progress.close();
            try {
                Files.deleteIfExists(finalTarget);
                System.out.println("[ProjectManager] Save cancelled — partial file deleted.");
            } catch (IOException ex) {
                System.err.println("[ProjectManager] Could not delete partial file: "
                        + ex.getMessage());
            }
        }));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        progress.showAndWait();
    }

    /**
     * Opens a load dialog and extracts a {@code .ttrpg} archive over the current working directory,
     * replacing {@code data/}, {@code assets/}, and {@code logs/}.
     * Shows a confirmation warning before proceeding.
     *
     * @return {@code true} if the project was successfully loaded, {@code false} if cancelled or failed
     */
    public static boolean loadProject(Stage ownerStage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Project");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("TTRPG Dash Project", "*." + FILE_EXTENSION));
        File savesDir = new File("saves");
        savesDir.mkdirs();
        chooser.setInitialDirectory(savesDir);
        File selected = chooser.showOpenDialog(ownerStage);
        if (selected == null) {
            return false;
        }

        Alert warning = new Alert(Alert.AlertType.WARNING,
                """
                Loading this project will permanently replace all current scenes, assets, and logs.

                Any unsaved changes will be lost and cannot be recovered.

                Continue?""",
                ButtonType.YES, ButtonType.NO);
        warning.setTitle("Load Project");
        warning.setHeaderText("All current data will be overwritten");
        Optional<ButtonType> choice = warning.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.YES) {
            return false;
        }

        Task<Void> task = buildLoadTask(selected.toPath());
        Stage progress = buildProgressDialog(task, ownerStage, "Loading Project…", false);

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progress.close();
            System.out.println("[ProjectManager] Project loaded from: " + selected);
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            progress.close();
            Throwable ex = task.getException();
            System.err.println("[ProjectManager] Load failed: "
                    + (ex != null ? ex.getMessage() : "unknown error"));
        }));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        progress.showAndWait();
        return task.getException() == null && !task.isCancelled();
    }



    private static Task<Void> buildSaveTask(SceneManager sceneManager, Path target) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<Path> dataFiles = listFiles(Paths.get("data"));
                List<Path> assetFiles = listFiles(Paths.get("assets"));
                List<Path> logFiles = listFiles(Paths.get("logs"));
                long total = dataFiles.size() + assetFiles.size() + logFiles.size();
                long done = 0;

                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
                    writeManifest(zos, sceneManager);

                    updateMessage("Saving scene data…");
                    for (Path file : dataFiles) {
                        if (isCancelled()) {
                            return null;
                        }
                        writeZipEntry(zos, Paths.get("data"), "data", file);
                        updateProgress(++done, total);
                    }

                    updateMessage("Saving assets…");
                    for (Path file : assetFiles) {
                        if (isCancelled()) {
                            return null;
                        }
                        writeZipEntry(zos, Paths.get("assets"), "assets", file);
                        updateProgress(++done, total);
                    }

                    updateMessage("Saving logs…");
                    for (Path file : logFiles) {
                        if (isCancelled()) {
                            return null;
                        }
                        writeZipEntry(zos, Paths.get("logs"), "logs", file);
                        updateProgress(++done, total);
                    }

                    updateMessage("Done.");
                }
                return null;
            }
        };
    }

    private static Stage buildProgressDialog(Task<?> task, Stage ownerStage,
            String title, boolean cancellable) {
        ProgressBar bar = new ProgressBar();
        bar.progressProperty().bind(task.progressProperty());
        bar.setPrefWidth(320);

        Label message = new Label("Preparing…");
        message.textProperty().bind(task.messageProperty());
        message.setStyle("-fx-text-fill: #c0c0c0; -fx-font-size: 12px;");

        VBox box = new VBox(10, message, bar);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(20, 24, 20, 24));
        box.setStyle("-fx-background-color: #0d0d1a;");

        Stage dialog = new Stage(StageStyle.UTILITY);
        dialog.initOwner(ownerStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);
        dialog.setResizable(false);

        if (cancellable) {
            dialog.setOnCloseRequest(e -> {
                e.consume();
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Cancel the save in progress?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setTitle("Cancel Save");
                confirm.initOwner(dialog);
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.YES) {
                        task.cancel();
                    }
                });
            });
        } else {
            dialog.setOnCloseRequest(javafx.event.Event::consume);
        }

        dialog.setScene(new Scene(box));
        return dialog;
    }

    /**
     * Deletes all scenes, assets, and logs, returning the application to a clean state.
     * Shows a confirmation warning before proceeding.
     *
     * @return {@code true} if the reset completed, {@code false} if the user cancelled or it failed
     */
    public static boolean resetProject(Stage ownerStage) {
        Alert warning = new Alert(Alert.AlertType.WARNING,
                """
                This will permanently delete ALL scenes, assets, and logs.

                The application will return to a completely clean state.
                This cannot be undone.

                Continue?""",
                ButtonType.YES, ButtonType.NO);
        warning.setTitle("Reset Project");
        warning.setHeaderText("All data will be permanently deleted");
        Optional<ButtonType> choice = warning.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.YES) {
            return false;
        }

        try {
            deleteDirectory(Paths.get("data"));
            deleteDirectory(Paths.get("assets"));
            deleteDirectory(Paths.get("logs"));
            return true;
        } catch (IOException e) {
            System.err.println("[ProjectManager] Reset failed: " + e.getMessage());
            return false;
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("[ProjectManager] Could not delete: " + path
                            + " — " + e.getMessage());
                }
            });
        }
    }

    private static Task<Void> buildLoadTask(Path source) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                Path workDir = Paths.get(System.getProperty("user.dir"))
                        .toAbsolutePath().normalize();
                try (ZipFile zipFile = new ZipFile(source.toFile())) {
                    long total = zipFile.size();
                    long done = 0;
                    updateMessage("Extracting files…");
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        Path target = workDir.resolve(entry.getName()).normalize();
                        if (!target.startsWith(workDir)) {
                            continue; // skip path-traversal entries
                        }
                        if (entry.isDirectory()) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());
                            try (var in = zipFile.getInputStream(entry)) {
                                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                        updateProgress(++done, total);
                    }
                }
                updateMessage("Done.");
                return null;
            }
        };
    }

    private static List<Path> listFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, Path sourceDir,
            String zipRoot, Path file) throws IOException {
        String entryName = zipRoot + "/"
                + sourceDir.relativize(file).toString().replace('\\', '/');
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
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
}
