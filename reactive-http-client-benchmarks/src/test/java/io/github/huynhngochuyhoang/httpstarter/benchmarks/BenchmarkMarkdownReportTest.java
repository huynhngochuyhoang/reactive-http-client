package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkMarkdownReportTest {

    @TempDir
    Path tempDir;

    @Test
    void labelsDiagnosticsNoNetworkRowsSeparatelyFromLoopbackFeatureRows() throws Exception {
        Path result = tempDir.resolve("release-jmh.json");
        Files.writeString(result, "[" + String.join(",",
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.starterFeatureMicrometerObserverGetNoBody",
                        "avgt", 58.0, "us/op"),
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterDiagnosticsOverheadBenchmark.diagnosticsNoNetworkOneObserverGetNoBody",
                        "thrpt", 123000.0, "ops/s")) + "]");

        BenchmarkMarkdownReport.writeIfResultFilePresent(new String[]{"-rff", result.toString()});

        String report = Files.readString(tempDir.resolve("release-jmh.md"));
        assertThat(report)
                .contains("| Micrometer Observer Get No Body | Optional starter feature | 58 us/op |")
                .contains("| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `avgt` | 58 us/op |")
                .contains("| diagnosticsNoNetworkOneObserverGetNoBody | No-network starter invocation | `thrpt` | 123000 ops/s |")
                .doesNotContain("| diagnosticsNoNetworkOneObserverGetNoBody | Optional starter feature |");
    }

    private static String result(String benchmarkName, String mode, double score, String unit) {
        return """
                {
                  "benchmark": "%s",
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
                """.formatted(benchmarkName, mode, score, unit, score, score, score, 1000.0);
    }
}
