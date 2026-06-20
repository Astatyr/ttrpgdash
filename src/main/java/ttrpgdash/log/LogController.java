package ttrpgdash.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ttrpgdash.entity.Entity;
import ttrpgdash.scene.SceneEntry;
import ttrpgdash.scene.SceneManager;
import ttrpgdash.scene.SceneState;
import ttrpgdash.scene.SceneStateManager;
import ttrpgdash.util.JsonStateManager;

/**
 * Orchestrates session logging with a pointer-based undo/redo mechanism.
 *
 * The log file ({@code logs/session_timestamp.log}) is the single source of truth.
 * Two lightweight parallel lists — {@code entryOffsets} (byte position of each
 * entry in the file) and {@code entryEvents} (event type of each entry) — act
 * as an index, giving O(1) access without duplicating entry content.
 *
 * {@code pointer} is an index into those lists marking the current "now".
 * Everything at or before {@code pointer} is applied history; everything after
 * is redo-able.  When a new action is taken, all entries after {@code pointer}
 * are physically deleted from the file and the lists before the new entry is
 * appended.
 *
 * Header entries (Start Log, Scene Snapshot, End Log) are written directly to
 * the file but do NOT enter the index, so the undo pointer is never inflated
 * by non-undoable records.  {@code minUndoPointer} stays at -1 after the header
 * block, meaning the pointer must be &ge; 0 (at least one action exists) to undo.
 */
public final class LogController {

    private static final String LOG_DIR = "logs";

    /** Increment when the log format changes in a way that may affect parsing. */
    private static final String LOG_VERSION = "1";
    private static final DateTimeFormatter FILE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                    .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final LogWriter writer = new LogWriter();

    private final List<Long> entryOffsets = new ArrayList<>();
    private final List<LogEvent> entryEvents = new ArrayList<>();
    private int pointer = -1;
    private int minUndoPointer = -1;

    private boolean enabled = false;
    private String activeSceneId;
    private String logFolder;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables logging, writes the Start Log header and scene snapshots,
     * then locks {@code minUndoPointer} so the header cannot be undone.
     * Header entries are written directly to the file and do NOT enter the index.
     */
    public void enable(SceneManager sceneManager, SceneState activeState) {
        if (enabled) {
            return;
        }
        // Reset all pointer state so a second session starts clean
        entryOffsets.clear();
        entryEvents.clear();
        pointer = -1;
        minUndoPointer = -1;

        String sessionName = "session_" + FILE_FMT.format(Instant.now());
        logFolder = LOG_DIR + "/" + sessionName;
        try {
            Files.createDirectories(Paths.get(logFolder));
            writer.open(Paths.get(logFolder + "/session.log"));
            activeSceneId = sceneManager.getActiveSceneId();
            enabled = true;
            writeStartLog(sceneManager, activeState);
            minUndoPointer = pointer; // pointer is still -1; undo requires pointer >= 0
        } catch (IOException e) {
            System.err.println("[LogController] Failed to open log: " + e.getMessage());
        }
    }

    /**
     * Writes End of Log and closes the file.
     */
    public void disable() {
        if (!enabled) {
            return;
        }
        writeHeaderEntry(LogEvent.END_LOG, Map.of("Time", DATE_FMT.format(Instant.now())));
        try {
            writer.close();
        } catch (IOException e) {
            System.err.println("[LogController] Failed to close log: " + e.getMessage());
        }
        enabled = false;
    }


    /**
     * Returns true if there is an action entry at the current pointer to reverse.
     */
    public boolean canUndo() {
        return pointer > minUndoPointer;
    }

    /**
     * Returns true if there is at least one entry after the pointer to re-apply.
     */
    public boolean canRedo() {
        return pointer < entryOffsets.size() - 1;
    }

