package ttrpgdash;

import java.io.File;

import javafx.geometry.Orientation;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ttrpgdash.entity.Entity;
import ttrpgdash.entity.SidebarPanel;
import ttrpgdash.entity.StatusEffect;
import ttrpgdash.map.MapCanvas;
import ttrpgdash.map.Token;
import ttrpgdash.music.MusicController;
import ttrpgdash.player.PlayerView;
import ttrpgdash.scene.SceneEntry;
import ttrpgdash.scene.SceneManager;
import ttrpgdash.scene.ScenePanel;
import ttrpgdash.scene.SceneState;
import ttrpgdash.scene.SceneStateManager;
import ttrpgdash.util.FileHelper;

/**
 * The DM's main window. Assembles the menu bar, sidebar, map canvas,
 * scene panel, and status bar. Manages scene switching and music.
 */
public class MainWindow {

    private final SceneManager sceneManager;
    private final MusicController musicController = new MusicController();
    private Stage stage;

    private SceneState sceneState;
    private MapCanvas mapCanvas;
    private SidebarPanel sidebarPanel;
    private ScenePanel scenePanel;
    private SplitPane splitPane;

    private final Label statusLabel = new Label("Ready");
    private PlayerView playerView;

    /**
     * Initialises the window for the given scene manager.
     * Call {@link #show(Stage)} afterwards to display the window.
     */
    public MainWindow(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.sceneState = SceneStateManager.loadScene(sceneManager.getActiveSceneId());
        buildComponents();
    }

    /**
     * Builds and displays the main window on the given stage.
     */
    public void show(Stage stage) {
        this.stage = stage;

        scenePanel.setOwnerStage(stage);
        wireSidebar();
        wireMapCanvas();

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
        root.setTop(topBar);
        root.setCenter(splitPane);
        root.setRight(scenePanel);
        root.setBottom(statusBar);
        root.setStyle("-fx-background-color: #0d0d1a;");

        Scene scene = new Scene(root, 1440, 800);
        scene.setFill(Color.rgb(13, 13, 26));

        stage.setScene(scene);
        stage.setTitle("TTRPG Dash — DM View");
        stage.setMinWidth(900);
        stage.setMinHeight(500);

        stage.show();

        mapCanvas.reloadFromState();
        sidebarPanel.refresh();
        setStatus("Scene: " + activeSceneName());
    }

    private void buildComponents() {
        mapCanvas = new MapCanvas(sceneState);
        sidebarPanel = new SidebarPanel(sceneState);
        scenePanel = new ScenePanel(sceneManager, musicController, sceneState);
        wireScenePanel();
    }

    private void wireSidebar() {
        sidebarPanel.setOwnerStage(stage);
        sidebarPanel.setOnPlaceEntity(entity -> {
            mapCanvas.setPendingEntity(entity);
            setStatus("Click on the map to place: " + entity.getName());
        });
        sidebarPanel.setOnDetailsEntity(this::showDetailsPopup);
        sidebarPanel.setOnEntitiesChanged(() -> {
            mapCanvas.syncTokens();
            refreshPlayerView();
            setStatus("Entities updated.");
        });
    }

    private void wireMapCanvas() {
        mapCanvas.setOnTokenRightClick((token, point) ->
                showTokenContextMenu(token, point.getX(), point.getY()));
        mapCanvas.setOnTokensChanged(this::refreshPlayerView);
    }

    private void wireScenePanel() {
        scenePanel.setOnSceneSwitch(this::switchToScene);
        scenePanel.setOnSceneAdd(this::addScene);
        scenePanel.setOnSceneMove((id, delta) -> {
            sceneManager.moveScene(id, delta);
            SceneStateManager.saveMaster(sceneManager);
            scenePanel.refreshSceneList();
        });
        scenePanel.setOnSceneRename((id, name) -> {
            sceneManager.getById(id).ifPresent(e -> e.setName(name));
            SceneStateManager.saveMaster(sceneManager);
            scenePanel.refreshSceneList();
            scenePanel.refreshMusic(sceneState);
        });
        scenePanel.setOnSceneDelete(this::deleteScene);
    }

