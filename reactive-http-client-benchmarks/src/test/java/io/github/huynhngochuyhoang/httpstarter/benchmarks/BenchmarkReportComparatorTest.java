package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkReportComparatorTest {

    @TempDir
    Path tempDir;

    @Test
    void writesComparisonForMatchingRows() throws Exception {
        Path current = report("current.json",
                result("clientSideOverheadStarterGetNoBody", "avgt", 12.0, "us/op", 11.0, 15.0, 18.0, 1200.0),
                result("clientSideOverheadStarterGetNoBody", "thrpt", 0.08, "ops/us", null, null, null, 1200.0));
        Path baseline = report("baseline.json",
                result("clientSideOverheadStarterGetNoBody", "avgt", 10.0, "us/op", 10.0, 12.0, 15.0, 1000.0),
                result("clientSideOverheadStarterGetNoBody", "thrpt", 0.09, "ops/us", null, null, null, 1000.0));
        Path output = tempDir.resolve("comparison.md");

        int exitCode = run(current, baseline, output);

        assertThat(exitCode).isZero();
        assertThat(Files.readString(output))
                .contains("Matched benchmark/mode rows: **2**")
                .contains("Current-only rows: **0**")
                .contains("Baseline-only rows: **0**")
                .contains("| clientSideOverheadStarterGetNoBody | `avgt` | average time | 12 us/op | 10 us/op | 2 us/op | 20% | review |")
                .contains("| clientSideOverheadStarterGetNoBody | `avgt` | p50 | 11 us/op | 10 us/op | 1 us/op | 10% | ok |")
                .contains("| clientSideOverheadStarterGetNoBody | `thrpt` | throughput | 0.08 ops/us | 0.09 ops/us | -0.01 ops/us | -11.111% | ok |")
                .contains("allocation per operation");
    }

    @Test
    void reportsMissingRowsWithoutFailing() throws Exception {
        Path current = report("current.json", result("clientSideOverheadStarterPostJson", "avgt", 11.0,
                "us/op", null, null, null, null));
        Path baseline = report("baseline.json", result("clientSideOverheadStarterGetNoBody", "avgt", 10.0,
                "us/op", null, null, null, null));
        Path output = tempDir.resolve("missing.md");

        int exitCode = run(current, baseline, output);

        assertThat(exitCode).isZero();
        assertThat(Files.readString(output))
                .contains("Matched benchmark/mode rows: **0**")
                .contains("Current-only rows: **1**")
                .contains("Baseline-only rows: **1**")
                .contains("missing rows remain explicit and are never compared")
                .contains("| clientSideOverheadStarterGetNoBody | `avgt` | row | n/a | n/a | n/a | n/a | missing current |")
                .contains("| clientSideOverheadStarterPostJson | `avgt` | row | n/a | n/a | n/a | n/a | missing baseline |");
    }

    @Test
    void failOnReviewReturnsNonZeroOnlyWhenRequested() throws Exception {
        Path current = report("current.json", result("starterFeatureMicrometerObserverGetNoBody", "avgt", 130.0,
                "us/op", null, null, null, null));
        Path baseline = report("baseline.json", result("starterFeatureMicrometerObserverGetNoBody", "avgt", 100.0,
                "us/op", null, null, null, null));
        Path output = tempDir.resolve("review.md");

        assertThat(run(current, baseline, output)).isZero();
        assertThat(run(current, baseline, output, "--fail-on-review")).isEqualTo(1);
    }

    private int run(Path current, Path baseline, Path output, String... extraArgs) throws IOException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String[] args = new String[6 + extraArgs.length];
        args[0] = "--current";
        args[1] = current.toString();
        args[2] = "--baseline";
        args[3] = baseline.toString();
        args[4] = "--output";
        args[5] = output.toString();
        System.arraycopy(extraArgs, 0, args, 6, extraArgs.length);
        return BenchmarkReportComparator.run(args,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private Path report(String fileName, String... results) throws IOException {
        Path report = tempDir.resolve(fileName);
        Files.writeString(report, "[" + String.join(",", results) + "]");
        return report;
    }

    private static String result(String methodName, String mode, double score, String unit,
                                 Double p50, Double p95, Double p99, Double allocation) {
        return """
                {
                  "benchmark": "io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.%s",
                  "mode": "%s",
                  "primaryMetric": {
                    "score": %s,
                    "scoreUnit": "%s",
                    "scorePercentiles": {
                      "50.0": %s,
                      "95.0": %s,
                      "99.0": %s
                    }
                  },
                  "secondaryMetrics": {
                    "gc.alloc.rate.norm": {
                      "score": %s
                    }
                  }
                }
                """.formatted(methodName, mode, score, unit, value(p50), value(p95), value(p99), value(allocation));
    }

    private static String value(Double value) {
        return value == null ? "null" : value.toString();
    }
}
