package ttrpgdash.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles all file I/O for the session log using a random-access {@link FileChannel}.
 *
 * Supports three operations beyond plain writing:
 *   - {@link #write(LogEntry)} returns the byte offset at which the entry was written
 *     so callers can record it for O(1) random access later.
 *   - {@link #truncateTo(long)} physically shortens the file, deleting entries after
 *     a given offset (used when a new action discards redo history).
 *   - {@link #readEntryAt(long)} seeks to a recorded offset and parses one entry.
 *
 * The shared {@link #parseSingle(String)} method is package-private so
 * {@link LogParser} can reuse the same parsing logic without duplication.
 */
public final class LogWriter {

    private FileChannel channel;

    /**
     * Opens (or creates) the log file.
     * Creates parent directories if needed.
     */
    public void open(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        channel = FileChannel.open(path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);
    }

    /**
     * Writes one entry to the end of the file.
     *
     * @return the byte offset at which this entry begins, for later random access.
     */
    public long write(LogEntry entry) throws IOException {
        long offset = channel.size();
        channel.position(offset);
        channel.write(ByteBuffer.wrap(format(entry).getBytes(StandardCharsets.UTF_8)));
        return offset;
    }

    /**
     * Truncates the file to the given byte offset, discarding everything after it.
     */
    public void truncateTo(long offset) throws IOException {
        channel.truncate(offset);
    }

    /**
     * Reads and parses the single log entry that begins at the given byte offset.
     * Returns null if the offset does not point to a recognised entry.
     */
    public LogEntry readEntryAt(long offset) throws IOException {
        channel.position(offset);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteBuffer buf = ByteBuffer.allocate(1);
        int newlineCount = 0;

        while (channel.read(buf) > 0) {
            byte b = buf.get(0);
            baos.write(b);
            if (b == '\n') {
                if (++newlineCount >= 2) {
                    break;
                }
            } else if (b != '\r') {
                newlineCount = 0;
            }
            buf.clear();
        }

        return parseSingle(baos.toString(StandardCharsets.UTF_8));
    }

    /**
     * Closes the file channel. No-op if already closed.
     */
    public void close() throws IOException {
        if (channel != null && channel.isOpen()) {
            channel.close();
            channel = null;
        }
    }

    public boolean isOpen() {
        return channel != null && channel.isOpen();
    }

    private String format(LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(toHeaderName(entry.getEvent())).append('\n');
        for (var field : entry.getFields().entrySet()) {
            sb.append(field.getKey()).append(": ").append(field.getValue()).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Parses a single log entry block (the text between two blank lines).
     * Extracts and removes the {@code Timestamp:} line for the entry timestamp;
     * all remaining key-value lines become the fields map.
     * Returns null if no recognised event header is found.
     */
    static LogEntry parseSingle(String text) {
        LogEvent event = null;
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith("# ")) {
                String header = line.substring(2).trim().toUpperCase().replace(" ", "_");
                try {
                    event = LogEvent.valueOf(header);
                } catch (IllegalArgumentException e) {
                    event = null;
                }
            } else if (event != null && line.contains(": ")) {
                int colon = line.indexOf(": ");
                fields.put(line.substring(0, colon).trim(),
                        line.substring(colon + 2).trim());
            }
        }
        if (event == null) {
            return null;
        }
        return new LogEntry(event, fields, Instant.now());
    }

    private static String toHeaderName(LogEvent event) {
        String[] parts = event.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
