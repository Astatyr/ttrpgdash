package ttrpgdash.player;

import javafx.animation.FadeTransition;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import ttrpgdash.map.MapCanvas;
import ttrpgdash.scene.SceneState;

/**
 * The player-facing window showing a read-only map and a player avatar bar.
 *
 * Displays the same map and tokens as the DM view but disables all editing
 * interactions. Players can still pan and zoom.
 * Call {@link #refresh()} whenever DM-side state changes.
 * Call {@link #fadeTransitionTo(Runnable)} when switching scenes to get a
 * 0.5-second black-overlay crossfade.
 */
public class PlayerView {

    private static final int FADE_MS = 250;

    private final Stage stage;
    private final BorderPane content;
    private final Rectangle overlay;
    private MapCanvas mapCanvas;
    private PlayerBar playerBar;

    /**
     * Creates and configures the player view window without showing it.
     * Call {@link #show(sceneState)} to display it.
     */
    public PlayerView(Stage ownerStage) {
        content = new BorderPane();
        content.setStyle("-fx-background-color: #0d0d1a;");

        overlay = new Rectangle();
        overlay.setFill(Color.BLACK);
        overlay.setOpacity(0);
        overlay.setMouseTransparent(true);

        StackPane root = new StackPane(content, overlay);
        overlay.widthProperty().bind(root.widthProperty());
        overlay.heightProperty().bind(root.heightProperty());

        stage = new Stage();
        stage.setTitle("TTRPG Dash — Player View");
        stage.initOwner(ownerStage);

        Scene scene = new Scene(root, 1280, 800);
        scene.setFill(Color.rgb(13, 13, 26));
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
    }

    /**
     * Shows the window and loads the given game state into the canvas and player bar.
     */
    public void show(SceneState sceneState) {
        buildContent(sceneState);
        stage.show();
        bindBarHeight();
        mapCanvas.reloadFromState();
        playerBar.refresh();
    }

    /**
     * Syncs tokens and the player bar to the latest sceneState.
     * No-op if the window is not currently visible.
     */
    public void refresh() {
        if (stage.isShowing()) {
            mapCanvas.syncTokens();
            playerBar.refresh();
        }
    }

    /**
     * Fades the screen to black (250 ms), runs {@code onMidpoint} to swap scene content,
     * then fades back in (250 ms). Total transition: 500 ms.
     */
    public void fadeTransitionTo(Runnable onMidpoint) {
        FadeTransition out = new FadeTransition(Duration.millis(FADE_MS), overlay);
        out.setFromValue(0);
        out.setToValue(1);
        out.setOnFinished(e -> {
            onMidpoint.run();
            FadeTransition in = new FadeTransition(Duration.millis(FADE_MS), overlay);
            in.setFromValue(1);
            in.setToValue(0);
            in.play();
        });
        out.play();
    }

    /**
     * Rebuilds the canvas and player bar for a new scene's sceneState.
     * Intended to be called inside {@link #fadeTransitionTo(Runnable)}.
     */
    public void refreshScene(SceneState newGameState) {
        buildContent(newGameState);
        bindBarHeight();
        mapCanvas.reloadFromState();
        playerBar.refresh();
    }

    public boolean isShowing() {
        return stage.isShowing();
    }

    private void buildContent(SceneState sceneState) {
        mapCanvas = new MapCanvas(sceneState, true);
        playerBar = new PlayerBar(sceneState);
        content.setCenter(mapCanvas);
        content.setBottom(playerBar);
    }

    private void bindBarHeight() {
        playerBar.prefHeightProperty().bind(stage.getScene().heightProperty().multiply(0.2));
    }
}
