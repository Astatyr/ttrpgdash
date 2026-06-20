package ttrpgdash.replay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import ttrpgdash.entity.CharacterEntity;
import ttrpgdash.entity.PlayerEntity;
import ttrpgdash.log.LogEntry;
import ttrpgdash.log.LogEvent;
import ttrpgdash.log.LogParseException;
import ttrpgdash.log.LogParser;
import ttrpgdash.scene.SceneState;
import ttrpgdash.util.FileHelper;
import ttrpgdash.util.JsonStateManager;

/**
 * Drives log replay: loads scene snapshots from the log folder, applies
 * log entries step by step, and fires a callback on each change.
 *
 * Header entries (Start Log, Scene Snapshot, End Log) are excluded from the
 * step list; step 0 represents the first user action after the initial state.
 *
 * Two advance modes:
 *   {@link #goToStep(int)} — resets to snapshots and replays 0..n (used by slider).
 *   {@link #stepForward()}  — applies only the next action (used by play timer).
 */
public final class LogReplayController {

    /** How much the view needs to refresh after a step. */
    public enum RefreshHint {
        /** Token positions or status changed — syncTokens() is enough. */
        TOKENS_ONLY,
        /** Map image or width changed — reloadFromState() needed. */
        MAP_RELOAD,
        /** Active scene switched — swap canvas then reloadFromState(). */
        SCENE_SWITCH
    }

    private final Path logFolder;
    private final Map<String, SceneState> sceneStates = new LinkedHashMap<>();
    private final List<LogEntry> actions = new ArrayList<>();
    private final String initialActiveSceneId;
    private String activeSceneId;
    private int currentStep = -1;
    private RefreshHint lastHint = RefreshHint.MAP_RELOAD;

    private Consumer<RefreshHint> onStepChanged;
    private Timeline playTimer;

    /**
     * Parses the log file and loads scene snapshot JSONs from the same folder.
     */
    public LogReplayController(Path logFile) throws IOException {
        this.logFolder = logFile.getParent();

        List<LogEntry> all = new LogParser().parse(logFile);

        String startScene = null;
        for (LogEntry entry : all) {
            if (entry.getEvent() == LogEvent.START_LOG) {
                startScene = entry.get("Active Scene");
                break;
            }
        }
        if (startScene == null) {
            throw new LogParseException(
                    "Log file has no Start Log header — may be empty or from an older format: "
                    + logFile.getFileName());
        }
        this.initialActiveSceneId = startScene;
        this.activeSceneId = startScene;

        loadSceneStates();

        if (sceneStates.isEmpty()) {
            throw new ReplayException(
                    "No scene snapshot files found in log folder '" + logFolder
                    + "'. The log may have been recorded with an older version that did not "
                    + "save snapshots alongside the log file.");
        }

        for (LogEntry entry : all) {
            LogEvent ev = entry.getEvent();
            if (ev != LogEvent.START_LOG && ev != LogEvent.SCENE_SNAPSHOT
                    && ev != LogEvent.END_LOG) {
                actions.add(entry);
            }
        }
    }



    /** Returns the active scene's state, or null if the scene ID is unrecognised. */
    public SceneState getActiveState() {
        return sceneStates.get(activeSceneId);
    }

    /** Returns the ID of the currently active scene. */
    public String getActiveSceneId() {
        return activeSceneId;
    }

    /** Returns the index of the last applied action (-1 = initial snapshot state). */
    public int getCurrentStep() {
        return currentStep;
    }

    /** Returns the total number of replayable actions. */
    public int getTotalSteps() {
        return actions.size();
    }

    /** Returns the refresh hint produced by the last applied action. */
    public RefreshHint getLastHint() {
        return lastHint;
    }

    /**
     * Registers a callback fired after every step change, passing a {@link RefreshHint}.
     */
    public void setOnStepChanged(Consumer<RefreshHint> handler) {
        this.onStepChanged = handler;
    }



    /**
     * Resets to the snapshot state and replays actions 0..{@code step}.
     * Always fires the callback with {@link RefreshHint#MAP_RELOAD}.
     * Stops any active playback first.
     */
    public void goToStep(int step) {
        pause();
        resetToInitial();
        currentStep = -1;
        for (int i = 0; i <= step && i < actions.size(); i++) {
            applyEntry(actions.get(i));
            currentStep = i;
        }
        lastHint = RefreshHint.MAP_RELOAD;
        fire(lastHint);
    }

    /**
     * Applies only the next action without a full reset.
     * More efficient than {@link #goToStep(int)} for play-mode ticks.
     *
     * @return the {@link RefreshHint} for the applied action
     */
    public RefreshHint stepForward() {
        if (currentStep + 1 >= actions.size()) {
            return lastHint;
        }
        currentStep++;
        applyEntry(actions.get(currentStep));
        fire(lastHint);
        return lastHint;
    }

