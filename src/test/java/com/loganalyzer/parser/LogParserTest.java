package com.loganalyzer.parser;

import com.loganalyzer.model.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LogParser}.
 */
class LogParserTest {

    private LogParser parser;

    @BeforeEach
    void setUp() {
        parser = new LogParser();
    }

    // -------------------------------------------------------------------------
    // Happy-path parsing
    // -------------------------------------------------------------------------

    @Test
    void parseValidLine_returnsCorrectEntry() {
        LogEntry entry = parser.parse("2026-03-06 14:23:45 ERROR 192.168.1.105 500 Database connection timeout");

        assertNotNull(entry);
        assertEquals(LocalDateTime.of(2026, 3, 6, 14, 23, 45), entry.getTimestamp());
        assertEquals(LogEntry.Level.ERROR, entry.getLevel());
        assertEquals("192.168.1.105", entry.getSource());
        assertEquals(500, entry.getStatusCode());
        assertEquals("Database connection timeout", entry.getMessage());
    }

    @Test
    void parseAllLevels() {
        assertLevel("INFO",  LogEntry.Level.INFO);
        assertLevel("WARN",  LogEntry.Level.WARN);
        assertLevel("ERROR", LogEntry.Level.ERROR);
        assertLevel("DEBUG", LogEntry.Level.DEBUG);
        assertLevel("FATAL", LogEntry.Level.FATAL);
    }

    @Test
    void parseLineWithMultiWordMessage() {
        LogEntry entry = parser.parse("2026-01-15 08:00:00 INFO 10.0.0.1 200 Request processed successfully today");

        assertNotNull(entry);
        assertEquals("Request processed successfully today", entry.getMessage());
    }

    @Test
    void parseLineWithServiceNameSource() {
        LogEntry entry = parser.parse("2026-06-01 12:30:00 WARN payment-service 503 Service temporarily unavailable");

        assertNotNull(entry);
        assertEquals("payment-service", entry.getSource());
        assertEquals(LogEntry.Level.WARN, entry.getLevel());
        assertEquals(503, entry.getStatusCode());
    }

    @Test
    void parseLevelCaseInsensitive() {
        LogEntry entry = parser.parse("2026-03-06 14:23:45 error 192.168.1.1 404 not found");
        assertNotNull(entry);
        assertEquals(LogEntry.Level.ERROR, entry.getLevel());
    }

    // -------------------------------------------------------------------------
    // Malformed input handling
    // -------------------------------------------------------------------------

    @Test
    void parseNullLine_returnsNull() {
        assertNull(parser.parse(null));
        assertEquals(1, parser.getMalformedCount());
    }

    @Test
    void parseBlankLine_returnsNull() {
        assertNull(parser.parse("   "));
        assertEquals(1, parser.getMalformedCount());
    }

    @Test
    void parseTooFewTokens_returnsNull() {
        assertNull(parser.parse("2026-03-06 14:23:45 ERROR 192.168.1.1"));
        assertEquals(1, parser.getMalformedCount());
    }

    @Test
    void parseInvalidLevel_returnsNull() {
        assertNull(parser.parse("2026-03-06 14:23:45 UNKNOWN 192.168.1.1 200 msg"));
        assertEquals(1, parser.getMalformedCount());
    }

    @Test
    void parseInvalidTimestamp_returnsNull() {
        assertNull(parser.parse("not-a-date 14:23:45 INFO 192.168.1.1 200 msg"));
        assertEquals(1, parser.getMalformedCount());
    }

    @Test
    void parseNonNumericStatusCode_returnsNull() {
        assertNull(parser.parse("2026-03-06 14:23:45 INFO 192.168.1.1 XYZ msg"));
        assertEquals(1, parser.getMalformedCount());
    }

    @Test
    void malformedCountAccumulates() {
        parser.parse(null);
        parser.parse("bad line");
        parser.parse("2026-03-06 14:23:45 INFO 1.1.1.1 200 ok"); // valid — no increment
        parser.parse("also bad");
        assertEquals(3, parser.getMalformedCount());
    }

    @Test
    void resetMalformedCount_resetsToZero() {
        parser.parse(null);
        parser.parse(null);
        assertEquals(2, parser.getMalformedCount());

        parser.resetMalformedCount();
        assertEquals(0, parser.getMalformedCount());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void assertLevel(String levelStr, LogEntry.Level expected) {
        LogEntry entry = parser.parse("2026-01-01 00:00:00 " + levelStr + " 127.0.0.1 200 msg");
        assertNotNull(entry, "Expected non-null for level: " + levelStr);
        assertEquals(expected, entry.getLevel());
    }
}
