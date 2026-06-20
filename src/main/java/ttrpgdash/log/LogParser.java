package ttrpgdash.log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a session log file and parses it into a list of {@link LogEntry} objects.
 * Entries that reference unknown event types are silently skipped.
 * Delegates per-entry parsing to {@link LogWriter#parseSingle(String)} to avoid
 * duplicating the format logic.
 */
public final class LogParser {

    /**
     * Parses the log file at the given path and returns all recognised entries.
     */
    public List<LogEntry> parse(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<LogEntry> entries = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# ") && current.length() > 0) {
                // Flush previous entry block
                LogEntry entry = LogWriter.parseSingle(current.toString());
                if (entry != null) {
                    entries.add(entry);
                }
                current.setLength(0);
            }
            current.append(line).append('\n');
        }

        // Flush final block
        if (current.length() > 0) {
            LogEntry entry = LogWriter.parseSingle(current.toString());
            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }
}
