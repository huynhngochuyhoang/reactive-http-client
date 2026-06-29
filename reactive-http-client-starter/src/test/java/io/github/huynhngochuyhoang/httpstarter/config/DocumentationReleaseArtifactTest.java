package io.github.huynhngochuyhoang.httpstarter.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationReleaseArtifactTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[[^]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern PROJECT_VERSION_SNIPPET = Pattern.compile(
            "<groupId>io\\.github\\.huynhngochuyhoang</groupId>\\s*"
                    + "<artifactId>reactive-http-client-[^<]+</artifactId>\\s*"
                    + "<version>([^<]+)</version>",
            Pattern.DOTALL);
    private static final String V15_CURRENT_RELEASE_VERSION = "2.10.0";
    private static final String V15_PRE_TRANSITION_BASELINE_VERSION = "2.9.0";
    private static final String V15_TARGET_MINOR_VERSION = "2.11.0";
    private static final String V15_PATCH_FALLBACK_VERSION = "2.10.1";

    @Test
    void localMarkdownLinksResolve() throws IOException {
        assertThat(brokenLocalMarkdownLinks(projectRoot())).as("broken local Markdown links").isEmpty();
    }

    @Test
    void readmeAndQuickStartVersionsMatchProjectVersion() throws Exception {
        String projectVersion = projectVersion(projectRoot().resolve("pom.xml"));

        assertVersionSnippets(projectRoot().resolve("README.md"), projectVersion);
        assertVersionSnippets(projectRoot().resolve("docs/01-quick-start.md"), projectVersion);
    }

    @Test
    void apiCompatibilityBaselineGuardIsDynamicAndProfileScoped() throws IOException {
        String pom = Files.readString(projectRoot().resolve("pom.xml"));
        int profileStart = pom.indexOf("<id>api-compatibility</id>");
        int profileEnd = pom.indexOf("</profile>", profileStart);

        assertThat(pom)
                .doesNotContain("api-compatibility-baseline-current-version-guard")
                .doesNotContain("ERROR-api.compatibility.baseline.version")
                .doesNotContain("<name>api.compatibility.baseline.version</name>");
        assertThat(profileStart).as("api-compatibility profile").isNotNegative();
        assertThat(profileEnd).as("api-compatibility profile end").isGreaterThan(profileStart);
        assertThat(pom.substring(profileStart, profileEnd))
                .contains("<id>reject-current-api-baseline</id>")
                .doesNotContain("<inherited>false</inherited>")
                .contains("<phase>validate</phase>")
                .contains("<equals arg1=\"${api.compatibility.baseline.version}\" arg2=\"${project.version}\"/>");
    }

    @Test
    void apiCompatibilityBaselineReleaseDocsStayAlignedWithPom() throws Exception {
        Path root = projectRoot();
        String pomXml = Files.readString(root.resolve("pom.xml"));
        String projectVersion = projectVersion(root.resolve("pom.xml"));
        String baselineVersion = pomProperty(pomXml, "api.compatibility.baseline.version");
        String releaseDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));
        String benchmarkDocs = Files.readString(root.resolve("docs/22-benchmarks.md"));
        JsonNode manifest = OBJECT_MAPPER.valueToTree(releaseEvidenceManifest(root.resolve("pom.xml")));

        assertThat(projectVersion).isNotEqualTo(baselineVersion);
        assertThat(releaseDocs)
                .contains("While the project version remains `" + projectVersion + "`")
                .contains("the baseline stays on `" + baselineVersion + "`")
                .contains("mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests validate")
                .contains("mvn -pl reactive-http-client-starter -Papi-compatibility -DskipTests verify")
                .contains("guard must reject\n`-Dapi.compatibility.baseline.version=" + projectVersion + "`")
                .contains("self-comparison is never\nvalid release evidence")
                .contains("While cutting `" + projectVersion + "`, keep `api.compatibility.baseline.version` on `"
                        + baselineVersion + "`")
                .contains("until the `" + projectVersion + "` artifacts are published and resolve")
                .contains("next development\ncycle may bump the reactor to the next version")
                .contains("update\n`api.compatibility.baseline.version` to `" + projectVersion + "`")
                .contains("Update benchmark\npublished-baseline commands")
                .contains("mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:"
                        + baselineVersion)
                .contains("mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:"
                        + baselineVersion)
                .contains("mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:"
                        + baselineVersion)
                .contains("V15 is planned as a minor `" + V15_TARGET_MINOR_VERSION + "` cycle")
                .contains("While the reactor still declares\n`" + V15_CURRENT_RELEASE_VERSION + "`, keep `api.compatibility.baseline.version` on `"
                        + V15_PRE_TRANSITION_BASELINE_VERSION + "`")
                .contains("When the reactor is bumped to `" + V15_TARGET_MINOR_VERSION + "`, first verify the published `"
                        + V15_CURRENT_RELEASE_VERSION + "`\nbaseline artifacts resolve")
                .contains("mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:"
                        + V15_CURRENT_RELEASE_VERSION)
                .contains("mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:"
                        + V15_CURRENT_RELEASE_VERSION)
                .contains("mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:"
                        + V15_CURRENT_RELEASE_VERSION)
                .contains("Only after those artifacts resolve should `api.compatibility.baseline.version`\nmove to `"
                        + V15_CURRENT_RELEASE_VERSION + "`")
                .contains("`published-starter-" + V15_CURRENT_RELEASE_VERSION + "` report paths")
                .contains("patch-only `" + V15_PATCH_FALLBACK_VERSION + "` scope")
                .contains("keep the API compatibility baseline on `" + baselineVersion + "`");

        assertThat(benchmarkDocs)
                .contains("The example version must match the root `api.compatibility.baseline.version`")
                .contains("(`" + baselineVersion + "` for this release line)")
                .contains("-Dbenchmark.starter.version=" + baselineVersion)
                .contains("-Dbenchmark.commit=" + baselineVersion)
                .contains("published-starter-" + baselineVersion + "/release-jmh.md")
                .contains("For the V15 minor transition")
                .contains("reactor\nremains `" + V15_CURRENT_RELEASE_VERSION + "`")
                .contains("bumped to `" + V15_TARGET_MINOR_VERSION + "`")
                .contains("published\n`" + V15_CURRENT_RELEASE_VERSION + "` artifacts resolve")
                .contains("move both `benchmark.starter.version` and\n`published-starter-<version>` paths to `"
                        + V15_CURRENT_RELEASE_VERSION + "`");

        assertThat(manifest.path("publishedBaselineArtifacts"))
                .extracting(artifact -> artifact.path("resolutionCommand").asText())
                .containsExactly(
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-starter:"
                                + baselineVersion,
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-test:"
                                + baselineVersion,
                        "mvn dependency:get -Dartifact=io.github.huynhngochuyhoang:reactive-http-client-otel:"
                                + baselineVersion);
        assertThat(manifest.path("benchmarkEvidence").path("publishedStarterCommand").asText())
                .contains("-Dbenchmark.starter.version=" + baselineVersion)
                .contains("-Dbenchmark.commit=" + baselineVersion);
    }

    @Test
    void benchmarkModuleUsesStarterDependencyManagement() throws IOException {
        String benchmarkPom = Files.readString(projectRoot().resolve("reactive-http-client-benchmarks/pom.xml"));

        assertThat(benchmarkPom)
                .contains("<benchmark.starter.version>")
                .contains("benchmark.starter.version")
                .contains("benchmark.include")
                .contains("${benchmark.include}")
                .contains("benchmark-compare")
                .contains("benchmark.compare.current")
                .contains("BenchmarkReportComparator")
                .contains("benchmark.spring-webflux.artifact")
                .contains("benchmark.reactor-netty.artifact");
        assertThat(dependencyBlock(benchmarkPom, "spring-webflux")).doesNotContain("<version>");
        assertThat(dependencyBlock(benchmarkPom, "reactor-netty-http")).doesNotContain("<version>");
    }

    @Test
    void benchmarkDocumentationScopesPerformanceClaimsToReleaseQualityReports() throws Exception {
        Path root = projectRoot();
        String projectVersion = projectVersion(root.resolve("pom.xml"));
        String baselineVersion = pomProperty(Files.readString(root.resolve("pom.xml")), "api.compatibility.baseline.version");
        Path promotedReport = root.resolve("docs/benchmark-report-" + projectVersion + ".md");
        String readmeDocs = Files.readString(root.resolve("README.md"));
        String changelogDocs = Files.readString(root.resolve("CHANGELOG.md"));
        String benchmarkDocs = Files.readString(root.resolve("docs/22-benchmarks.md"));
        String benchmarkConsumerDocs = Files.readString(root.resolve("docs/24-benchmark-consumer-examples.md"));
        String promotedReportDocs = Files.readString(promotedReport);
        String performanceSummaryDocs = Files.readString(root.resolve("docs/23-performance-summary.md"));
        String performanceTroubleshootingDocs = Files.readString(root.resolve("docs/25-performance-troubleshooting.md"));
        String releaseCompatibilityDocs = Files.readString(root.resolve("docs/20-native-release-compatibility.md"));

        assertThat(readmeDocs)
                .contains("[Benchmarks](docs/22-benchmarks.md)")
                .contains("[Benchmark Report " + projectVersion + "](docs/benchmark-report-" + projectVersion + ".md)")
                .contains("[Performance Summary](docs/23-performance-summary.md)");

        assertThat(changelogDocs)
                .contains("[Benchmark Report " + projectVersion + "](docs/benchmark-report-" + projectVersion + ".md)")
                .contains("release-quality evidence for starter `" + projectVersion + "` benchmark scenarios")
                .contains("scenario names, and release-quality report links")
                .contains("smoke-only reports from being")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw `WebClient`");

        assertThat(benchmarkDocs)
                .contains("## Methodology and Limits")
                .contains("## Comparison Model")
                .contains("| Raw `WebClient` |")
                .contains("| Spring HTTP Interface |")
                .contains("| Starter |")
                .contains("## Publishing Performance Claims")
                .contains("release-quality report version")
                .contains("exact JMH scenario name")
                .contains("Avoid broad wording such as")
                .contains("smoke-only report links")
                .contains("Benchmark Report " + projectVersion)
                .contains("Performance Summary")
                .contains("Benchmark Consumer Examples")
                .contains("24-benchmark-consumer-examples.md")
                .contains("## Release-Note Benchmark Evidence")
                .contains("Benchmark evidence:")
                .contains("Promoted report: [Benchmark Report " + projectVersion + "](docs/benchmark-report-" + projectVersion + ".md)")
                .contains("paths relative\nto the repository root")
                .contains("Current candidate command")
                .contains("Published baseline command")
                .contains("Current candidate report")
                .contains("Published baseline report")
                .contains("Scenarios cited")
                .contains("## Current vs Published Baseline Pairing")
                .contains("current candidate report and published-baseline report as a pair")
                .contains("published-starter-<version>/release-jmh.md")
                .contains("resolve every published baseline artifact")
                .contains("benchmark-compare")
                .contains("benchmark.compare.current")
                .contains("benchmark.compare.baseline")
                .contains("benchmark-comparison.md")
                .contains("fail-on-review")
                .contains("exits successfully by default")
                .contains("do not label\ncurrent candidate numbers as baseline numbers")
                .contains("benchmark.include")
                .contains("clientSideOverhead.*")
                .contains("GetPathQueryHeader")
                .contains("ResponseEntity")
                .contains("ClientErrorSmallBody")
                .contains("ServerErrorSmallBody")
                .contains("starterErrorMappingProblemDetailSmallBody")
                .contains("target/benchmark-reports/release-note")
                .contains("generated release evidence manifest is not enough")
                .contains("reactive-http-client-benchmark-evidence.md")
                .contains("target/release-evidence")
                .contains("same manifest data")
                .contains("## Release-Maintainer Performance Claim Checklist")
                .contains("Before adding or approving a public performance claim")
                .contains("not a generated\n  `target/benchmark-reports` file and not a smoke-only report")
                .contains("exact scenario or scenario group")
                .contains("compared surfaces")
                .contains("allocation per operation")
                .contains("review-trigger movement is rerun on the same machine")
                .contains("Broad claims such as");

        assertThat(promotedReport).exists();
        assertThat(promotedReportDocs)
                .startsWith("# Reactive HTTP Client Benchmark Report")
                .contains("## Promotion Metadata")
                .contains("## Interpretation")
                .contains("## Report Pairing")
                .contains("## Environment")
                .contains("## Comparison Summary")
                .contains("## Starter-Only and Optional Feature Rows")
                .contains("## Raw Results")
                .contains("## Promotion Notes")
                .contains("Report version: `" + projectVersion + "`")
                .contains("Starter version under test: `" + projectVersion + "`")
                .contains("Evidence level: **Release-quality**, not smoke evidence.")
                .contains("| `starterVersion` | " + projectVersion + " |")
                .contains("| `benchmarkCommit` |")
                .contains("| `javaVersion` |")
                .contains("| `springBootVersion` |")
                .contains("| `availableProcessors` |")
                .contains("## Comparison Summary")
                .contains("## Report Pairing")
                .contains("Current candidate: this promoted report measures starter `" + projectVersion + "`")
                .contains("Published baseline: the paired baseline report path is `reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                        + baselineVersion + "/release-jmh.md`")
                .contains("Numeric rows in this promoted report are current-candidate `" + projectVersion + "` rows")
                .doesNotContain("/home/");

        assertThat(performanceSummaryDocs)
                .contains("## Methodology First")
                .contains("Quick benchmark output is smoke-only")
                .contains("Raw `WebClient`")
                .contains("Spring HTTP Interface")
                .contains("Starter default path")
                .contains("Starter optional features")
                .contains("Starter error mapping")
                .contains("`Get No Body`")
                .contains("`Post Json`")
                .contains("Proxy dispatch")
                .contains("metadata and request-plan lookup")
                .contains("Annotation argument resolution")
                .contains("Diagnostics hook checks")
                .contains("Resilience wrapper selection")
                .contains("Response envelope handling")
                .contains("starter-only error-mapping overhead")
                .contains("starter `" + projectVersion + "`")
                .contains("Current-vs-baseline comparisons should use the paired report paths")
                .contains("published-starter-" + baselineVersion + "/release-jmh.md")
                .contains("Resolve the published baseline artifacts before promoting a comparison")
                .contains("Performance Troubleshooting")
                .contains("25-performance-troubleshooting.md")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw `WebClient`");

        assertThat(benchmarkConsumerDocs)
                .startsWith("# Benchmark Consumer Examples")
                .contains("[benchmark methodology](22-benchmarks.md#methodology-and-limits)")
                .contains("[2.10.0 promoted report](benchmark-report-" + projectVersion + ".md)")
                .contains("## Equivalent Success Path")
                .contains("## Raw WebClient")
                .contains("webClient.get()")
                .contains(".queryParam(\"expand\", \"summary\")")
                .contains(".header(\"X-Tenant\", \"benchmark\")")
                .contains("## Spring HTTP Interface")
                .contains("@HttpExchange")
                .contains("@GetExchange(\"/users/{id}\")")
                .contains("## Starter Interface")
                .contains("@ReactiveHttpClient(name = \"benchmark-starter\")")
                .contains("@GET(\"/users/{id}\")")
                .contains("Same local loopback server and base URL")
                .contains("Same HTTP method, path variable, query parameter, and request header")
                .contains("Same response-body type and terminal consumption")
                .contains("## Starter-Only Rows")
                .contains("Optional feature rows are starter-only unless the baseline client performs the")
                .contains("same extra work")
                .contains("Problem Detail rows are also starter-only unless the baseline installs an")
                .contains("equivalent `application/problem+json` mapper")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw WebClient");

        assertThat(performanceTroubleshootingDocs)
                .startsWith("# Performance Troubleshooting")
                .contains("[Benchmarks](22-benchmarks.md)")
                .contains("[Performance Summary](23-performance-summary.md)")
                .contains("[Observability](08-observability.md)")
                .contains("[Exchange Logging](13-exchange-logging.md)")
                .contains("[Lifecycle Hooks](19-lifecycle-hooks.md)")
                .contains("## Locate the Time")
                .contains("Starter client abstraction overhead")
                .contains("Downstream service latency")
                .contains("Network latency")
                .contains("Application serialization and body processing")
                .contains("## Inspect Metadata First")
                .contains("metadata-only")
                .contains("METADATA_ONLY")
                .contains("## Use Observability Signals")
                .contains("Keep tag cardinality bounded")
                .contains("## Read Lifecycle Attempts Carefully")
                .contains("logical subscription attempts")
                .contains("## Check Timeout Source")
                .contains("Timeout diagnostics")
                .contains("lifecycle hooks and observer events do not expose response\n  headers")
                .contains("## Account for Body Size")
                .contains("JSON request bodies")
                .contains("Error body capture is bounded")
                .contains("Streaming responses")
                .contains("## Compare Workload Shape")
                .contains("Does the baseline client perform the same optional work?")
                .contains("## Investigation Checklist")
                .doesNotContain("near zero overhead")
                .doesNotContain("always faster")
                .doesNotContain("same performance as raw WebClient");

        assertThat(releaseCompatibilityDocs)
                .contains("promote the release-quality report into")
                .contains("docs/benchmark-report-<version>.md")
                .contains("do not link generated `target/` reports directly")
                .contains("current candidate and published-baseline reports at the distinct paths")
                .contains("resolve the listed baseline artifacts before report\npromotion")
                .doesNotContain("reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md` in");

        List<String> invalidReportLinks = new ArrayList<>();
        List<String> promotedReportLinks = new ArrayList<>();
        try (Stream<Path> files = publicMarkdownFiles(root)) {
            for (Path markdown : files.toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(markdown));
                while (matcher.find()) {
                    String target = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
                    if (target.contains("benchmark-reports/") || target.contains("smoke-only-jmh.md")) {
                        invalidReportLinks.add(root.relativize(markdown) + " -> " + target);
                    }
                    if (target.contains("benchmark-report-")) {
                        promotedReportLinks.add(target);
                    }
                }
            }
        }

        assertThat(invalidReportLinks)
                .as("public docs must link promoted release-quality benchmark reports, not target or smoke reports")
                .isEmpty();
        assertThat(promotedReportLinks)
                .as("public docs should link the promoted current-version benchmark report")
                .contains("docs/benchmark-report-" + projectVersion + ".md",
                        "benchmark-report-" + projectVersion + ".md");
    }

    @Test
    void promotedBenchmarkReportVersionsMatchReleaseDocumentation() throws Exception {
        Path root = projectRoot();
        String projectVersion = projectVersion(root.resolve("pom.xml"));
        Path currentReport = root.resolve("docs/benchmark-report-" + projectVersion + ".md");

        assertPromotedReportMetadata(currentReport, projectVersion);

        assertCurrentBenchmarkReportReferences(root.resolve("README.md"), projectVersion);
        assertCurrentBenchmarkReportReferences(root.resolve("docs/22-benchmarks.md"), projectVersion);
        assertCurrentBenchmarkReportReferences(root.resolve("docs/23-performance-summary.md"), projectVersion);
        assertCurrentBenchmarkReportReferences(root.resolve("docs/24-benchmark-consumer-examples.md"), projectVersion);

        String changelog = Files.readString(root.resolve("CHANGELOG.md"));
        String releaseSection = changelogSection(changelog, projectVersion);
        assertThat(releaseSection)
                .contains("[Benchmark Report " + projectVersion + "](docs/benchmark-report-" + projectVersion + ".md)");
        assertBenchmarkReportReferences("CHANGELOG.md release " + projectVersion, releaseSection, projectVersion);

        try (Stream<Path> reports = Files.list(root.resolve("docs"))) {
            for (Path report : reports
                    .filter(path -> path.getFileName().toString().matches("benchmark-report-\\d+\\.\\d+\\.\\d+\\.md"))
                    .toList()) {
                String fileName = report.getFileName().toString();
                String reportVersion = fileName.substring("benchmark-report-".length(),
                        fileName.length() - ".md".length());
                assertPromotedReportMetadata(report, reportVersion);
            }
        }
    }

    @Test
    void generatedConfigurationReferenceMatchesMetadata() throws IOException {
        Path reference = projectRoot().resolve("docs/configuration-properties.md");

        assertThat(Files.readString(reference))
                .isEqualTo(configurationReferenceMarkdown(configurationMetadata(projectRoot())));
    }

    @Test
    void releaseEvidenceManifestIsGeneratedUnderTarget() throws Exception {
        Path root = projectRoot();
        Path manifest = root.resolve("target/release-evidence/reactive-http-client-release-evidence.json");
        Path benchmarkEvidenceSnippet = root.resolve("target/release-evidence/reactive-http-client-benchmark-evidence.md");
        String pomXml = Files.readString(root.resolve("pom.xml"));
        Files.createDirectories(manifest.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(manifest.toFile(), releaseEvidenceManifest(root.resolve("pom.xml")));

        JsonNode generated = OBJECT_MAPPER.readTree(manifest.toFile());
        Files.writeString(benchmarkEvidenceSnippet, releaseNoteBenchmarkEvidenceMarkdown(generated));
        String benchmarkEvidenceMarkdown = Files.readString(benchmarkEvidenceSnippet);

        assertThat(manifest.normalize()).startsWith(root.resolve("target"));
        assertThat(benchmarkEvidenceSnippet.normalize()).startsWith(root.resolve("target"));
        assertThat(generated.path("projectVersion").asText()).isEqualTo(projectVersion(root.resolve("pom.xml")));
        assertThat(generated.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(generated.path("apiCompatibilityBaselineMatchesProjectVersion").asBoolean()).isFalse();
        JsonNode readiness = generated.path("readiness");
        assertThat(readiness.path("projectVersion").asText()).isEqualTo(generated.path("projectVersion").asText());
        assertThat(readiness.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(generated.path("apiCompatibilityBaselineVersion").asText());
        assertThat(readiness.path("apiCompatibilityBaselineMatchesProjectVersion").asBoolean()).isFalse();
        assertThat(readiness.path("generatedTestEvidence").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("manualReleaseEvidence").path("status").asText()).isEqualTo("pending");
        List<String> pendingReleaseCommands = streamText(readiness.path("manualReleaseEvidence").path("pendingCommands"));
        assertThat(pendingReleaseCommands).contains("mvn -Papi-compatibility -DskipTests verify");
        assertThat(pendingReleaseCommands)
                .anySatisfy(command -> assertThat(command)
                        .contains("benchmark-release")
                        .contains("benchmark.commit=$(git rev-parse --short HEAD)"));
        assertThat(readiness.path("manualBenchmarkEvidence").path("status").asText()).isEqualTo("pending");
        List<String> pendingBenchmarkCommands = streamText(readiness.path("manualBenchmarkEvidence").path("pendingCommands"));
        assertThat(pendingBenchmarkCommands)
                .contains("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify");
        assertThat(pendingBenchmarkCommands)
                .anySatisfy(command -> assertThat(command)
                        .contains("benchmark-release")
                        .contains("benchmark.commit=$(git rev-parse --short HEAD)"));
        assertThat(readiness.path("manualCompatibilityEvidence").path("status").asText()).isEqualTo("pending");
        assertThat(readiness.path("manualCompatibilityEvidence").path("pendingCommands"))
                .extracting(JsonNode::asText)
                .containsExactly("mvn -Papi-compatibility -DskipTests verify",
                        "bash scripts/verify-api-compatibility-fixtures.sh");
        assertThat(readiness.path("promotedBenchmarkReport").path("path").asText())
                .isEqualTo("docs/benchmark-report-" + generated.path("projectVersion").asText() + ".md");
        assertThat(readiness.path("promotedBenchmarkReport").path("status").asText()).isEqualTo("present");
        assertThat(readiness.path("configurationReference").path("status").asText()).isEqualTo("current");
        assertThat(readiness.path("markdownLinks").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("staleBenchmarkReportLinks").path("status").asText()).isEqualTo("pass");
        assertThat(readiness.path("releaseEvidenceDirectory").asText()).isEqualTo("target/release-evidence/");
        assertThat(readiness.path("targetOnlyEvidence").path("sourceControlled").asBoolean()).isFalse();
        assertThat(readiness.path("targetOnlyEvidence").path("commitGeneratedEvidence").asBoolean()).isFalse();
        assertThat(generated.path("benchmarkDependencyManagement").path("springBootVersion").asText())
                .isEqualTo(pomProperty(pomXml, "spring-boot.version"));
        assertThat(generated.path("benchmarkDependencyManagement").path("reactorNettyVersionSource").asText())
                .contains("spring-boot-dependencies");
        assertThat(generated.path("publishedBaselineArtifacts"))
                .extracting(artifact -> artifact.path("artifact").asText())
                .containsExactly(
                        "io.github.huynhngochuyhoang:reactive-http-client-starter:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"),
                        "io.github.huynhngochuyhoang:reactive-http-client-test:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"),
                        "io.github.huynhngochuyhoang:reactive-http-client-otel:"
                                + pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(generated.path("publishedBaselineArtifacts"))
                .extracting(artifact -> artifact.path("status").asText())
                .containsOnly("pending");
        assertThat(generated.path("checks"))
                .extracting(check -> check.path("command").asText())
                .containsExactly(
                        "mvn test",
                        "mvn -Papi-compatibility -DskipTests verify",
                        "bash scripts/verify-api-compatibility-fixtures.sh",
                        "git diff --check",
                        "mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package",
                        "mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify",
                        "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)");
        JsonNode benchmarkEvidence = generated.path("benchmarkEvidence");
        assertThat(benchmarkEvidence.path("manualOrProfileGated").asBoolean()).isTrue();
        assertThat(benchmarkEvidence.path("currentWorkspaceCommand").asText())
                .contains("benchmark-release")
                .contains("-am verify");
        assertThat(benchmarkEvidence.path("publishedStarterCommand").asText())
                .contains("-Pbenchmarks,benchmark-release,benchmark-published-baseline")
                .contains("-Dbenchmark.starter.version=" + pomProperty(pomXml, "api.compatibility.baseline.version"))
                .contains(" clean verify ")
                .doesNotContain(" -am ");
        assertThat(benchmarkEvidence.path("reportDirectory").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/");
        assertThat(benchmarkEvidence.path("releaseReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        assertThat(benchmarkEvidence.path("publishedStarterReleaseReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                        + pomProperty(pomXml, "api.compatibility.baseline.version") + "/release-jmh.md");
        assertThat(benchmarkEvidence.path("promotedReport").asText())
                .isEqualTo("docs/benchmark-report-" + generated.path("projectVersion").asText() + ".md");
        assertThat(benchmarkEvidence.path("currentCandidateReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        assertThat(benchmarkEvidence.path("publishedBaselineReport").asText())
                .isEqualTo("reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                        + pomProperty(pomXml, "api.compatibility.baseline.version") + "/release-jmh.md");
        JsonNode comparisonPair = benchmarkEvidence.path("comparisonPair");
        assertThat(comparisonPair.path("currentStarterVersion").asText())
                .isEqualTo(generated.path("projectVersion").asText());
        assertThat(comparisonPair.path("publishedBaselineStarterVersion").asText())
                .isEqualTo(pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(comparisonPair.path("currentCandidateReport").asText())
                .isEqualTo(benchmarkEvidence.path("currentCandidateReport").asText());
        assertThat(comparisonPair.path("publishedBaselineReport").asText())
                .isEqualTo(benchmarkEvidence.path("publishedBaselineReport").asText());
        assertThat(comparisonPair.path("reportsSharePath").asBoolean()).isFalse();
        assertThat(comparisonPair.path("baselineArtifactsMustResolveBeforePromotion").asBoolean()).isTrue();
        assertThat(benchmarkEvidence.path("releaseNoteScenarioNames"))
                .extracting(JsonNode::asText)
                .containsExactly(
                        "Get No Body",
                        "Get Path Query Header",
                        "Post Json",
                        "Response Entity",
                        "Client Error Small Body",
                        "Server Error Small Body",
                        "Problem Detail Small Body");
        assertThat(benchmarkEvidence.path("releaseNoteScenarioRerunCommand").asText())
                .contains("benchmark-release")
                .contains("benchmark.include")
                .contains("clientSideOverhead.*")
                .contains("GetNoBody")
                .contains("GetPathQueryHeader")
                .contains("PostJson")
                .contains("ResponseEntity")
                .contains("ClientErrorSmallBody")
                .contains("ServerErrorSmallBody")
                .contains("starterErrorMappingProblemDetailSmallBody")
                .contains("target/benchmark-reports/release-note")
                .doesNotContain("reactive-http-client-benchmarks/target/benchmarks.jar");
        assertThat(benchmarkEvidence.path("requiresPromotedReportForPerformanceClaims").asBoolean()).isTrue();
        assertThat(benchmarkEvidence.path("pendingEvidenceCanSupportPerformanceClaims").asBoolean()).isFalse();
        JsonNode reviewTriggers = benchmarkEvidence.path("reviewTriggers");
        assertThat(reviewTriggers.path("hardGate").asBoolean()).isFalse();
        assertThat(reviewTriggers.path("normalCiRunsBenchmarks").asBoolean()).isFalse();
        assertThat(reviewTriggers.path("rerunBeforeClaimingTrend").asBoolean()).isTrue();
        assertThat(reviewTriggers.path("latencyPercentChange").asInt()).isEqualTo(20);
        assertThat(reviewTriggers.path("allocationPercentChange").asInt()).isEqualTo(15);
        assertThat(reviewTriggers.path("allocationBytesPerOperationChange").asInt()).isEqualTo(4096);
        assertThat(reviewTriggers.path("optionalFeaturePercentChange").asInt()).isEqualTo(25);
        assertThat(reviewTriggers.path("dimensions"))
                .extracting(JsonNode::asText)
                .containsExactly("average time", "p50", "p95", "p99", "allocation per operation");
        assertThat(reviewTriggers.path("instruction").asText())
                .contains("review trigger")
                .contains("not an automatic release blocker");
        assertThat(benchmarkEvidence.path("refreshRequiredWhen"))
                .extracting(JsonNode::asText)
                .containsExactly(
                        "request construction changes",
                        "observability changes",
                        "resilience wrapping changes",
                        "transport or client-builder changes",
                        "public performance claims");
        assertThat(benchmarkEvidenceMarkdown)
                .startsWith("Benchmark evidence:\n")
                .contains("Promoted report: [Benchmark Report " + generated.path("projectVersion").asText()
                        + "](docs/benchmark-report-" + generated.path("projectVersion").asText() + ".md)")
                .contains("Current candidate command: `" + benchmarkEvidence.path("currentWorkspaceCommand").asText() + "`")
                .contains("Published baseline command: `" + benchmarkEvidence.path("publishedStarterCommand").asText() + "`")
                .contains("Current candidate report: `" + benchmarkEvidence.path("currentCandidateReport").asText() + "`")
                .contains("Published baseline report: `" + benchmarkEvidence.path("publishedBaselineReport").asText() + "`")
                .contains("Scenarios cited: `Get No Body`, `Get Path Query Header`, `Post Json`, `Response Entity`, `Client Error Small Body`, `Server Error Small Body`, `Problem Detail Small Body`")
                .contains("Paste this block only after the promoted report exists")
                .contains("starter `" + generated.path("projectVersion").asText() + "`")
                .contains("published baseline `" + generated.path("apiCompatibilityBaselineVersion").asText() + "`")
                .doesNotContain("smoke-only-jmh")
                .doesNotContain("smokeReport");
    }

    private static List<String> streamText(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static String releaseNoteBenchmarkEvidenceMarkdown(JsonNode manifest) {
        JsonNode evidence = manifest.path("benchmarkEvidence");
        String projectVersion = manifest.path("projectVersion").asText();
        String baselineVersion = manifest.path("apiCompatibilityBaselineVersion").asText();
        String scenarios = inlineCodeList(evidence.path("releaseNoteScenarioNames"));

        return "Benchmark evidence:\n"
                + "- Promoted report: [Benchmark Report " + projectVersion + "]("
                + evidence.path("promotedReport").asText() + ")\n"
                + "- Current candidate command: `" + evidence.path("currentWorkspaceCommand").asText() + "`\n"
                + "- Published baseline command: `" + evidence.path("publishedStarterCommand").asText() + "`\n"
                + "- Current candidate report: `" + evidence.path("currentCandidateReport").asText() + "`\n"
                + "- Published baseline report: `" + evidence.path("publishedBaselineReport").asText() + "`\n"
                + "- Scenarios cited: " + scenarios + "\n"
                + "- Note: Paste this block only after the promoted report exists for starter `"
                + projectVersion + "` and published baseline `" + baselineVersion + "`.\n";
    }

    private static String inlineCodeList(JsonNode values) {
        List<String> rendered = new ArrayList<>();
        values.forEach(value -> rendered.add("`" + value.asText() + "`"));
        return String.join(", ", rendered);
    }

    private static void assertPromotedReportMetadata(Path report, String expectedVersion) throws IOException {
        String reportDocs = Files.readString(report);

        assertThat(report).exists();
        assertThat(report.getFileName().toString())
                .as("promoted benchmark report filename")
                .isEqualTo("benchmark-report-" + expectedVersion + ".md");
        assertThat(reportDocs)
                .contains("- Report version: `" + expectedVersion + "`.")
                .contains("- Starter version under test: `" + expectedVersion + "`.")
                .contains("| `projectVersion` | " + expectedVersion + " |")
                .contains("| `starterVersion` | " + expectedVersion + " |");
    }

    private static void assertCurrentBenchmarkReportReferences(Path markdown, String projectVersion) throws IOException {
        assertBenchmarkReportReferences(markdown.toString(), Files.readString(markdown), projectVersion);
    }

    private static void assertBenchmarkReportReferences(String source, String text, String projectVersion) {
        List<String> versions = benchmarkReportVersions(text);

        assertThat(versions)
                .as(source + " benchmark report references")
                .isNotEmpty()
                .containsOnly(projectVersion);
    }

    private static List<String> benchmarkReportVersions(String text) {
        Matcher matcher = Pattern.compile("benchmark-report-(\\d+\\.\\d+\\.\\d+)\\.md").matcher(text);
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        return versions;
    }

    private static List<String> staleBenchmarkReportReferences(Path root, String projectVersion) throws IOException {
        List<Path> releaseDocs = List.of(
                root.resolve("README.md"),
                root.resolve("CHANGELOG.md"),
                root.resolve("docs/22-benchmarks.md"),
                root.resolve("docs/23-performance-summary.md"),
                root.resolve("docs/24-benchmark-consumer-examples.md"));
        Pattern pattern = Pattern.compile("benchmark-report-(\\d+\\.\\d+\\.\\d+)\\.md");
        List<String> stale = new ArrayList<>();
        for (Path releaseDoc : releaseDocs) {
            Matcher matcher = pattern.matcher(Files.readString(releaseDoc));
            while (matcher.find()) {
                if (!projectVersion.equals(matcher.group(1))) {
                    stale.add(root.relativize(releaseDoc) + " -> " + matcher.group());
                }
            }
        }
        return stale;
    }

    private static String changelogSection(String changelog, String version) {
        String heading = "## [" + version + "]";
        int start = changelog.indexOf(heading);
        if (start < 0) {
            throw new IllegalStateException("Missing changelog section for " + version);
        }
        int end = changelog.indexOf("\n## [", start + heading.length());
        return end < 0 ? changelog.substring(start) : changelog.substring(start, end);
    }

    private static List<String> brokenLocalMarkdownLinks(Path root) throws IOException {
        List<String> brokenLinks = new ArrayList<>();
        try (Stream<Path> files = markdownFiles(root)) {
            for (Path markdown : files.toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(markdownWithoutFencedCode(Files.readString(markdown)));
                while (matcher.find()) {
                    String target = matcher.group(1);
                    if (isExternal(target)) {
                        continue;
                    }
                    String[] parts = target.split("#", 2);
                    String pathOnly = parts[0];
                    String anchor = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                    Path resolved = pathOnly.isBlank()
                            ? markdown
                            : markdown.getParent()
                                    .resolve(URLDecoder.decode(pathOnly, StandardCharsets.UTF_8))
                                    .normalize();
                    if (!Files.exists(resolved)) {
                        brokenLinks.add(root.relativize(markdown) + " -> " + target);
                        continue;
                    }
                    if (!anchor.isBlank() && !markdownAnchors(resolved).contains(anchor)) {
                        brokenLinks.add(root.relativize(markdown) + " -> " + target + " (missing anchor)");
                    }
                }
            }
        }
        return brokenLinks;
    }

    private static Stream<Path> markdownFiles(Path root) throws IOException {
        return Stream.of(
                        Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")),
                        Files.walk(root.resolve("docs")),
                        Files.walk(root.resolve("roadmaps")))
                .flatMap(stream -> stream)
                .filter(path -> path.toString().endsWith(".md"));
    }

    private static Stream<Path> publicMarkdownFiles(Path root) throws IOException {
        return Stream.concat(
                        Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")),
                        Files.walk(root.resolve("docs")))
                .filter(path -> path.toString().endsWith(".md"));
    }

    private static Set<String> markdownAnchors(Path markdown) throws IOException {
        Set<String> anchors = new HashSet<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (String line : Files.readAllLines(markdown)) {
            if (!line.startsWith("#")) {
                continue;
            }
            String heading = line.replaceFirst("^#{1,6}\\s+", "").replaceFirst("\\s+#*$", "");
            if (heading.isBlank()) {
                continue;
            }
            String anchor = markdownAnchor(heading);
            int duplicate = occurrences.merge(anchor, 1, Integer::sum);
            anchors.add(duplicate == 1 ? anchor : anchor + "-" + (duplicate - 1));
        }
        return anchors;
    }

    private static String markdownAnchor(String heading) {
        String normalized = heading.toLowerCase(Locale.ROOT);
        StringBuilder anchor = new StringBuilder();
        boolean previousDash = false;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                anchor.append(current);
                previousDash = false;
            }
            else if (Character.isWhitespace(current) || current == '-') {
                if (!previousDash && anchor.length() > 0) {
                    anchor.append('-');
                    previousDash = true;
                }
            }
        }
        while (anchor.length() > 0 && anchor.charAt(anchor.length() - 1) == '-') {
            anchor.setLength(anchor.length() - 1);
        }
        return anchor.toString();
    }

    private static String markdownWithoutFencedCode(String markdown) {
        StringBuilder out = new StringBuilder(markdown.length());
        boolean fenced = false;
        for (String line : markdown.split("\\R", -1)) {
            if (line.startsWith("```")) {
                fenced = !fenced;
                out.append('\n');
                continue;
            }
            if (!fenced) {
                out.append(line);
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static boolean isExternal(String target) {
        return target.startsWith("http://")
                || target.startsWith("https://")
                || target.startsWith("mailto:");
    }

    private static void assertVersionSnippets(Path markdown, String projectVersion) throws IOException {
        Matcher matcher = PROJECT_VERSION_SNIPPET.matcher(Files.readString(markdown));
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }

        assertThat(versions)
                .as("%s reactive-http-client dependency snippets", markdown.getFileName())
                .isNotEmpty()
                .containsOnly(projectVersion);
    }

    private static String dependencyBlock(String pom, String artifactId) {
        Matcher matcher = Pattern.compile("<dependency>\\s*(?:(?!</dependency>).)*?<artifactId>"
                        + Pattern.quote(artifactId) + "</artifactId>(?:(?!</dependency>).)*?</dependency>",
                Pattern.DOTALL)
                .matcher(pom);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing benchmark dependency: " + artifactId);
        }
        return matcher.group();
    }

    private static String projectVersion(Path pom) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(pom.toFile());
        return document.getElementsByTagName("version").item(0).getTextContent();
    }

    private static Map<String, Object> releaseEvidenceManifest(Path pom) throws Exception {
        String pomXml = Files.readString(pom);
        String projectVersion = projectVersion(pom);
        String baselineVersion = pomProperty(pomXml, "api.compatibility.baseline.version");
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("projectVersion", projectVersion);
        manifest.put("apiCompatibilityBaselineVersion", baselineVersion);
        manifest.put("apiCompatibilityBaselineMatchesProjectVersion", projectVersion.equals(baselineVersion));
        manifest.put("javaVersion", System.getProperty("java.version"));
        manifest.put("javaBaseline", pomProperty(pomXml, "java.version"));
        manifest.put("springBootBaseline", pomProperty(pomXml, "spring-boot.version"));
        Map<String, Object> benchmarkEvidence = benchmarkEvidence(projectVersion, baselineVersion);
        List<Map<String, String>> checks = List.of(
                check("mvn test", "pass", "Generated by DocumentationReleaseArtifactTest during the current test run."),
                check("mvn -Papi-compatibility -DskipTests verify", "pending", "Run before release."),
                check("bash scripts/verify-api-compatibility-fixtures.sh", "pending", "Run before release."),
                check("git diff --check", "pending", "Run before release."),
                check("mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package", "pending",
                        "Lightweight benchmark compile check; does not run JMH."),
                check("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify", "pending",
                        "Harness smoke only; do not publish these numbers."),
                check("mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)", "pending",
                        "Run when request-path behavior changed or release notes make performance claims."));
        manifest.put("readiness", releaseReadiness(pom.getParent(), projectVersion, baselineVersion, benchmarkEvidence, checks));
        manifest.put("benchmarkDependencyManagement", benchmarkDependencyManagement(pomXml));
        manifest.put("publishedBaselineArtifacts", publishedBaselineArtifacts(baselineVersion));
        manifest.put("benchmarkEvidence", benchmarkEvidence);
        manifest.put("checks", checks);
        return manifest;
    }

    private static Map<String, Object> releaseReadiness(Path root,
                                                        String projectVersion,
                                                        String baselineVersion,
                                                        Map<String, Object> benchmarkEvidence,
                                                        List<Map<String, String>> checks) throws IOException {
        String promotedReport = (String) benchmarkEvidence.get("promotedReport");
        List<String> pendingManualCommands = checks.stream()
                .filter(check -> "pending".equals(check.get("status")))
                .map(check -> check.get("command"))
                .toList();
        List<String> pendingBenchmarkCommands = pendingManualCommands.stream()
                .filter(command -> command.contains("benchmark"))
                .toList();
        List<String> pendingCompatibilityCommands = pendingManualCommands.stream()
                .filter(command -> command.contains("api-compatibility")
                        || command.contains("verify-api-compatibility-fixtures"))
                .toList();
        boolean configurationReferenceCurrent = Files.readString(root.resolve("docs/configuration-properties.md"))
                .equals(configurationReferenceMarkdown(configurationMetadata(root)));
        List<String> brokenLinks = brokenLocalMarkdownLinks(root);
        List<String> staleBenchmarkLinks = staleBenchmarkReportReferences(root, projectVersion);

        LinkedHashMap<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("projectVersion", projectVersion);
        readiness.put("apiCompatibilityBaselineVersion", baselineVersion);
        readiness.put("apiCompatibilityBaselineMatchesProjectVersion", projectVersion.equals(baselineVersion));
        readiness.put("generatedTestEvidence", readinessStatus("pass",
                "Generated by DocumentationReleaseArtifactTest in target/release-evidence/."));
        readiness.put("manualReleaseEvidence", readinessManualStatus(pendingManualCommands));
        readiness.put("manualBenchmarkEvidence", readinessManualStatus(pendingBenchmarkCommands));
        readiness.put("manualCompatibilityEvidence", readinessManualStatus(pendingCompatibilityCommands));
        readiness.put("promotedBenchmarkReport", readinessPathStatus(promotedReport,
                Files.exists(root.resolve(promotedReport)) ? "present" : "missing"));
        readiness.put("configurationReference", readinessPathStatus("docs/configuration-properties.md",
                configurationReferenceCurrent ? "current" : "stale"));
        readiness.put("markdownLinks", readinessListStatus(brokenLinks.isEmpty() ? "pass" : "fail", "brokenLinks", brokenLinks));
        readiness.put("staleBenchmarkReportLinks", readinessListStatus(
                staleBenchmarkLinks.isEmpty() ? "pass" : "fail", "references", staleBenchmarkLinks));
        readiness.put("releaseEvidenceDirectory", "target/release-evidence/");
        readiness.put("targetOnlyEvidence", Map.of(
                "directory", "target/release-evidence/",
                "sourceControlled", false,
                "commitGeneratedEvidence", false));
        return readiness;
    }

    private static Map<String, Object> readinessStatus(String status, String note) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put("note", note);
        return value;
    }

    private static Map<String, Object> readinessManualStatus(List<String> pendingCommands) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("status", pendingCommands.isEmpty() ? "complete" : "pending");
        value.put("pendingCommands", pendingCommands);
        return value;
    }

    private static Map<String, Object> readinessPathStatus(String path, String status) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("path", path);
        value.put("status", status);
        return value;
    }

    private static Map<String, Object> readinessListStatus(String status, String field, List<String> values) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put(field, values);
        return value;
    }

    private static Map<String, Object> benchmarkEvidence(String projectVersion, String baselineVersion) {
        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("manualOrProfileGated", true);
        evidence.put("currentWorkspaceCommand",
                "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)");
        evidence.put("publishedStarterCommand",
                "mvn -Pbenchmarks,benchmark-release,benchmark-published-baseline -pl reactive-http-client-benchmarks clean verify -Dbenchmark.starter.version="
                        + baselineVersion + " -Dbenchmark.commit=" + baselineVersion);
        evidence.put("reportDirectory", "reactive-http-client-benchmarks/target/benchmark-reports/");
        evidence.put("smokeReport", "reactive-http-client-benchmarks/target/benchmark-reports/smoke-only-jmh.md");
        evidence.put("releaseReport", "reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        evidence.put("publishedStarterReleaseReport", "reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                + baselineVersion + "/release-jmh.md");
        evidence.put("promotedReport", "docs/benchmark-report-" + projectVersion + ".md");
        evidence.put("currentCandidateReport", "reactive-http-client-benchmarks/target/benchmark-reports/release-jmh.md");
        evidence.put("publishedBaselineReport", "reactive-http-client-benchmarks/target/benchmark-reports/published-starter-"
                + baselineVersion + "/release-jmh.md");
        evidence.put("comparisonPair", comparisonPair(projectVersion, baselineVersion,
                (String) evidence.get("currentCandidateReport"),
                (String) evidence.get("publishedBaselineReport"),
                (String) evidence.get("promotedReport")));
        evidence.put("releaseNoteScenarioNames", List.of(
                "Get No Body",
                "Get Path Query Header",
                "Post Json",
                "Response Entity",
                "Client Error Small Body",
                "Server Error Small Body",
                "Problem Detail Small Body"));
        evidence.put("releaseNoteScenarioRerunCommand",
                "mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify "
                        + "-Dbenchmark.commit=$(git rev-parse --short HEAD) "
                        + "-Dbenchmark.include='.*(clientSideOverhead.*(GetNoBody|"
                        + "GetPathQueryHeader|PostJson|ResponseEntity|"
                        + "ClientErrorSmallBody|ServerErrorSmallBody)|"
                        + "starterErrorMappingProblemDetailSmallBody).*' "
                        + "-Dbenchmark.result.dir=target/benchmark-reports/release-note");
        evidence.put("requiresPromotedReportForPerformanceClaims", true);
        evidence.put("pendingEvidenceCanSupportPerformanceClaims", false);
        evidence.put("reviewTriggers", benchmarkReviewTriggers());
        evidence.put("refreshRequiredWhen", List.of(
                "request construction changes",
                "observability changes",
                "resilience wrapping changes",
                "transport or client-builder changes",
                "public performance claims"));
        evidence.put("releaseNotesInstruction",
                "Attach or link the promoted report when publishing performance claims; never publish smoke-only numbers.");
        return evidence;
    }

    private static Map<String, Object> benchmarkReviewTriggers() {
        LinkedHashMap<String, Object> triggers = new LinkedHashMap<>();
        triggers.put("hardGate", false);
        triggers.put("normalCiRunsBenchmarks", false);
        triggers.put("rerunBeforeClaimingTrend", true);
        triggers.put("latencyPercentChange", 20);
        triggers.put("allocationPercentChange", 15);
        triggers.put("allocationBytesPerOperationChange", 4096);
        triggers.put("optionalFeaturePercentChange", 25);
        triggers.put("dimensions", List.of("average time", "p50", "p95", "p99", "allocation per operation"));
        triggers.put("instruction",
                "Treat threshold crossings as review triggers, not an automatic release blocker; rerun current and baseline reports on the same machine before publishing performance claims.");
        return triggers;
    }

    private static Map<String, Object> comparisonPair(String projectVersion, String baselineVersion,
                                                      String currentReport, String baselineReport,
                                                      String promotedReport) {
        LinkedHashMap<String, Object> pair = new LinkedHashMap<>();
        pair.put("currentStarterVersion", projectVersion);
        pair.put("publishedBaselineStarterVersion", baselineVersion);
        pair.put("currentCandidateReport", currentReport);
        pair.put("publishedBaselineReport", baselineReport);
        pair.put("promotedReport", promotedReport);
        pair.put("reportsSharePath", currentReport.equals(baselineReport));
        pair.put("baselineArtifactsMustResolveBeforePromotion", true);
        pair.put("comparisonNote", "Compare starter " + projectVersion + " current-candidate report with starter "
                + baselineVersion + " published-baseline report; do not reuse one report for both labels.");
        return pair;
    }

    private static Map<String, Object> benchmarkDependencyManagement(String pomXml) {
        LinkedHashMap<String, Object> dependencyManagement = new LinkedHashMap<>();
        dependencyManagement.put("source", "root spring-boot-dependencies BOM");
        dependencyManagement.put("springBootVersion", pomProperty(pomXml, "spring-boot.version"));
        dependencyManagement.put("reactorNettyVersionSource", "resolved from spring-boot-dependencies");
        dependencyManagement.put("benchmarkModuleUsesStarterParent", true);
        return dependencyManagement;
    }

    private static List<Map<String, String>> publishedBaselineArtifacts(String baselineVersion) {
        return List.of(
                baselineArtifact("reactive-http-client-starter", baselineVersion),
                baselineArtifact("reactive-http-client-test", baselineVersion),
                baselineArtifact("reactive-http-client-otel", baselineVersion));
    }

    private static Map<String, String> baselineArtifact(String artifactId, String baselineVersion) {
        String gav = "io.github.huynhngochuyhoang:" + artifactId + ":" + baselineVersion;
        LinkedHashMap<String, String> artifact = new LinkedHashMap<>();
        artifact.put("artifact", gav);
        artifact.put("status", "pending");
        artifact.put("resolutionCommand", "mvn dependency:get -Dartifact=" + gav);
        artifact.put("note", "Run before release; unresolved published artifacts are release blockers.");
        return artifact;
    }

    private static Map<String, String> check(String command, String status, String note) {
        LinkedHashMap<String, String> check = new LinkedHashMap<>();
        check.put("command", command);
        check.put("status", status);
        check.put("note", note);
        return check;
    }

    private static String pomProperty(String pomXml, String property) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(property) + ">([^<]+)</" + Pattern.quote(property) + ">")
                .matcher(pomXml);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing Maven property: " + property);
        }
        return matcher.group(1);
    }

    private static List<JsonNode> configurationMetadata(Path root) throws IOException {
        return List.of(
                metadata(root, "reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json"),
                metadata(root, "reactive-http-client-otel/src/main/resources/META-INF/additional-spring-configuration-metadata.json"));
    }

    private static JsonNode metadata(Path root, String path) throws IOException {
        return OBJECT_MAPPER.readTree(root.resolve(path).toFile());
    }

    private static String configurationReferenceMarkdown(List<JsonNode> metadataFiles) throws IOException {
        StringWriter out = new StringWriter();
        out.append("# Configuration Properties\n\n");
        out.append("> Generated from:\n");
        out.append("> - `reactive-http-client-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`\n");
        out.append("> - `reactive-http-client-otel/src/main/resources/META-INF/additional-spring-configuration-metadata.json`\n");
        out.append("> `DocumentationReleaseArtifactTest` fails when this file drifts from metadata.\n\n");
        out.append("| Property | Type | Default | Description | Deprecated |\n");
        out.append("|---|---|---|---|---|\n");

        List<JsonNode> properties = new ArrayList<>();
        for (JsonNode metadata : metadataFiles) {
            metadata.path("properties").forEach(properties::add);
        }
        properties.sort(Comparator.comparing(property -> property.path("name").asText()));

        for (JsonNode property : properties) {
            out.append("| `").append(escapeCell(property.path("name").asText())).append("` ");
            out.append("| `").append(escapeCell(property.path("type").asText())).append("` ");
            out.append("| ").append(escapeCell(defaultValue(property))).append(" ");
            out.append("| ").append(escapeCell(property.path("description").asText())).append(" ");
            out.append("| ").append(escapeCell(deprecation(property))).append(" |\n");
        }
        return out.toString();
    }

    private static String defaultValue(JsonNode property) throws IOException {
        if (!property.has("defaultValue")) {
            return "";
        }
        return "`" + OBJECT_MAPPER.writeValueAsString(property.get("defaultValue")) + "`";
    }

    private static String deprecation(JsonNode property) {
        JsonNode deprecation = property.path("deprecation");
        if (deprecation.isMissingNode()) {
            return "";
        }
        String replacement = deprecation.path("replacement").asText("");
        if (replacement.isBlank()) {
            return deprecation.path("level").asText("warning");
        }
        return deprecation.path("level").asText("warning") + "; replacement: `" + replacement + "`";
    }

    private static String escapeCell(String value) {
        return value
                .replace("\r", "")
                .replace("\n", " ")
                .replace("|", "\\|");
    }

    private static Path projectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(cwd.resolve("README.md")) && Files.isDirectory(cwd.resolve("docs"))) {
            return cwd;
        }
        return cwd.getParent();
    }
}
