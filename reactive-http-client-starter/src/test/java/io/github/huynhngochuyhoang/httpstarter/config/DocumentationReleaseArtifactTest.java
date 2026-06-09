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

    @Test
    void localMarkdownLinksResolve() throws IOException {
        Path root = projectRoot();
        List<String> brokenLinks = new ArrayList<>();

        try (Stream<Path> files = markdownFiles(root)) {
            for (Path markdown : files.toList()) {
                Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(markdown));
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

        assertThat(brokenLinks).as("broken local Markdown links").isEmpty();
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
    void benchmarkModuleUsesStarterDependencyManagement() throws IOException {
        String benchmarkPom = Files.readString(projectRoot().resolve("reactive-http-client-benchmarks/pom.xml"));

        assertThat(benchmarkPom)
                .contains("<benchmark.starter.version>")
                .contains("benchmark.starter.version")
                .contains("benchmark.spring-webflux.artifact")
                .contains("benchmark.reactor-netty.artifact");
        assertThat(dependencyBlock(benchmarkPom, "spring-webflux")).doesNotContain("<version>");
        assertThat(dependencyBlock(benchmarkPom, "reactor-netty-http")).doesNotContain("<version>");
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
        String pomXml = Files.readString(root.resolve("pom.xml"));
        Files.createDirectories(manifest.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(manifest.toFile(), releaseEvidenceManifest(root.resolve("pom.xml")));

        JsonNode generated = OBJECT_MAPPER.readTree(manifest.toFile());

        assertThat(manifest.normalize()).startsWith(root.resolve("target"));
        assertThat(generated.path("projectVersion").asText()).isEqualTo(projectVersion(root.resolve("pom.xml")));
        assertThat(generated.path("apiCompatibilityBaselineVersion").asText())
                .isEqualTo(pomProperty(pomXml, "api.compatibility.baseline.version"));
        assertThat(generated.path("apiCompatibilityBaselineMatchesProjectVersion").asBoolean()).isFalse();
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
        assertThat(benchmarkEvidence.path("refreshRequiredWhen"))
                .extracting(JsonNode::asText)
                .containsExactly(
                        "request construction changes",
                        "observability changes",
                        "resilience wrapping changes",
                        "transport or client-builder changes",
                        "public performance claims");
    }

    private static Stream<Path> markdownFiles(Path root) throws IOException {
        return Stream.of(
                        Stream.of(root.resolve("README.md"), root.resolve("CHANGELOG.md")),
                        Files.walk(root.resolve("docs")),
                        Files.walk(root.resolve("roadmaps")))
                .flatMap(stream -> stream)
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
        manifest.put("benchmarkDependencyManagement", benchmarkDependencyManagement(pomXml));
        manifest.put("publishedBaselineArtifacts", publishedBaselineArtifacts(baselineVersion));
        manifest.put("benchmarkEvidence", benchmarkEvidence(baselineVersion));
        manifest.put("checks", List.of(
                check("mvn test", "pass", "Generated by DocumentationReleaseArtifactTest during the current test run."),
                check("mvn -Papi-compatibility -DskipTests verify", "pending", "Run before release."),
                check("bash scripts/verify-api-compatibility-fixtures.sh", "pending", "Run before release."),
                check("git diff --check", "pending", "Run before release."),
                check("mvn -Pbenchmarks -pl reactive-http-client-benchmarks -am package", "pending",
                        "Lightweight benchmark compile check; does not run JMH."),
                check("mvn -Pbenchmarks,benchmark-smoke -pl reactive-http-client-benchmarks -am verify", "pending",
                        "Harness smoke only; do not publish these numbers."),
                check("mvn -Pbenchmarks,benchmark-release -pl reactive-http-client-benchmarks -am verify -Dbenchmark.commit=$(git rev-parse --short HEAD)", "pending",
                        "Run when request-path behavior changed or release notes make performance claims.")));
        return manifest;
    }

    private static Map<String, Object> benchmarkEvidence(String baselineVersion) {
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
        evidence.put("refreshRequiredWhen", List.of(
                "request construction changes",
                "observability changes",
                "resilience wrapping changes",
                "transport or client-builder changes",
                "public performance claims"));
        evidence.put("releaseNotesInstruction",
                "Attach or link the release report when publishing performance claims; never publish smoke-only numbers.");
        return evidence;
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
