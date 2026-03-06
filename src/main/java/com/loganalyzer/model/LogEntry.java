package com.loganalyzer.model;

import java.time.LocalDateTime;

/**
 * Immutable model representing a single parsed log line.
 * Fields: timestamp, log level, source (IP/service), message, and HTTP status code.
 */
public final class LogEntry {

    /** Supported log severity levels. */
    public enum Level {
        INFO, WARN, ERROR, DEBUG, FATAL
    }

    private final LocalDateTime timestamp;
    private final Level level;
    private final String source;
    private final String message;
    private final int statusCode;

    /**
     * Constructs a new LogEntry with all fields.
     *
     * @param timestamp  the date/time of the log event
     * @param level      the severity level
     * @param source     the originating IP address or service name
     * @param message    the log message text
     * @param statusCode the HTTP status code (e.g. 200, 404, 500)
     */
    public LogEntry(LocalDateTime timestamp, Level level, String source, String message, int statusCode) {
        this.timestamp = timestamp;
        this.level = level;
        this.source = source;
        this.message = message;
        this.statusCode = statusCode;
    }

    /** @return the timestamp of this log entry */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /** @return the log severity level */
    public Level getLevel() {
        return level;
    }

    /** @return the source IP address or service name */
    public String getSource() {
        return source;
    }

    /** @return the log message text */
    public String getMessage() {
        return message;
    }

    /** @return the HTTP status code */
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return String.format("%s %s %s %d %s", timestamp, level, source, statusCode, message);
    }
}
