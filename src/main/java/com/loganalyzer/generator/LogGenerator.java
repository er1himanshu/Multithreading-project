package com.loganalyzer.generator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Utility that generates realistic sample log files for testing and benchmarking.
 *
 * <p>Can be invoked from the command line:
 * <pre>
 *   java com.loganalyzer.generator.LogGenerator &lt;outputDir&gt; &lt;numFiles&gt; &lt;linesPerFile&gt;
 * </pre>
 */
public class LogGenerator {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Distribution weights: INFO 50%, WARN 20%, ERROR 15%, DEBUG 10%, FATAL 5% */
    private static final String[] LEVELS = {
            "INFO", "INFO", "INFO", "INFO", "INFO",
            "WARN", "WARN", "WARN", "WARN", "WARN",
            "WARN", "WARN", "WARN", "WARN", "WARN",
            "ERROR", "ERROR", "ERROR", "ERROR", "ERROR",
            "ERROR", "ERROR", "ERROR", "ERROR", "ERROR",
            "DEBUG", "DEBUG", "DEBUG", "DEBUG", "DEBUG",
            "DEBUG", "DEBUG",
            "FATAL", "FATAL", "FATAL"
    };

    private static final int[] STATUS_CODES = {
            200, 200, 200, 200, 200, 200, 200,
            201, 201,
            204,
            301,
            302,
            400, 400,
            401,
            403,
            404, 404, 404,
            500, 500, 500,
            502,
            503
    };

    private static final String[] MESSAGES = {
            "Request processed successfully",
            "User authentication successful",
            "Database query executed",
            "Cache miss - fetching from database",
            "Cache hit - returning cached response",
            "Scheduled job started",
            "Scheduled job completed",
            "Connection pool exhausted - waiting",
            "Slow query detected - took 2500ms",
            "Memory usage above 80 percent threshold",
            "Disk usage critical - 95 percent full",
            "Database connection timeout",
            "Service unavailable - downstream failure",
            "Rate limit exceeded for client",
            "Invalid request parameters",
            "Unauthorized access attempt blocked",
            "File not found - returning 404",
            "Internal server error in payment module",
            "SSL certificate expiring in 7 days",
            "Backup completed successfully",
            "Health check passed",
            "Health check failed - service degraded",
            "Configuration reloaded",
            "Worker thread restarted after crash",
            "Circuit breaker OPEN for service orders"
    };

    private final Random random;

    /** Creates a LogGenerator with a random seed. */
    public LogGenerator() {
        this.random = new Random();
    }

    /** Creates a LogGenerator with the given seed (for reproducible output). */
    public LogGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generates {@code numFiles} log files, each containing {@code linesPerFile} entries,
     * written into {@code outputDir}.
     *
     * @param outputDir    directory in which to write the generated files
     * @param numFiles     number of log files to generate
     * @param linesPerFile number of log lines per file
     * @throws IOException if the output directory cannot be created or files cannot be written
     */
    public void generate(File outputDir, int numFiles, int linesPerFile) throws IOException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory: " + outputDir.getAbsolutePath());
        }

        System.out.printf("Generating %d log file(s) with %,d lines each in: %s%n",
                numFiles, linesPerFile, outputDir.getAbsolutePath());

        for (int i = 1; i <= numFiles; i++) {
            File file = new File(outputDir, String.format("server-%02d.log", i));
            generateFile(file, linesPerFile);
            System.out.printf("  Written: %s%n", file.getName());
        }

        System.out.printf("Done. Total lines: %,d%n", (long) numFiles * linesPerFile);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void generateFile(File file, int linesPerFile) throws IOException {
        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (int i = 0; i < linesPerFile; i++) {
                LocalDateTime ts = baseTime.plusSeconds(i * 2L + random.nextInt(2));
                String level = LEVELS[random.nextInt(LEVELS.length)];
                String source = randomIp();
                int statusCode = STATUS_CODES[random.nextInt(STATUS_CODES.length)];
                String message = MESSAGES[random.nextInt(MESSAGES.length)];

                writer.write(ts.format(FORMATTER));
                writer.write(' ');
                writer.write(level);
                writer.write(' ');
                writer.write(source);
                writer.write(' ');
                writer.write(Integer.toString(statusCode));
                writer.write(' ');
                writer.write(message);
                writer.newLine();
            }
        }
    }

    private String randomIp() {
        return (10 + random.nextInt(246)) + "."
                + (1 + random.nextInt(254)) + "."
                + (1 + random.nextInt(254)) + "."
                + (1 + random.nextInt(254));
    }

    // -------------------------------------------------------------------------
    // Standalone entry point
    // -------------------------------------------------------------------------

    /**
     * Standalone entry point.
     *
     * <p>Usage: {@code java com.loganalyzer.generator.LogGenerator <outputDir> <numFiles> <linesPerFile>}
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: LogGenerator <outputDir> <numFiles> <linesPerFile>");
            System.exit(1);
        }
        try {
            File outputDir = new File(args[0]);
            int numFiles = Integer.parseInt(args[1]);
            int linesPerFile = Integer.parseInt(args[2]);

            new LogGenerator().generate(outputDir, numFiles, linesPerFile);
        } catch (NumberFormatException e) {
            System.err.println("numFiles and linesPerFile must be integers.");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error generating logs: " + e.getMessage());
            System.exit(1);
        }
    }
}
