package ttrpgdash.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import ttrpgdash.OutputPanel;
import ttrpgdash.entity.Entity;
import ttrpgdash.log.LogController;
import ttrpgdash.map.MapController;
import ttrpgdash.scene.SceneController;

/**
 * Manages the sandboxed Lua runtime for the scripting system.
 *
 * The same {@link Globals} instance persists for the session, so functions
 * defined in the project script ({@code data/scripts/project.lua}) are
 * available when later scripts run.
 *
 * The {@code dashboard} table exposes the following API to Lua:
 * <pre>
 *   dashboard.output(text)              — print [SCRIPT] to the output panel
 *   dashboard.roll(notation)            — roll dice, print [DICE], return number
 *   dashboard.addStatus(name, effect)   — add status effect to a named entity
 *   dashboard.removeStatus(name, effect)— remove status effect from a named entity
 *   dashboard.getEntity(name)           — return read-only entity table
 * </pre>
 */
public final class ScriptEngine {

    private static final String PROJECT_SCRIPT = "data/scripts/project.lua";

    private final Globals globals;
    private final OutputPanel outputPanel;
    private final SceneController sceneController;
    private final MapController mapController;
    private final LogController logController;

    /**
     * Set to true by {@link #cancel()} to interrupt an in-progress async script
     * at the next API call boundary. Volatile so it is visible across threads.
     */
    private volatile boolean cancelRequested = false;

    /**
     * Creates the engine, builds the sandbox, and wires the dashboard API.
     * Call {@link #loadProjectScript()} after construction to load global definitions.
     */
    public ScriptEngine(OutputPanel outputPanel, SceneController sceneController,
            MapController mapController, LogController logController) {
        this.outputPanel = outputPanel;
        this.sceneController = sceneController;
        this.mapController = mapController;
        this.logController = logController;

        globals = JsePlatform.standardGlobals();
        sandbox();
        globals.set("dashboard", buildDashboard());
    }

    /**
     * Signals any currently running script to stop at its next API call.
     * Called by undo and redo so that stepping back through the log always
     * wins over an in-progress script.
     */
    public void cancel() {
        cancelRequested = true;
    }

    /**
     * Loads {@code data/scripts/project.lua} if it exists.
     * Functions defined there are available to all subsequent script executions.
     */
    public void loadProjectScript() {
        Path path = Paths.get(PROJECT_SCRIPT);
        if (Files.exists(path)) {
            execute(path);
            outputPanel.log("SYSTEM", "Project script loaded.");
        }
    }

