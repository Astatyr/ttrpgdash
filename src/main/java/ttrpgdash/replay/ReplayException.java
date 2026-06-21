package ttrpgdash.replay;

import java.io.IOException;

/**
 * Thrown when a replay session cannot be set up — for example when the log
 * folder contains no scene snapshot files, or when the active scene recorded
 * in the log has no corresponding snapshot to replay from.
 *
 * Extends {@link IOException} so existing I/O catch blocks handle it,
 * while callers that need to report setup failures can catch it specifically.
 */
public final class ReplayException extends IOException {

    /**
     * Creates the exception with a descriptive message.
     */
    public ReplayException(String message) {
        super(message);
    }

    /**
     * Creates the exception wrapping the underlying cause.
     */
    public ReplayException(String message, Throwable cause) {
        super(message, cause);
    }
}
