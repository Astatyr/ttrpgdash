package ttrpgdash;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ttrpgdash.map.MapCanvas;
import ttrpgdash.model.GameState;

/**
 * The player-facing window showing a read-only map and a player avatar bar.
 *
 * Displays the same map and tokens as the DM view but disables all editing
 * interactions. Players can still pan and zoom.
 * Call {@link #refresh()} whenever DM-side state changes.
 */
public class PlayerView {

    private final Stage stage;
    private final MapCanvas mapCanvas;
    private final PlayerBar playerBar;

    /**
     * Creates and configures the player view window without showing it.
     * Call {@link #show()} to display it.
     */
    public PlayerView(GameState gameState, Stage ownerStage) {
        this.mapCanvas = new MapCanvas(gameState, true);
        this.playerBar = new PlayerBar(gameState);

        stage = new Stage();
        stage.setTitle("TTRPG Dash — Player View");
        stage.initOwner(ownerStage);

        BorderPane root = new BorderPane();
        root.setCenter(mapCanvas);
        root.setBottom(playerBar);
        root.setStyle("-fx-background-color: #0d0d1a;");

        Scene scene = new Scene(root, 1280, 800);
        scene.setFill(Color.rgb(13, 13, 26));

        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
    }

    /**
     * Shows the window and loads the current state into the canvas and player bar.
     */
    public void show() {
        stage.show();
        playerBar.prefHeightProperty().bind(stage.getScene().heightProperty().multiply(0.2));
        mapCanvas.reloadFromState();
        playerBar.refresh();
    }

    /**
     * Syncs the canvas tokens and player bar to the latest GameState.
     * No-op if the window is not currently visible.
     */
    public void refresh() {
        if (stage.isShowing()) {
            mapCanvas.syncTokens();
            playerBar.refresh();
        }
    }

    public boolean isShowing() {
        return stage.isShowing();
    }
}
