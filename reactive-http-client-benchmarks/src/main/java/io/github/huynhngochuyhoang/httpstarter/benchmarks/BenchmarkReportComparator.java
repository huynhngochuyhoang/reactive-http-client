package io.github.huynhngochuyhoang.httpstarter.benchmarks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.PrintStream;
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
import java.util.Objects;
import java.util.TreeSet;

public final class BenchmarkReportComparator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final double LATENCY_REVIEW_PERCENT = 20.0;
    private static final double OPTIONAL_FEATURE_REVIEW_PERCENT = 25.0;
    private static final double ALLOCATION_REVIEW_PERCENT = 15.0;
    private static final double ALLOCATION_REVIEW_BYTES = 4096.0;

    private BenchmarkReportComparator() {
    }

    public static void main(String[] args) throws IOException {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) throws IOException {
        Options options = Options.parse(args);
        if (options == null) {
            printUsage(err);
            return 2;
        }

        Comparison comparison = compare(options.current(), options.baseline());
        String markdown = render(options.current(), options.baseline(), comparison.rows());
        Path outputParent = options.output().getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }
        Files.writeString(options.output(), markdown);
        out.println("Wrote benchmark comparison to " + options.output());

        if (options.failOnReview() && comparison.hasReview()) {
            return 1;
        }
        return 0;
    }

    private static Comparison compare(Path currentReport, Path baselineReport) throws IOException {
        Map<ResultKey, BenchmarkResult> current = readResults(currentReport);
        Map<ResultKey, BenchmarkResult> baseline = readResults(baselineReport);
        TreeSet<ResultKey> keys = new TreeSet<>(Comparator
                .comparing(ResultKey::benchmarkName)
                .thenComparing(BenchmarkReportComparator::modeSortKey));
        keys.addAll(current.keySet());
        keys.addAll(baseline.keySet());

        List<ComparisonRow> rows = new ArrayList<>();
        boolean hasReview = false;
        for (ResultKey key : keys) {
            BenchmarkResult currentResult = current.get(key);
            BenchmarkResult baselineResult = baseline.get(key);
            if (currentResult == null || baselineResult == null) {
                rows.add(ComparisonRow.missing(key, currentResult, baselineResult));
                continue;
            }
            for (Metric metric : currentResult.metrics()) {
                Metric baselineMetric = baselineResult.metric(metric.name());
                if (metric.value() == null && (baselineMetric == null || baselineMetric.value() == null)) {
                    continue;
                }
                ComparisonRow row = ComparisonRow.compare(key, metric, baselineMetric);
                rows.add(row);
                hasReview = hasReview || row.review();
            }
        }
        return new Comparison(rows, hasReview);
    }

    private static Map<ResultKey, BenchmarkResult> readResults(Path report) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(report.toFile());
        Map<ResultKey, BenchmarkResult> results = new LinkedHashMap<>();
        if (!root.isArray()) {
            return results;
        }
        for (JsonNode node : root) {
            String benchmarkName = methodName(text(node, "benchmark"));
            String mode = text(node, "mode");
            JsonNode primary = node.path("primaryMetric");
            List<Metric> metrics = new ArrayList<>();
            if ("avgt".equals(mode)) {
                metrics.add(new Metric("average time", number(primary.path("score")), text(primary, "scoreUnit"),
                        true, thresholdPercent(benchmarkName)));
            }
            if ("thrpt".equals(mode)) {
                metrics.add(new Metric("throughput", number(primary.path("score")), text(primary, "scoreUnit"),
                        false, 0.0));
            }
            addPercentile(metrics, primary, "p50", "50.0", benchmarkName, !"thrpt".equals(mode));
            addPercentile(metrics, primary, "p95", "95.0", benchmarkName, !"thrpt".equals(mode));
            addPercentile(metrics, primary, "p99", "99.0", benchmarkName, !"thrpt".equals(mode));
            metrics.add(new Metric("allocation per operation",
                    number(node.path("secondaryMetrics").path("gc.alloc.rate.norm").path("score")),
                    "B/op", true, ALLOCATION_REVIEW_PERCENT));
            results.put(new ResultKey(benchmarkName, mode), new BenchmarkResult(metrics));
        }
        return results;
    }

    private static void addPercentile(List<Metric> metrics, JsonNode primary, String name,
                                      String percentile, String benchmarkName, boolean reviewable) {
        metrics.add(new Metric(name, number(primary.path("scorePercentiles").path(percentile)),
                text(primary, "scoreUnit"), reviewable, thresholdPercent(benchmarkName)));
    }

    private static double thresholdPercent(String benchmarkName) {
        return benchmarkName.startsWith("starterFeature") ? OPTIONAL_FEATURE_REVIEW_PERCENT : LATENCY_REVIEW_PERCENT;
    }

    private static String render(Path currentReport, Path baselineReport, List<ComparisonRow> rows) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Benchmark Report Comparison\n\n");
        markdown.append("- Generated at: `").append(Instant.now()).append("`\n");
        markdown.append("- Current report: `").append(currentReport).append("`\n");
        markdown.append("- Baseline report: `").append(baselineReport).append("`\n");
        markdown.append("- Review triggers are informational by default; use `--fail-on-review` for local non-zero exit.\n\n");
        appendScenarioCompleteness(markdown, rows);
        markdown.append("| Benchmark | Mode | Metric | Current | Baseline | Delta | Relative Delta | Status |\n");
        markdown.append("| --- | --- | --- | ---: | ---: | ---: | ---: | --- |\n");
        for (ComparisonRow row : rows) {
            markdown.append("| ").append(markdownCell(row.key().benchmarkName()))
                    .append(" | `").append(row.key().mode()).append("`")
                    .append(" | ").append(markdownCell(row.metricName()))
                    .append(" | ").append(metric(row.currentValue(), row.unit()))
                    .append(" | ").append(metric(row.baselineValue(), row.unit()))
                    .append(" | ").append(metric(row.absoluteDelta(), row.unit()))
                    .append(" | ").append(percent(row.relativeDeltaPercent()))
                    .append(" | ").append(row.status())
                    .append(" |\n");
        }
        return markdown.toString();
    }

    private static void appendScenarioCompleteness(StringBuilder markdown, List<ComparisonRow> rows) {
        TreeSet<ResultKey> matched = new TreeSet<>(Comparator
                .comparing(ResultKey::benchmarkName)
                .thenComparing(BenchmarkReportComparator::modeSortKey));
        TreeSet<ResultKey> currentOnly = new TreeSet<>(matched.comparator());
        TreeSet<ResultKey> baselineOnly = new TreeSet<>(matched.comparator());
        for (ComparisonRow row : rows) {
            if ("missing baseline".equals(row.status())) {
                currentOnly.add(row.key());
            }
            else if ("missing current".equals(row.status())) {
                baselineOnly.add(row.key());
            }
            else {
                matched.add(row.key());
            }
        }
        markdown.append("## Scenario Completeness\n\n");
        markdown.append("- Matched benchmark/mode rows: **").append(matched.size()).append("**.\n");
        markdown.append("- Current-only rows: **").append(currentOnly.size()).append("**.\n");
        markdown.append("- Baseline-only rows: **").append(baselineOnly.size()).append("**.\n");
        markdown.append("- Deltas are calculated only for matched rows; missing rows remain explicit and are never compared.\n\n");
    }

    private static boolean crossesReviewTrigger(Metric current, Metric baseline, Double relativeDeltaPercent, Double absoluteDelta) {
        if (current == null || baseline == null || current.value() == null || baseline.value() == null) {
            return false;
        }
        if (!current.reviewable()) {
            return false;
        }
        if ("allocation per operation".equals(current.name())) {
            return (relativeDeltaPercent != null && relativeDeltaPercent >= ALLOCATION_REVIEW_PERCENT)
                    || (absoluteDelta != null && absoluteDelta >= ALLOCATION_REVIEW_BYTES);
        }
        return relativeDeltaPercent != null && Math.abs(relativeDeltaPercent) >= current.reviewPercent();
    }

    private static String metric(Double value, String unit) {
        if (value == null) {
            return "n/a";
        }
        if (unit == null || unit.isBlank()) {
            return NUMBER_FORMAT.format(value);
        }
        return NUMBER_FORMAT.format(value) + " " + unit;
    }

    private static String percent(Double value) {
        return value == null ? "n/a" : NUMBER_FORMAT.format(value) + "%";
    }

    private static String markdownCell(String value) {
        return value == null ? "" : value.replace("\n", " ").replace("\r", " ").replace("|", "\\|");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
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

    private static String methodName(String benchmark) {
        int index = benchmark.lastIndexOf('.');
        return index >= 0 ? benchmark.substring(index + 1) : benchmark;
    }

    private static int modeSortKey(ResultKey key) {
        return switch (key.mode()) {
            case "thrpt" -> 0;
            case "avgt" -> 1;
            case "sample" -> 2;
            default -> 9;
        };
    }

    private static void printUsage(PrintStream err) {
        err.println("Usage: BenchmarkReportComparator --current <jmh.json> --baseline <jmh.json> "
                + "[--output <comparison.md>] [--fail-on-review]");
    }

    private record Options(Path current, Path baseline, Path output, boolean failOnReview) {

        static Options parse(String[] args) {
            Path current = null;
            Path baseline = null;
            Path output = null;
            boolean failOnReview = false;
            try {
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    switch (arg) {
                        case "--current" -> current = Path.of(requiredValue(args, ++i));
                        case "--baseline" -> baseline = Path.of(requiredValue(args, ++i));
                        case "--output" -> output = Path.of(requiredValue(args, ++i));
                        case "--fail-on-review" -> failOnReview = true;
                        default -> {
                            if (arg.startsWith("--fail-on-review=")) {
                                failOnReview = Boolean.parseBoolean(arg.substring("--fail-on-review=".length()));
                            } else {
                                return null;
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                return null;
            }
            if (current == null || baseline == null) {
                return null;
            }
            if (output == null) {
                output = current.resolveSibling("benchmark-comparison.md");
            }
            return new Options(current, baseline, output, failOnReview);
        }

        private static String requiredValue(String[] args, int index) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Missing option value");
            }
            return args[index];
        }
    }

    private record ResultKey(String benchmarkName, String mode) {
    }

    private record BenchmarkResult(List<Metric> metrics) {

        Metric metric(String name) {
            return metrics.stream()
                    .filter(metric -> Objects.equals(metric.name(), name))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record Metric(String name, Double value, String unit, boolean reviewable, double reviewPercent) {
    }

    private record Comparison(List<ComparisonRow> rows, boolean hasReview) {
    }

    private record ComparisonRow(
            ResultKey key,
            String metricName,
            Double currentValue,
            Double baselineValue,
            Double absoluteDelta,
            Double relativeDeltaPercent,
            String unit,
            String status
    ) {

        static ComparisonRow compare(ResultKey key, Metric current, Metric baseline) {
            Double baselineValue = baseline == null ? null : baseline.value();
            Double absoluteDelta = current.value() != null && baselineValue != null
                    ? current.value() - baselineValue : null;
            Double relativeDelta = absoluteDelta != null && baselineValue != 0.0
                    ? (absoluteDelta / baselineValue) * 100.0 : null;
            boolean review = crossesReviewTrigger(current, baseline, relativeDelta, absoluteDelta);
            return new ComparisonRow(key, current.name(), current.value(), baselineValue, absoluteDelta,
                    relativeDelta, current.unit(), review ? "review" : "ok");
        }

        static ComparisonRow missing(ResultKey key, BenchmarkResult current, BenchmarkResult baseline) {
            String status = current == null ? "missing current" : "missing baseline";
            return new ComparisonRow(key, "row", null, null, null, null, "", status);
        }

        boolean review() {
            return "review".equals(status);
        }
    }
}
