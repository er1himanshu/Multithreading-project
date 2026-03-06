package com.loganalyzer.aggregator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MetricsAggregator}.
 */
class MetricsAggregatorTest {

    private MetricsAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new MetricsAggregator();
    }

    // -------------------------------------------------------------------------
    // Basic increment tests
    // -------------------------------------------------------------------------

    @Test
    void incrementLevel_tracksCount() {
        aggregator.incrementLevel("ERROR");
        aggregator.incrementLevel("ERROR");
        aggregator.incrementLevel("INFO");

        Map<String, Long> counts = aggregator.getCountsByLevel();
        assertEquals(2L, counts.get("ERROR"));
        assertEquals(1L, counts.get("INFO"));
    }

    @Test
    void incrementSource_tracksCount() {
        aggregator.incrementSource("10.0.0.1");
        aggregator.incrementSource("10.0.0.1");
        aggregator.incrementSource("10.0.0.2");

        Map<String, Long> counts = aggregator.getCountsBySource();
        assertEquals(2L, counts.get("10.0.0.1"));
        assertEquals(1L, counts.get("10.0.0.2"));
    }

    @Test
    void incrementStatusCode_tracksCount() {
        aggregator.incrementStatusCode(200);
        aggregator.incrementStatusCode(200);
        aggregator.incrementStatusCode(404);

        Map<Integer, Long> counts = aggregator.getCountsByStatusCode();
        assertEquals(2L, counts.get(200));
        assertEquals(1L, counts.get(404));
    }

    @Test
    void addTotalLines_accumulates() {
        aggregator.addTotalLines(100);
        aggregator.addTotalLines(200);
        assertEquals(300L, aggregator.getTotalLines());
    }

    @Test
    void addMalformedLines_accumulates() {
        aggregator.addMalformedLines(5);
        aggregator.addMalformedLines(3);
        assertEquals(8L, aggregator.getMalformedLines());
    }

    // -------------------------------------------------------------------------
    // Merge tests
    // -------------------------------------------------------------------------

    @Test
    void merge_combinesPartialResults() {
        Map<String, Long> levels = new HashMap<>();
        levels.put("ERROR", 10L);
        levels.put("INFO", 50L);

        Map<String, Long> sources = new HashMap<>();
        sources.put("1.2.3.4", 30L);

        Map<Integer, Long> statuses = new HashMap<>();
        statuses.put(200, 45L);
        statuses.put(500, 5L);

        Map<String, Object> partial = new HashMap<>();
        partial.put("levelCounts", levels);
        partial.put("sourceCounts", sources);
        partial.put("statusCounts", statuses);
        partial.put("totalLines", 60L);
        partial.put("malformedLines", 2L);

        aggregator.merge(partial);

        assertEquals(10L, aggregator.getCountsByLevel().get("ERROR"));
        assertEquals(50L, aggregator.getCountsByLevel().get("INFO"));
        assertEquals(30L, aggregator.getCountsBySource().get("1.2.3.4"));
        assertEquals(45L, aggregator.getCountsByStatusCode().get(200));
        assertEquals(5L,  aggregator.getCountsByStatusCode().get(500));
        assertEquals(60L, aggregator.getTotalLines());
        assertEquals(2L,  aggregator.getMalformedLines());
    }

    @Test
    void merge_addsToPreviousValues() {
        aggregator.incrementLevel("ERROR");

        Map<String, Long> levels = new HashMap<>();
        levels.put("ERROR", 9L);
        Map<String, Object> partial = new HashMap<>();
        partial.put("levelCounts", levels);

        aggregator.merge(partial);

        assertEquals(10L, aggregator.getCountsByLevel().get("ERROR"));
    }

    // -------------------------------------------------------------------------
    // Top-N tests
    // -------------------------------------------------------------------------

    @Test
    void getTopSources_returnsTopNInDescendingOrder() {
        aggregator.incrementSource("A");
        aggregator.incrementSource("B");
        aggregator.incrementSource("B");
        aggregator.incrementSource("C");
        aggregator.incrementSource("C");
        aggregator.incrementSource("C");

        List<Map.Entry<String, Long>> top2 = aggregator.getTopSources(2);
        assertEquals(2, top2.size());
        assertEquals("C", top2.get(0).getKey());
        assertEquals(3L, top2.get(0).getValue());
        assertEquals("B", top2.get(1).getKey());
        assertEquals(2L, top2.get(1).getValue());
    }

    @Test
    void getTopSources_withFewerThanNSources() {
        aggregator.incrementSource("X");
        aggregator.incrementSource("Y");

        List<Map.Entry<String, Long>> top10 = aggregator.getTopSources(10);
        assertEquals(2, top10.size());
    }

    @Test
    void getTopSources_emptyAggregator() {
        assertTrue(aggregator.getTopSources(10).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Concurrency tests
    // -------------------------------------------------------------------------

    @Test
    void concurrentIncrements_areThreadSafe() throws InterruptedException {
        int numThreads = 20;
        int incrementsPerThread = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < incrementsPerThread; i++) {
                        aggregator.incrementLevel("INFO");
                        aggregator.addTotalLines(1);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long expected = (long) numThreads * incrementsPerThread;
        assertEquals(expected, aggregator.getCountsByLevel().get("INFO"));
        assertEquals(expected, aggregator.getTotalLines());
    }

    @Test
    void concurrentMerges_areThreadSafe() throws InterruptedException {
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    Map<String, Long> levels = new HashMap<>();
                    levels.put("ERROR", 100L);
                    Map<String, Object> partial = new HashMap<>();
                    partial.put("levelCounts", levels);
                    partial.put("totalLines", 100L);
                    aggregator.merge(partial);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(1000L, aggregator.getCountsByLevel().get("ERROR"));
        assertEquals(1000L, aggregator.getTotalLines());
    }
}
