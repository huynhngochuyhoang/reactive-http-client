package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import io.github.huynhngochuyhoang.httpstarter.core.StarterInvocationInternalsBenchmark;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterDiagnosticsOverheadBenchmark.micrometerObserverPrometheusHistogramGetNoBody",
                        "avgt", 51.0, "us/op"),
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterDiagnosticsOverheadBenchmark.diagnosticsNoNetworkOpenCircuitRejection",
                        "sample", 67.0, "us/op"),
                result("io.github.huynhngochuyhoang.httpstarter.benchmarks.StarterDiagnosticsOverheadBenchmark.runtimeDiagnosticsProviderClientSummaries",
                        "avgt", 5.0, "us/op"));

        assertThat(report)
                .doesNotContain("## Starter-Only and Optional Feature Rows")
                .contains("| metadataOnlyExchangeLoggingGetNoBody | No-network starter invocation | `avgt` | 42 us/op |")
                .contains("| diagnosticsNoNetworkOneObserverGetNoBody | No-network starter invocation | `thrpt` | 123000 ops/s |")
                .contains("| micrometerObserverPrometheusHistogramGetNoBody | No-network starter invocation | `avgt` | 51 us/op |")
                .contains("| diagnosticsNoNetworkOpenCircuitRejection | No-network starter invocation | `sample` | 67 us/op |")
                .contains("| runtimeDiagnosticsProviderClientSummaries | No-network starter invocation | `avgt` | 5 us/op |")
                .doesNotContain("| metadataOnlyExchangeLoggingGetNoBody | Optional starter feature |")
                .doesNotContain("| diagnosticsNoNetworkOneObserverGetNoBody | Optional starter feature |")
                .doesNotContain("| runtimeDiagnosticsProviderClientSummaries | Optional starter feature |");
    }

    @Test
    void rendersBoot4SameStackContextAndCompleteVersionMetadata() throws Exception {
        Properties environment = new Properties();
        environment.setProperty("stackContext", "Spring Boot 4 migration candidate");
        environment.setProperty("comparisonPolicy", "same-stack only; cross-stack results are migration context");
        environment.setProperty("springBootVersion", "4.0.0");
        environment.setProperty("springFrameworkVersion", "7.0.1");
        environment.setProperty("reactorNettyVersion", "1.3.0");
        environment.setProperty("nettyVersion", "4.2.7.Final");
        environment.setProperty("jacksonVersion", "3.0.3");
        environment.setProperty("micrometerVersion", "1.16.1");
        environment.setProperty("openTelemetryVersion", "1.55.0");

        String report = renderReport(environment, result(
                "io.github.huynhngochuyhoang.httpstarter.benchmarks.LoopbackClientComparisonBenchmark.clientSideOverheadStarterGetNoBody",
                "avgt", 10.0, "us/op"));

        assertThat(report)
                .contains("Stack context: **Spring Boot 4 migration candidate**")
                .contains("Boot 3 versus Boot 4 movement is stack-migration context")
                .contains("Review thresholds are manual signals")
                .contains("| `springFrameworkVersion` | 7.0.1 |")
                .contains("| `nettyVersion` | 4.2.7.Final |")
                .contains("| `jacksonVersion` | 3.0.3 |")
                .contains("| `micrometerVersion` | 1.16.1 |")
                .contains("| `openTelemetryVersion` | 1.55.0 |");
    }

    @Test
    void everyCurrentBenchmarkMethodHasAnExplicitClassification() throws Exception {
        Stream<Class<?>> benchmarkTypes = Stream.of(
                LoopbackClientComparisonBenchmark.class,
                StarterInvocationBenchmark.class,
                StarterInvocationInternalsBenchmark.class,
                StarterDiagnosticsOverheadBenchmark.class);

        String[] results = benchmarkTypes
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Benchmark.class))
                .map(method -> result(method.getDeclaringClass().getName() + "." + method.getName(),
                        "avgt", 1.0, "us/op"))
                .toArray(String[]::new);

        assertThat(renderReport(results)).contains("## Raw Results");
    }

    @Test
    void discoveredBenchmarksSatisfyTheFairnessContract() {
        BenchmarkFairnessContract.validateDiscoveredBenchmarks();
    }

    @Test
    void rejectsIncompleteClientSideComparisonScenarios() {
        assertThatThrownBy(() -> BenchmarkFairnessContract.validate(java.util.List.of(
                new BenchmarkFairnessContract.BenchmarkMethod(
                        LoopbackClientComparisonBenchmark.class.getName(),
                        "clientSideOverheadRawWebClientNewScenario"),
                new BenchmarkFairnessContract.BenchmarkMethod(
                        LoopbackClientComparisonBenchmark.class.getName(),
                        "clientSideOverheadStarterNewScenario"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NewScenario")
                .hasMessageContaining("SpringHttpExchange");
    }

    @Test
    void rejectsLoopbackClassificationsOnNoNetworkBenchmarks() {
        assertThatThrownBy(() -> BenchmarkFairnessContract.validate(java.util.List.of(
                new BenchmarkFairnessContract.BenchmarkMethod(
                        StarterInvocationBenchmark.class.getName(),
                        "starterFeatureNoNetworkScenario"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mixes loopback and no-network classification");
    }

    @Test
    void rejectsUnclassifiedBenchmarkPrefixes() {
        assertThatThrownBy(() -> renderReport(result(
                "io.github.huynhngochuyhoang.httpstarter.benchmarks.NewBenchmark.unclassifiedScenario",
                "avgt", 1.0, "us/op")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclassified benchmark method [unclassifiedScenario]")
                .hasMessageContaining("documented benchmark naming prefix");
    }

    @Test
    void rejectsUnknownClientSideSurfaces() {
        assertThatThrownBy(() -> renderReport(result(
                "io.github.huynhngochuyhoang.httpstarter.benchmarks.NewBenchmark.clientSideOverheadCustomGetNoBody",
                "avgt", 1.0, "us/op")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown client-side benchmark surface [CustomGetNoBody]");
    }

    @Test
    void rejectsEmptyScenarioSuffixes() {
        assertThatThrownBy(() -> renderReport(result(
                "io.github.huynhngochuyhoang.httpstarter.benchmarks.NewBenchmark.starterFeature",
                "avgt", 1.0, "us/op")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must include a scenario after its classification prefix");
        assertThatThrownBy(() -> renderReport(result(
                "io.github.huynhngochuyhoang.httpstarter.benchmarks.NewBenchmark.metadata",
                "avgt", 1.0, "us/op")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must include a scenario after its classification prefix");
    }

    private String renderReport(String... results) throws Exception {
        return renderReport(new Properties(), results);
    }

    private String renderReport(Properties environment, String... results) throws Exception {
        Path result = tempDir.resolve("release-jmh.json");
        Files.writeString(result, "[" + String.join(",", results) + "]");
        try (var output = Files.newOutputStream(tempDir.resolve("release-jmh.json.environment.properties"))) {
            environment.store(output, "test benchmark environment");
        }
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
