package com.loganalyzer.analyzer;

import com.loganalyzer.aggregator.MetricsAggregator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Core multithreaded log analyzer.
 *
 * <p>Accepts a directory of log files and a thread-pool size, distributes one
 * {@link LogFileTask} per file across the pool, waits for all tasks to finish
 * using {@link Future}, and merges results into a {@link MetricsAggregator}.
 *
 * <p>Also supports a single-threaded reference run for performance comparison.
 */
public class ConcurrentLogAnalyzer {

    private final int numThreads;

    /**
     * Creates an analyzer that will use the given number of threads.
     *
     * @param numThreads number of worker threads in the pool
     */
    public ConcurrentLogAnalyzer(int numThreads) {
        this.numThreads = numThreads;
    }

    /**
     * Analyzes all {@code *.log} files in {@code logDir} using the configured thread pool.
     *
     * @param logDir directory containing log files to process
     * @return a populated {@link MetricsAggregator} with aggregated results,
     *         or {@code null} if the directory is invalid or contains no log files
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public MetricsAggregator analyze(File logDir) throws InterruptedException {
        File[] logFiles = listLogFiles(logDir);
        if (logFiles == null || logFiles.length == 0) {
            System.err.println("No .log files found in: " + logDir.getAbsolutePath());
            return null;
        }

        MetricsAggregator aggregator = new MetricsAggregator();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (File file : logFiles) {
                futures.add(executor.submit(new LogFileTask(file)));
            }

            for (Future<Map<String, Object>> future : futures) {
                try {
                    aggregator.merge(future.get());
                } catch (ExecutionException e) {
                    System.err.println("Error processing file: " + e.getCause().getMessage());
                }
            }
        } finally {
            executor.shutdown();
        }

        return aggregator;
    }

    /**
     * Runs analysis single-threaded (one thread), then multi-threaded ({@code numThreads} threads),
     * and prints a performance comparison.
     *
     * @param logDir directory containing log files to process
     * @return the {@link MetricsAggregator} produced by the multi-threaded run
     * @throws InterruptedException if interrupted while waiting for tasks
     */
    public MetricsAggregator analyzeWithBenchmark(File logDir) throws InterruptedException {
        System.out.println("Running single-threaded analysis for benchmarking...");
        ConcurrentLogAnalyzer singleThreaded = new ConcurrentLogAnalyzer(1);
        long startSingle = System.currentTimeMillis();
        singleThreaded.analyze(logDir);
        long singleTime = System.currentTimeMillis() - startSingle;

        System.out.println("Running multi-threaded analysis...");
        long startMulti = System.currentTimeMillis();
        MetricsAggregator result = analyze(logDir);
        long multiTime = System.currentTimeMillis() - startMulti;

        System.out.println();
        System.out.println("=== Performance Comparison ===");
        System.out.printf("Single-threaded:          %,d ms%n", singleTime);
        System.out.printf("Multi-threaded (%d threads): %,d ms%n", numThreads, multiTime);
        if (multiTime > 0) {
            System.out.printf("Speedup:                  %.2fx%n", (double) singleTime / multiTime);
        }
        System.out.println();

        return result;
    }

    /**
     * Returns the number of threads this analyzer uses.
     *
     * @return thread count
     */
    public int getNumThreads() {
        return numThreads;
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static File[] listLogFiles(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".log"));
        if (files != null) {
            Arrays.sort(files);
        }
        return files;
    }
}
