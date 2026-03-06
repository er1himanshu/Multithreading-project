package com.loganalyzer.report;

import com.loganalyzer.aggregator.MetricsAggregator;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Formats and prints a human-readable analysis summary to standard output.
 */
public class ReportGenerator {

    /**
     * Prints a full analysis report to {@code System.out}.
     *
     * @param aggregator    the aggregated metrics to report on
     * @param numFiles      the number of log files that were processed
     * @param numThreads    the number of worker threads used
     * @param elapsedMillis total wall-clock time of the multi-threaded analysis (ms)
     */
    public void printReport(MetricsAggregator aggregator, int numFiles,
                            int numThreads, long elapsedMillis) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("            CONCURRENT LOG ANALYSIS REPORT                 ");
        System.out.println("============================================================");
        System.out.printf("  Files processed   : %,d%n", numFiles);
        System.out.printf("  Threads used      : %d%n", numThreads);
        System.out.printf("  Processing time   : %,d ms%n", elapsedMillis);
        System.out.println("------------------------------------------------------------");

        // Totals
        System.out.printf("  Total lines       : %,d%n", aggregator.getTotalLines());
        System.out.printf("  Malformed (skipped): %,d%n", aggregator.getMalformedLines());
        System.out.println("------------------------------------------------------------");

        // Breakdown by log level
        System.out.println("  Log Level Distribution:");
        Map<String, Long> byLevel = new TreeMap<>(aggregator.getCountsByLevel());
        byLevel.forEach((level, count) ->
                System.out.printf("    %-8s : %,d%n", level, count));
        System.out.println("------------------------------------------------------------");

        // Status code distribution
        System.out.println("  HTTP Status Code Distribution:");
        Map<Integer, Long> byStatus = new TreeMap<>(aggregator.getCountsByStatusCode());
        byStatus.forEach((code, count) ->
                System.out.printf("    %d : %,d%n", code, count));
        System.out.println("------------------------------------------------------------");

        // Top 10 sources
        System.out.println("  Top 10 Source IPs / Services:");
        List<Map.Entry<String, Long>> topSources = aggregator.getTopSources(10);
        if (topSources.isEmpty()) {
            System.out.println("    (none)");
        } else {
            int rank = 1;
            for (Map.Entry<String, Long> entry : topSources) {
                System.out.printf("    %2d. %-20s %,d requests%n",
                        rank++, entry.getKey(), entry.getValue());
            }
        }
        System.out.println("============================================================");
        System.out.println();
    }
}
