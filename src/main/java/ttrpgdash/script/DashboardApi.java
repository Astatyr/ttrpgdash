package ttrpgdash.script;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import ttrpgdash.OutputPanel;
import ttrpgdash.entity.CharacterEntity;
import ttrpgdash.entity.Entity;
import ttrpgdash.entity.PlayerEntity;
import ttrpgdash.log.LogController;
import ttrpgdash.map.MapController;
import ttrpgdash.scene.SceneController;

/**
 * Builds the {@code dashboard} Lua table that scripts call into.
 *
 * Owns the cancel flag so that {@link ScriptEngine#cancel()} can stop the
 * current script at the next API call boundary without ScriptEngine needing
 * to know anything about which calls are in progress.
 */
final class DashboardApi {

    private final OutputPanel outputPanel;
    private final SceneController sceneController;
    private final MapController mapController;
    private final LogController logController;

    /**
     * Set to true by {@link #cancel()} to interrupt a running script at its
     * next API call. Volatile so it is visible across threads.
     */
    private volatile boolean cancelRequested = false;

    DashboardApi(OutputPanel outputPanel, SceneController sceneController,
            MapController mapController, LogController logController) {
        this.outputPanel = outputPanel;
        this.sceneController = sceneController;
        this.mapController = mapController;
        this.logController = logController;
    }

    /** Signals the running script to stop at its next API call. */
    void cancel() {
        cancelRequested = true;
    }

    /** Clears the cancel flag before a new script execution begins. */
    void reset() {
        cancelRequested = false;
    }

    /**
     * Constructs and returns the {@code dashboard} LuaTable to be installed
     * into the script sandbox.
     */
    LuaTable build() {
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
                        .map(entity -> (LuaValue) entityToTable(entity))
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
                        outputPanel.log("SCRIPT",
                                entityName + " is not on the map — use place() first.");
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

        api.set("getPlayers", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable result = new LuaTable();
                int idx = 1;
                for (PlayerEntity p : sceneController.getActiveState().getPlayers()) {
                    result.set(idx++, entityToTable(p));
                }
                return result;
            }
        });

        api.set("getCharacters", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable result = new LuaTable();
                int idx = 1;
                for (CharacterEntity c : sceneController.getActiveState().getCharacters()) {
                    result.set(idx++, entityToTable(c));
                }
                return result;
            }
        });

        return api;
    }

    /**
     * Converts an {@link Entity} to a read-only Lua table.
     * Package-private so {@link ScriptEngine} can use it to set {@code self}
     * before running an entity ability.
     */
    static LuaTable entityToTable(Entity e) {
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
