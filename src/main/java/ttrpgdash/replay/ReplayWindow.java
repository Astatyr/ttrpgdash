package ttrpgdash.replay;

import java.io.IOException;
import java.nio.file.Path;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ttrpgdash.map.MapCanvas;
import ttrpgdash.player.PlayerBar;
import ttrpgdash.replay.LogReplayController.RefreshHint;
import ttrpgdash.scene.SceneState;

/**
 * A standalone window that plays back a logged session step by step.
 *
 * Multiple instances can coexist, each bound to a different log file.
 * Layout mirrors {@link ttrpgdash.player.PlayerView}: map fills the centre,
 * a player bar sits at the bottom, and replay controls (play/pause + slider)
 * sit below the player bar.
 *
 * Use {@link #open(Path, Stage)} to construct and show the window.
 */
public final class ReplayWindow {

    private final LogReplayController controller;
    private final BorderPane content = new BorderPane();
    private final Stage stage;

    private final HBox controlStrip;
    private final VBox bottomContainer = new VBox();

    private MapCanvas mapCanvas;
    private PlayerBar playerBar;
    private String displayedSceneId;

    private final Button playBtn;
    private final Slider slider;
    private final Label stepLabel;
    private boolean sliderUpdating = false;

    private ReplayWindow(Path logFile, Stage ownerStage) throws IOException {
        controller = new LogReplayController(logFile);

        content.setStyle("-fx-background-color: #0d0d1a;");

        // ── Controls ──────────────────────────────────────────────────────────
        playBtn = new Button("▶");
        playBtn.setStyle("-fx-background-color: #2a2a4a; -fx-text-fill: #6fa8dc; "
                + "-fx-font-size: 14px; -fx-padding: 4 14 4 14; -fx-cursor: hand;");
        playBtn.setOnAction(e -> togglePlay());

        int maxStep = Math.max(0, controller.getTotalSteps() - 1);
        slider = new Slider(0, maxStep, 0);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setDisable(maxStep == 0);
        slider.valueProperty().addListener((obs, oldV, newV) -> {
            if (!sliderUpdating) {
                controller.goToStep(newV.intValue());
            }
        });
        HBox.setHgrow(slider, Priority.ALWAYS);

        stepLabel = new Label("0 / " + controller.getTotalSteps());
        stepLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-min-width: 60px;");

        controlStrip = new HBox(8, playBtn, slider, stepLabel);
        controlStrip.setAlignment(Pos.CENTER_LEFT);
        controlStrip.setPadding(new Insets(4, 8, 6, 8));
        controlStrip.setStyle("-fx-background-color: #0d0d1a;"
                + " -fx-border-color: #1a1a3a; -fx-border-width: 1 0 0 0;");

        content.setBottom(bottomContainer);

        // ── Stage ─────────────────────────────────────────────────────────────
        stage = new Stage();
        stage.initOwner(ownerStage);
        String sessionName = logFile.getParent() != null
                ? logFile.getParent().getFileName().toString()
                : logFile.getFileName().toString();
        stage.setTitle("Replay — " + sessionName);
        stage.setOnCloseRequest(e -> controller.pause());

        Scene scene = new Scene(content, 1280, 820);
        scene.setFill(Color.rgb(13, 13, 26));
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(520);

        // ── Wire ─────────────────────────────────────────────────────────────
        controller.setOnStepChanged(this::handleRefresh);

        displayedSceneId = controller.getActiveSceneId();
        rebuildCanvas();
        updateControls();
    }

    /**
     * Creates and immediately shows a replay window for the given log file.
     * Logs an error and returns silently if the file cannot be parsed.
     */
    public static void open(Path logFile, Stage ownerStage) {
        try {
            new ReplayWindow(logFile, ownerStage).show();
        } catch (IOException e) {
            System.err.println("[ReplayWindow] Failed to open log: " + e.getMessage());
        }
    }

    private void show() {
        stage.show();
        mapCanvas.reloadFromState();
        playerBar.refresh();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void rebuildCanvas() {
        SceneState state = controller.getActiveState();
        if (state == null) {
            return;
        }
        displayedSceneId = controller.getActiveSceneId();
        mapCanvas = new MapCanvas(state, true);
        playerBar = new PlayerBar(state);
        content.setCenter(mapCanvas);
        bottomContainer.getChildren().setAll(playerBar, controlStrip);
        playerBar.prefHeightProperty().bind(stage.heightProperty().multiply(0.18));
    }

    private void handleRefresh(RefreshHint hint) {
        if (hint == RefreshHint.SCENE_SWITCH
                || !controller.getActiveSceneId().equals(displayedSceneId)) {
            rebuildCanvas();
        }
        if (hint == RefreshHint.MAP_RELOAD || hint == RefreshHint.SCENE_SWITCH) {
            mapCanvas.reloadFromState();
        } else {
            mapCanvas.syncTokens();
        }
        playerBar.refresh();
        updateControls();
    }

    private void togglePlay() {
        if (controller.isPlaying()) {
            controller.pause();
            playBtn.setText("▶");
        } else {
            if (controller.getCurrentStep() >= controller.getTotalSteps() - 1) {
                controller.goToStep(0);
            }
            controller.play();
            playBtn.setText("⏸");
        }
    }

    private void updateControls() {
        int display = controller.getCurrentStep() + 1;
        stepLabel.setText(display + " / " + controller.getTotalSteps());

        sliderUpdating = true;
        slider.setValue(Math.max(0, controller.getCurrentStep()));
        sliderUpdating = false;

        if (!controller.isPlaying()) {
            playBtn.setText("▶");
        }
    }
}
