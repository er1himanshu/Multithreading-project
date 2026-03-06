package com.loganalyzer.aggregator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Thread-safe metrics aggregator for log analysis results.
 *
 * <p>Uses {@link ConcurrentHashMap} with {@link LongAdder} values for lock-free
 * concurrent updates, and {@link AtomicLong} for scalar counters.
 */
public class MetricsAggregator {

    /** Counts of log entries by level name (INFO, WARN, ERROR, etc.). */
    private final ConcurrentHashMap<String, LongAdder> countsByLevel = new ConcurrentHashMap<>();

    /** Counts of log entries by source IP / service name. */
    private final ConcurrentHashMap<String, LongAdder> countsBySource = new ConcurrentHashMap<>();

    /** Counts of log entries by HTTP status code. */
    private final ConcurrentHashMap<Integer, LongAdder> countsByStatusCode = new ConcurrentHashMap<>();

    /** Total number of successfully parsed log lines. */
    private final AtomicLong totalLines = new AtomicLong(0);

    /** Total number of malformed / unparseable log lines. */
    private final AtomicLong malformedLines = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Increment helpers
    // -------------------------------------------------------------------------

    /**
     * Records one log entry for the given level.
     *
     * @param level the log level name (e.g. "ERROR")
     */
    public void incrementLevel(String level) {
        countsByLevel.computeIfAbsent(level, k -> new LongAdder()).increment();
    }

    /**
     * Records one log entry for the given source.
     *
     * @param source the source IP or service name
     */
    public void incrementSource(String source) {
        countsBySource.computeIfAbsent(source, k -> new LongAdder()).increment();
    }

    /**
     * Records one log entry for the given HTTP status code.
     *
     * @param statusCode the HTTP status code
     */
    public void incrementStatusCode(int statusCode) {
        countsByStatusCode.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
    }

    /**
     * Increments the total successfully-parsed line counter by {@code count}.
     *
     * @param count number of lines to add
     */
    public void addTotalLines(long count) {
        totalLines.addAndGet(count);
    }

    /**
     * Increments the malformed-line counter by {@code count}.
     *
     * @param count number of malformed lines to add
     */
    public void addMalformedLines(long count) {
        malformedLines.addAndGet(count);
    }

    // -------------------------------------------------------------------------
    // Merge from partial results
    // -------------------------------------------------------------------------

    /**
     * Merges partial metrics produced by a single worker task into this aggregator.
     *
     * @param partial the partial metrics map with keys:
     *                {@code "levelCounts"}, {@code "sourceCounts"},
     *                {@code "statusCounts"}, {@code "totalLines"}, {@code "malformedLines"}
     */
    @SuppressWarnings("unchecked")
    public void merge(Map<String, Object> partial) {
        Map<String, Long> levelCounts = (Map<String, Long>) partial.get("levelCounts");
        if (levelCounts != null) {
            levelCounts.forEach((k, v) -> {
                countsByLevel.computeIfAbsent(k, x -> new LongAdder()).add(v);
            });
        }

        Map<String, Long> sourceCounts = (Map<String, Long>) partial.get("sourceCounts");
        if (sourceCounts != null) {
            sourceCounts.forEach((k, v) -> {
                countsBySource.computeIfAbsent(k, x -> new LongAdder()).add(v);
            });
        }

        Map<Integer, Long> statusCounts = (Map<Integer, Long>) partial.get("statusCounts");
        if (statusCounts != null) {
            statusCounts.forEach((k, v) -> {
                countsByStatusCode.computeIfAbsent(k, x -> new LongAdder()).add(v);
            });
        }

        Object tl = partial.get("totalLines");
        if (tl instanceof Long l) {
            totalLines.addAndGet(l);
        }

        Object ml = partial.get("malformedLines");
        if (ml instanceof Long l) {
            malformedLines.addAndGet(l);
        }
    }

    // -------------------------------------------------------------------------
    // Top-N query
    // -------------------------------------------------------------------------

    /**
     * Returns the top {@code n} source IPs/services by request count, sorted descending.
     *
     * @param n the maximum number of entries to return
     * @return list of {@code Map.Entry<String, Long>} pairs, largest count first
     */
    public List<Map.Entry<String, Long>> getTopSources(int n) {
        return countsBySource.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().sum()))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(n)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Getters (snapshot copies)
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot map of counts by log level.
     *
     * @return map from level name to count
     */
    public Map<String, Long> getCountsByLevel() {
        return countsByLevel.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

    /**
     * Returns a snapshot map of counts by source IP/service.
     *
     * @return map from source to count
     */
    public Map<String, Long> getCountsBySource() {
        return countsBySource.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

    /**
     * Returns a snapshot map of counts by HTTP status code.
     *
     * @return map from status code to count
     */
    public Map<Integer, Long> getCountsByStatusCode() {
        return countsByStatusCode.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().sum()));
    }

    /**
     * Returns the total number of successfully parsed log lines.
     *
     * @return total line count
     */
    public long getTotalLines() {
        return totalLines.get();
    }

    /**
     * Returns the total number of malformed log lines encountered.
     *
     * @return malformed line count
     */
    public long getMalformedLines() {
        return malformedLines.get();
    }
}