    /**
     * Executes the Lua file at the given path.
     * Any error is caught and printed to the output panel rather than thrown.
     */
    public void execute(Path scriptFile) {
        cancelRequested = false;
        try {
            String code = Files.readString(scriptFile);
            globals.load(code, scriptFile.getFileName().toString()).call();
        } catch (LuaError e) {
            outputPanel.log("SYSTEM", "Script error in "
                    + scriptFile.getFileName() + ": " + e.getMessage());
        } catch (java.io.IOException e) {
            outputPanel.log("SYSTEM", "Could not read "
                    + scriptFile.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Executes a named Lua function that was defined by a previously loaded script.
     * No-op if the function does not exist in the current globals.
     */
    public void callFunction(String functionName) {
        cancelRequested = false;
        LuaValue fn = globals.get(functionName);
        if (fn.isnil() || !fn.isfunction()) {
            outputPanel.log("SYSTEM", "Function not found: " + functionName);
            return;
        }
        try {
            fn.call();
        } catch (LuaError e) {
            outputPanel.log("SYSTEM", "Error in " + functionName + ": " + e.getMessage());
        }
    }



    private void sandbox() {
        globals.set("io", LuaValue.NIL);
        globals.set("os", LuaValue.NIL);
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("load", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("package", LuaValue.NIL);
        globals.set("debug", LuaValue.NIL);
    }

    private LuaTable buildDashboard() {
        LuaTable api = new LuaTable();

        api.set("output", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue text) {
                outputPanel.log("SCRIPT", text.checkjstring());
                return LuaValue.NIL;
            }
        });

        api.set("roll", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue notation) {
                try {
                    String desc = DiceRoller.describe(notation.checkjstring());
                    int result = 0;
                    int eqIdx = desc.lastIndexOf("=");
                    if (eqIdx >= 0) {
                        result = Integer.parseInt(desc.substring(eqIdx + 1).trim());
                    }
                    outputPanel.log("DICE", desc);
                    return LuaValue.valueOf(result);
                } catch (IllegalArgumentException e) {
                    outputPanel.log("SYSTEM", "Roll error: " + e.getMessage());
                    return LuaValue.valueOf(0);
                }
            }
        });

        api.set("addStatus", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue nameArg, LuaValue effectArg) {
                if (cancelRequested) {
                    throw new LuaError("Script cancelled.");
                }
                String entityName = nameArg.checkjstring();
                String effect = effectArg.checkjstring();
                sceneController.getActiveState().findByName(entityName).ifPresent(e -> {
                    e.addStatusEffect(effect);
                    sceneController.getActiveState().entityChanged();
                    mapController.getMapCanvas().syncTokens();
                    logController.logAddStatusEffect(entityName, effect);
                });
                return LuaValue.NIL;
            }
        });

        api.set("removeStatus", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue nameArg, LuaValue effectArg) {
                if (cancelRequested) {
                    throw new LuaError("Script cancelled.");
                }
                String entityName = nameArg.checkjstring();
                String effect = effectArg.checkjstring();
                sceneController.getActiveState().findByName(entityName).ifPresent(e -> {
                    e.removeStatusEffect(effect);
                    sceneController.getActiveState().entityChanged();
                    mapController.getMapCanvas().syncTokens();
                    logController.logRemoveStatusEffect(entityName, effect);
                });
                return LuaValue.NIL;
            }
        });

        api.set("getEntity", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue nameArg) {
                return sceneController.getActiveState()
                        .findByName(nameArg.checkjstring())
                        .map(entity -> (LuaValue) ScriptEngine.entityToTable(entity))
                        .orElse(LuaValue.NIL);
            }
        });

        api.set("move", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue nameArg, LuaValue xArg, LuaValue yArg) {
                if (cancelRequested) {
                    throw new LuaError("Script cancelled.");
                }
                String entityName = nameArg.checkjstring();
                double x = xArg.checkdouble();
                double y = yArg.checkdouble();
                sceneController.getActiveState().findByName(entityName).ifPresent(entity -> {
                    if (!entity.isOnMap()) {
                        outputPanel.log("SCRIPT", entityName + " is not on the map — use place() first.");
                        return;
                    }
                    double fromX = entity.getXFraction();
                    double fromY = entity.getYFraction();
                    entity.setXFraction(x);
                    entity.setYFraction(y);
                    sceneController.getActiveState().entityChanged();
                    mapController.getMapCanvas().syncTokens();
                    logController.logMoveToken(entity, fromX, fromY, x, y);
                });
                return LuaValue.NIL;
            }
        });

        api.set("place", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue nameArg, LuaValue xArg, LuaValue yArg) {
                if (cancelRequested) {
                    throw new LuaError("Script cancelled.");
                }
                String entityName = nameArg.checkjstring();
                double x = xArg.checkdouble();
                double y = yArg.checkdouble();
                sceneController.getActiveState().findByName(entityName).ifPresent(entity -> {
                    entity.setOnMap(true);
                    entity.setXFraction(x);
                    entity.setYFraction(y);
                    sceneController.getActiveState().entityChanged();
                    mapController.getMapCanvas().syncTokens();
                    logController.logPlaceToken(entity, x, y);
                });
                return LuaValue.NIL;
            }
        });

        api.set("addEntity", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue nameArg, LuaValue sizeArg, LuaValue isPlayerArg) {
                if (cancelRequested) {
                    throw new LuaError("Script cancelled.");
                }
                String entityName = nameArg.checkjstring();
                double size = sizeArg.checkdouble();
                boolean isPlayer = isPlayerArg.checkboolean();
                sceneController.addEntityToSession(entityName, size, isPlayer);
                sceneController.getActiveState().findByName(entityName)
                        .ifPresent(logController::logAddEntity);
                outputPanel.log("SCRIPT", "Added " + (isPlayer ? "player" : "character")
                        + ": " + entityName + " (" + size + "ft)");
                return LuaValue.NIL;
            }
        });

        return api;
    }

    private static LuaTable entityToTable(Entity e) {
        LuaTable t = new LuaTable();
        t.set("name", LuaValue.valueOf(e.getName()));
        t.set("onMap", LuaValue.valueOf(e.isOnMap()));
        t.set("xFraction", LuaValue.valueOf(e.getXFraction()));
        t.set("yFraction", LuaValue.valueOf(e.getYFraction()));
        t.set("sizeInFeet", LuaValue.valueOf(e.getSizeInFeet()));
        t.set("type", LuaValue.valueOf(e.getEntityType()));
        LuaTable status = new LuaTable();
        List<String> effects = e.getStatusEffects();
        for (int i = 0; i < effects.size(); i++) {
            status.set(i + 1, LuaValue.valueOf(effects.get(i)));
        }
        t.set("status", status);
        return t;
    }
}
