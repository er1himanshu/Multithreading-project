package com.loganalyzer.analyzer;

import com.loganalyzer.aggregator.MetricsAggregator;
import com.loganalyzer.model.LogEntry;
import com.loganalyzer.parser.LogParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A {@link Callable} task that processes a single log file and returns partial metrics.
 *
 * <p>Each task reads the file line-by-line (memory-efficient) using a {@link BufferedReader},
 * parses each line with a {@link LogParser}, and accumulates counts into local maps
 * that are returned to the caller without any shared mutable state.
 */
public class LogFileTask implements Callable<Map<String, Object>> {

    private final File logFile;

    /**
     * Creates a new task for the given log file.
     *
     * @param logFile the log file to process
     */
    public LogFileTask(File logFile) {
        this.logFile = logFile;
    }

    /**
     * Reads and parses the log file, accumulating partial metrics.
     *
     * @return a map containing:
     *         <ul>
     *           <li>{@code "levelCounts"} – {@code Map<String, Long>}</li>
     *           <li>{@code "sourceCounts"} – {@code Map<String, Long>}</li>
     *           <li>{@code "statusCounts"} – {@code Map<Integer, Long>}</li>
     *           <li>{@code "totalLines"} – {@code Long}</li>
     *           <li>{@code "malformedLines"} – {@code Long}</li>
     *         </ul>
     * @throws IOException if the file cannot be read
     */
    @Override
    public Map<String, Object> call() throws IOException {
        LogParser parser = new LogParser();

        Map<String, Long> levelCounts = new HashMap<>();
        Map<String, Long> sourceCounts = new HashMap<>();
        Map<Integer, Long> statusCounts = new HashMap<>();
        long totalLines = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = parser.parse(line);
                if (entry != null) {
                    totalLines++;
                    levelCounts.merge(entry.getLevel().name(), 1L, Long::sum);
                    sourceCounts.merge(entry.getSource(), 1L, Long::sum);
                    statusCounts.merge(entry.getStatusCode(), 1L, Long::sum);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("levelCounts", levelCounts);
        result.put("sourceCounts", sourceCounts);
        result.put("statusCounts", statusCounts);
        result.put("totalLines", totalLines);
        result.put("malformedLines", parser.getMalformedCount());
        return result;
    }

    /**
     * Returns the log file being processed by this task.
     *
     * @return the log file
     */
    public File getLogFile() {
        return logFile;
    }
}
