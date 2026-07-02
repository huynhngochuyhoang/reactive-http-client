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
    void classifiesClientSideOverheadRowsIntoComparisonSummary() throws Exception {
        String report = renderReport(
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.clientSideOverheadRawWebClientPostJson",
                        "avgt", 10.0, "us/op"),
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.clientSideOverheadSpringHttpExchangePostJson",
                        "avgt", 12.0, "us/op"),
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.clientSideOverheadStarterPostJson",
                        "avgt", 15.0, "us/op"));

        assertThat(report)
                .contains("| Post Json | 15 us/op | 50% slower | 25% slower |")
                .contains("| clientSideOverheadRawWebClientPostJson | Client-side overhead | `avgt` | 10 us/op |")
                .contains("| clientSideOverheadSpringHttpExchangePostJson | Client-side overhead | `avgt` | 12 us/op |")
                .contains("| clientSideOverheadStarterPostJson | Client-side overhead | `avgt` | 15 us/op |");
    }

    @Test
    void classifiesLoopbackStarterFeatureRowsAsOptionalStarterFeatures() throws Exception {
        String report = renderReport(result(
                "io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.starterFeatureMicrometerObserverGetNoBody",
                "avgt", 58.0, "us/op"));

        assertThat(report)
                .contains("| Micrometer Observer Get No Body | Optional starter feature | 58 us/op |")
                .contains("| starterFeatureMicrometerObserverGetNoBody | Optional starter feature | `avgt` | 58 us/op |");
    }

    @Test
    void classifiesStarterErrorMappingRowsAsStarterOnlyErrorMapping() throws Exception {
        String report = renderReport(result(
                "io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.starterErrorMappingProblemDetailSmallBody",
                "avgt", 77.0, "us/op"));

        assertThat(report)
                .contains("| Problem Detail Small Body | Starter-only error-mapping overhead | 77 us/op |")
                .contains("| starterErrorMappingProblemDetailSmallBody | Starter-only error-mapping overhead | `avgt` | 77 us/op |");
    }

    @Test
    void classifiesNoNetworkRowsWithoutOptionalFeatureSummary() throws Exception {
        String report = renderReport(
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterDiagnosticsOverheadBenchmark.metadataOnlyExchangeLoggingGetNoBody",
                        "avgt", 42.0, "us/op"),
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterDiagnosticsOverheadBenchmark.diagnosticsNoNetworkOneObserverGetNoBody",
                        "thrpt", 123000.0, "ops/s"),
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterDiagnosticsOverheadBenchmark.runtimeDiagnosticsProviderClientSummaries",
                        "avgt", 5.0, "us/op"));

        assertThat(report)
                .doesNotContain("## Starter-Only and Optional Feature Rows")
                .contains("| metadataOnlyExchangeLoggingGetNoBody | No-network starter invocation | `avgt` | 42 us/op |")
                .contains("| diagnosticsNoNetworkOneObserverGetNoBody | No-network starter invocation | `thrpt` | 123000 ops/s |")
                .contains("| runtimeDiagnosticsProviderClientSummaries | No-network starter invocation | `avgt` | 5 us/op |")
                .doesNotContain("| metadataOnlyExchangeLoggingGetNoBody | Optional starter feature |")
                .doesNotContain("| diagnosticsNoNetworkOneObserverGetNoBody | Optional starter feature |")
                .doesNotContain("| runtimeDiagnosticsProviderClientSummaries | Optional starter feature |");
    }

    private String renderReport(String... results) throws Exception {
        Path result = tempDir.resolve("release-jmh.json");
        Files.writeString(result, "[" + String.join(",", results) + "]");
        BenchmarkMarkdownReport.writeIfResultFilePresent(new String[]{"-rff", result.toString()});
        return Files.readString(tempDir.resolve("release-jmh.md"));
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
