package ttrpgdash.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a session log file and parses it into a list of {@link LogEntry} objects.
 * Entries that reference unknown event types are silently skipped.
 */
public final class LogParser {

    /**
     * Parses the log file at the given path and returns all recognised entries.
     */
    public List<LogEntry> parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<LogEntry> entries = new ArrayList<>();

        LogEvent currentEvent = null;
        Map<String, String> currentFields = new LinkedHashMap<>();

        for (String line : lines) {
            if (line.startsWith("# ")) {
                if (currentEvent != null) {
                    entries.add(new LogEntry(currentEvent, currentFields, Instant.now()));
                    currentFields = new LinkedHashMap<>();
                }
                String header = line.substring(2).trim().toUpperCase().replace(" ", "_");
                try {
                    currentEvent = LogEvent.valueOf(header);
                } catch (IllegalArgumentException e) {
                    currentEvent = null;
                }
            } else if (currentEvent != null && line.contains(": ")) {
                int colon = line.indexOf(": ");
                currentFields.put(line.substring(0, colon).trim(),
                        line.substring(colon + 2).trim());
            }
        }

        if (currentEvent != null) {
            entries.add(new LogEntry(currentEvent, currentFields, Instant.now()));
        }

        return entries;
    }
}