    private void switchToScene(String sceneId) {
        if (sceneId.equals(sceneManager.getActiveSceneId())) {
            return;
        }

        sceneState.save();
        musicController.stopAll();

        sceneState = SceneStateManager.loadScene(sceneId);
        sceneManager.setActiveSceneId(sceneId);
        SceneStateManager.saveMaster(sceneManager);

        mapCanvas = new MapCanvas(sceneState);
        sidebarPanel = new SidebarPanel(sceneState);
        wireSidebar();
        wireMapCanvas();

        double divPos = splitPane.getDividerPositions()[0];
        splitPane.getItems().setAll(sidebarPanel, mapCanvas);
        splitPane.setDividerPositions(divPos);

        mapCanvas.reloadFromState();
        sidebarPanel.refresh();

        scenePanel.refreshSceneList();
        scenePanel.refreshMusic(sceneState);

        if (playerView != null && playerView.isShowing()) {
            SceneState newGs = sceneState;
            playerView.fadeTransitionTo(() -> playerView.refreshScene(newGs));
        }

        setStatus("Scene: " + activeSceneName());
    }

    private void addScene() {
        String id = "scene_" + Long.toHexString(System.currentTimeMillis());
        int order = sceneManager.getScenes().size();
        SceneEntry entry = new SceneEntry(id, "New Scene", order);
        sceneManager.addScene(entry);
        SceneState newGs = SceneStateManager.createNewScene(id);
        newGs.save();
        SceneStateManager.saveMaster(sceneManager);
        switchToScene(id);
    }

    private void deleteScene(String id) {
        if (sceneManager.getScenes().size() <= 1) {
            return;
        }
        boolean wasActive = id.equals(sceneManager.getActiveSceneId());
        sceneManager.removeScene(id);
        if (wasActive) {
            String nextId = sceneManager.getScenes().get(0).getId();
            sceneManager.setActiveSceneId(nextId);
            SceneStateManager.saveMaster(sceneManager);
            switchToScene(nextId);
        } else {
            SceneStateManager.saveMaster(sceneManager);
            scenePanel.refreshSceneList();
        }
    }

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.setStyle("-fx-background-color: #16163a;");

        Menu fileMenu = new Menu("File");

        MenuItem ReplayLog = new MenuItem("Replay Log…");
        ReplayLog.setOnAction(e -> loadLogFromFile());

        fileMenu.getItems().addAll(ReplayLog, new SeparatorMenuItem());

        Menu mapMenu = new Menu("Map");

        MenuItem loadMap = new MenuItem("Load Map PNG…");
        loadMap.setOnAction(e -> loadMapFromFile());

        MenuItem setWidth = new MenuItem("Set Map Width in Feet…");
        setWidth.setOnAction(e -> promptMapWidth());

        MenuItem fitMap = new MenuItem("Fit Map to Window");
        fitMap.setOnAction(e -> {
            mapCanvas.reloadFromState();
            refreshPlayerView();
        });

        MenuItem clearMap = new MenuItem("Clear Map…");
        clearMap.setOnAction(e -> {
            sceneState.clearMapOnly();
            mapCanvas.reloadFromState();
            refreshPlayerView();
            setStatus("Map cleared.");
        });

        CheckMenuItem toggleNames = new CheckMenuItem("Show Names");
        toggleNames.setSelected(true);
        toggleNames.setOnAction(e -> {
            mapCanvas.setNamesVisible(toggleNames.isSelected());
            refreshPlayerView();
        });

        CheckMenuItem toggleStatus = new CheckMenuItem("Show Status Effects");
        toggleStatus.setSelected(true);
        toggleStatus.setOnAction(e -> {
            mapCanvas.setStatusVisible(toggleStatus.isSelected());
            refreshPlayerView();
        });

        mapMenu.getItems().addAll(loadMap, setWidth, new SeparatorMenuItem(), fitMap,
                new SeparatorMenuItem(), clearMap, new SeparatorMenuItem(),
                toggleNames, toggleStatus);

        Menu optionsMenu = new Menu("Options");

        CheckMenuItem enableLog = new CheckMenuItem("Enable Logging");
        enableLog.setSelected(false);
        enableLog.setOnAction(e -> {
            // Handle logging enablement
            /*
            TODO for logging:
            - Enable/disable logging should be logged as an action.
            - Log should be disabled when quitting the app.
            - Add / delete player/character
            - player/character movements
            - applying status effects
            - mount or dismount token
            - scene change (as there are multiple scenes the dungeon master can switch to)
            */
        });

        MenuItem clearPositions = new MenuItem("Clear Token Positions");
        clearPositions.setOnAction(e -> {
            sceneState.clearMapPositions();
            mapCanvas.syncTokens();
            refreshPlayerView();
            setStatus("Token positions cleared.");
        });

