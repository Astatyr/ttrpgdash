package ttrpgdash.log;

/**
 * All action types that can appear in a session log.
 * The enum name is used to derive the text header written to the log file
 * (e.g. MOVE_PLAYER → "Move Player").
 */
public enum LogEvent {

    START_LOG,
    SCENE_SNAPSHOT,
    END_LOG,

    ADD_PLAYER,
    REMOVE_PLAYER,
    ADD_CHARACTER,
    REMOVE_CHARACTER,

    PLACE_PLAYER,
    PLACE_CHARACTER,
    MOVE_PLAYER,
    MOVE_CHARACTER,
    REMOVE_FROM_MAP,

    MOUNT,
    DISMOUNT,

    ADD_STATUS_EFFECT,
    REMOVE_STATUS_EFFECT,

    ADD_MAP,
    CHANGE_MAP_SIZE,

    ADD_SCENE,
    DELETE_SCENE,
    RENAME_SCENE,
    SWITCH_SCENE
}