    /**
     * Reads the entry at the current pointer, moves the pointer back, and returns
     * the entry for the caller to reverse.
     */
    public LogEntry doUndo() {
        if (!canUndo()) {
            return null;
        }
        try {
            LogEntry entry = writer.readEntryAt(entryOffsets.get(pointer));
            pointer--;
            return entry;
        } catch (IOException e) {
            System.err.println("[LogController] Failed to read entry for undo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Advances the pointer and reads the entry there, returning it for the caller
     * to re-apply.
     */
    public LogEntry doRedo() {
        if (!canRedo()) {
            return null;
        }
        try {
            pointer++;
            return writer.readEntryAt(entryOffsets.get(pointer));
        } catch (IOException e) {
            System.err.println("[LogController] Failed to read entry for redo: " + e.getMessage());
            pointer--;
            return null;
        }
    }


    /** Records a scene switch, storing both the previous and next scene IDs for undo. */
    public void onSceneSwitched(String newSceneId) {
        if (!enabled) {
            return;
        }
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Previous Scene ID", activeSceneId != null ? activeSceneId : "none");
        f.put("Scene ID", newSceneId);
        activeSceneId = newSceneId;
        writeEntry(LogEvent.SWITCH_SCENE, f);
    }

    /** Logs the addition of a player or character to the session. */
    public void logAddEntity(Entity entity) {
        if (!enabled) {
            return;
        }
        LogEvent event = isPlayer(entity) ? LogEvent.ADD_PLAYER : LogEvent.ADD_CHARACTER;
        writeEntry(event, fields("Name", entity.getName(), "Size", fmt(entity.getSizeInFeet())));
    }

    /** Logs the removal of a player or character from the session. */
    public void logRemoveEntity(Entity entity) {
        if (!enabled) {
            return;
        }
        LogEvent event = isPlayer(entity) ? LogEvent.REMOVE_PLAYER : LogEvent.REMOVE_CHARACTER;
        writeEntry(event, fields("Name", entity.getName(), "Size", fmt(entity.getSizeInFeet())));
    }

    /** Logs the initial placement of a token on the map. */
    public void logPlaceToken(Entity entity, double x, double y) {
        if (!enabled) {
            return;
        }
        LogEvent event = isPlayer(entity) ? LogEvent.PLACE_PLAYER : LogEvent.PLACE_CHARACTER;
        writeEntry(event, fields("Name", entity.getName(), "X", fmt(x), "Y", fmt(y)));
    }

    /**
     * Logs a token move with both old and new positions for undo support.
     */
    public void logMoveToken(Entity entity,
                             double fromX, double fromY,
                             double toX, double toY) {
        if (!enabled) {
            return;
        }
        LogEvent event = isPlayer(entity) ? LogEvent.MOVE_PLAYER : LogEvent.MOVE_CHARACTER;
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Name", entity.getName());
        f.put("From X", fmt(fromX));
        f.put("From Y", fmt(fromY));
        f.put("X", fmt(toX));
        f.put("Y", fmt(toY));
        writeEntry(event, f);
    }

    /** Logs the removal of a token from the map, capturing its last position and status effects. */
    public void logRemoveFromMap(Entity entity) {
        if (!enabled) {
            return;
        }
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Name", entity.getName());
        f.put("X", fmt(entity.getXFraction()));
        f.put("Y", fmt(entity.getYFraction()));
        String statusStr = String.join(", ", entity.getStatusEffects());
        f.put("Status Effects", statusStr.isEmpty() ? "none" : statusStr);
        writeEntry(LogEvent.REMOVE_FROM_MAP, f);
    }

    /** Logs a rider mounting a mount token. */
    public void logMount(String rider, String mount) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.MOUNT, fields("Rider", rider, "Mount", mount));
    }

    /** Logs a rider dismounting. */
    public void logDismount(String rider, String mount) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.DISMOUNT, fields("Rider", rider, "Mount", mount));
    }

    /** Logs a status effect being applied to an entity. */
    public void logAddStatusEffect(String entityName, String status) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.ADD_STATUS_EFFECT, fields("Name", entityName, "Status", status));
    }

    /** Logs a status effect being removed from an entity. */
    public void logRemoveStatusEffect(String entityName, String status) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.REMOVE_STATUS_EFFECT, fields("Name", entityName, "Status", status));
    }

    /** Logs a map image being loaded onto the scene. */
    public void logAddMap(String mapPath, double sizeInFeet) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.ADD_MAP, fields("Map", mapPath, "Map Size In Feet", fmt(sizeInFeet)));
    }

    /** Logs a change to the map's real-world width in feet. */
    public void logChangeMapSize(double oldSize, double newSize) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.CHANGE_MAP_SIZE,
                fields("Old Size In Feet", fmt(oldSize), "New Size In Feet", fmt(newSize)));
    }

    /** Logs a new scene being created. */
    public void logAddScene(String sceneId, String name) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.ADD_SCENE, fields("Scene ID", sceneId, "Name", name));
    }

    /** Logs a scene being deleted. */
    public void logDeleteScene(String sceneId, String sceneName) {
        if (!enabled) {
            return;
        }
        writeEntry(LogEvent.DELETE_SCENE, fields("Scene ID", sceneId, "Name", sceneName));
    }

    /** Logs a scene rename, storing both old and new names for undo. */
    public void logRenameScene(String sceneId, String oldName, String newName) {
        if (!enabled) {
            return;
        }
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Scene ID", sceneId);
        f.put("Old Name", oldName);
        f.put("New Name", newName);
        writeEntry(LogEvent.RENAME_SCENE, f);
    }


    /**
     * Writes an action entry: truncates superseded redo history, appends to file,
     * and records offset + event in the index so undo/redo can reach it.
     */
    private void writeEntry(LogEvent event, Map<String, String> fields) {
        // If pointer is behind the file end, everything after it is redo history
        // that this new action supersedes — delete it from the file now.
        if (pointer < entryOffsets.size() - 1) {
            long truncateAt = entryOffsets.get(pointer + 1);
            try {
                writer.truncateTo(truncateAt);
            } catch (IOException e) {
                System.err.println("[LogController] Failed to truncate log: " + e.getMessage());
            }
            entryOffsets.subList(pointer + 1, entryOffsets.size()).clear();
            entryEvents.subList(pointer + 1, entryEvents.size()).clear();
        }

        LogEntry entry = new LogEntry(event, fields, Instant.now());
        try {
            long offset = writer.write(entry);
            entryOffsets.add(offset);
            entryEvents.add(event);
            pointer = entryOffsets.size() - 1;
        } catch (IOException e) {
            System.err.println("[LogController] Failed to write entry: " + e.getMessage());
        }
    }

    /**
     * Writes a header entry (Start Log, Scene Snapshot, End Log) directly to the
     * file without adding it to the undo/redo index.
     */
    private void writeHeaderEntry(LogEvent event, Map<String, String> fields) {
        LogEntry entry = new LogEntry(event, fields, Instant.now());
        try {
            writer.write(entry);
        } catch (IOException e) {
            System.err.println("[LogController] Failed to write header entry: " + e.getMessage());
        }
    }

    private void writeStartLog(SceneManager sceneManager, SceneState activeState) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("Log Version", LOG_VERSION);
        header.put("Time", DATE_FMT.format(Instant.now()));
        header.put("Active Scene", activeSceneId);
        StringBuilder ids = new StringBuilder("[");
        for (SceneEntry e : sceneManager.getScenes()) {
            if (ids.length() > 1) {
                ids.append(", ");
            }
            ids.append(e.getId());
        }
        ids.append("]");
        header.put("Scenes", ids.toString());
        writeHeaderEntry(LogEvent.START_LOG, header);

        for (SceneEntry entry : sceneManager.getScenes()) {
            SceneState state = entry.getId().equals(activeSceneId)
                    ? activeState
                    : SceneStateManager.loadScene(entry.getId());
            writeSceneSnapshot(entry.getId(), state);
            JsonStateManager.save(state, logFolder + "/" + entry.getId() + ".json");
        }
    }

    private void writeSceneSnapshot(String sceneId, SceneState state) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Scene ID", sceneId);
        f.put("Map", state.getMapImagePath() != null ? state.getMapImagePath() : "null");
        f.put("Map Size In Feet", fmt(state.getMapWidthInFeet()));
        for (var p : state.getPlayers()) {
            f.put("Player", p.getName() + " | id=" + p.getId()
                    + " | size=" + fmt(p.getSizeInFeet())
                    + " | x=" + fmt(p.getXFraction())
                    + " | y=" + fmt(p.getYFraction())
                    + " | onMap=" + p.isOnMap());
        }
        for (var c : state.getCharacters()) {
            f.put("Character", c.getName() + " | id=" + c.getId()
                    + " | size=" + fmt(c.getSizeInFeet())
                    + " | x=" + fmt(c.getXFraction())
                    + " | y=" + fmt(c.getYFraction())
                    + " | onMap=" + c.isOnMap());
        }
        writeHeaderEntry(LogEvent.SCENE_SNAPSHOT, f);
    }

    private static boolean isPlayer(Entity entity) {
        return "player".equals(entity.getEntityType());
    }

    private static String fmt(double value) {
        return String.format("%.6f", value);
    }

    private static Map<String, String> fields(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