        MenuItem clearAll = new MenuItem("Clear All…");
        clearAll.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Remove all entities and reset the map for this scene?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Clear All");
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    sceneState.clearAll();
                    mapCanvas.reloadFromState();
                    sidebarPanel.refresh();
                    refreshPlayerView();
                    setStatus("Scene cleared.");
                }
            });
        });

        optionsMenu.getItems().addAll(enableLog, new SeparatorMenuItem(), clearPositions,
                new SeparatorMenuItem(), clearAll);

        Menu viewMenu = new Menu("View");
        MenuItem openPlayerView = new MenuItem("Open Player View");
        openPlayerView.setOnAction(e -> {
            if (playerView == null) {
                playerView = new PlayerView(stage);
            }
            playerView.show(sceneState);
        });
        viewMenu.getItems().add(openPlayerView);

        Menu UndoMenu = new Menu("Undo");
        MenuItem undoAction = new MenuItem("Undo Last Action");
        undoAction.setOnAction(e -> {
            // Implement undo functionality
            /*
            TODO: undo is based on the last action in the log - up to the point when the log was enabled.
            After an action is undone and replaced by log-able action, the undone action should be removed from the log
            to prevent redo of an invalid action.
            */
        });

        Menu RedoMenu = new Menu("Redo");
        MenuItem redoAction = new MenuItem("Redo Last Action");
        redoAction.setOnAction(e -> {
            // Implement redo functionality
        });

        bar.getMenus().addAll(fileMenu, mapMenu, optionsMenu, viewMenu, UndoMenu, RedoMenu);
        return bar;
    }

    private void loadLogFromFile() {
        // Implement log replay functionality
    }

    private void loadMapFromFile() {
        File file = FileHelper.browseForMap(stage);
        if (file == null) {
            return;
        }
        String mapPath = FileHelper.toRelativePath(file);
        sceneState.setMapImagePath(mapPath);
        mapCanvas.loadMap(mapPath);
        refreshPlayerView();
        setStatus("Map loaded: " + file.getName());
    }

    private void promptMapWidth() {
        TextInputDialog dialog = new TextInputDialog(
                String.valueOf((int) sceneState.getMapWidthInFeet()));
        dialog.setTitle("Map Width");
        dialog.setHeaderText("How wide is the map in feet?");
        dialog.setContentText("Width in feet:");
        dialog.showAndWait().ifPresent(val -> {
            try {
                double feet = Double.parseDouble(val);
                sceneState.setMapWidthInFeet(feet);
                mapCanvas.onMapWidthChanged();
                setStatus("Map width set to " + feet + " ft.");
            } catch (NumberFormatException ex) {
                setStatus("Invalid value — map width unchanged.");
            }
        });
    }

    private void showTokenContextMenu(Token token, double screenX, double screenY) {
        ContextMenu menu = new ContextMenu();

        MenuItem nameItem = new MenuItem(token.getEntity().getName());
        nameItem.setDisable(true);
        menu.getItems().add(nameItem);
        menu.getItems().add(new SeparatorMenuItem());

        Menu statusMenu = new Menu("Buff / Debuff");
        for (String s : StatusEffect.ALL) {
            boolean active = token.getEntity().getStatusEffects().contains(s);
            MenuItem item = new MenuItem((active ? "✓ " : "    ") + s);
            item.setOnAction(e -> {
                if (active) {
                    token.getEntity().removeStatusEffect(s);
                } else {
                    token.getEntity().addStatusEffect(s);
                }
                sceneState.entityChanged();
                mapCanvas.repaint();
                refreshPlayerView();
            });
            statusMenu.getItems().add(item);
        }
        menu.getItems().add(statusMenu);

        MenuItem removeFromMap = new MenuItem("Remove from Map");
        removeFromMap.setOnAction(e -> {
            mapCanvas.removeSelectedToken();
            sidebarPanel.disarmCard();
            refreshPlayerView();
            setStatus(token.getEntity().getName() + " removed from map.");
        });
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(removeFromMap);

        MenuItem viewDetails = new MenuItem("View Details");
        viewDetails.setOnAction(e -> showDetailsPopup(token.getEntity()));
        menu.getItems().add(viewDetails);

        menu.show(mapCanvas.getScene().getWindow(), screenX, screenY);
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

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void refreshPlayerView() {
        if (playerView != null && playerView.isShowing()) {
            playerView.refresh();
        }
    }

    private String activeSceneName() {
        return sceneManager.getActiveEntry().map(SceneEntry::getName).orElse("Unknown");
    }
}
