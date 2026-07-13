package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

final class BenchmarkMarkdownReport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final List<String> NO_NETWORK_PREFIXES = List.of(
            "metadata",
            "cached",
            "argumentResolution",
            "proxyInvocation",
            "diagnosticsDisabled",
            "diagnosticsNoNetwork",
            "metadataOnly",
            "micrometerObserver",
            "runtimeDiagnosticsProvider");
    private static final List<String> ENVIRONMENT_KEYS = List.of(
            "generatedAt",
            "projectVersion",
            "starterVersion",
            "apiCompatibilityBaselineVersion",
            "benchmarkCommit",
            "stackContext",
            "comparisonPolicy",
            "springBootVersion",
            "springFrameworkVersion",
            "springWebFluxVersion",
            "reactorNettyVersion",
            "nettyVersion",
            "jacksonVersion",
            "micrometerVersion",
            "openTelemetryVersion",
            "baselineSpringWebFluxVersion",
            "baselineReactorNettyVersion",
            "dependencyManagement",
            "javaVersion",
            "javaVm",
            "osName",
            "osArch",
            "availableProcessors",
            "jvmInputArguments",
            "resultFile"
    );

    private BenchmarkMarkdownReport() {
    }

    static void writeIfResultFilePresent(String[] args) throws IOException {
        Path resultFile = resultFile(args);
        if (resultFile == null || !Files.exists(resultFile)) {
            return;
        }
        write(resultFile);
    }

    private static void write(Path resultFile) throws IOException {
        List<BenchmarkResult> results = readResults(resultFile);
        Properties environment = readEnvironment(resultFile);
        Path reportFile = markdownReportFile(resultFile);
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, renderReport(resultFile, environment, results));
    }

    private static List<BenchmarkResult> readResults(Path resultFile) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(resultFile.toFile());
        List<BenchmarkResult> results = new ArrayList<>();
        if (!root.isArray()) {
            return results;
        }
        for (JsonNode node : root) {
            String fullName = text(node, "benchmark");
            String benchmarkName = methodName(fullName);
            String mode = text(node, "mode");
            JsonNode primary = node.path("primaryMetric");
            results.add(new BenchmarkResult(
                    benchmarkName,
                    classification(benchmarkName),
                    mode,
                    number(primary.path("score")),
                    text(primary, "scoreUnit"),
                    percentile(primary, "50.0"),
                    percentile(primary, "95.0"),
                    percentile(primary, "99.0"),
                    secondaryScore(node, "gc.alloc.rate"),
                    secondaryScore(node, "gc.alloc.rate.norm")
            ));
        }
        results.sort(Comparator
                .comparing((BenchmarkResult result) -> result.classification().sortKey())
                .thenComparing(result -> result.classification().scenario())
                .thenComparing(result -> result.classification().clientSortKey())
                .thenComparing(BenchmarkMarkdownReport::modeSortKey));
        return results;
    }

    private static Properties readEnvironment(Path resultFile) throws IOException {
        Properties properties = new Properties();
        Path metadataFile = resultFile.resolveSibling(resultFile.getFileName() + ".environment.properties");
        if (!Files.exists(metadataFile)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(metadataFile)) {
            properties.load(input);
        }
        return properties;
    }

    private static String renderReport(Path resultFile, Properties environment, List<BenchmarkResult> results) {
        boolean smokeOnly = Boolean.parseBoolean(environment.getProperty("smokeOnly",
                String.valueOf(resultFile.getFileName().toString().contains("smoke-only"))));
        String runLabel = smokeOnly ? "SMOKE-ONLY harness check" : "Release-quality candidate";

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Reactive HTTP Client Benchmark Report\n\n");
        markdown.append("- Generated at: `").append(escapeInline(environment.getProperty("generatedAt", Instant.now().toString()))).append("`\n");
        markdown.append("- Result file: `").append(escapeInline(resultFile.toString())).append("`\n");
        markdown.append("- Run label: **").append(runLabel).append("**\n\n");

        markdown.append("## Interpretation\n\n");
        markdown.append("- Stack context: **")
                .append(escapeInline(environment.getProperty("stackContext", "unknown")))
                .append("**. Comparisons within this report use one dependency stack.\n");
        markdown.append("- Client-side overhead comparisons use the same local loopback server, request shape, transport, codecs, and validation guardrails for raw `WebClient`, Spring HTTP Interface, and the starter.\n");
        markdown.append("- Boot 3 versus Boot 4 movement is stack-migration context, not evidence of a pure starter optimization.\n");
        markdown.append("- Smoke-only results prove the harness starts and writes artifacts; do not publish them as performance evidence.\n");
        markdown.append("- Optional starter feature rows enable exactly one feature at a time and are not claims about default runtime overhead.\n");
        markdown.append("- Starter-only rows measure starter-specific work, such as Problem Detail error mapping, where the baselines do not install equivalent behavior.\n");
        markdown.append("- Local loopback, JVM warmup, CPU scheduling, and Netty event-loop behavior affect the numbers; use this report as trend evidence for named scenarios.\n");
        markdown.append("- Review thresholds are manual signals; this harness does not enforce hard performance gates.\n\n");

        appendEnvironment(markdown, environment);
        appendComparisonSummary(markdown, results);
        appendStarterOnlySummary(markdown, results);
        appendRawResults(markdown, results);
        appendPromotionNotes(markdown);
        return markdown.toString();
    }

    private static void appendEnvironment(StringBuilder markdown, Properties environment) {
        markdown.append("## Environment\n\n");
        markdown.append("| Key | Value |\n");
        markdown.append("| --- | --- |\n");
        for (String key : ENVIRONMENT_KEYS) {
            markdown.append("| `").append(key).append("` | ")
                    .append(markdownCell(environment.getProperty(key, "unknown")))
                    .append(" |\n");
        }
        markdown.append('\n');
    }

    private static void appendComparisonSummary(StringBuilder markdown, List<BenchmarkResult> results) {
        Map<String, Map<String, BenchmarkResult>> averageResults = new LinkedHashMap<>();
        for (BenchmarkResult result : results) {
            Classification classification = result.classification();
            if (!classification.clientSideComparison() || !"avgt".equals(result.mode())) {
                continue;
            }
            averageResults.computeIfAbsent(classification.scenario(), ignored -> new LinkedHashMap<>())
                    .put(classification.client(), result);
        }

        markdown.append("## Comparison Summary\n\n");
        markdown.append("| Scenario | Starter avg | vs raw WebClient | vs Spring HTTP Interface |\n");
        markdown.append("| --- | ---: | ---: | ---: |\n");
        for (Map.Entry<String, Map<String, BenchmarkResult>> entry : averageResults.entrySet()) {
            Map<String, BenchmarkResult> byClient = entry.getValue();
            BenchmarkResult starter = byClient.get("Starter");
            if (starter == null) {
                continue;
            }
            markdown.append("| ").append(markdownCell(displayScenario(entry.getKey())))
                    .append(" | ").append(score(starter))
                    .append(" | ").append(delta(starter, byClient.get("Raw WebClient")))
                    .append(" | ").append(delta(starter, byClient.get("Spring HTTP Interface")))
                    .append(" |\n");
        }
        markdown.append('\n');
    }

    private static void appendStarterOnlySummary(StringBuilder markdown, List<BenchmarkResult> results) {
        List<BenchmarkResult> starterOnly = results.stream()
                .filter(result -> result.classification().starterOnly())
                .filter(result -> "avgt".equals(result.mode()))
                .toList();
        if (starterOnly.isEmpty()) {
            return;
        }
        markdown.append("## Starter-Only and Optional Feature Rows\n\n");
        markdown.append("| Scenario | Label | Average |\n");
        markdown.append("| --- | --- | ---: |\n");
        for (BenchmarkResult result : starterOnly) {
            markdown.append("| ").append(markdownCell(displayScenario(result.classification().scenario())))
                    .append(" | ").append(markdownCell(result.classification().category()))
                    .append(" | ").append(score(result))
                    .append(" |\n");
        }
        markdown.append('\n');
    }

    private static void appendRawResults(StringBuilder markdown, List<BenchmarkResult> results) {
        markdown.append("## Raw Results\n\n");
        markdown.append("| Benchmark | Label | Mode | Score | p50 | p95 | p99 | Allocation rate | Allocation/op |\n");
        markdown.append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (BenchmarkResult result : results) {
            markdown.append("| ").append(markdownCell(result.benchmarkName()))
                    .append(" | ").append(markdownCell(result.classification().category()))
                    .append(" | `").append(result.mode()).append("`")
                    .append(" | ").append(score(result))
                    .append(" | ").append(number(result.p50(), result.unit()))
                    .append(" | ").append(number(result.p95(), result.unit()))
                    .append(" | ").append(number(result.p99(), result.unit()))
                    .append(" | ").append(number(result.allocationRate(), "MB/sec"))
                    .append(" | ").append(number(result.allocationPerOperation(), "B/op"))
                    .append(" |\n");
        }
        markdown.append('\n');
    }

    private static void appendPromotionNotes(StringBuilder markdown) {
        markdown.append("## Promotion Notes\n\n");
        markdown.append("- Generated reports live under `reactive-http-client-benchmarks/target/benchmark-reports/` and are ignored by source control through the existing `target/` rule.\n");
        markdown.append("- To promote a release report, copy a selected release-quality Markdown report into `docs/`, give it a versioned name, and link it from release notes only when performance claims rely on that report.\n");
        markdown.append("- Do not promote smoke-only reports.\n");
    }

    private static Classification classification(String benchmarkName) {
        if (benchmarkName.startsWith("clientSideOverhead")) {
            String remainder = benchmarkName.substring("clientSideOverhead".length());
            ClientAndScenario clientAndScenario = clientAndScenario(remainder);
            requireScenario(benchmarkName, clientAndScenario.scenario());
            return new Classification("Client-side overhead", clientAndScenario.client(), clientAndScenario.scenario(),
                    true, false, sortPrefix("client", clientAndScenario.scenario()));
        }
        if (benchmarkName.startsWith("starterFeature")) {
            String scenario = benchmarkName.substring("starterFeature".length());
            requireScenario(benchmarkName, scenario);
            return new Classification("Optional starter feature", "Starter", scenario,
                    false, true, sortPrefix("feature", scenario));
        }
        if (benchmarkName.startsWith("starterErrorMapping")) {
            String scenario = benchmarkName.substring("starterErrorMapping".length());
            requireScenario(benchmarkName, scenario);
            return new Classification("Starter-only error-mapping overhead", "Starter", scenario,
                    false, true, sortPrefix("starter-error", scenario));
        }
        String noNetworkPrefix = NO_NETWORK_PREFIXES.stream()
                .filter(benchmarkName::startsWith)
                .findFirst()
                .orElse(null);
        if (noNetworkPrefix != null) {
            requireScenario(benchmarkName, benchmarkName.substring(noNetworkPrefix.length()));
            return new Classification("No-network starter invocation", "Starter", benchmarkName,
                    false, false, sortPrefix("invocation", benchmarkName));
        }
        throw new IllegalArgumentException("Unclassified benchmark method [" + benchmarkName
                + "]. Use a documented benchmark naming prefix before generating release evidence.");
    }

    private static ClientAndScenario clientAndScenario(String remainder) {
        if (remainder.startsWith("RawWebClient")) {
            return new ClientAndScenario("Raw WebClient", remainder.substring("RawWebClient".length()));
        }
        if (remainder.startsWith("SpringHttpExchange")) {
            return new ClientAndScenario("Spring HTTP Interface", remainder.substring("SpringHttpExchange".length()));
        }
        if (remainder.startsWith("Starter")) {
            return new ClientAndScenario("Starter", remainder.substring("Starter".length()));
        }
        throw new IllegalArgumentException("Unknown client-side benchmark surface [" + remainder
                + "]. Use RawWebClient, SpringHttpExchange, or Starter.");
    }

    private static void requireScenario(String benchmarkName, String scenario) {
        if (scenario.isBlank()) {
            throw new IllegalArgumentException("Benchmark method [" + benchmarkName
                    + "] must include a scenario after its classification prefix.");
        }
    }

    private static String delta(BenchmarkResult starter, BenchmarkResult baseline) {
        if (starter == null || baseline == null || starter.score() == null || baseline.score() == null || baseline.score() == 0) {
            return "n/a";
        }
        double percent = ((starter.score() - baseline.score()) / baseline.score()) * 100.0;
        String suffix = percent >= 0 ? " slower" : " faster";
        return NUMBER_FORMAT.format(Math.abs(percent)) + "%" + suffix;
    }

    private static String score(BenchmarkResult result) {
        return number(result.score(), result.unit());
    }

    private static String number(Double value, String unit) {
        if (value == null) {
            return "n/a";
        }
        if (unit == null || unit.isBlank()) {
            return NUMBER_FORMAT.format(value);
        }
        return NUMBER_FORMAT.format(value) + " " + unit;
    }

    private static String displayScenario(String scenario) {
        if (scenario == null || scenario.isBlank()) {
            return "unknown";
        }
        return scenario.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    }

    private static String markdownCell(String value) {
        return escapeInline(value).replace("|", "\\|");
    }

    private static String escapeInline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", " ");
    }

    private static String methodName(String benchmark) {
        int index = benchmark.lastIndexOf('.');
        return index >= 0 ? benchmark.substring(index + 1) : benchmark;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private static Double percentile(JsonNode primary, String percentile) {
        return number(primary.path("scorePercentiles").path(percentile));
    }

    private static Double secondaryScore(JsonNode result, String metricName) {
        JsonNode score = result.path("secondaryMetrics").path(metricName).path("score");
        return number(score);
    }

    private static Double number(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        String value = node.asText();
        if (value == null || value.isBlank() || "NaN".equals(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int modeSortKey(BenchmarkResult result) {
        return switch (result.mode()) {
            case "thrpt" -> 0;
            case "avgt" -> 1;
            case "sample" -> 2;
            default -> 9;
        };
    }

    private static String sortPrefix(String category, String scenario) {
        return category + ":" + scenario;
    }

    private static Path markdownReportFile(Path resultFile) {
        String fileName = resultFile.getFileName().toString();
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - ".json".length());
        }
        return resultFile.resolveSibling(fileName + ".md");
    }

    private static Path resultFile(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("-rff".equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }
        return null;
    }

    private record BenchmarkResult(
            String benchmarkName,
            Classification classification,
            String mode,
            Double score,
            String unit,
            Double p50,
            Double p95,
            Double p99,
            Double allocationRate,
            Double allocationPerOperation
    ) {
    }

    private record Classification(
            String category,
            String client,
            String scenario,
            boolean clientSideComparison,
            boolean starterOnly,
            String sortKey
    ) {
        String clientSortKey() {
            return switch (client) {
                case "Raw WebClient" -> "0";
                case "Spring HTTP Interface" -> "1";
                case "Starter" -> "2";
                default -> "9";
            };
        }
    }

    private record ClientAndScenario(String client, String scenario) {
    }
}
