# Concurrent Log Analysis Tool

A high-performance, multithreaded command-line tool written in Java 17 for concurrent parsing and analysis of large-scale server log files.

---

## Features

- **Concurrent Processing** — uses `ExecutorService` with a configurable fixed thread pool to process multiple log files simultaneously
- **Memory Efficient** — streams each file line-by-line via `BufferedReader`; never loads an entire file into memory
- **Thread-Safe Aggregation** — `ConcurrentHashMap` with `LongAdder` and `AtomicLong` ensure correct results under concurrent access
- **Log Generation** — built-in generator creates realistic sample log files of any size for benchmarking
- **Performance Benchmarking** — compare single-threaded vs. multi-threaded processing times with a printed speedup ratio
- **Rich Reports** — summary of total lines, malformed lines, log-level distribution, HTTP status code distribution, and top-10 source IPs
- **No External Runtime Dependencies** — only JUnit 5 is needed (test scope); everything else is standard `java.util.concurrent`, `java.io`, `java.nio`

---

## Project Structure

```
Multithreading-project/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/loganalyzer/
    │   ├── Main.java                        # CLI entry point
    │   ├── model/
    │   │   └── LogEntry.java                # Immutable parsed log-line model
    │   ├── parser/
    │   │   └── LogParser.java               # Parses raw lines into LogEntry objects
    │   ├── analyzer/
    │   │   ├── ConcurrentLogAnalyzer.java   # Core multithreaded analyzer
    │   │   └── LogFileTask.java             # Callable task per log file
    │   ├── aggregator/
    │   │   └── MetricsAggregator.java       # Thread-safe metrics aggregation
    │   ├── report/
    │   │   └── ReportGenerator.java         # Console report formatting
    │   └── generator/
    │       └── LogGenerator.java            # Sample log-file generator
    └── test/java/com/loganalyzer/
        ├── parser/
        │   └── LogParserTest.java
        ├── analyzer/
        │   └── ConcurrentLogAnalyzerTest.java
        └── aggregator/
            └── MetricsAggregatorTest.java
```

---

## Log Line Format

```
2026-03-06 14:23:45 ERROR 192.168.1.105 500 Database connection timeout
```

Fields: `<YYYY-MM-DD HH:MM:SS> <LEVEL> <source> <statusCode> <message...>`

Supported levels: `INFO`, `WARN`, `ERROR`, `DEBUG`, `FATAL`

---

## Build

Requires Java 17+ and Maven 3.6+.

```bash
mvn clean package
```

This produces a fat (shaded) JAR at `target/log-analyzer-1.0.0.jar`.

### Run Tests Only

```bash
mvn test
```

---

## Run

### Generate Sample Logs

```bash
java -jar target/log-analyzer-1.0.0.jar --generate 10 100000 --dir ./logs
```

Generates 10 log files with 100 000 lines each inside `./logs/`.

### Analyze Existing Logs

```bash
java -jar target/log-analyzer-1.0.0.jar --dir ./logs --threads 8
```

### Generate and Analyze in One Step

```bash
java -jar target/log-analyzer-1.0.0.jar --generate 10 100000 --dir ./logs --threads 8
```

### Run with Performance Benchmark

```bash
java -jar target/log-analyzer-1.0.0.jar --dir ./logs --threads 8 --benchmark
```

### CLI Options

| Option | Required | Description |
|---|---|---|
| `--dir <path>` | ✅ | Directory containing `*.log` files |
| `--threads <n>` | ❌ | Thread-pool size (default: available processors) |
| `--generate <numFiles> <linesPerFile>` | ❌ | Generate sample logs before analysis |
| `--benchmark` | ❌ | Print single-threaded vs. multi-threaded comparison |

---

## Sample Output

```
============================================================
            CONCURRENT LOG ANALYSIS REPORT
============================================================
  Files processed   : 10
  Threads used      : 8
  Processing time   : 1,234 ms
------------------------------------------------------------
  Total lines       : 1,000,000
  Malformed (skipped): 0
------------------------------------------------------------
  Log Level Distribution:
    DEBUG    : 142,600
    ERROR    : 142,900
    FATAL    :  50,200
    INFO     : 499,800
    WARN     : 164,500
------------------------------------------------------------
  HTTP Status Code Distribution:
    200 : 580,000
    201 :  83,000
    ...
------------------------------------------------------------
  Top 10 Source IPs / Services:
     1. 127.14.203.87         4,321 requests
     2. 45.230.12.6           4,289 requests
    ...
============================================================
```

Benchmark output:

```
=== Performance Comparison ===
Single-threaded:          5,870 ms
Multi-threaded (8 threads): 1,234 ms
Speedup:                  4.76x
```

---

## Architecture Overview

The tool follows a classic **producer–consumer / fan-out–fan-in** pattern:

1. **Main** parses CLI arguments and orchestrates the pipeline.
2. **ConcurrentLogAnalyzer** lists all `*.log` files in the target directory, submits one `LogFileTask` per file to an `ExecutorService` (fixed thread pool), and collects `Future<Map<String, Object>>` handles.
3. **LogFileTask** (one per file) reads the file line-by-line with `BufferedReader`, parses each line with `LogParser`, accumulates local (non-shared) `HashMap` counters, and returns them as a plain map — no synchronization needed inside the task.
4. **MetricsAggregator** merges partial results from every completed future using thread-safe `ConcurrentHashMap<K, LongAdder>`. The `LongAdder` type is preferred over `AtomicLong` for high-contention write paths.
5. **ReportGenerator** reads a snapshot of the aggregator and prints the formatted report.

---

## Technologies Used

- **Java 17** — records-free immutable model, text blocks, pattern switch
- **`java.util.concurrent`** — `ExecutorService`, `Executors`, `Future`, `ConcurrentHashMap`, `AtomicLong`, `LongAdder`, `CountDownLatch`
- **`java.io` / `java.nio`** — `BufferedReader` for streaming file I/O
- **Maven 3** — build, packaging (`maven-shade-plugin` for fat JAR)
- **JUnit 5** — unit and integration testing
