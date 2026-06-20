package ttrpgdash.log;

import java.io.IOException;

/**
 * Thrown when a session log file cannot be interpreted — for example when the
 * required {@code Start Log} header is absent or when the file is completely empty.
 *
 * Extends {@link IOException} so callers that already handle I/O errors
 * pick this up automatically, while code that needs to distinguish parse
 * failures from read failures can catch it specifically.
 */
public class LogParseException extends IOException {

    /**
     * Creates the exception with a descriptive message.
     */
    public LogParseException(String message) {
        super(message);
    }

    /**
     * Creates the exception wrapping the underlying cause.
     */
    public LogParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
