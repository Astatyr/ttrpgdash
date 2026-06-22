package ttrpgdash.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import ttrpgdash.OutputPanel;
import ttrpgdash.entity.Entity;
import ttrpgdash.log.LogController;
import ttrpgdash.map.MapController;
import ttrpgdash.scene.SceneController;

/**
 * Manages the sandboxed Lua runtime for the scripting system.
 *
 * Responsibilities: sandbox setup, script execution, project-script loading,
 * ability file reading, and cancel coordination.
 * The actual {@code dashboard} API surface is defined in {@link DashboardApi}.
 *
 * The same {@link Globals} instance persists for the session so functions
 * defined in {@code data/scripts/project.lua} are available to all later scripts.
 */
public final class ScriptEngine {

    private static final String PROJECT_SCRIPT = "data/scripts/project.lua";

    private final Globals globals;
    private final OutputPanel outputPanel;
    private final DashboardApi dashboardApi;

    /**
     * Creates the engine, sandboxes the globals, and installs the dashboard API.
     * Call {@link #loadProjectScript()} after construction to load global definitions.
     */
    public ScriptEngine(OutputPanel outputPanel, SceneController sceneController,
            MapController mapController, LogController logController) {
        this.outputPanel = outputPanel;
        globals = JsePlatform.standardGlobals();
        sandboxGlobals(globals);
        dashboardApi = new DashboardApi(outputPanel, sceneController,
                mapController, logController);
        globals.set("dashboard", dashboardApi.build());
    }

    /**
     * Signals any currently running script to stop at its next API call.
     * Called by undo and redo so stepping back always wins over a running script.
     */
    public void cancel() {
        dashboardApi.cancel();
    }

    /**
     * Loads {@code data/scripts/project.lua} if it exists.
     * Functions defined there are available to all subsequent script executions.
     */
    public void loadProjectScript() {
        Path path = Paths.get(PROJECT_SCRIPT);
        if (Files.exists(path)) {
            execute(path);
        }
    }

    /**
     * Executes the Lua file at the given path.
     * Errors are caught and printed to the output panel rather than thrown.
     */
    public void execute(Path scriptFile) {
        dashboardApi.reset();
        try {
            String code = Files.readString(scriptFile);
            globals.load(code, scriptFile.getFileName().toString()).call();
        } catch (LuaError e) {
            outputPanel.log("SYSTEM", "Script error in "
                    + scriptFile.getFileName() + ": " + e.getMessage());
        } catch (IOException e) {
            outputPanel.log("SYSTEM", "Could not read "
                    + scriptFile.getFileName() + ": " + e.getMessage());
        }
    }

    /**
     * Executes a named Lua function that was defined by a previously loaded script.
     * No-op with a message if the function does not exist.
     */
    public void callFunction(String functionName) {
        dashboardApi.reset();
        LuaValue fn = globals.get(functionName);
        if (fn.isnil() || !fn.isfunction()) {
            outputPanel.log("SYSTEM", "Function not found: " + functionName);
            return;
        }
        try {
            fn.call();
        } catch (LuaError e) {
            outputPanel.log("SYSTEM",
                    "Error in " + functionName + ": " + e.getMessage());
        }
    }

    /**
     * Reads an {@code abilities.lua} file in an isolated sandbox and returns the
     * ability names and descriptions without touching the main execution globals.
     * Returns an empty list if the file cannot be read or defines no abilities table.
     */
    public List<String[]> getAbilityList(Path abilitiesFile) {
        Globals temp = JsePlatform.standardGlobals();
        sandboxGlobals(temp);
        try {
            temp.load(Files.readString(abilitiesFile),
                    abilitiesFile.getFileName().toString()).call();
            LuaValue raw = temp.get("abilities");
            if (!raw.istable()) {
                return List.of();
            }
            LuaTable table = (LuaTable) raw;
            List<String[]> result = new ArrayList<>();
            for (int i = 1; i <= table.length(); i++) {
                LuaValue entry = table.get(i);
                if (entry.istable()) {
                    String abilityName = entry.get("name").optjstring("Ability " + i);
                    String desc = entry.get("desc").optjstring("");
                    result.add(new String[]{abilityName, desc});
                }
            }
            return result;
        } catch (LuaError | IOException e) {
            outputPanel.log("SYSTEM", "Could not read abilities: "
                    + e.getMessage());
            return List.of();
        }
    }

    /**
     * Loads an {@code abilities.lua} file into the main runtime, injects {@code self}
     * as the triggering entity, then calls the named ability's {@code run()} function.
     */
    public void runAbility(Path abilitiesFile, String abilityName, Entity entity) {
        dashboardApi.reset();
        try {
            globals.set("self", DashboardApi.entityToTable(entity));
            globals.load(Files.readString(abilitiesFile),
                    abilitiesFile.getFileName().toString()).call();
            LuaValue raw = globals.get("abilities");
            if (!raw.istable()) {
                outputPanel.log("SYSTEM",
                        "No abilities table in " + abilitiesFile.getFileName());
                return;
            }
            LuaTable table = (LuaTable) raw;
            for (int i = 1; i <= table.length(); i++) {
                LuaValue entry = table.get(i);
                if (!entry.istable()) {
                    continue;
                }
                if (abilityName.equals(entry.get("name").optjstring(""))) {
                    LuaValue runFn = entry.get("run");
                    if (runFn.isfunction()) {
                        outputPanel.log("SCRIPT",
                                abilityName + " [" + entity.getName() + "]");
                        runFn.call();
                    }
                    return;
                }
            }
            outputPanel.log("SYSTEM", "Ability not found: " + abilityName);
        } catch (LuaError e) {
            outputPanel.log("SYSTEM",
                    "Error in " + abilityName + ": " + e.getMessage());
        } catch (IOException e) {
            outputPanel.log("SYSTEM",
                    "Could not read " + abilitiesFile.getFileName() + ": " + e.getMessage());
        }
    }

    private static void sandboxGlobals(Globals g) {
        g.set("io", LuaValue.NIL);
        g.set("os", LuaValue.NIL);
        g.set("dofile", LuaValue.NIL);
        g.set("loadfile", LuaValue.NIL);
        g.set("load", LuaValue.NIL);
        g.set("require", LuaValue.NIL);
        g.set("package", LuaValue.NIL);
        g.set("debug", LuaValue.NIL);
    }
}
