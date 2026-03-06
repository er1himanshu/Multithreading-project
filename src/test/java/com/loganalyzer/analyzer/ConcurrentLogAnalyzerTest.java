package com.loganalyzer.analyzer;

import com.loganalyzer.aggregator.MetricsAggregator;
import com.loganalyzer.generator.LogGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ConcurrentLogAnalyzer}.
 */
class ConcurrentLogAnalyzerTest {

    @TempDir
    Path tempDir;

    private File logDir;

    @BeforeEach
    void setUp() {
        logDir = tempDir.toFile();
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void analyzeEmptyDirectory_returnsNull() throws InterruptedException {
        ConcurrentLogAnalyzer analyzer = new ConcurrentLogAnalyzer(4);
        assertNull(analyzer.analyze(logDir));
    }

    @Test
    void analyzeSingleFile_countsTotalLines() throws IOException, InterruptedException {
        writeLogFile("test.log",
                "2026-01-01 00:00:00 INFO 10.0.0.1 200 ok",
                "2026-01-01 00:00:01 ERROR 10.0.0.2 500 fail",
                "2026-01-01 00:00:02 WARN 10.0.0.1 404 missing");

        ConcurrentLogAnalyzer analyzer = new ConcurrentLogAnalyzer(2);
        MetricsAggregator result = analyzer.analyze(logDir);

        assertNotNull(result);
        assertEquals(3L, result.getTotalLines());
        assertEquals(0L, result.getMalformedLines());
    }

    @Test
    void analyzeMalformedLines_areTracked() throws IOException, InterruptedException {
        writeLogFile("mixed.log",
                "2026-01-01 00:00:00 INFO 10.0.0.1 200 ok",
                "this is not a valid log line",
                "2026-01-01 00:00:01 ERROR 10.0.0.2 500 fail");

        ConcurrentLogAnalyzer analyzer = new ConcurrentLogAnalyzer(2);
        MetricsAggregator result = analyzer.analyze(logDir);

        assertNotNull(result);
        assertEquals(2L, result.getTotalLines());
        assertEquals(1L, result.getMalformedLines());
    }

    @Test
    void analyzeLevelCounts_areCorrect() throws IOException, InterruptedException {
        writeLogFile("levels.log",
                "2026-01-01 00:00:00 INFO 1.1.1.1 200 a",
                "2026-01-01 00:00:01 INFO 1.1.1.1 200 b",
                "2026-01-01 00:00:02 ERROR 1.1.1.1 500 c",
                "2026-01-01 00:00:03 WARN 1.1.1.1 400 d",
                "2026-01-01 00:00:04 FATAL 1.1.1.1 503 e",
                "2026-01-01 00:00:05 DEBUG 1.1.1.1 200 f");

        MetricsAggregator result = new ConcurrentLogAnalyzer(2).analyze(logDir);

        assertNotNull(result);
        assertEquals(2L, result.getCountsByLevel().get("INFO"));
        assertEquals(1L, result.getCountsByLevel().get("ERROR"));
        assertEquals(1L, result.getCountsByLevel().get("WARN"));
        assertEquals(1L, result.getCountsByLevel().get("FATAL"));
        assertEquals(1L, result.getCountsByLevel().get("DEBUG"));
    }

    // -------------------------------------------------------------------------
    // Multi-file correctness
    // -------------------------------------------------------------------------

    @Test
    void analyzeMultipleFiles_aggregatesAllLines() throws IOException, InterruptedException {
        writeLogFile("a.log",
                "2026-01-01 00:00:00 INFO 1.1.1.1 200 msg");
        writeLogFile("b.log",
                "2026-01-01 00:00:01 ERROR 2.2.2.2 500 msg");
        writeLogFile("c.log",
                "2026-01-01 00:00:02 WARN 3.3.3.3 404 msg");

        MetricsAggregator result = new ConcurrentLogAnalyzer(3).analyze(logDir);

        assertNotNull(result);
        assertEquals(3L, result.getTotalLines());
    }

    // -------------------------------------------------------------------------
    // Thread-count invariance
    // -------------------------------------------------------------------------

    @Test
    void resultsAreIdenticalRegardlessOfThreadCount() throws IOException, InterruptedException {
        // Generate a moderate set of files
        new LogGenerator(42L).generate(logDir, 4, 500);

        MetricsAggregator r1 = new ConcurrentLogAnalyzer(1).analyze(logDir);
        MetricsAggregator r4 = new ConcurrentLogAnalyzer(4).analyze(logDir);
        MetricsAggregator r8 = new ConcurrentLogAnalyzer(8).analyze(logDir);

        assertNotNull(r1);
        assertNotNull(r4);
        assertNotNull(r8);

        // Total lines must be identical across all thread counts
        assertEquals(r1.getTotalLines(), r4.getTotalLines(),
                "Total lines should be equal for 1 vs 4 threads");
        assertEquals(r1.getTotalLines(), r8.getTotalLines(),
                "Total lines should be equal for 1 vs 8 threads");

        // Malformed lines
        assertEquals(r1.getMalformedLines(), r4.getMalformedLines());
        assertEquals(r1.getMalformedLines(), r8.getMalformedLines());

        // Level counts
        assertEquals(r1.getCountsByLevel(), r4.getCountsByLevel());
        assertEquals(r1.getCountsByLevel(), r8.getCountsByLevel());

        // Status code distribution
        assertEquals(r1.getCountsByStatusCode(), r4.getCountsByStatusCode());
        assertEquals(r1.getCountsByStatusCode(), r8.getCountsByStatusCode());
    }

    @Test
    void singleThreadedAndMultiThreaded_sameResults() throws IOException, InterruptedException {
        new LogGenerator(99L).generate(logDir, 3, 200);

        MetricsAggregator single = new ConcurrentLogAnalyzer(1).analyze(logDir);
        MetricsAggregator multi  = new ConcurrentLogAnalyzer(4).analyze(logDir);

        assertNotNull(single);
        assertNotNull(multi);
        assertEquals(single.getTotalLines(), multi.getTotalLines());
        assertEquals(single.getCountsByLevel(), multi.getCountsByLevel());
    }

    // -------------------------------------------------------------------------
    // Large-file correctness
    // -------------------------------------------------------------------------

    @Test
    void analyzeLargeGeneratedFiles_completes() throws IOException, InterruptedException {
        new LogGenerator(7L).generate(logDir, 5, 10_000);

        MetricsAggregator result = new ConcurrentLogAnalyzer(
                Runtime.getRuntime().availableProcessors()).analyze(logDir);

        assertNotNull(result);
        assertTrue(result.getTotalLines() > 0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void writeLogFile(String name, String... lines) throws IOException {
        File f = new File(logDir, name);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        }
    }
}
