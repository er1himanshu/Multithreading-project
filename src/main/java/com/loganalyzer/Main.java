package com.loganalyzer;

import com.loganalyzer.aggregator.MetricsAggregator;
import com.loganalyzer.analyzer.ConcurrentLogAnalyzer;
import com.loganalyzer.generator.LogGenerator;
import com.loganalyzer.report.ReportGenerator;

import java.io.File;
import java.io.IOException;

/**
 * Command-line entry point for the Concurrent Log Analyzer.
 *
 * <p>Usage:
 * <pre>
 *   java -jar log-analyzer.jar --dir &lt;logDirectory&gt; [--threads &lt;count&gt;] [--generate &lt;numFiles&gt; &lt;linesPerFile&gt;]
 * </pre>
 *
 * <p>Options:
 * <ul>
 *   <li>{@code --dir &lt;path&gt;}         (required) directory containing {@code *.log} files</li>
 *   <li>{@code --threads &lt;n&gt;}        (optional) thread-pool size; defaults to available processors</li>
 *   <li>{@code --generate &lt;n&gt; &lt;m&gt;} (optional) generate {@code n} files of {@code m} lines before analysis</li>
 *   <li>{@code --benchmark}             (optional) run single-threaded first and print speedup comparison</li>
 * </ul>
 */
public class Main {

    private Main() {}

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        // ---- Defaults ----
        String logDirPath = null;
        int threads = Runtime.getRuntime().availableProcessors();
        int generateFiles = 0;
        int generateLines = 0;
        boolean benchmark = false;

        // ---- Parse arguments ----
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dir" -> {
                    if (i + 1 >= args.length) die("--dir requires a path argument");
                    logDirPath = args[++i];
                }
                case "--threads" -> {
                    if (i + 1 >= args.length) die("--threads requires a number");
                    threads = parsePositiveInt(args[++i], "--threads");
                }
                case "--generate" -> {
                    if (i + 2 >= args.length) die("--generate requires <numFiles> <linesPerFile>");
                    generateFiles = parsePositiveInt(args[++i], "--generate numFiles");
                    generateLines = parsePositiveInt(args[++i], "--generate linesPerFile");
                }
                case "--benchmark" -> benchmark = true;
                default -> System.err.println("Unknown argument ignored: " + args[i]);
            }
        }

        if (logDirPath == null) {
            die("--dir <logDirectory> is required.\n"
                    + "Usage: java -jar log-analyzer.jar --dir <path> [--threads <n>] "
                    + "[--generate <numFiles> <linesPerFile>] [--benchmark]");
        }

        File logDir = new File(logDirPath);

        // ---- Optional log generation ----
        if (generateFiles > 0) {
            try {
                new LogGenerator().generate(logDir, generateFiles, generateLines);
            } catch (IOException e) {
                die("Failed to generate log files: " + e.getMessage());
            }
        }

        // ---- Analysis ----
        ConcurrentLogAnalyzer analyzer = new ConcurrentLogAnalyzer(threads);
        MetricsAggregator aggregator;

        try {
            long startMs = System.currentTimeMillis();

            if (benchmark) {
                aggregator = analyzer.analyzeWithBenchmark(logDir);
            } else {
                System.out.printf("Analyzing logs in '%s' using %d thread(s)...%n",
                        logDir.getAbsolutePath(), threads);
                aggregator = analyzer.analyze(logDir);
            }

            long elapsedMs = System.currentTimeMillis() - startMs;

            if (aggregator == null) {
                System.err.println("No results — check that the directory contains *.log files.");
                System.exit(1);
            }

            int numFiles = countLogFiles(logDir);
            new ReportGenerator().printReport(aggregator, numFiles, threads, elapsedMs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            die("Analysis was interrupted.");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int countLogFiles(File dir) {
        if (!dir.isDirectory()) return 0;
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".log"));
        return files == null ? 0 : files.length;
    }

    private static int parsePositiveInt(String value, String context) {
        try {
            int n = Integer.parseInt(value);
            if (n <= 0) die(context + " must be a positive integer, got: " + value);
            return n;
        } catch (NumberFormatException e) {
            die(context + " must be an integer, got: " + value);
            return -1; // unreachable
        }
    }

    private static void die(String message) {
        System.err.println("ERROR: " + message);
        System.exit(1);
    }
}
