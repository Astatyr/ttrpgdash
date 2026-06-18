package ttrpgdash;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ttrpgdash.audio.MusicController;
import ttrpgdash.model.GameState;
import ttrpgdash.model.MusicTrack;
import ttrpgdash.model.SceneEntry;
import ttrpgdash.model.SceneManager;
import ttrpgdash.util.FileHelper;

/**
 * Right-side panel showing the scene list and music tracks for the active scene.
 *
 * Scene management: add, rename, reorder, delete scenes.
 * Music per scene: add tracks from assets/music/, play/stop, loop toggle, volume slider.
 */
public final class ScenePanel extends VBox {

    private static final String DARK = "#12121e";
    private static final String BORDER = "#1a1a3a";
    private static final String ACCENT = "#6fa8dc";
    private static final String TEXT = "#c0c0c0";
    private static final String TEXT_DIM = "#666";

    private final SceneManager sceneManager;
    private final MusicController musicController;
    private GameState activeGameState;
    private Stage ownerStage;

    private final VBox sceneListBox = new VBox();
    private final VBox musicListBox = new VBox();
    private final Label musicHeader = new Label();

    private Consumer<String> onSceneSwitch;
    private Runnable onSceneAdd;
    private BiConsumer<String, Integer> onSceneMove;
    private BiConsumer<String, String> onSceneRename;
    private Consumer<String> onSceneDelete;

    /**
     * Creates the scene panel bound to the given manager, controller, and initial game state.
     */
    public ScenePanel(SceneManager sceneManager,
                      MusicController musicController,
                      GameState activeGameState) {
        this.sceneManager = sceneManager;
        this.musicController = musicController;
        this.activeGameState = activeGameState;

        setStyle("-fx-background-color: " + DARK + ";");
        setPrefWidth(250);
        setMinWidth(220);
        setMaxWidth(300);

        buildUI();
        refreshSceneList();
        refreshMusic(activeGameState);
    }

    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
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
        ScrollPane scenesScroll = makeScrollPane(sceneListBox);
        VBox.setVgrow(scenesScroll, Priority.ALWAYS);

        Button addSceneBtn = makeButton("+ Add Scene", ACCENT);
        addSceneBtn.setOnAction(e -> {
            if (onSceneAdd != null) {
                onSceneAdd.run();
            }
        });
        HBox addSceneRow = new HBox(addSceneBtn);
        addSceneRow.setPadding(new Insets(6, 8, 6, 8));

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + BORDER + ";");

        musicHeader.setStyle("-fx-text-fill: " + TEXT_DIM
                + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 8 8 4 8;");

        musicListBox.setSpacing(4);
        musicListBox.setPadding(new Insets(0, 4, 0, 4));
        ScrollPane musicScroll = makeScrollPane(musicListBox);
        VBox.setVgrow(musicScroll, Priority.ALWAYS);

        Button addMusicBtn = makeButton("+ Add Music", "#7a6a9e");
        addMusicBtn.setOnAction(e -> addMusicTrack());
        HBox addMusicRow = new HBox(addMusicBtn);
        addMusicRow.setPadding(new Insets(4, 8, 8, 8));

        getChildren().addAll(
                scenesHeader, scenesScroll, addSceneRow,
                sep,
                musicHeader, musicScroll, addMusicRow
        );
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
     * Rebuilds the music track list for the given game state.
     */
    public void refreshMusic(GameState gs) {
        this.activeGameState = gs;
        String sceneName = sceneManager.getActiveEntry()
                .map(SceneEntry::getName).orElse("Scene");
        musicHeader.setText("MUSIC  —  " + sceneName);
        musicListBox.getChildren().clear();
        for (MusicTrack track : gs.getMusicTracks()) {
            musicListBox.getChildren().add(buildMusicRow(track));
        }
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

    private VBox buildMusicRow(MusicTrack track) {
        Label name = new Label(track.getName());
        name.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 11px;");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Button playBtn = makeSmallBtn(musicController.isPlaying(track.getId()) ? "⏹" : "▶");
        playBtn.setOnAction(e -> {
            if (musicController.isPlaying(track.getId())) {
                musicController.stop(track.getId());
                playBtn.setText("▶");
            } else {
                musicController.play(track);
                playBtn.setText("⏹");
            }
        });

        ToggleButton loopBtn = new ToggleButton("↺");
        loopBtn.setSelected(track.isLoop());
        loopBtn.setStyle("-fx-background-color: " + (track.isLoop() ? ACCENT : "#333")
                + "; -fx-text-fill: white; -fx-font-size: 11px;"
                + "-fx-padding: 2 5 2 5; -fx-cursor: hand;");
        loopBtn.setOnAction(e -> {
            track.setLoop(loopBtn.isSelected());
            loopBtn.setStyle("-fx-background-color: " + (loopBtn.isSelected() ? ACCENT : "#333")
                    + "; -fx-text-fill: white; -fx-font-size: 11px;"
                    + "-fx-padding: 2 5 2 5; -fx-cursor: hand;");
            musicController.setLoop(track.getId(), track.isLoop());
            activeGameState.entityChanged();
        });

        Button removeBtn = makeSmallBtn("×");
        removeBtn.setOnAction(e -> {
            musicController.stop(track.getId());
            activeGameState.getMusicTracks().remove(track);
            activeGameState.entityChanged();
            refreshMusic(activeGameState);
        });

        HBox topRow = new HBox(4, name, playBtn, loopBtn, removeBtn);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Slider volumeSlider = new Slider(0, 1, track.getVolume());
        volumeSlider.setStyle("-fx-padding: 0;");
        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            track.setVolume(newV.doubleValue());
            musicController.setVolume(track.getId(), newV.doubleValue());
            activeGameState.entityChanged();
        });

        Label volLabel = new Label("vol");
        volLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 9px;");
        HBox.setHgrow(volumeSlider, Priority.ALWAYS);
        HBox bottomRow = new HBox(4, volLabel, volumeSlider);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.setPadding(new Insets(0, 2, 0, 2));

        VBox card = new VBox(2, topRow, bottomRow);
        card.setStyle("-fx-background-color: #1e1e2e; -fx-border-color: " + BORDER
                + "; -fx-border-width: 0 0 1 0; -fx-padding: 4 4 4 4;");
        return card;
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

    private void addMusicTrack() {
        File file = FileHelper.browseForMusic(ownerStage);
        if (file == null) {
            return;
        }
        String relativePath = FileHelper.toRelativePath(file);
        String name = file.getName().replaceAll("\\.[^.]+$", "");
        String id = name.toLowerCase().replaceAll("[^a-z0-9]", "") + "_"
                + Long.toHexString(System.currentTimeMillis());
        MusicTrack track = new MusicTrack(id, name, relativePath);
        activeGameState.getMusicTracks().add(track);
        activeGameState.entityChanged();
        refreshMusic(activeGameState);
    }

    private Label makeHeader(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT_DIM
                + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 10 8 4 8;");
        return label;
    }

    private ScrollPane makeScrollPane(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: " + DARK + "; -fx-background-color: " + DARK
                + "; -fx-border-color: transparent;");
        return scroll;
    }

    private Button makeButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + color
                + "; -fx-font-size: 11px; -fx-cursor: hand; -fx-border-color: " + color
                + "; -fx-border-radius: 4; -fx-padding: 3 10 3 10;");
        return btn;
    }

    private Button makeSmallBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_DIM
                + "; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 2 4 2 4;");
        return btn;
    }
}
