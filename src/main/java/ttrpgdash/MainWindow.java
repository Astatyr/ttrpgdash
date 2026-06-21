package ttrpgdash;

import java.io.File;

import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ttrpgdash.entity.Entity;
import ttrpgdash.entity.SidebarPanel;
import ttrpgdash.entity.StatusEffect;
import ttrpgdash.log.LogController;
import ttrpgdash.log.RedoHandler;
import ttrpgdash.log.UndoHandler;
import ttrpgdash.map.MapCanvas;
import ttrpgdash.map.MapController;
import ttrpgdash.map.Token;
import ttrpgdash.player.PlayerView;
import ttrpgdash.project.ProjectManager;
import ttrpgdash.replay.ReplayWindow;
import ttrpgdash.script.ScriptEngine;
import ttrpgdash.scene.SceneController;
import ttrpgdash.scene.SceneEntry;
import ttrpgdash.scene.SceneManager;
import ttrpgdash.scene.ScenePanel;
import ttrpgdash.scene.SceneState;
import ttrpgdash.scene.SceneStateManager;
import ttrpgdash.util.FileHelper;

/**
 * Composition root for the DM window.
 * Responsible for layout assembly, UI dialogs, and wiring the controllers.
 * Contains no business logic — all mutations go through SceneController or MapController.
 */
public final class MainWindow {

    private Stage stage;
    private SplitPane splitPane;
    private ScenePanel scenePanel;
    private final Label statusLabel = new Label("Ready");
    private final OutputPanel outputPanel = new OutputPanel();
    private PlayerView playerView;

    private final SceneController sceneController;
    private MapController mapController;
    private final LogController logController = new LogController();
    private UndoHandler undoHandler;
    private RedoHandler redoHandler;
    private ScriptEngine scriptEngine;

    /**
     * Creates the window for the given scene manager.
     * Call {@link #show(Stage)} to display it.
     */
    public MainWindow(SceneManager sceneManager) {
        this.sceneController = new SceneController(sceneManager);
    }

