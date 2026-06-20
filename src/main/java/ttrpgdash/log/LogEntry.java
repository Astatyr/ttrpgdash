package ttrpgdash.log;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable record of a single logged action.
 * Fields are stored in insertion order to match the text file format.
 */
public final class LogEntry {

    private final LogEvent event;
    private final Map<String, String> fields;
    private final Instant timestamp;

    /**
     * Creates a log entry with the given event, fields, and timestamp.
     */
    public LogEntry(LogEvent event, Map<String, String> fields, Instant timestamp) {
        this.event = event;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.timestamp = timestamp;
    }

    public LogEvent getEvent() {
        return event;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public String get(String key) {
        return fields.get(key);
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