    /** Starts playback at one action per second. */
    public void play() {
        if (playTimer != null) {
            playTimer.stop();
        }
        playTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (currentStep + 1 < actions.size()) {
                stepForward();
            } else {
                pause();
                fire(RefreshHint.TOKENS_ONLY);
            }
        }));
        playTimer.setCycleCount(Timeline.INDEFINITE);
        playTimer.play();
    }

    /** Stops playback without changing the current step. */
    public void pause() {
        if (playTimer != null) {
            playTimer.stop();
        }
    }

    /** Returns true if the play timer is currently running. */
    public boolean isPlaying() {
        return playTimer != null && playTimer.getStatus() == Animation.Status.RUNNING;
    }



    private void loadSceneStates() {
        if (logFolder == null || !Files.isDirectory(logFolder)) {
            return;
        }
        try (var stream = Files.list(logFolder)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                  .forEach(p -> {
                      String fname = p.getFileName().toString();
                      String sceneId = fname.substring(0, fname.length() - 5);
                      SceneState state = JsonStateManager.load(p.toString());
                      state.disableSave();
                      sceneStates.put(sceneId, state);
                  });
        } catch (IOException e) {
            System.err.println("[LogReplayController] Could not read log folder: " + e.getMessage());
        }
    }

    private void resetToInitial() {
        sceneStates.clear();
        activeSceneId = initialActiveSceneId;
        loadSceneStates();
    }

    private void applyEntry(LogEntry entry) {
        lastHint = RefreshHint.TOKENS_ONLY;
        SceneState state = sceneStates.get(activeSceneId);
        if (state == null) {
            return;
        }
        switch (entry.getEvent()) {
        case MOVE_PLAYER, MOVE_CHARACTER -> state.findByName(entry.get("Name")).ifPresent(e -> {
            e.setXFraction(Double.parseDouble(entry.get("X")));
            e.setYFraction(Double.parseDouble(entry.get("Y")));
        });
        case PLACE_PLAYER, PLACE_CHARACTER -> state.findByName(entry.get("Name")).ifPresent(e -> {
            e.setOnMap(true);
            e.setXFraction(Double.parseDouble(entry.get("X")));
            e.setYFraction(Double.parseDouble(entry.get("Y")));
        });
        case REMOVE_FROM_MAP -> state.findByName(entry.get("Name")).ifPresent(e -> {
            e.setOnMap(false);
            e.setMountedOnId(null);
        });
        case ADD_PLAYER -> {
            String name = entry.get("Name");
            double size = Double.parseDouble(entry.get("Size"));
            PlayerEntity p = new PlayerEntity(FileHelper.generateId(name), name, size);
            String av = FileHelper.CHARACTERS_DIR + "/" + name + "/Avatar.png";
            if (FileHelper.fileExists(av)) {
                p.setAvatarPath(av);
            }
            state.addPlayer(p);
        }
        case ADD_CHARACTER -> {
            String name = entry.get("Name");
            double size = Double.parseDouble(entry.get("Size"));
            CharacterEntity c = new CharacterEntity(FileHelper.generateId(name), name, size);
            String av = FileHelper.CHARACTERS_DIR + "/" + name + "/Avatar.png";
            if (FileHelper.fileExists(av)) {
                c.setAvatarPath(av);
            }
            state.addCharacter(c);
        }
        case REMOVE_PLAYER ->
            state.findByName(entry.get("Name")).ifPresent(e -> state.removePlayer(e.getId()));
        case REMOVE_CHARACTER ->
            state.findByName(entry.get("Name")).ifPresent(e -> state.removeCharacter(e.getId()));
        case ADD_STATUS_EFFECT ->
            state.findByName(entry.get("Name"))
                 .ifPresent(e -> e.addStatusEffect(entry.get("Status")));
        case REMOVE_STATUS_EFFECT ->
            state.findByName(entry.get("Name"))
                 .ifPresent(e -> e.removeStatusEffect(entry.get("Status")));
        case MOUNT -> state.findByName(entry.get("Mount")).ifPresent(mount ->
            state.findByName(entry.get("Rider")).ifPresent(rider ->
                rider.setMountedOnId(mount.getId())));
        case DISMOUNT ->
            state.findByName(entry.get("Rider")).ifPresent(r -> r.setMountedOnId(null));
        case ADD_MAP -> {
            state.setMapImagePath(entry.get("Map"));
            lastHint = RefreshHint.MAP_RELOAD;
        }
        case CHANGE_MAP_SIZE -> {
            state.setMapWidthInFeet(Double.parseDouble(entry.get("New Size In Feet")));
            lastHint = RefreshHint.MAP_RELOAD;
        }
        case SWITCH_SCENE -> {
            activeSceneId = entry.get("Scene ID");
            lastHint = RefreshHint.SCENE_SWITCH;
        }
        case ADD_SCENE -> {
            String sceneId = entry.get("Scene ID");
            if (!sceneStates.containsKey(sceneId)) {
                Path sceneFile = logFolder.resolve(sceneId + ".json");
                SceneState ns = Files.exists(sceneFile)
                        ? JsonStateManager.load(sceneFile.toString())
                        : new SceneState();
                ns.disableSave();
                sceneStates.put(sceneId, ns);
            }
            lastHint = RefreshHint.SCENE_SWITCH;
        }
        case DELETE_SCENE -> {
            String sceneId = entry.get("Scene ID");
            sceneStates.remove(sceneId);
            if (sceneId.equals(activeSceneId)) {
                activeSceneId = initialActiveSceneId;
                lastHint = RefreshHint.SCENE_SWITCH;
            }
        }
        case RENAME_SCENE -> { // name change has no visible effect in the replay canvas
        }
        default -> { // no-op for unrecognised future events
        }
        }
    }

    private void fire(RefreshHint hint) {
        if (onStepChanged != null) {
            onStepChanged.accept(hint);
        }
    }
}
