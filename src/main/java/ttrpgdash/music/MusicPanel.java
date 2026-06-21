package ttrpgdash.music;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ttrpgdash.scene.SceneState;
import ttrpgdash.util.FileHelper;

/**
 * Panel displaying the music tracks for the currently active scene.
 * Handles add, remove, play/stop, loop toggle, and per-track volume.
 */
public final class MusicPanel extends VBox {

    private static final String DARK = "#12121e";
    private static final String BORDER = "#1a1a3a";
    private static final String ACCENT = "#6fa8dc";
    private static final String TEXT = "#c0c0c0";
    private static final String TEXT_DIM = "#666";

    private final MusicController musicController;
    private SceneState sceneState;
    private Stage ownerStage;
    private Runnable onMusicChanged;

    private final Label header = new Label("MUSIC");
    private final VBox trackListBox = new VBox();

    /**
     * Creates the music panel bound to the given controller and initial scene state.
     */
    public MusicPanel(MusicController musicController, SceneState sceneState) {
        this.musicController = musicController;
        this.sceneState = sceneState;

        header.setStyle("-fx-text-fill: " + TEXT_DIM
                + "; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 8 8 4 8;");

        trackListBox.setSpacing(4);
        trackListBox.setPadding(new Insets(0, 4, 0, 4));

        ScrollPane scroll = new ScrollPane(trackListBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: " + DARK + "; -fx-background-color: " + DARK
                + "; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button addBtn = new Button("+ Add Music");
        addBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #7a6a9e;"
                + "-fx-font-size: 11px; -fx-cursor: hand; -fx-border-color: #7a6a9e;"
                + "-fx-border-radius: 4; -fx-padding: 3 10 3 10;");
        addBtn.setOnAction(e -> addTrack());
        HBox addRow = new HBox(addBtn);
        addRow.setPadding(new Insets(4, 8, 8, 8));

        getChildren().addAll(header, scroll, addRow);
        refresh(sceneState);
    }

    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }

    public void setOnMusicChanged(Runnable handler) {
        this.onMusicChanged = handler;
    }

    /**
     * Rebuilds the track list from the given scene state.
     */
    public void refresh(SceneState gs) {
        this.sceneState = gs;
        trackListBox.getChildren().clear();
        for (MusicTrack track : gs.getMusicTracks()) {
            trackListBox.getChildren().add(buildTrackRow(track));
        }
    }

    private VBox buildTrackRow(MusicTrack track) {
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
        loopBtn.setMinWidth(28);
        loopBtn.setStyle("-fx-background-color: " + (track.isLoop() ? ACCENT : "#333")
                + "; -fx-text-fill: white; -fx-font-size: 11px;"
                + "-fx-padding: 2 5 2 5; -fx-cursor: hand;");
        loopBtn.setOnAction(e -> {
            track.setLoop(loopBtn.isSelected());
            loopBtn.setStyle("-fx-background-color: " + (loopBtn.isSelected() ? ACCENT : "#333")
                    + "; -fx-text-fill: white; -fx-font-size: 11px;"
                    + "-fx-padding: 2 5 2 5; -fx-cursor: hand;");
            musicController.setLoop(track.getId(), track.isLoop());
            if (onMusicChanged != null) {
                onMusicChanged.run();
            }
        });

        Button removeBtn = makeSmallBtn("×");
        removeBtn.setOnAction(e -> {
            musicController.stop(track.getId());
            sceneState.removeMusicTrack(track);
            if (onMusicChanged != null) {
                onMusicChanged.run();
            }
            refresh(sceneState);
        });

        HBox topRow = new HBox(4, name, playBtn, loopBtn, removeBtn);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Slider volumeSlider = new Slider(0, 1, track.getVolume());
        volumeSlider.setStyle("-fx-padding: 0;");
        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            track.setVolume(newV.doubleValue());
            musicController.setVolume(track.getId(), newV.doubleValue());
            if (onMusicChanged != null) {
                onMusicChanged.run();
            }
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

    private void addTrack() {
        File file = FileHelper.browseForMusic(ownerStage);
        if (file == null) {
            return;
        }
        // Copy into assets/music/ if selected from outside it
        File musicDir = new File(FileHelper.MUSIC_DIR).getAbsoluteFile();
        if (!file.getAbsoluteFile().getParentFile().equals(musicDir)) {
            file = FileHelper.copyToMusicDir(file);
            if (file == null) {
                return;
            }
        }
        String relativePath = FileHelper.toRelativePath(file);
        String name = file.getName().replaceAll("\\.[^.]+$", "");
        String id = name.toLowerCase().replaceAll("[^a-z0-9]", "") + "_"
                + Long.toHexString(System.currentTimeMillis());
        MusicTrack track = new MusicTrack(id, name, relativePath);
        sceneState.addMusicTrack(track);
        if (onMusicChanged != null) {
            onMusicChanged.run();
        }
        refresh(sceneState);
    }

    private Button makeSmallBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_DIM
                + "; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 2 6 2 6;");
        btn.setMinWidth(28);
        return btn;
    }
}