    /**
     * Assembles the layout, wires all controllers, and shows the stage.
     */
    public void show(Stage stage) {
        this.stage = stage;

        SceneState state = sceneController.getActiveState();
        MapCanvas mapCanvas = new MapCanvas(state);
        SidebarPanel sidebarPanel = new SidebarPanel(state);

        mapController = new MapController(stage);
        mapController.setLogController(logController);
        mapController.attachScene(state, mapCanvas, sidebarPanel);
        mapController.setOnStateChanged(this::refreshPlayerView);
        mapController.setOnTokenRightClick(this::showTokenContextMenu);
        mapController.setOnStatusMessage(this::setStatus);

        sceneController.setLogController(logController);
        scenePanel = new ScenePanel(sceneController.getSceneManager(),
                sceneController.getMusicController(), state);
        wireScenePanel(scenePanel);
        sceneController.setOnSceneChanged(this::onSceneChanged);
        sceneController.setOnSceneListChanged(() -> scenePanel.refreshSceneList());
        sceneController.setOnStateChanged(() -> {
            mapController.getMapCanvas().reloadFromState();
            mapController.getSidebarPanel().refresh();
            refreshPlayerView();
        });
        sceneController.setOnEntitiesChanged(() -> {
            mapController.getMapCanvas().syncTokens();
            mapController.getSidebarPanel().refresh();
            refreshPlayerView();
        });

        undoHandler = new UndoHandler(mapController, sceneController);
        redoHandler = new RedoHandler(mapController, sceneController);

        sidebarPanel.setOwnerStage(stage);
        sidebarPanel.setOnDetailsEntity(this::showDetailsPopup);
        wireEntityLogging(sidebarPanel);

        splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(sidebarPanel, mapCanvas);
        splitPane.setDividerPositions(0.22);
        SplitPane.setResizableWithParent(sidebarPanel, false);

        statusLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-padding: 3 8 3 8;");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setStyle(
                "-fx-background-color: #0d0d1a; -fx-border-color: #1a1a3a; -fx-border-width: 1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        MenuBar menuBar = buildMenuBar();
        Label versionLabel = new Label("v" + App.VERSION);
        versionLabel.setStyle("-fx-text-fill: #444; -fx-font-size: 11px; -fx-padding: 4 12 4 8;");
        HBox topBar = new HBox(menuBar, versionLabel);
        HBox.setHgrow(menuBar, Priority.ALWAYS);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #16163a;");

        BorderPane root = new BorderPane();
        VBox bottomArea = new VBox(outputPanel, statusBar);
        root.setTop(topBar);
        root.setCenter(splitPane);
        root.setRight(scenePanel);
        root.setBottom(bottomArea);
        root.setStyle("-fx-background-color: #0d0d1a;");

        Scene scene = new Scene(root, 1440, 800);
        scene.setFill(Color.rgb(13, 13, 26));
        scene.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                performUndo();
            } else if (e.isControlDown() && e.getCode() == KeyCode.Y) {
                performRedo();
            }
        });

        scriptEngine = new ScriptEngine(outputPanel, sceneController, mapController, logController);
        scriptEngine.loadProjectScript();

        // Defensive reset: ensure logging is always off at startup
        logController.disable();

        stage.setOnCloseRequest(e -> logController.disable());

        stage.setScene(scene);
        stage.setTitle("TTRPG Dash — DM View");
        stage.setMinWidth(900);
        stage.setMinHeight(500);
        stage.show();

        mapCanvas.reloadFromState();
        sidebarPanel.refresh();
        setStatus("Scene: " + sceneController.getActiveSceneName());
    }

    private void wireScenePanel(ScenePanel panel) {
        panel.setOnSceneSwitch(sceneController::switchToScene);
        panel.setOnSceneAdd(sceneController::addScene);
        panel.setOnSceneMove(sceneController::moveScene);
        panel.setOnSceneRename((id, newName) -> {
            String oldName = sceneController.getSceneManager()
                    .getById(id).map(SceneEntry::getName).orElse("Unknown");
            logController.logRenameScene(id, oldName, newName);
            sceneController.renameScene(id, newName);
        });
        panel.setOnSceneDelete(sceneController::deleteScene);
        panel.setOnMusicChanged(() -> sceneController.getActiveState().entityChanged());
    }

    private void wireEntityLogging(SidebarPanel sidebar) {
        sidebar.setOnEntityAdded(e -> logController.logAddEntity(e));
        sidebar.setOnEntityRemoved(e -> logController.logRemoveEntity(e));
    }

    private void onSceneChanged(SceneState newState) {
        MapCanvas newCanvas = new MapCanvas(newState);
        SidebarPanel newSidebar = new SidebarPanel(newState);
        newSidebar.setOwnerStage(stage);
        newSidebar.setOnDetailsEntity(this::showDetailsPopup);
        wireEntityLogging(newSidebar);

        mapController.attachScene(newState, newCanvas, newSidebar);

        double divPos = splitPane.getDividerPositions()[0];
        splitPane.getItems().setAll(newSidebar, newCanvas);
        splitPane.setDividerPositions(divPos);

        newCanvas.reloadFromState();
        newSidebar.refresh();

        scenePanel.refreshSceneList();
        scenePanel.refreshMusic(newState);

        if (playerView != null && playerView.isShowing()) {
            playerView.fadeTransitionTo(() -> playerView.refreshScene(newState));
        }

        setStatus("Scene: " + sceneController.getActiveSceneName());
    }

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.setStyle("-fx-background-color: #16163a;");


        Menu fileMenu = new Menu("File");

        CheckMenuItem enableLog = new CheckMenuItem("Enable Logging");
        enableLog.setSelected(false);
        enableLog.setOnAction(e -> {
            if (enableLog.isSelected()) {
                logController.enable(sceneController.getSceneManager(),
                        sceneController.getActiveState());
                setStatus("Logging started.");
            } else {
                logController.disable();
                setStatus("Logging stopped.");
            }
        });

        MenuItem replayLog = new MenuItem("Replay Log…");
        replayLog.setOnAction(e -> loadLogFromFile());

        MenuItem saveProject = new MenuItem("Save Project…");
        saveProject.setOnAction(e ->
                ProjectManager.saveProject(sceneController.getSceneManager(), stage));

        MenuItem loadProject = new MenuItem("Load Project…");
        loadProject.setOnAction(e -> {
            if (ProjectManager.loadProject(stage)) {
                rebuildWith(SceneStateManager.loadMaster());
            }
        });

        MenuItem clearAll = new MenuItem("Clear All…");
        clearAll.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Remove all entities and reset the map for this scene?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Clear All");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    sceneController.clearAll();
                    setStatus("Scene cleared.");
                }
            });
        });

        MenuItem resetProject = new MenuItem("Reset Project…");
        resetProject.setOnAction(e -> {
            if (ProjectManager.resetProject(stage)) {
                rebuildWith(SceneStateManager.loadMaster());
            }
        });

        fileMenu.getItems().addAll(enableLog, replayLog, new SeparatorMenuItem(),
                saveProject, loadProject, resetProject, new SeparatorMenuItem(), clearAll);


        Menu mapMenu = new Menu("Map");

        MenuItem loadMap = new MenuItem("Load Map PNG…");
        loadMap.setOnAction(e -> mapController.browseAndLoadMap());

        MenuItem setWidth = new MenuItem("Set Map Width in Feet…");
        setWidth.setOnAction(e -> promptMapWidth());

        MenuItem fitMap = new MenuItem("Fit Map to Window");
        fitMap.setOnAction(e -> mapController.fitMap());

        MenuItem clearMap = new MenuItem("Clear Map…");
        clearMap.setOnAction(e -> mapController.clearMap());

        MenuItem clearPositions = new MenuItem("Clear Token Positions");
        clearPositions.setOnAction(e -> mapController.clearTokenPositions());

        CheckMenuItem toggleNames = new CheckMenuItem("Show Names");
        toggleNames.setSelected(true);
        toggleNames.setOnAction(e -> mapController.setNamesVisible(toggleNames.isSelected()));

        CheckMenuItem toggleStatus = new CheckMenuItem("Show Status Effects");
        toggleStatus.setSelected(true);
        toggleStatus.setOnAction(e -> mapController.setStatusVisible(toggleStatus.isSelected()));

        mapMenu.getItems().addAll(loadMap, setWidth, new SeparatorMenuItem(), fitMap,
                new SeparatorMenuItem(), clearMap, clearPositions,
                new SeparatorMenuItem(), toggleNames, toggleStatus);


        Menu viewMenu = new Menu("View");
        MenuItem openPlayerView = new MenuItem("Open Player View");
        openPlayerView.setOnAction(e -> {
            if (playerView == null) {
                playerView = new PlayerView(stage);
            }
            playerView.show(sceneController.getActiveState());
        });
        viewMenu.getItems().add(openPlayerView);


        Menu undoMenu = new Menu("Undo");
        undoMenu.setOnShowing(e -> {
            undoMenu.hide();
            performUndo();
        });

        Menu redoMenu = new Menu("Redo");
        redoMenu.setOnShowing(e -> {
            redoMenu.hide();
            performRedo();
        });

        bar.getMenus().addAll(fileMenu, mapMenu, viewMenu, undoMenu, redoMenu);
        return bar;
    }

    /** Builds and shows the token right-click context menu. All actions delegate to MapController. */
    private void showTokenContextMenu(Token token, Point2D screenPoint) {
        ContextMenu menu = new ContextMenu();

        MenuItem nameItem = new MenuItem(token.getEntity().getName());
        nameItem.setDisable(true);
        menu.getItems().add(nameItem);
        menu.getItems().add(new SeparatorMenuItem());

        Menu statusMenu = new Menu("Buff / Debuff");
        for (String s : StatusEffect.ALL) {
            boolean active = token.getEntity().getStatusEffects().contains(s);
            MenuItem item = new MenuItem((active ? "✓ " : "    ") + s);
            item.setOnAction(e -> mapController.toggleStatus(token.getEntity(), s));
            statusMenu.getItems().add(item);
        }
        menu.getItems().add(statusMenu);

        MenuItem removeFromMap = new MenuItem("Remove from Map");
        removeFromMap.setOnAction(e -> {
            mapController.removeToken(token);
            setStatus(token.getEntity().getName() + " removed from map.");
        });
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(removeFromMap);

        MenuItem viewDetails = new MenuItem("View Details");
        viewDetails.setOnAction(e -> showDetailsPopup(token.getEntity()));
        menu.getItems().add(viewDetails);

        menu.show(mapController.getMapCanvas().getScene().getWindow(),
                screenPoint.getX(), screenPoint.getY());
    }

    private void showDetailsPopup(Entity entity) {
        String detailsPath = entity.getDetailsPath();
        if (!FileHelper.fileExists(detailsPath)) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "No Details.png found for " + entity.getName() + ".\n\n"
                    + "Place a Details.png inside the character folder.", ButtonType.OK);
            alert.setTitle("No Details");
            alert.showAndWait();
            return;
        }

        Stage popup = new Stage();
        popup.setTitle("Details — " + entity.getName());
        popup.initOwner(stage);

        Image img = FileHelper.loadImage(detailsPath);
        ImageView view = new ImageView(img);
        view.setPreserveRatio(true);
        view.setFitWidth(Math.min(img.getWidth(), 900));
        view.setFitHeight(Math.min(img.getHeight(), 700));

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);

        Scene scene = new Scene(scroll);
        popup.setScene(scene);
        popup.show();
    }

    private void promptMapWidth() {
        double current = sceneController.getActiveState().getMapWidthInFeet();
        TextInputDialog dialog = new TextInputDialog(String.valueOf((int) current));
        dialog.setTitle("Map Width");
        dialog.setHeaderText("How wide is the map in feet?");
        dialog.setContentText("Width in feet:");
        dialog.showAndWait().ifPresent(val -> {
            try {
                mapController.setMapWidth(Double.parseDouble(val));
            } catch (NumberFormatException ex) {
                setStatus("Invalid value — map width unchanged.");
            }
        });
    }

    private void loadLogFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Session Log");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Log files", "*.log"));
        File logsDir = new File("logs");
        if (logsDir.exists() && logsDir.isDirectory()) {
            chooser.setInitialDirectory(logsDir);
        }
        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }
        ReplayWindow.open(selected.toPath(), stage);
    }

    /**
     * Tears down the current session and rebuilds the window with a fresh scene manager.
     * Closes the player view (releasing its bindings), stops logging, prunes orphaned scene
     * files, then mounts a new MainWindow on the same stage.
     */
    private void rebuildWith(SceneManager newManager) {
        if (playerView != null) {
            playerView.close();
        }
        logController.disable();
        SceneStateManager.pruneOrphanedSceneFiles(newManager);
        new MainWindow(newManager).show(stage);
    }

    private void performUndo() {
        scriptEngine.cancel();
        if (logController.canUndo()) {
            var entry = logController.doUndo();
            if (entry != null) {
                undoHandler.undo(entry);
                setStatus("Undone: " + entry.getEvent().name().replace('_', ' ').toLowerCase());
            }
        } else {
            setStatus("Nothing to undo.");
        }
    }

    private void performRedo() {
        scriptEngine.cancel();
        if (logController.canRedo()) {
            var entry = logController.doRedo();
            if (entry != null) {
                redoHandler.apply(entry);
                setStatus("Redone: " + entry.getEvent().name().replace('_', ' ').toLowerCase());
            }
        } else {
            setStatus("Nothing to redo.");
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
        outputPanel.log("SYSTEM", message);
    }

    private void refreshPlayerView() {
        if (playerView != null && playerView.isShowing()) {
            playerView.refresh();
        }
    }
}
