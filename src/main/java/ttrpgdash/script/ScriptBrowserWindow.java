package ttrpgdash.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Non-blocking window listing all {@code .lua} files found in {@code data/scripts/}.
 *
 * The DM can click a script to run it (output goes to the output panel), refresh
 * the list after adding new files, and leave the window open while working.
 * Only one instance is shown at a time — calling {@link #show()} brings it to front.
 */
public final class ScriptBrowserWindow {

    private static final String SCRIPTS_DIR = "data/scripts";

    private final ScriptEngine scriptEngine;
    private final Stage stage;
    private final ListView<Path> fileList = new ListView<>();
    private final Label statusLabel = new Label();

    /**
     * Creates the script browser window without showing it.
     */
    public ScriptBrowserWindow(Stage ownerStage, ScriptEngine scriptEngine) {
        this.scriptEngine = scriptEngine;

        fileList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        });
        fileList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                runSelected();
            }
        });
        fileList.setStyle("-fx-background-color: #0d0d1a; -fx-control-inner-background: #0d0d1a;"
                + " -fx-text-fill: #c0c0c0;");

        Button runBtn = new Button("▶  Run");
        runBtn.setStyle("-fx-background-color: #2a2a4a; -fx-text-fill: #6fa8dc;"
                + " -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 4 14 4 14;");
        runBtn.setOnAction(e -> runSelected());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666;"
                + " -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 10 4 10;"
                + " -fx-border-color: #333; -fx-border-radius: 3;");
        refreshBtn.setOnAction(e -> refresh());

        statusLabel.setStyle("-fx-text-fill: #555; -fx-font-size: 10px;");

        HBox footer = new HBox(8, runBtn, refreshBtn, statusLabel);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 8, 6, 8));
        footer.setStyle("-fx-background-color: #0a0a16;");

        VBox root = new VBox(fileList, footer);
        VBox.setVgrow(fileList, Priority.ALWAYS);
        root.setStyle("-fx-background-color: #0d0d1a;");

        Scene scene = new Scene(root, 340, 400);
        scene.setFill(Color.rgb(13, 13, 26));

        stage = new Stage();
        stage.initOwner(ownerStage);
        stage.setTitle("Script Browser — data/scripts/");
        stage.setMinWidth(240);
        stage.setMinHeight(200);
        stage.setScene(scene);

        refresh();
    }

    /**
     * Shows the window, or brings it to the front if already visible.
     */
    public void show() {
        if (stage.isShowing()) {
            stage.toFront();
        } else {
            stage.show();
        }
    }

    /**
     * Re-scans {@code data/scripts/} and repopulates the list.
     */
    public void refresh() {
        fileList.getItems().clear();
        Path dir = Paths.get(SCRIPTS_DIR);
        if (!Files.isDirectory(dir)) {
            statusLabel.setText("data/scripts/ not found");
            return;
        }
        try (var stream = Files.list(dir)) {
            List<Path> scripts = stream
                    .filter(p -> p.getFileName().toString().endsWith(".lua"))
                    .sorted()
                    .collect(Collectors.toList());
            fileList.getItems().addAll(scripts);
            statusLabel.setText(scripts.size() + " script" + (scripts.size() == 1 ? "" : "s"));
        } catch (IOException e) {
            statusLabel.setText("Error reading scripts folder");
            System.err.println("[ScriptBrowserWindow] " + e.getMessage());
        }
    }

    private void runSelected() {
        Path selected = fileList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            scriptEngine.execute(selected);
        }
    }
}
