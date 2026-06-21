package ttrpgdash.scene;

import java.io.IOException;

/**
 * Thrown when a scene JSON file exists on disk but cannot be deserialised —
 * for example when the file is truncated or contains malformed JSON.
 *
 * Distinct from a plain {@link IOException} (file unreadable) so callers can
 * tell the difference between "file missing — start fresh" and "file corrupted
 * — something went wrong".
 */
public final class ScenePersistenceException extends IOException {

    /**
     * Creates the exception with a descriptive message.
     */
    public ScenePersistenceException(String message) {
        super(message);
    }

    /**
     * Creates the exception wrapping the underlying cause.
     */
    public ScenePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
