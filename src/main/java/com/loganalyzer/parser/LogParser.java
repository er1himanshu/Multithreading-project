package com.loganalyzer.parser;

import com.loganalyzer.model.LogEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parses raw log lines into {@link LogEntry} objects.
 *
 * <p>Expected format:
 * <pre>
 *   2026-03-06 14:23:45 ERROR 192.168.1.105 500 Database connection timeout
 * </pre>
 * Fields: {@code <timestamp> <level> <source> <statusCode> <message...>}
 *
 * <p>Malformed lines are skipped; their count is tracked internally.
 */
public class LogParser {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong malformedCount = new AtomicLong(0);

    /**
     * Parses a single log line.
     *
     * @param line the raw log line
     * @return a {@link LogEntry}, or {@code null} if the line is malformed
     */
    public LogEntry parse(String line) {
        if (line == null || line.isBlank()) {
            malformedCount.incrementAndGet();
            return null;
        }

        // Format: YYYY-MM-DD HH:MM:SS LEVEL SOURCE STATUS_CODE MESSAGE...
        // The timestamp uses two tokens (date and time), so split into at most 6 parts
        String[] parts = line.strip().split("\\s+", 6);
        if (parts.length < 6) {
            malformedCount.incrementAndGet();
            return null;
        }

        try {
            String dateTimeStr = parts[0] + " " + parts[1];
            LocalDateTime timestamp = LocalDateTime.parse(dateTimeStr, FORMATTER);

            LogEntry.Level level = LogEntry.Level.valueOf(parts[2].toUpperCase());

            String source = parts[3];

            int statusCode = Integer.parseInt(parts[4]);

            String message = parts[5];

            return new LogEntry(timestamp, level, source, message, statusCode);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            malformedCount.incrementAndGet();
            return null;
        }
    }

    /**
     * Returns the count of malformed lines encountered so far.
     *
     * @return number of malformed log lines
     */
    public long getMalformedCount() {
        return malformedCount.get();
    }

    /**
     * Resets the malformed line counter to zero.
     */
    public void resetMalformedCount() {
        malformedCount.set(0);
    }
}
