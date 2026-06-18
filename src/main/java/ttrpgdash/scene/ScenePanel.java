package ttrpgdash.scene;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ttrpgdash.music.MusicController;
import ttrpgdash.music.MusicPanel;

/**
 * Right-side panel responsible solely for scene list management:
 * add, rename, reorder, and delete scenes.
 * Music playback for the active scene is handled by the embedded {@link MusicPanel}.
 */
public final class ScenePanel extends VBox {

    private static final String DARK = "#12121e";
    private static final String BORDER = "#1a1a3a";
    private static final String ACCENT = "#6fa8dc";
    private static final String TEXT = "#c0c0c0";
    private static final String TEXT_DIM = "#666";

    private final SceneManager sceneManager;
    private final MusicPanel musicPanel;

    private final VBox sceneListBox = new VBox();

    private Consumer<String> onSceneSwitch;
    private Runnable onSceneAdd;
    private BiConsumer<String, Integer> onSceneMove;
    private BiConsumer<String, String> onSceneRename;
    private Consumer<String> onSceneDelete;

    /**
     * Creates the scene panel with a scene list section and an embedded music panel.
     */
    public ScenePanel(SceneManager sceneManager,
                      MusicController musicController,
                      SceneState initialState) {
        this.sceneManager = sceneManager;
        this.musicPanel = new MusicPanel(musicController, initialState);

        setStyle("-fx-background-color: " + DARK + ";");
        setPrefWidth(250);
        setMinWidth(220);
        setMaxWidth(300);

        buildUI();
        refreshSceneList();
    }

    /**
     * Forwards the owner stage to the embedded MusicPanel for its file chooser.
     */
    public void setOwnerStage(Stage stage) {
        musicPanel.setOwnerStage(stage);
    }

    public void setOnSceneSwitch(Consumer<String> handler) {
        this.onSceneSwitch = handler;
    }

    public void setOnSceneAdd(Runnable handler) {
        this.onSceneAdd = handler;
    }

    public void setOnSceneMove(BiConsumer<String, Integer> handler) {
        this.onSceneMove = handler;
    }

    public void setOnSceneRename(BiConsumer<String, String> handler) {
        this.onSceneRename = handler;
    }

    public void setOnSceneDelete(Consumer<String> handler) {
        this.onSceneDelete = handler;
    }

    private void buildUI() {
        Label scenesHeader = makeHeader("SCENES");

        sceneListBox.setSpacing(2);
        ScrollPane scenesScroll = new ScrollPane(sceneListBox);
        scenesScroll.setFitToWidth(true);
        scenesScroll.setStyle("-fx-background: " + DARK + "; -fx-background-color: " + DARK
                + "; -fx-border-color: transparent;");
        VBox.setVgrow(scenesScroll, Priority.ALWAYS);

        Button addSceneBtn = new Button("+ Add Scene");
        addSceneBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + ACCENT
                + "; -fx-font-size: 11px; -fx-cursor: hand; -fx-border-color: " + ACCENT
                + "; -fx-border-radius: 4; -fx-padding: 3 10 3 10;");
        addSceneBtn.setOnAction(e -> {
            if (onSceneAdd != null) {
                onSceneAdd.run();
            }
        });
        HBox addSceneRow = new HBox(addSceneBtn);
        addSceneRow.setPadding(new Insets(6, 8, 6, 8));

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + BORDER + ";");

        VBox.setVgrow(musicPanel, Priority.ALWAYS);

        getChildren().addAll(scenesHeader, scenesScroll, addSceneRow, sep, musicPanel);
    }

    /**
     * Rebuilds the scene list from the current SceneManager state.
     */
    public void refreshSceneList() {
        sceneListBox.getChildren().clear();
        for (SceneEntry entry : sceneManager.getScenes()) {
            sceneListBox.getChildren().add(buildSceneRow(entry));
        }
    }

    /**
     * Refreshes the embedded MusicPanel for the given scene state.
     */
    public void refreshMusic(SceneState gs) {
        musicPanel.refresh(gs);
    }

    private HBox buildSceneRow(SceneEntry entry) {
        boolean active = entry.getId().equals(sceneManager.getActiveSceneId());

        Button nameBtn = new Button(entry.getName());
        nameBtn.setMaxWidth(Double.MAX_VALUE);
        nameBtn.setStyle("-fx-background-color: " + (active ? "#1a2a3a" : "transparent")
                + "; -fx-text-fill: " + (active ? ACCENT : TEXT)
                + "; -fx-font-size: 12px; -fx-alignment: center-left;"
                + "-fx-border-color: " + (active ? ACCENT : "transparent")
                + "; -fx-border-width: 0 0 0 2; -fx-padding: 4 8 4 8;");
        HBox.setHgrow(nameBtn, Priority.ALWAYS);
        nameBtn.setOnAction(e -> {
            if (onSceneSwitch != null) {
                onSceneSwitch.accept(entry.getId());
            }
        });

        Button upBtn = makeSmallBtn("↑");
        Button downBtn = makeSmallBtn("↓");
        Button renameBtn = makeSmallBtn("✎");
        Button deleteBtn = makeSmallBtn("×");

        upBtn.setOnAction(e -> {
            if (onSceneMove != null) {
                onSceneMove.accept(entry.getId(), -1);
            }
        });
        downBtn.setOnAction(e -> {
            if (onSceneMove != null) {
                onSceneMove.accept(entry.getId(), 1);
            }
        });
        renameBtn.setOnAction(e -> promptRename(entry));
        deleteBtn.setOnAction(e -> {
            if (sceneManager.getScenes().size() > 1 && onSceneDelete != null) {
                onSceneDelete.accept(entry.getId());
            }
        });
        deleteBtn.setDisable(sceneManager.getScenes().size() <= 1);

        HBox row = new HBox(2, nameBtn, upBtn, downBtn, renameBtn, deleteBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 4, 1, 4));
        return row;
    }

    private void promptRename(SceneEntry entry) {
        TextInputDialog dialog = new TextInputDialog(entry.getName());
        dialog.setTitle("Rename Scene");
        dialog.setHeaderText(null);
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank() && onSceneRename != null) {
                onSceneRename.accept(entry.getId(), name.trim());
            }
        });
    }

    private Label makeHeader(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT_DIM
                + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 10 8 4 8;");
        return label;
    }

    private Button makeSmallBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_DIM
                + "; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 2 4 2 4;");
        return btn;
    }
}
