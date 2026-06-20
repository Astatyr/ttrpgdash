package ttrpgdash;

import javafx.application.Application;
import javafx.stage.Stage;
import ttrpgdash.scene.SceneManager;
import ttrpgdash.scene.SceneStateManager;

/**
 * Application entry point.
 *
 * Loads the SceneManager from data/scenes.json (migrating from data/state.json on first
 * run), then launches the main DM window.
 *
 * Run with:  ./gradlew run
 */
public class App extends Application {

    /** Application version — keep in sync with {@code version} in build.gradle. */
    public static final String VERSION = "1.0";

    @Override
    public void start(Stage primaryStage) {
        SceneManager sceneManager = SceneStateManager.loadMaster();
        SceneStateManager.pruneOrphanedSceneFiles(sceneManager);
        new MainWindow(sceneManager).show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
