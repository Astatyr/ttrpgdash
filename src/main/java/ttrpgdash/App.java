package ttrpgdash;

import javafx.application.Application;
import javafx.stage.Stage;
import ttrpgdash.model.GameState;
import ttrpgdash.util.JsonStateManager;

/**
 * Application entry point.
 *
 * Loads GameState from data/state.json (or creates a fresh one),
 * then launches the main DM window.
 *
 * Run with:  ./gradlew run
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Load persisted state (or start fresh if none exists)
        GameState gameState = JsonStateManager.load();

        // Build and show the main DM window
        new MainWindow(primaryStage, gameState);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
